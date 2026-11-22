/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#pragma once

namespace Window {
class SessionController;
} // namespace Window

namespace Lumen {

// Posts from every channel the user follows in one list, newest first.
void ShowFeed(not_null<Window::SessionController*> controller);

} // namespace Lumen
