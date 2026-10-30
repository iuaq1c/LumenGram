/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#include "lumen/lumen_message_tools.h"

#include "base/unixtime.h"
#include "data/data_document.h"
#include "data/data_media_types.h"
#include "data/data_peer.h"
#include "data/data_session.h"
#include "data/stickers/data_stickers.h"
#include "data/stickers/data_stickers_set.h"
#include "history/history_item_reply_markup.h"
#include "history/history.h"
#include "history/history_item.h"
#include "history/history_item_components.h"
#include "lang/lang_keys.h"
#include "lumen/lumen_bookmarks.h"
#include "lumen/lumen_message_history.h"
#include "lumen/lumen_peer_info.h"
#include "lumen/lumen_settings.h"
#include "main/main_session.h"
#include "main/session/session_show.h"
#include "settings/settings_common.h"
#include "ui/layers/generic_box.h"
#include "ui/text/text_entity.h"
#include "ui/ui_utility.h"
#include "ui/widgets/buttons.h"
#include "ui/widgets/popup_menu.h"
#include "ui/wrap/vertical_layout.h"
#include "styles/style_layers.h"
#include "styles/style_menu_icons.h"
#include "styles/style_settings.h"

#include <QtGui/QClipboard>
#include <QtGui/QGuiApplication>

namespace Lumen {
namespace {

struct DetailsRow {
	QString title;
	QString value;
};

[[nodiscard]] QString FormatFullDate(TimeId date) {
	return QDateTime::fromSecsSinceEpoch(date).toString(
		u"dd.MM.yyyy, HH:mm:ss"_q);
}

[[nodiscard]] QString PeerText(not_null<PeerData*> peer) {
	return peer->name() + u" · "_q + PeerIdText(peer);
}

// Title and link of the sticker set the sticker in the message belongs to.
[[nodiscard]] QString StickerSetText(not_null<HistoryItem*> item) {
	const auto media = item->media();
	const auto document = media ? media->document() : nullptr;
	const auto sticker = document ? document->sticker() : nullptr;
	if (!sticker || sticker->set.empty()) {
		return QString();
	}
	const auto &sets = item->history()->owner().stickers().sets();
	const auto i = sets.find(sticker->set.id);
	const auto title = (i != end(sets)) ? i->second->title : QString();
	const auto shortName = (i != end(sets))
		? i->second->shortName
		: sticker->set.shortName;
	const auto link = shortName.isEmpty()
		? QString()
		: (u"https://t.me/addstickers/"_q + shortName);
	return (title.isEmpty() || link.isEmpty())
		? (title + link)
		: (title + u" · "_q + link);
}

// Callback data is usually text, otherwise show it as hex.
[[nodiscard]] QString CallbackDataText(const QByteArray &data) {
	const auto text = QString::fromUtf8(data);
	const auto printable = (text.toUtf8() == data) && std::all_of(
		text.begin(),
		text.end(),
		[](QChar ch) { return ch.isPrint(); });
	return printable
		? text
		: (u"0x"_q + QString::fromLatin1(data.toHex()));
}

[[nodiscard]] std::vector<DetailsRow> CollectRows(
		not_null<HistoryItem*> item) {
	auto result = std::vector<DetailsRow>();
	const auto peer = item->history()->peer;
	result.push_back({
		tr::lng_lumen_details_message_id(tr::now),
		QString::number(item->id.bare),
	});
	result.push_back({
		tr::lng_lumen_details_chat_id(tr::now),
		PeerIdText(peer),
	});
	if (item->from() != peer) {
		result.push_back({
			tr::lng_lumen_details_sender(tr::now),
			PeerText(item->from()),
		});
	}
	result.push_back({
		tr::lng_lumen_details_sent(tr::now),
		FormatFullDate(item->date()),
	});
	if (const auto edited = item->Get<HistoryMessageEdited>()) {
		if (edited->date) {
			result.push_back({
				tr::lng_lumen_details_edited(tr::now),
				FormatFullDate(edited->date),
			});
		}
	}
	if (const auto forwarded = item->Get<HistoryMessageForwarded>()) {
		if (const auto sender = forwarded->originalSender) {
			result.push_back({
				tr::lng_lumen_details_forwarded_from(tr::now),
				PeerText(sender),
			});
		} else if (const auto &info = forwarded->originalHiddenSenderInfo) {
			result.push_back({
				tr::lng_lumen_details_forwarded_from(tr::now),
				info->name,
			});
		}
		if (forwarded->originalDate) {
			result.push_back({
				tr::lng_lumen_details_forwarded_date(tr::now),
				FormatFullDate(forwarded->originalDate),
			});
		}
	}
	if (const auto views = item->viewsCount(); views >= 0) {
		result.push_back({
			tr::lng_lumen_details_views(tr::now),
			QString::number(views),
		});
	}
	if (const auto set = StickerSetText(item); !set.isEmpty()) {
		result.push_back({
			tr::lng_lumen_details_sticker_set(tr::now),
			set,
		});
	}
	if (const auto markup = item->Get<HistoryMessageReplyMarkup>()) {
		using Type = HistoryMessageMarkupButton::Type;
		for (const auto &row : markup->data.rows) {
			for (const auto &button : row) {
				if (button.type != Type::Callback
					&& button.type != Type::CallbackWithPassword) {
					continue;
				}
				result.push_back({
					tr::lng_lumen_details_button(
						tr::now,
						lt_text,
						button.text),
					CallbackDataText(button.data),
				});
			}
		}
	}
	return result;
}

} // namespace

void AddMessageTools(
		not_null<Ui::PopupMenu*> menu,
		std::shared_ptr<Main::SessionShow> show,
		not_null<HistoryItem*> item) {
	if (!item->isRegular()) {
		return;
	}
	const auto itemId = item->fullId();
	menu->addAction(tr::lng_lumen_message_details(tr::now), [=] {
		if (const auto strong = show->session().data().message(itemId)) {
			ShowMessageDetails(show, strong);
		}
	}, &st::menuIconInfo);
	if (HasEditHistory(item)) {
		menu->addAction(tr::lng_lumen_edit_history(tr::now), [=] {
			if (const auto strong = show->session().data().message(itemId)) {
				ShowEditHistory(show, strong);
			}
		}, &st::menuIconEdit);
	}
	const auto bookmarked = IsBookmarked(item);
	menu->addAction((bookmarked
		? tr::lng_lumen_bookmark_remove
		: tr::lng_lumen_bookmark_add)(tr::now), [=] {
		if (const auto strong = show->session().data().message(itemId)) {
			ToggleBookmark(strong);
			show->showToast(bookmarked
				? tr::lng_lumen_bookmark_removed(tr::now)
				: tr::lng_lumen_bookmark_added(tr::now));
		}
	}, &st::menuIconFave);
}

void AddMessagesScreenshotAction(
		not_null<Ui::PopupMenu*> menu,
		not_null<QWidget*> list,
		Fn<QRect()> area,
		std::shared_ptr<Ui::Show> show) {
	const auto weak = QPointer<QWidget>(list.get());
	menu->addAction(tr::lng_lumen_messages_screenshot(tr::now), [=] {
		const auto widget = weak.data();
		const auto rect = widget ? area() : QRect();
		if (rect.isEmpty()) {
			return;
		}
		const auto image = Ui::GrabWidgetToImage(widget, rect, st::windowBg->c);
		QGuiApplication::clipboard()->setImage(image);
		show->showToast(tr::lng_lumen_messages_screenshot_done(tr::now));
	}, &st::menuIconPhoto);
}

void ShowMessageDetails(
		std::shared_ptr<Main::SessionShow> show,
		not_null<HistoryItem*> item) {
	const auto rows = CollectRows(item);
	show->show(Box([=](not_null<Ui::GenericBox*> box) {
		box->setTitle(tr::lng_lumen_message_details());
		box->setWidth(st::boxWideWidth);
		const auto container = box->verticalLayout();
		for (const auto &row : rows) {
			const auto value = row.value;
			Settings::AddButtonWithLabel(
				container,
				rpl::single(row.title),
				rpl::single(value),
				st::settingsButtonNoIcon
			)->addClickHandler([=] {
				TextUtilities::SetClipboardText({ value });
				show->showToast(tr::lng_text_copied(tr::now));
			});
		}
		box->addButton(tr::lng_close(), [=] { box->closeBox(); });
	}));
}

TextWithEntities WithServiceTime(
		not_null<HistoryItem*> item,
		const TextWithEntities &text) {
	if (!ServiceTime() || !item->isRegular() || text.empty()) {
		return text;
	}
	auto result = text;
	result.append(u" · "_q).append(
		FormatMessageTime(base::unixtime::parse(item->date()).time()));
	return result;
}

} // namespace Lumen
