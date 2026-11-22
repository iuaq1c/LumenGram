/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#pragma once

class HistoryItem;

namespace Main {
class Session;
} // namespace Main

namespace Window {
class SessionController;
} // namespace Window

namespace Lumen {

// Local bookmarks of messages, kept on this computer for each account.
[[nodiscard]] bool IsBookmarked(not_null<HistoryItem*> item);
void ToggleBookmark(not_null<HistoryItem*> item);

[[nodiscard]] rpl::producer<int> BookmarksCountValue(
	not_null<Main::Session*> session);

void ShowBookmarks(not_null<Window::SessionController*> controller);

} // namespace Lumen
