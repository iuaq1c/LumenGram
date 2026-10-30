/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#include "lumen/lumen_intro_parts.h"

#include "ui/style/style_core_scale.h"
#include "styles/palette.h"
#include "styles/style_intro.h"
#include "styles/style_widgets.h"

#include <QtGui/QRadialGradient>

namespace Lumen {
namespace {

constexpr auto kLogoSize = 112;
constexpr auto kGlowScale = 1.25;
constexpr auto kGlowAlpha = 0.5;

[[nodiscard]] const QImage &Logo() {
	static const auto result = QImage(u":/gui/art/logo_256.png"_q);
	return result;
}

struct TransparentField {
	style::owned_color transparent = style::owned_color(QColor(0, 0, 0, 0));
	style::InputField field;
};

} // namespace

void PaintIntroCover(QPainter &p, int width, int top) {
	const auto size = style::ConvertScaleExact(float64(kLogoSize));
	const auto center = QPointF(width / 2., top + st::introCoverHeight / 2.);
	const auto glowRadius = size * kGlowScale;

	auto glowColor = st::windowBgActive->c;
	glowColor.setAlphaF(kGlowAlpha);
	auto glow = QRadialGradient(center, glowRadius);
	glow.setColorAt(0., glowColor);
	glowColor.setAlphaF(0.);
	glow.setColorAt(1., glowColor);

	p.save();
	p.setRenderHint(QPainter::Antialiasing);
	p.setRenderHint(QPainter::SmoothPixmapTransform);
	p.setPen(Qt::NoPen);
	p.setBrush(glow);
	p.drawEllipse(center, glowRadius, glowRadius);
	p.drawImage(
		QRectF(center.x() - size / 2., center.y() - size / 2., size, size),
		Logo());
	p.restore();
}

const style::InputField &IntroField(const style::InputField &st) {
	static auto cache = base::flat_map<
		const style::InputField*,
		std::unique_ptr<TransparentField>>();
	auto &entry = cache[&st];
	if (!entry) {
		entry = std::make_unique<TransparentField>(TransparentField{
			.field = st,
		});
		entry->field.textBg = entry->transparent.color();
		entry->field.textBgActive = entry->transparent.color();
	}
	return entry->field;
}

} // namespace Lumen
