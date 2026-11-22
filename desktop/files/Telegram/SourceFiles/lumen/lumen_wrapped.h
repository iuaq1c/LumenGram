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

// "Year in review": messages written this year, the person of the year,
// the word and emoji of the year and the time of day the user writes.
void ShowWrapped(not_null<Window::SessionController*> controller);

} // namespace Lumen
