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

// "Do not disturb" with a status like "Сплю до 08:00": notifications are
// muted, the auto-reply answers with it and LumenGram users see it.
[[nodiscard]] bool DndActive();
[[nodiscard]] rpl::producer<bool> DndActiveValue();
[[nodiscard]] QString DndText();
[[nodiscard]] QDateTime DndUntil();

// "Сплю до 08:00"
[[nodiscard]] QString DndStatusLine();

[[nodiscard]] bool DndAutoReply();
void SetDndAutoReply(bool enabled);

// Presets: emoji and status text.
[[nodiscard]] std::vector<QString> DndPresets();

// `time` is the clock time, the status ends at its next occurrence.
void StartDnd(
	not_null<Main::Session*> session,
	const QString &text,
	QTime time,
	Fn<void(QString)> fail);
void StopDnd(not_null<Main::Session*> session);

} // namespace Lumen
