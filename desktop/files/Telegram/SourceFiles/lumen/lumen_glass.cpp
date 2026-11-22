/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#include "lumen/lumen_glass.h"

#include "core/application.h"
#include "core/core_settings.h"
#include "mainwidget.h"
#include "ui/chat/chat_theme.h"
#include "ui/image/image_prepare.h"
#include "window/section_widget.h"
#include "window/window_session_controller.h"

namespace Lumen {
namespace {

constexpr auto kLiquidGlassPref = "lumen/liquid_glass"_cs;
constexpr auto kBlurRadius = 24;
constexpr auto kTintAlpha = 0.42;
constexpr auto kSaturation = 1.6;

// WHY: blurring is expensive, so redo it only when the area or wallpaper change.
struct GlassCache {
	QImage image;
	QRect area;
	QSize fill;
	QString backgroundKey;
	qint64 pixmapKey = 0;
};

[[nodiscard]] std::map<const QWidget*, GlassCache> &Caches() {
	static auto result = std::map<const QWidget*, GlassCache>();
	return result;
}

[[nodiscard]] GlassCache &CacheFor(not_null<QWidget*> widget) {
	auto &caches = Caches();
	const auto i = caches.find(widget.get());
	if (i != end(caches)) {
		return i->second;
	}
	QObject::connect(widget, &QObject::destroyed, [=] {
		Caches().erase(widget.get());
	});
	return caches[widget.get()];
}

void Saturate(QImage &image, float64 factor) {
	const auto pixels = reinterpret_cast<uint32*>(image.bits());
	const auto count = image.width() * image.height();
	for (auto i = 0; i != count; ++i) {
		const auto c = pixels[i];
		const auto a = int(c >> 24);
		const auto r = int((c >> 16) & 0xFF);
		const auto g = int((c >> 8) & 0xFF);
		const auto b = int(c & 0xFF);
		const auto gray = (r * 77 + g * 150 + b * 29) >> 8;
		const auto apply = [&](int v) {
			return std::clamp(
				int(gray + (v - gray) * factor),
				0,
				a);
		};
		pixels[i] = (uint32(a) << 24)
			| (uint32(apply(r)) << 16)
			| (uint32(apply(g)) << 8)
			| uint32(apply(b));
	}
}

[[nodiscard]] QImage PrepareGlass(
		not_null<Ui::ChatTheme*> theme,
		QSize fill,
		QRect area) {
	const auto ratio = style::DevicePixelRatio();
	const auto radius = style::ConvertScale(kBlurRadius);
	const auto padded = area.marginsAdded(
		{ radius, radius, radius, radius });
	const auto inner = padded.intersected(QRect(QPoint(), fill));
	if (inner.isEmpty()) {
		return QImage();
	}
	auto source = QImage(
		inner.size() * ratio,
		QImage::Format_ARGB32_Premultiplied);
	source.setDevicePixelRatio(ratio);
	source.fill(Qt::transparent);
	{
		auto q = QPainter(&source);
		q.translate(-inner.topLeft());
		Window::SectionWidget::PaintBackground(q, theme, fill, inner, true);
	}

	// WHY: the blur fades to transparent at the image border, so the
	// padding outside the wallpaper is filled with a stretched copy first,
	// otherwise the bar edges get a dark halo.
	auto image = QImage(
		padded.size() * ratio,
		QImage::Format_ARGB32_Premultiplied);
	image.setDevicePixelRatio(ratio);
	{
		auto q = QPainter(&image);
		q.setRenderHint(QPainter::SmoothPixmapTransform);
		q.drawImage(QRect(QPoint(), padded.size()), source);
		q.drawImage(inner.topLeft() - padded.topLeft(), source);
	}
	image = Images::BlurLargeImage(std::move(image), radius * ratio);
	Saturate(image, kSaturation);
	auto result = image.copy(QRect(
		(area.topLeft() - padded.topLeft()) * ratio,
		area.size() * ratio));
	result.setDevicePixelRatio(ratio);
	return result;
}

} // namespace

bool LiquidGlass() {
	return Core::App().settings().readPref<bool>(kLiquidGlassPref);
}

void SetLiquidGlass(bool enabled) {
	Core::App().settings().writePref<bool>(kLiquidGlassPref, enabled);
	Core::App().saveSettingsDelayed();
}

void PaintGlass(
		QPainter &p,
		not_null<Window::SessionController*> controller,
		not_null<QWidget*> section,
		not_null<QWidget*> widget,
		QRect rect,
		const QColor &tint) {
	const auto theme = controller->currentChatTheme();
	const auto content = controller->content();
	const auto fill = QSize(section->width(), content->height());
	const auto area = QRect(
		widget->mapTo(section, rect.topLeft())
			- QPoint(0, content->backgroundFromY()),
		rect.size());
	const auto &background = theme->background();
	const auto pixmapKey = theme->backgroundState(fill).now.pixmap.cacheKey();

	auto &cache = CacheFor(widget);
	if (cache.image.isNull()
		|| cache.area != area
		|| cache.fill != fill
		|| cache.backgroundKey != background.key
		|| cache.pixmapKey != pixmapKey) {
		cache.image = PrepareGlass(theme.get(), fill, area);
		cache.area = area;
		cache.fill = fill;
		cache.backgroundKey = background.key;
		cache.pixmapKey = pixmapKey;
	}

	if (cache.image.isNull()) {
		p.fillRect(rect, tint);
		return;
	}
	p.drawImage(rect, cache.image);

	auto color = tint;
	color.setAlphaF(kTintAlpha);
	p.fillRect(rect, color);

	// A soft sheen fading down from a thin bright rim, like a glass plate.
	const auto isDark = (tint.lightnessF() < 0.5);
	auto sheen = QLinearGradient(rect.topLeft(), rect.bottomLeft());
	sheen.setColorAt(0., QColor(255, 255, 255, isDark ? 22 : 70));
	sheen.setColorAt(0.5, QColor(255, 255, 255, 0));
	p.fillRect(rect, sheen);

	const auto line = std::max(style::ConvertScale(1), 1);
	p.fillRect(
		QRect(rect.x(), rect.y(), rect.width(), line),
		QColor(255, 255, 255, isDark ? 36 : 120));
}

} // namespace Lumen
