/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#pragma once

namespace Main {
class Session;
} // namespace Main

namespace Lumen {

[[nodiscard]] bool FullscreenEffects();
void SetFullscreenEffects(bool enabled);

// A message of one ❤️, 🔥, 🎉 and similar emoji fills the window with it
// for a moment when it arrives in the open chat.
void SetupFullscreenEffects(not_null<Main::Session*> session);

} // namespace Lumen
