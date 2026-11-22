/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#include "lumen/lumen_badges.h"

#include "data/data_peer.h"
#include "lumen/lumen_server.h"
#include "ui/style/style_core_scale.h"
#include "styles/palette.h"

#include <QtGui/QPainterPath>

namespace Lumen {
namespace {

constexpr auto kBadgeSize = 16;
constexpr auto kBadgeSkip = 3;

constexpr auto kDevelopers = std::array<uint64, 1>{ {
	1638932794ULL,
} };

[[nodiscard]] bool Contains(const auto &list, uint64 id) {
	return ranges::contains(list, id);
}

void PaintSparkle(QPainter &p, QPointF center, float64 radius) {
	auto path = QPainterPath();
	const auto inner = radius * 0.28;
	for (auto i = 0; i != 8; ++i) {
		const auto angle = M_PI / 4. * i - M_PI / 2.;
		const auto r = (i % 2) ? inner : radius;
		const auto point = center
			+ QPointF(r * std::cos(angle), r * std::sin(angle));
		if (i) {
			path.lineTo(point);
		} else {
			path.moveTo(point);
		}
	}
	path.closeSubpath();
	p.drawPath(path);
}

void PaintHeart(QPainter &p, QRectF rect) {
	const auto w = rect.width();
	const auto h = rect.height();
	const auto x = rect.x();
	const auto y = rect.y();
	auto path = QPainterPath();
	path.moveTo(x + w * 0.5, y + h * 0.9);
	path.cubicTo(
		x + w * 0.05, y + h * 0.6,
		x - w * 0.02, y + h * 0.15,
		x + w * 0.3, y + h * 0.12);
	path.cubicTo(
		x + w * 0.42, y + h * 0.1,
		x + w * 0.5, y + h * 0.22,
		x + w * 0.5, y + h * 0.3);
	path.cubicTo(
		x + w * 0.5, y + h * 0.22,
		x + w * 0.58, y + h * 0.1,
		x + w * 0.7, y + h * 0.12);
	path.cubicTo(
		x + w * 1.02, y + h * 0.15,
		x + w * 0.95, y + h * 0.6,
		x + w * 0.5, y + h * 0.9);
	p.drawPath(path);
}

} // namespace

BadgeKind PeerBadgeKind(not_null<PeerData*> peer) {
	if (!peer->isUser()) {
		return BadgeKind::None;
	}
	const auto id = peerToUser(peer->id).bare;
	return Contains(kDevelopers, id)
		? BadgeKind::Developer
		: IsSupporter(id)
		? BadgeKind::Supporter
		: BadgeKind::None;
}

void PaintBadge(QPainter &p, BadgeKind kind, QRect rect) {
	if (kind == BadgeKind::None) {
		return;
	}
	p.save();
	p.setRenderHint(QPainter::Antialiasing);
	p.setPen(Qt::NoPen);
	const auto r = QRectF(rect);
	if (kind == BadgeKind::Developer) {
		p.setBrush(st::windowBgActive);
		p.drawEllipse(r);
		p.setBrush(st::windowFgActive);
		PaintSparkle(p, r.center(), r.width() * 0.34);
	} else {
		p.setBrush(st::windowBgActive);
		PaintHeart(p, r);
	}
	p.restore();
}

int DrawPeerBadge(
		QPainter &p,
		not_null<PeerData*> peer,
		QRect rectForName,
		int nameWidth) {
	const auto kind = PeerBadgeKind(peer);
	if (kind == BadgeKind::None) {
		return 0;
	}
	const auto skip = style::ConvertScale(kBadgeSkip);
	const auto size = std::min(
		style::ConvertScale(kBadgeSize),
		rectForName.height());
	const auto x = rectForName.x()
		+ std::min(nameWidth + skip, rectForName.width() - size);
	const auto y = rectForName.y() + (rectForName.height() - size) / 2;
	PaintBadge(p, kind, QRect(x, y, size, size));
	return skip + size;
}

} // namespace Lumen
