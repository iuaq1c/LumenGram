/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#pragma once

class HistoryItem;

namespace Window {
class SessionController;
} // namespace Window

namespace Lumen {

// Text of every transcribed voice and round video message, kept on this
// computer so it can be searched.
void SaveVoiceText(not_null<HistoryItem*> item, const QString &text);

void ShowVoiceSearch(not_null<Window::SessionController*> controller);

} // namespace Lumen
