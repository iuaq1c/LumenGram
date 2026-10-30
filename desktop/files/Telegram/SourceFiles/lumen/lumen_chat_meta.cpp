/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#include "lumen/lumen_chat_meta.h"

#include "apiwrap.h"
#include "core/application.h"
#include "core/core_settings.h"
#include "data/data_changes.h"
#include "data/data_chat.h"
#include "data/data_peer.h"
#include "data/data_session.h"
#include "history/history.h"
#include "history/history_item.h"
#include "history/view/history_view_element.h"
#include "lang/lang_keys.h"
#include "main/main_session.h"
#include "mtproto/dedicated_file_loader.h"
#include "ui/layers/generic_box.h"
#include "ui/widgets/fields/input_field.h"
#include "ui/widgets/labels.h"
#include "ui/widgets/menu/menu_add_action_callback_factory.h"
#include "ui/widgets/popup_menu.h"
#include "ui/vertical_list.h"
#include "window/window_session_controller.h"
#include "styles/style_boxes.h"
#include "styles/style_layers.h"
#include "styles/style_menu_icons.h"
#include "styles/style_widgets.h"

#include <QtCore/QJsonDocument>
#include <QtCore/QJsonObject>
#include <QtCore/QRegularExpression>

namespace Lumen {
namespace {

constexpr auto kMaxNameLength = 64;
constexpr auto kMaxNoteLength = 1024;
constexpr auto kLabelWidth = 3;
constexpr auto kTopSenders = 5;
constexpr auto kTopWords = 8;
constexpr auto kMinWordLength = 4;

struct Meta {
	QString name;
	QString note;
	int label = 0;
};

// Node-based maps: name() hands out references into them.
using AccountMeta = std::map<uint64, Meta>;

struct State {
	std::map<uint64, AccountMeta> accounts;
	int namesVersion = 0;
	rpl::event_stream<> changes;
};

[[nodiscard]] State &Current() {
	static auto result = State();
	return result;
}

[[nodiscard]] std::string Pref(uint64 account) {
	return "lumen/chats_" + std::to_string(account);
}

[[nodiscard]] AccountMeta &Account(uint64 account) {
	auto &accounts = Current().accounts;
	if (const auto i = accounts.find(account); i != end(accounts)) {
		return i->second;
	}
	auto &result = accounts[account];
	const auto bytes = Core::App().settings().readPref<QByteArray>(
		Pref(account));
	const auto object = QJsonDocument::fromJson(bytes).object();
	for (auto i = object.begin(); i != object.end(); ++i) {
		const auto entry = i.value().toObject();
		result[i.key().toULongLong()] = Meta{
			.name = entry.value(u"n"_q).toString(),
			.note = entry.value(u"o"_q).toString(),
			.label = std::clamp(entry.value(u"l"_q).toInt(), 0, kLabelColors),
		};
	}
	return result;
}

[[nodiscard]] uint64 AccountOf(not_null<const PeerData*> peer) {
	return peer->session().uniqueId();
}

void Save(uint64 account) {
	auto object = QJsonObject();
	for (const auto &[peerId, meta] : Account(account)) {
		if (meta.name.isEmpty() && meta.note.isEmpty() && !meta.label) {
			continue;
		}
		auto entry = QJsonObject();
		if (!meta.name.isEmpty()) {
			entry.insert(u"n"_q, meta.name);
		}
		if (!meta.note.isEmpty()) {
			entry.insert(u"o"_q, meta.note);
		}
		if (meta.label) {
			entry.insert(u"l"_q, meta.label);
		}
		object.insert(QString::number(peerId), entry);
	}
	Core::App().settings().writePref<QByteArray>(
		Pref(account),
		QJsonDocument(object).toJson(QJsonDocument::Compact));
	Core::App().saveSettingsDelayed();
}

[[nodiscard]] Meta &MetaFor(not_null<PeerData*> peer) {
	return Account(AccountOf(peer))[peer->id.value];
}

void Changed(not_null<PeerData*> peer, bool name) {
	Save(AccountOf(peer));
	if (name) {
		++Current().namesVersion;
		peer->session().changes().peerUpdated(
			peer,
			Data::PeerUpdate::Flag::Name);
	}
	if (const auto history = peer->owner().historyLoaded(peer)) {
		history->updateChatListEntry();
	}
	Current().changes.fire({});
}

[[nodiscard]] QColor LabelColor(int label) {
	static const auto colors = std::array<QColor, kLabelColors>{ {
		QColor(0xef, 0x5b, 0x5b),
		QColor(0xf5, 0x9d, 0x42),
		QColor(0xe8, 0xc5, 0x3a),
		QColor(0x4c, 0xbf, 0x6a),
		QColor(0x4b, 0x9b, 0xf0),
		QColor(0xa0, 0x7c, 0xf0),
	} };
	return colors[std::clamp(label, 1, kLabelColors) - 1];
}

[[nodiscard]] QString LabelName(int label) {
	switch (label) {
	case 1: return tr::lng_lumen_label_red(tr::now);
	case 2: return tr::lng_lumen_label_orange(tr::now);
	case 3: return tr::lng_lumen_label_yellow(tr::now);
	case 4: return tr::lng_lumen_label_green(tr::now);
	case 5: return tr::lng_lumen_label_blue(tr::now);
	case 6: return tr::lng_lumen_label_purple(tr::now);
	}
	return tr::lng_lumen_label_none(tr::now);
}

void ShowTextBox(
		not_null<Window::SessionController*> controller,
		rpl::producer<QString> title,
		rpl::producer<QString> placeholder,
		QString value,
		int maxLength,
		bool multiline,
		Fn<void(QString)> save) {
	controller->show(Box([=, title = std::move(title), placeholder = std::move(placeholder)](
			not_null<Ui::GenericBox*> box) mutable {
		box->setTitle(std::move(title));
		const auto field = box->addRow(object_ptr<Ui::InputField>(
			box,
			multiline ? st::newGroupDescription : st::defaultInputField,
			multiline
				? Ui::InputField::Mode::MultiLine
				: Ui::InputField::Mode::SingleLine,
			std::move(placeholder),
			value));
		field->setMaxLength(maxLength);
		box->setFocusCallback([=] { field->setFocusFast(); });
		const auto submit = [=] {
			save(field->getLastText().trimmed());
			box->closeBox();
		};
		if (!multiline) {
			field->submits() | rpl::on_next(submit, field->lifetime());
		}
		box->addButton(tr::lng_settings_save(), submit);
		box->addButton(tr::lng_cancel(), [=] { box->closeBox(); });
	}));
}

void ShowStatsBox(
		not_null<Window::SessionController*> controller,
		not_null<PeerData*> peer) {
	const auto history = peer->owner().history(peer);
	auto senders = base::flat_map<PeerData*, int>();
	auto hours = std::array<int, 24>{};
	auto words = base::flat_map<QString, int>();
	auto total = 0;
	static const auto wordRe = QRegularExpression(u"[\\p{L}]+"_q);
	for (const auto &block : history->blocks) {
		for (const auto &view : block->messages) {
			const auto item = view->data();
			if (item->isService() || !item->isRegular()) {
				continue;
			}
			++total;
			++senders[item->from().get()];
			++hours[QDateTime::fromSecsSinceEpoch(item->date()).time().hour()];
			auto it = wordRe.globalMatch(item->originalText().text.toLower());
			while (it.hasNext()) {
				const auto word = it.next().captured();
				if (word.size() >= kMinWordLength) {
					++words[word];
				}
			}
		}
	}
	auto senderList = std::vector<std::pair<PeerData*, int>>();
	for (const auto &[sender, count] : senders) {
		senderList.emplace_back(sender, count);
	}
	ranges::sort(senderList, ranges::greater(), &std::pair<PeerData*, int>::second);
	auto wordList = std::vector<std::pair<QString, int>>();
	for (const auto &[word, count] : words) {
		wordList.emplace_back(word, count);
	}
	ranges::sort(wordList, ranges::greater(), &std::pair<QString, int>::second);
	const auto busiest = int(ranges::max_element(hours) - begin(hours));

	auto lines = QStringList();
	lines.push_back(tr::lng_lumen_stats_total(
		tr::now,
		lt_total,
		QString::number(total)));
	if (total) {
		lines.push_back(QString());
		lines.push_back(tr::lng_lumen_stats_senders(tr::now));
		for (auto i = 0; i != std::min(kTopSenders, int(senderList.size())); ++i) {
			const auto &[sender, count] = senderList[i];
			lines.push_back(u"%1 — %2 (%3%)"_q
				.arg(sender ? sender->name() : QString())
				.arg(count)
				.arg(count * 100 / total));
		}
		lines.push_back(QString());
		lines.push_back(tr::lng_lumen_stats_hour(
			tr::now,
			lt_hour,
			u"%1:00–%2:00"_q.arg(busiest).arg((busiest + 1) % 24)));
		if (!wordList.empty()) {
			auto top = QStringList();
			for (auto i = 0; i != std::min(kTopWords, int(wordList.size())); ++i) {
				top.push_back(u"%1 (%2)"_q
					.arg(wordList[i].first)
					.arg(wordList[i].second));
			}
			lines.push_back(tr::lng_lumen_stats_words(
				tr::now,
				lt_words,
				top.join(u", "_q)));
		}
	}
	controller->show(Box([=](not_null<Ui::GenericBox*> box) {
		box->setTitle(tr::lng_lumen_stats_title());
		box->addRow(object_ptr<Ui::FlatLabel>(
			box,
			lines.join('\n'),
			st::boxLabel));
		Ui::AddSkip(box->verticalLayout());
		box->addRow(object_ptr<Ui::FlatLabel>(
			box,
			tr::lng_lumen_stats_about(),
			st::boxDividerLabel));
		box->addButton(tr::lng_close(), [=] { box->closeBox(); });
	}));
}

} // namespace

const QString *LocalName(not_null<const PeerData*> peer) {
	auto &accounts = Current().accounts;
	const auto account = AccountOf(peer);
	auto i = accounts.find(account);
	auto &meta = (i != end(accounts)) ? i->second : Account(account);
	const auto j = meta.find(peer->id.value);
	return (j != end(meta) && !j->second.name.isEmpty())
		? &j->second.name
		: nullptr;
}

int LocalNamesVersion() {
	return Current().namesVersion;
}

QString ChatNote(not_null<PeerData*> peer) {
	return MetaFor(peer).note;
}

rpl::producer<QString> ChatNoteValue(not_null<PeerData*> peer) {
	return rpl::single(rpl::empty) | rpl::then(
		Current().changes.events()
	) | rpl::map([=] {
		return ChatNote(peer);
	}) | rpl::distinct_until_changed();
}

int ChatLabel(not_null<PeerData*> peer) {
	return MetaFor(peer).label;
}

void PaintChatLabel(QPainter &p, not_null<PeerData*> peer, QRect row) {
	const auto label = ChatLabel(peer);
	if (!label) {
		return;
	}
	p.fillRect(
		QRect(row.x(), row.y(), style::ConvertScale(kLabelWidth), row.height()),
		LabelColor(label));
}

void FillChatMenu(
		not_null<Window::SessionController*> controller,
		not_null<PeerData*> peer,
		const Window::PeerMenuCallback &addAction) {
	addAction(Window::PeerMenuCallback::Args{
		.text = u"LumenGram"_q,
		.handler = nullptr,
		.icon = &st::menuIconSettings,
		.fillSubmenu = [=](not_null<Ui::PopupMenu*> menu) {
			menu->addAction(tr::lng_lumen_local_name(tr::now), [=] {
				ShowTextBox(
					controller,
					tr::lng_lumen_local_name(),
					tr::lng_lumen_local_name_placeholder(),
					MetaFor(peer).name,
					kMaxNameLength,
					false,
					[=](QString value) {
						MetaFor(peer).name = value;
						Changed(peer, true);
					});
			}, &st::menuIconEdit);
			menu->addAction(tr::lng_lumen_chat_note(tr::now), [=] {
				ShowTextBox(
					controller,
					tr::lng_lumen_chat_note(),
					tr::lng_lumen_chat_note_placeholder(),
					MetaFor(peer).note,
					kMaxNoteLength,
					true,
					[=](QString value) {
						MetaFor(peer).note = value;
						Changed(peer, false);
					});
			}, &st::menuIconInfo);
			Ui::Menu::CreateAddActionCallback(menu)(Window::PeerMenuCallback::Args{
				.text = tr::lng_lumen_label(tr::now),
				.handler = nullptr,
				.icon = &st::menuIconPalette,
				.fillSubmenu = [=](not_null<Ui::PopupMenu*> colors) {
					for (auto label = 0; label <= kLabelColors; ++label) {
						colors->addAction(LabelName(label), [=] {
							MetaFor(peer).label = label;
							Changed(peer, false);
						});
					}
				},
			});
			menu->addAction(tr::lng_lumen_stats_title(tr::now), [=] {
				ShowStatsBox(controller, peer);
			}, &st::menuIconStats);
		},
	});
}

void AddJumpToStart(
		not_null<Window::SessionController*> controller,
		not_null<PeerData*> peer,
		const Window::PeerMenuCallback &addAction) {
	const auto weak = base::make_weak(controller);
	addAction(tr::lng_lumen_jump_to_start(tr::now), [=] {
		const auto first = peer->migrateFrom()
			? static_cast<PeerData*>(peer->migrateFrom())
			: peer.get();
		// offset_id = 1 with add_offset = -1 returns the oldest message.
		first->session().api().request(MTPmessages_GetHistory(
			first->input(),
			MTP_int(1), // offset_id
			MTP_int(0), // offset_date
			MTP_int(-1), // add_offset
			MTP_int(1), // limit
			MTP_int(0), // max_id
			MTP_int(0), // min_id
			MTP_long(0) // hash
		)).done([=](const MTPmessages_Messages &result) {
			const auto strong = weak.get();
			const auto message = MTP::GetMessagesElement(result);
			if (!strong || !message) {
				return;
			}
			strong->showPeerHistory(
				first,
				Window::SectionShow::Way::Forward,
				IdFromMessage(*message));
		}).send();
	}, &st::menuIconSchedule);
}

} // namespace Lumen
