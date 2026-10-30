/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#pragma once

class PeerData;

namespace Lumen {

enum class BadgeKind : uchar {
	None,
	Developer,
	Supporter,
};

[[nodiscard]] BadgeKind PeerBadgeKind(not_null<PeerData*> peer);

void PaintBadge(QPainter &p, BadgeKind kind, QRect rect);

int DrawPeerBadge(
	QPainter &p,
	not_null<PeerData*> peer,
	QRect rectForName,
	int nameWidth);

} // namespace Lumen
