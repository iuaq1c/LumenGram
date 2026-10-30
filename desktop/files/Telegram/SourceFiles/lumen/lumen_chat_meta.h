/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#pragma once

#include "window/window_peer_menu.h"

class PeerData;

namespace Window {
class SessionController;
} // namespace Window

namespace Lumen {

// Local names, notes and color labels of chats, kept on this computer.
inline constexpr auto kLabelColors = 6;

[[nodiscard]] const QString *LocalName(not_null<const PeerData*> peer);
[[nodiscard]] int LocalNamesVersion();
[[nodiscard]] QString ChatNote(not_null<PeerData*> peer);
[[nodiscard]] rpl::producer<QString> ChatNoteValue(not_null<PeerData*> peer);
[[nodiscard]] int ChatLabel(not_null<PeerData*> peer);

void PaintChatLabel(QPainter &p, not_null<PeerData*> peer, QRect row);

void FillChatMenu(
	not_null<Window::SessionController*> controller,
	not_null<PeerData*> peer,
	const Window::PeerMenuCallback &addAction);

// Opens the chat at its first message, the old group first if migrated.
void AddJumpToStart(
	not_null<Window::SessionController*> controller,
	not_null<PeerData*> peer,
	const Window::PeerMenuCallback &addAction);

} // namespace Lumen
