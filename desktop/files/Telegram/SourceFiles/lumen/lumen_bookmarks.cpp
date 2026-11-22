/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#include "lumen/lumen_bookmarks.h"

#include "core/application.h"
#include "core/core_settings.h"
#include "data/data_peer.h"
#include "data/data_session.h"
#include "history/history.h"
#include "history/history_item.h"
#include "lang/lang_keys.h"
#include "main/main_session.h"
#include "settings/settings_common.h"
#include "ui/boxes/confirm_box.h"
#include "ui/layers/generic_box.h"
#include "ui/widgets/buttons.h"
#include "ui/widgets/labels.h"
#include "ui/wrap/vertical_layout.h"
#include "window/window_session_controller.h"
#include "styles/style_layers.h"
#include "styles/style_settings.h"

#include <QtCore/QJsonArray>
#include <QtCore/QJsonDocument>
#include <QtCore/QJsonObject>

namespace Lumen {
namespace {

constexpr auto kBookmarksPref = "lumen/bookmarks"_cs;
constexpr auto kPreviewLength = 120;
constexpr auto kMaxBookmarks = 500;

struct Bookmark {
	uint64 account = 0;
	PeerId peerId = 0;
	MsgId msgId = 0;
	TimeId date = 0;
	QString chat;
	QString preview;
};

[[nodiscard]] rpl::event_stream<> &Changes() {
	static auto result = rpl::event_stream<>();
	return result;
}

[[nodiscard]] std::vector<Bookmark> Load() {
	auto result = std::vector<Bookmark>();
	const auto bytes = Core::App().settings().readPref<QByteArray>(
		kBookmarksPref);
	const auto list = QJsonDocument::fromJson(bytes).array();
	for (const auto &value : list) {
		const auto entry = value.toObject();
		auto bookmark = Bookmark{
			.account = entry.value(u"a"_q).toString().toULongLong(),
			.peerId = PeerId(entry.value(u"p"_q).toString().toULongLong()),
			.msgId = MsgId(entry.value(u"m"_q).toString().toLongLong()),
			.date = TimeId(entry.value(u"d"_q).toInteger()),
			.chat = entry.value(u"c"_q).toString(),
			.preview = entry.value(u"t"_q).toString(),
		};
		if (bookmark.account && bookmark.peerId.value && bookmark.msgId.bare) {
			result.push_back(std::move(bookmark));
		}
	}
	return result;
}

void Save(const std::vector<Bookmark> &list) {
	auto array = QJsonArray();
	for (const auto &bookmark : list) {
		array.push_back(QJsonObject{
			{ u"a"_q, QString::number(bookmark.account) },
			{ u"p"_q, QString::number(bookmark.peerId.value) },
			{ u"m"_q, QString::number(bookmark.msgId.bare) },
			{ u"d"_q, qint64(bookmark.date) },
			{ u"c"_q, bookmark.chat },
			{ u"t"_q, bookmark.preview },
		});
	}
	Core::App().settings().writePref<QByteArray>(
		kBookmarksPref,
		QJsonDocument(array).toJson(QJsonDocument::Compact));
	Core::App().saveSettingsDelayed();
	Changes().fire({});
}

[[nodiscard]] bool Matches(
		const Bookmark &bookmark,
		not_null<HistoryItem*> item) {
	return (bookmark.account == item->history()->session().uniqueId())
		&& (bookmark.peerId == item->history()->peer->id)
		&& (bookmark.msgId == item->id);
}

[[nodiscard]] QString PreviewText(not_null<HistoryItem*> item) {
	auto text = item->notificationText().text.simplified();
	if (text.size() > kPreviewLength) {
		text = text.mid(0, kPreviewLength - 1) + QChar(0x2026);
	}
	return text;
}

[[nodiscard]] QString FormatDate(TimeId date) {
	return QDateTime::fromSecsSinceEpoch(date).toString(
		u"dd.MM.yyyy HH:mm"_q);
}

[[nodiscard]] int CountFor(uint64 account) {
	return int(ranges::count(
		Load(),
		account,
		&Bookmark::account));
}

} // namespace

bool IsBookmarked(not_null<HistoryItem*> item) {
	return ranges::any_of(Load(), [&](const Bookmark &bookmark) {
		return Matches(bookmark, item);
	});
}

void ToggleBookmark(not_null<HistoryItem*> item) {
	auto list = Load();
	const auto i = ranges::find_if(list, [&](const Bookmark &bookmark) {
		return Matches(bookmark, item);
	});
	if (i != end(list)) {
		list.erase(i);
	} else {
		const auto peer = item->history()->peer;
		list.insert(begin(list), Bookmark{
			.account = item->history()->session().uniqueId(),
			.peerId = peer->id,
			.msgId = item->id,
			.date = item->date(),
			.chat = peer->name(),
			.preview = PreviewText(item),
		});
		if (list.size() > kMaxBookmarks) {
			list.resize(kMaxBookmarks);
		}
	}
	Save(list);
}

rpl::producer<int> BookmarksCountValue(not_null<Main::Session*> session) {
	const auto account = session->uniqueId();
	return rpl::single(rpl::empty) | rpl::then(
		Changes().events()
	) | rpl::map([=] {
		return CountFor(account);
	});
}

void ShowBookmarks(not_null<Window::SessionController*> controller) {
	const auto session = &controller->session();
	const auto account = session->uniqueId();
	controller->show(Box([=](not_null<Ui::GenericBox*> box) {
		box->setTitle(tr::lng_lumen_bookmarks());
		box->setWidth(st::boxWideWidth);
		const auto container = box->verticalLayout();
		const auto refill = [=] {
			container->clear();
			auto any = false;
			for (const auto &bookmark : Load()) {
				if (bookmark.account != account) {
					continue;
				}
				any = true;
				const auto peer = session->data().peerLoaded(bookmark.peerId);
				const auto chat = peer ? peer->name() : bookmark.chat;
				const auto preview = bookmark.preview.isEmpty()
					? tr::lng_lumen_bookmarks_media(tr::now)
					: bookmark.preview;
				const auto peerId = bookmark.peerId;
				const auto msgId = bookmark.msgId;
				Settings::AddButtonWithLabel(
					container,
					rpl::single(chat + u": "_q + preview),
					rpl::single(FormatDate(bookmark.date)),
					st::settingsButtonNoIcon
				)->addClickHandler([=] {
					box->closeBox();
					controller->showPeerHistory(
						peerId,
						Window::SectionShow::Way::ClearStack,
						msgId);
				});
			}
			if (!any) {
				container->add(
					object_ptr<Ui::FlatLabel>(
						container,
						tr::lng_lumen_bookmarks_empty(),
						st::boxLabel),
					st::boxPadding);
			}
			container->resizeToWidth(st::boxWideWidth);
		};
		refill();
		Changes().events() | rpl::on_next(refill, box->lifetime());

		box->addButton(tr::lng_close(), [=] { box->closeBox(); });
		box->addLeftButton(tr::lng_lumen_bookmarks_clear(), [=] {
			if (!CountFor(account)) {
				return;
			}
			controller->show(Ui::MakeConfirmBox({
				.text = tr::lng_lumen_bookmarks_clear_sure(),
				.confirmed = [=](Fn<void()> close) {
					auto list = Load();
					list.erase(
						ranges::remove(list, account, &Bookmark::account),
						end(list));
					Save(list);
					close();
				},
				.confirmText = tr::lng_lumen_bookmarks_clear(),
			}));
		});
	}));
}

} // namespace Lumen
