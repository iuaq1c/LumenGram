/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#pragma once

namespace Api {
struct MessageToSend;
} // namespace Api

namespace Lumen {

[[nodiscard]] bool UndoSendEnabled();
void SetUndoSendEnabled(bool enabled);

// False means send now, true means it is held behind an "Undo" toast.
[[nodiscard]] bool DelaySend(
	const Api::MessageToSend &message,
	Fn<void()> send);

} // namespace Lumen
