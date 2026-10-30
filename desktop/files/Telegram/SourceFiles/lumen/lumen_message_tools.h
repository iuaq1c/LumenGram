/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#pragma once

class HistoryItem;

namespace Main {
class SessionShow;
} // namespace Main

namespace Ui {
class PopupMenu;
class Show;
} // namespace Ui

namespace Lumen {

// Adds LumenGram actions to the context menu of a single message.
void AddMessageTools(
	not_null<Ui::PopupMenu*> menu,
	std::shared_ptr<Main::SessionShow> show,
	not_null<HistoryItem*> item);

// Copies the given part of the messages list to the clipboard as a picture.
void AddMessagesScreenshotAction(
	not_null<Ui::PopupMenu*> menu,
	not_null<QWidget*> list,
	Fn<QRect()> area,
	std::shared_ptr<Ui::Show> show);

void ShowMessageDetails(
	std::shared_ptr<Main::SessionShow> show,
	not_null<HistoryItem*> item);

// Appends the send time to a service message text if enabled.
[[nodiscard]] TextWithEntities WithServiceTime(
	not_null<HistoryItem*> item,
	const TextWithEntities &text);

} // namespace Lumen
