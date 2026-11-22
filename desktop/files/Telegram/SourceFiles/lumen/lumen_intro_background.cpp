/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#include "lumen/lumen_intro_background.h"

#include "ui/effects/animation_value.h"
#include "ui/power_saving.h"
#include "ui/style/style_core.h"
#include "ui/style/style_core_scale.h"
#include "styles/palette.h"

#include <QtGui/QRadialGradient>

#include <random>

namespace Lumen {
namespace {

constexpr auto kFrameTimeout = crl::time(50);
constexpr auto kFrameDivider = 8;
constexpr auto kMinFrameSide = 16;
constexpr auto kDriftPeriod = crl::time(30000);
constexpr auto kPulsePeriod = crl::time(1600);
constexpr auto kDarkAlpha = 0.32;
constexpr auto kLightAlpha = 0.18;
constexpr auto kPulseAlpha = 0.55;
constexpr auto kPulseSpread = 14;
constexpr auto kPulseWidth = 2.;
constexpr auto kStarsCount = 140;
constexpr auto kStarsSeed = 20261020;
constexpr auto kStarMinSize = 0.6;
constexpr auto kStarMaxSize = 1.8;
constexpr auto kStarAlpha = 0.85;
constexpr auto kStarsDrift = 0.035;

struct Spot {
	float64 x = 0.;
	float64 y = 0.;
	float64 driftX = 0.;
	float64 driftY = 0.;
	int speedX = 1;
	int speedY = 1;
	float64 shift = 0.;
	float64 alpha = 1.;
};

constexpr auto kSpots = std::array<Spot, 3>{ {
	{ 0.22, 0.28, 0.16, 0.14, 1, 1, 0.0, 1.0 },
	{ 0.78, 0.36, 0.14, 0.18, 1, 2, 2.1, 0.9 },
	{ 0.50, 0.82, 0.22, 0.10, 2, 1, 4.2, 0.8 },
} };

[[nodiscard]] QColor WithAlpha(QColor color, float64 alpha) {
	color.setAlphaF(std::clamp(alpha, 0., 1.));
	return color;
}

} // namespace

IntroBackground::IntroBackground(Fn<void()> update)
: _update(std::move(update))
, _timer([=] { _update(); })
, _started(crl::now()) {
	auto engine = std::mt19937(kStarsSeed);
	auto random = std::uniform_real_distribution<float64>(0., 1.);
	_stars.reserve(kStarsCount);
	for (auto i = 0; i != kStarsCount; ++i) {
		const auto depth = random(engine);
		_stars.push_back({
			.x = random(engine),
			.y = random(engine),
			.size = kStarMinSize + (kStarMaxSize - kStarMinSize) * depth,
			.alpha = 0.35 + 0.65 * random(engine),
			.twinkle = 1. + std::floor(random(engine) * 4.),
			.shift = random(engine) * 2. * M_PI,
			.depth = depth,
		});
	}
	refreshColors();
	refreshAnimated();

	style::PaletteChanged(
	) | rpl::on_next([=] {
		refreshColors();
		_frame = QImage();
		_update();
	}, _lifetime);

	PowerSaving::Changes(
	) | rpl::on_next([=] {
		refreshAnimated();
		_update();
	}, _lifetime);
}

void IntroBackground::refreshColors() {
	_base = st::windowBg->c;
	_dark = (_base.lightnessF() < 0.5);
	_starColor = anim::color(st::windowFg, st::windowBgActive, 0.25);
	const auto alpha = _dark ? kDarkAlpha : kLightAlpha;
	_spots = {
		WithAlpha(st::windowBgActive->c, alpha * kSpots[0].alpha),
		WithAlpha(st::activeButtonBg->c, alpha * kSpots[1].alpha),
		WithAlpha(st::lightButtonFg->c, alpha * kSpots[2].alpha),
	};
}

void IntroBackground::refreshAnimated() {
	_animated = !anim::Disabled()
		&& !PowerSaving::On(PowerSaving::kAnimations);
	if (_animated) {
		_timer.callEach(kFrameTimeout);
	} else {
		_timer.cancel();
	}
}

float64 IntroBackground::phase() const {
	if (!_animated) {
		return 0.;
	}
	const auto passed = (crl::now() - _started) % kDriftPeriod;
	return 2. * M_PI * float64(passed) / kDriftPeriod;
}

void IntroBackground::prepareFrame(QSize size) {
	const auto width = std::max(size.width() / kFrameDivider, kMinFrameSide);
	const auto height = std::max(
		size.height() / kFrameDivider,
		kMinFrameSide);
	const auto frameSize = QSize(width, height);
	if (!_animated && _frame.size() == frameSize) {
		return;
	}
	if (_frame.size() != frameSize) {
		_frame = QImage(frameSize, QImage::Format_ARGB32_Premultiplied);
	}
	_frame.fill(_base);

	auto q = QPainter(&_frame);
	q.setRenderHint(QPainter::Antialiasing);
	q.setPen(Qt::NoPen);
	const auto t = phase();
	const auto radius = std::max(width, height) * 0.45;
	for (auto i = 0; i != int(kSpots.size()); ++i) {
		const auto &spot = kSpots[i];
		const auto center = QPointF(
			width * (spot.x + spot.driftX * std::sin(spot.speedX * t + spot.shift)),
			height * (spot.y + spot.driftY * std::cos(spot.speedY * t + spot.shift)));
		auto gradient = QRadialGradient(center, radius);
		gradient.setColorAt(0., _spots[i]);
		gradient.setColorAt(1., WithAlpha(_spots[i], 0.));
		q.setBrush(gradient);
		q.drawEllipse(center, radius, radius);
	}
}

void IntroBackground::paint(QPainter &p, QRect rect) {
	prepareFrame(rect.size());
	p.save();
	p.setRenderHint(QPainter::SmoothPixmapTransform);
	p.drawImage(rect, _frame);
	p.restore();
	if (_dark) {
		paintStars(p, rect);
	}
}

void IntroBackground::paintStars(QPainter &p, QRect rect) const {
	const auto t = phase();
	const auto drift = _animated
		? float64(crl::now() - _started) / kDriftPeriod
		: 0.;
	p.save();
	p.setRenderHint(QPainter::Antialiasing);
	p.setPen(Qt::NoPen);
	for (const auto &star : _stars) {
		const auto twinkle = 0.6
			+ 0.4 * std::sin(star.twinkle * t + star.shift);
		const auto x = star.x - kStarsDrift * star.depth * drift;
		const auto position = QPointF(
			rect.x() + (x - std::floor(x)) * rect.width(),
			rect.y() + star.y * rect.height());
		const auto radius = style::ConvertScaleExact(star.size);
		p.setBrush(WithAlpha(_starColor, kStarAlpha * star.alpha * twinkle));
		p.drawEllipse(position, radius, radius);
	}
	p.restore();
}

void IntroBackground::setPulseActive(bool active) {
	if (_pulseActive == active) {
		return;
	}
	_pulseActive = active;
	if (active) {
		_pulseStarted = crl::now();
	}
	_update();
}

void IntroBackground::paintPulse(QPainter &p, QRect button, int radius) {
	if (!_animated || !_pulseActive) {
		return;
	}
	const auto passed = (crl::now() - _pulseStarted) % kPulsePeriod;
	const auto progress = float64(passed) / kPulsePeriod;
	const auto grow = style::ConvertScale(kPulseSpread)
		* anim::easeOutCirc(1., progress);
	const auto rect = QRectF(button).marginsAdded(
		QMarginsF(grow, grow, grow, grow));

	p.save();
	p.setRenderHint(QPainter::Antialiasing);
	p.setBrush(Qt::NoBrush);
	p.setPen(QPen(
		WithAlpha(st::windowBgActive->c, kPulseAlpha * (1. - progress)),
		style::ConvertScaleExact(kPulseWidth)));
	p.drawRoundedRect(rect, radius + grow, radius + grow);
	p.restore();
}

} // namespace Lumen
