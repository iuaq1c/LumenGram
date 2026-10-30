/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#include "lumen/lumen_message_history.h"

#include "core/application.h"
#include "core/core_settings.h"
#include "data/data_session.h"
#include "history/history.h"
#include "history/history_item.h"
#include "history/history_item_components.h"
#include "history/history_item_edition.h"
#include "lang/lang_keys.h"
#include "main/main_session.h"
#include "main/session/session_show.h"
#include "ui/layers/generic_box.h"
#include "ui/text/text_utilities.h"
#include "ui/widgets/labels.h"
#include "ui/wrap/vertical_layout.h"
#include "styles/style_boxes.h"
#include "styles/style_layers.h"
#include "styles/style_widgets.h"

namespace Lumen {
namespace {

constexpr auto kSaveDeletedPref = "lumen/save_deleted"_cs;
constexpr auto kSaveEditsPref = "lumen/save_edits"_cs;

struct Version {
	TextWithEntities text;
	TimeId date = 0;
};

using Key = std::pair<uint64, FullMsgId>;

[[nodiscard]] Key KeyFor(not_null<const HistoryItem*> item) {
	return { item->history()->session().uniqueId(), item->fullId() };
}

// WHY: the server no longer has these, and Telegram keeps no local copy of
// messages, so the saved state lives only while the app is running.
[[nodiscard]] base::flat_set<Key> &Deleted() {
	static auto result = base::flat_set<Key>();
	return result;
}

[[nodiscard]] base::flat_map<Key, std::vector<Version>> &Edits() {
	static auto result = base::flat_map<Key, std::vector<Version>>();
	return result;
}

[[nodiscard]] QString FormatDate(TimeId date) {
	return QDateTime::fromSecsSinceEpoch(date).toString(
		u"dd.MM.yyyy, HH:mm:ss"_q);
}

} // namespace

bool SaveDeletedMessages() {
	return Core::App().settings().readPref<bool>(kSaveDeletedPref, true);
}

void SetSaveDeletedMessages(bool enabled) {
	Core::App().settings().writePref<bool>(kSaveDeletedPref, enabled);
	Core::App().saveSettingsDelayed();
}

bool SaveEditHistory() {
	return Core::App().settings().readPref<bool>(kSaveEditsPref, true);
}

void SetSaveEditHistory(bool enabled) {
	Core::App().settings().writePref<bool>(kSaveEditsPref, enabled);
	Core::App().saveSettingsDelayed();
}

bool KeepDeleted(not_null<HistoryItem*> item) {
	if (!SaveDeletedMessages()
		|| !item->isRegular()
		|| item->isService()
		|| item->isSending()
		|| item->hasFailed()) {
		return false;
	}
	if (Deleted().emplace(KeyFor(item)).second) {
		item->history()->owner().requestItemViewRefresh(item);
	}
	return true;
}

bool IsDeleted(not_null<const HistoryItem*> item) {
	return !Deleted().empty() && Deleted().contains(KeyFor(item));
}

void RememberEdit(
		not_null<HistoryItem*> item,
		const HistoryMessageEdition &edition) {
	if (!SaveEditHistory() || !item->isRegular()) {
		return;
	}
	const auto &was = item->originalText();
	if (was.text.isEmpty() || was == edition.textWithEntities) {
		return;
	}
	const auto edited = item->Get<HistoryMessageEdited>();
	auto &versions = Edits()[KeyFor(item)];
	versions.push_back({
		.text = was,
		.date = (edited && edited->date) ? edited->date : item->date(),
	});
}

bool HasEditHistory(not_null<const HistoryItem*> item) {
	return Edits().contains(KeyFor(item));
}

void ShowEditHistory(
		std::shared_ptr<Main::SessionShow> show,
		not_null<HistoryItem*> item) {
	const auto i = Edits().find(KeyFor(item));
	if (i == end(Edits())) {
		return;
	}
	auto versions = i->second;
	const auto edited = item->Get<HistoryMessageEdited>();
	versions.push_back({
		.text = item->originalText(),
		.date = (edited && edited->date) ? edited->date : item->date(),
	});
	show->show(Box([=](not_null<Ui::GenericBox*> box) {
		box->setTitle(tr::lng_lumen_edit_history());
		box->setWidth(st::boxWideWidth);
		const auto last = int(versions.size()) - 1;
		for (auto index = 0; index <= last; ++index) {
			const auto &version = versions[index];
			const auto date = FormatDate(version.date);
			box->addRow(object_ptr<Ui::FlatLabel>(
				box,
				(index == last)
					? tr::lng_lumen_edit_history_current(
						tr::now,
						lt_date,
						date)
					: date,
				st::boxDividerLabel));
			const auto text = box->addRow(object_ptr<Ui::FlatLabel>(
				box,
				st::boxLabel));
			text->setMarkedText(version.text);
			text->setSelectable(true);
		}
		box->addButton(tr::lng_close(), [=] { box->closeBox(); });
	}));
}

} // namespace Lumen
