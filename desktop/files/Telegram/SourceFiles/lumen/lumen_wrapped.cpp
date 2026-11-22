/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#include "lumen/lumen_wrapped.h"

#include "apiwrap.h"
#include "base/call_delayed.h"
#include "data/data_chat.h"
#include "data/data_channel.h"
#include "data/data_session.h"
#include "data/data_user.h"
#include "dialogs/dialogs_indexed_list.h"
#include "dialogs/dialogs_main_list.h"
#include "dialogs/dialogs_row.h"
#include "history/history.h"
#include "lang/lang_keys.h"
#include "main/main_session.h"
#include "ui/layers/generic_box.h"
#include "ui/widgets/labels.h"
#include "window/window_session_controller.h"
#include "styles/style_boxes.h"
#include "styles/style_layers.h"

#include <QtCore/QRegularExpression>

namespace Lumen {
namespace {

constexpr auto kMaxChats = 40;
constexpr auto kSamplePerChat = 100;
constexpr auto kTopPeople = 5;
constexpr auto kTopEmoji = 5;
constexpr auto kMinWordLength = 4;
// Pause between requests, so Telegram does not slow the account down.
constexpr auto kRequestPause = crl::time(350);
constexpr auto kNightToHour = 5;

struct Person {
	QString name;
	int count = 0;
};

struct State {
	std::vector<not_null<PeerData*>> peers;
	int index = 0;
	TimeId yearStart = 0;
	int year = 0;
	int total = 0;
	int chats = 0;
	std::vector<Person> top;
	base::flat_map<QString, int> words;
	base::flat_map<QString, int> emoji;
	std::array<int, 24> hours = {};
	int sampled = 0;
};

[[nodiscard]] const base::flat_set<QString> &StopWords() {
	static const auto result = [] {
		auto list = base::flat_set<QString>();
		for (const auto &word : QString::fromUtf8(
				"что это как так вот был была было были меня тебя тебе "
				"если когда чтобы только уже еще ещё очень тоже просто "
				"потом сейчас есть нет там тут надо можно нужно будет "
				"буду него неё нее них который которая которые этот эта "
				"эти того всё все всех себя типа вообще давай ладно хорошо "
				"щас короче кстати почему зачем that this with have what "
				"from your just there they will would").split(' ')) {
			list.emplace(word);
		}
		return list;
	}();
	return result;
}

void Sample(const std::shared_ptr<State> &state, const MTPMessage &message) {
	message.match([&](const MTPDmessage &data) {
		if (data.vdate().v < state->yearStart) {
			return;
		}
		++state->sampled;
		++state->hours[QDateTime::fromSecsSinceEpoch(
			data.vdate().v).time().hour()];
		const auto text = qs(data.vmessage());
		if (text.isEmpty() || text.startsWith('/')) {
			return;
		}
		static const auto kWord = QRegularExpression(u"\\p{L}+"_q);
		auto words = kWord.globalMatch(text.toLower());
		while (words.hasNext()) {
			const auto word = words.next().captured();
			if (word.size() >= kMinWordLength && !StopWords().contains(word)) {
				++state->words[word];
			}
		}
		for (auto i = 0; i < text.size();) {
			const auto code = text.at(i).isHighSurrogate() && i + 1 < text.size()
				? QChar::surrogateToUcs4(text.at(i), text.at(i + 1))
				: char32_t(text.at(i).unicode());
			const auto length = (code > 0xFFFF) ? 2 : 1;
			if ((code >= 0x1F300 && code <= 0x1FAFF)
				|| (code >= 0x2600 && code <= 0x27BF)) {
				++state->emoji[text.mid(i, length)];
			}
			i += length;
		}
	}, [](const auto &) {});
}

[[nodiscard]] QString Chronotype(const std::shared_ptr<State> &state, int busiest) {
	const auto night = std::accumulate(
		state->hours.begin(),
		state->hours.begin() + kNightToHour + 1,
		0);
	const auto percent = state->sampled ? night * 100 / state->sampled : 0;
	return (percent >= 15 || busiest < 5)
		? tr::lng_lumen_wrapped_owl(tr::now)
		: (busiest < 12)
		? tr::lng_lumen_wrapped_lark(tr::now)
		: (busiest < 18)
		? tr::lng_lumen_wrapped_day(tr::now)
		: tr::lng_lumen_wrapped_evening(tr::now);
}

[[nodiscard]] QString Report(const std::shared_ptr<State> &state) {
	auto lines = QStringList();
	lines.push_back(tr::lng_lumen_wrapped_messages(
		tr::now,
		lt_number,
		QLocale().toString(state->total),
		lt_chats,
		QString::number(state->chats)));
	ranges::sort(state->top, ranges::greater(), &Person::count);
	if (!state->top.empty()) {
		lines.push_back(QString());
		lines.push_back(tr::lng_lumen_wrapped_person(tr::now));
		for (auto i = 0; i < int(state->top.size()) && i < kTopPeople; ++i) {
			lines.push_back(u"%1. %2 · %3"_q
				.arg(i + 1)
				.arg(state->top[i].name)
				.arg(QLocale().toString(state->top[i].count)));
		}
	}
	auto bestWord = QString();
	auto bestCount = 0;
	for (const auto &[word, count] : state->words) {
		if (count > bestCount) {
			bestWord = word;
			bestCount = count;
		}
	}
	if (!bestWord.isEmpty()) {
		lines.push_back(QString());
		lines.push_back(tr::lng_lumen_wrapped_word(
			tr::now,
			lt_word,
			bestWord,
			lt_number,
			QString::number(bestCount)));
	}
	auto emoji = std::vector<std::pair<QString, int>>();
	emoji.reserve(state->emoji.size());
	for (const auto &[text, count] : state->emoji) {
		emoji.emplace_back(text, count);
	}
	ranges::sort(emoji, ranges::greater(), &std::pair<QString, int>::second);
	if (!emoji.empty()) {
		auto list = QStringList();
		for (auto i = 0; i < int(emoji.size()) && i < kTopEmoji; ++i) {
			list.push_back(emoji[i].first);
		}
		lines.push_back(tr::lng_lumen_wrapped_emoji(tr::now) + ' ' + list.join(' '));
	}
	if (state->sampled) {
		const auto busiest = int(ranges::max_element(state->hours)
			- state->hours.begin());
		lines.push_back(QString());
		lines.push_back(Chronotype(state, busiest));
		lines.push_back(tr::lng_lumen_wrapped_hour(
			tr::now,
			lt_hour,
			u"%1:00"_q.arg(busiest, 2, 10, QChar('0'))));
	}
	lines.push_back(QString());
	lines.push_back(tr::lng_lumen_wrapped_made(tr::now));
	return lines.join('\n');
}

void Next(
		const std::shared_ptr<State> &state,
		not_null<Main::Session*> session,
		Fn<void(QString)> progress,
		Fn<void(QString)> done) {
	if (state->index >= int(state->peers.size())) {
		done(Report(state));
		return;
	}
	const auto peer = state->peers[state->index++];
	progress(tr::lng_lumen_wrapped_loading(
		tr::now,
		lt_percent,
		QString::number(state->index * 100 / int(state->peers.size()))));
	using Flag = MTPmessages_Search::Flag;
	session->api().request(MTPmessages_Search(
		MTP_flags(Flag::f_from_id),
		peer->input(),
		MTP_string(),
		MTP_inputPeerSelf(),
		MTP_inputPeerEmpty(),
		MTP_vector<MTPReaction>(),
		MTP_int(0), // top_msg_id
		MTP_inputMessagesFilterEmpty(),
		MTP_int(state->yearStart), // min_date
		MTP_int(0), // max_date
		MTP_int(0), // offset_id
		MTP_int(0), // add_offset
		MTP_int(kSamplePerChat),
		MTP_int(0), // max_id
		MTP_int(0), // min_id
		MTP_long(0) // hash
	)).done([=](const MTPmessages_Messages &result) {
		auto count = 0;
		const auto sample = [&](const QVector<MTPMessage> &messages) {
			for (const auto &message : messages) {
				Sample(state, message);
			}
		};
		result.match([&](const MTPDmessages_messages &data) {
			count = data.vmessages().v.size();
			sample(data.vmessages().v);
		}, [&](const MTPDmessages_messagesSlice &data) {
			count = data.vcount().v;
			sample(data.vmessages().v);
		}, [&](const MTPDmessages_channelMessages &data) {
			count = data.vcount().v;
			sample(data.vmessages().v);
		}, [](const MTPDmessages_messagesNotModified &) {
		});
		if (count > 0) {
			state->total += count;
			++state->chats;
			state->top.push_back({ peer->name(), count });
		}
		base::call_delayed(kRequestPause, session, [=] {
			Next(state, session, progress, done);
		});
	}).fail([=] {
		base::call_delayed(kRequestPause, session, [=] {
			Next(state, session, progress, done);
		});
	}).send();
}

} // namespace

void ShowWrapped(not_null<Window::SessionController*> controller) {
	const auto session = &controller->session();
	const auto state = std::make_shared<State>();
	state->year = QDate::currentDate().year();
	state->yearStart = QDateTime(
		QDate(state->year, 1, 1),
		QTime(0, 0)).toSecsSinceEpoch();
	for (const auto &row : session->data().chatsList()->indexed()->all()) {
		if (int(state->peers.size()) >= kMaxChats) {
			break;
		}
		const auto history = row->history();
		if (!history) {
			continue;
		}
		const auto peer = history->peer;
		const auto user = peer->asUser();
		const auto channel = peer->asChannel();
		if ((user && !user->isBot() && !user->isSelf())
			|| peer->isChat()
			|| (channel && channel->isMegagroup())) {
			state->peers.push_back(peer);
		}
	}

	controller->show(Box([=](not_null<Ui::GenericBox*> box) {
		box->setTitle(tr::lng_lumen_wrapped_title(
			lt_year,
			rpl::single(QString::number(state->year))));
		box->setWidth(st::boxWideWidth);
		const auto text = box->lifetime().make_state<rpl::variable<QString>>(
			tr::lng_lumen_wrapped_loading(tr::now, lt_percent, u"0"_q));
		box->addRow(object_ptr<Ui::FlatLabel>(
			box,
			text->value(),
			st::boxLabel));
		box->addButton(tr::lng_close(), [=] { box->closeBox(); });
		const auto weak = base::make_weak(box);
		Next(state, session, [=](QString value) {
			if (weak) {
				*text = value;
			}
		}, [=](QString value) {
			if (weak) {
				*text = value;
			}
		});
	}));
}

} // namespace Lumen
