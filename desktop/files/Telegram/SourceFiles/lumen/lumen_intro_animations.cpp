/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#include "lumen/lumen_intro_animations.h"

#include "ui/effects/animation_value.h"
#include "styles/style_intro.h"

namespace Lumen {
namespace {

constexpr auto kCrossfadeScale = 0.04;

} // namespace

IntroEntrance::IntroEntrance(Fn<void()> updated)
: _updated(std::move(updated)) {
}

void IntroEntrance::start(
		std::vector<IntroEntranceGroup> groups,
		int extraGroups) {
	finish();
	if (anim::Disabled()) {
		for (const auto &group : groups) {
			if (group.appear) {
				group.appear();
			}
		}
		return;
	}
	_groups = int(groups.size()) + extraGroups;
	for (auto i = 0; i != int(groups.size()); ++i) {
		for (const auto &widget : groups[i].widgets) {
			if (widget) {
				_placed.push_back({
					.widget = widget,
					.index = i,
					.y = widget->y(),
				});
			}
		}
		_appear.push_back(std::move(groups[i].appear));
	}
	const auto duration = st::lumenIntroEntranceStagger * (_groups - 1)
		+ st::lumenIntroEntranceDuration;
	_animation.start([=] { apply(); }, 0., 1., duration);
	apply();
}

void IntroEntrance::finish() {
	_animation.stop();
	apply();
}

bool IntroEntrance::animating() const {
	return _animation.animating();
}

float64 IntroEntrance::progress(int index) const {
	if (!_animation.animating()) {
		return 1.;
	}
	const auto duration = st::lumenIntroEntranceStagger * (_groups - 1)
		+ st::lumenIntroEntranceDuration;
	const auto passed = _animation.value(1.) * duration
		- st::lumenIntroEntranceStagger * index;
	const auto local = std::clamp(
		passed / st::lumenIntroEntranceDuration,
		0.,
		1.);
	return anim::easeOutCubic(1., local);
}

int IntroEntrance::shift(int index) const {
	return int(base::SafeRound(
		(1. - progress(index)) * st::lumenIntroEntranceShift));
}

void IntroEntrance::apply() {
	for (auto &placed : _placed) {
		const auto widget = placed.widget.data();
		if (!widget) {
			continue;
		}
		if (widget->y() != placed.y) {
			placed.applied = 0; // relayouted by its owner meanwhile
		}
		const auto desired = shift(placed.index);
		if (desired != placed.applied) {
			widget->move(widget->x(), widget->y() - placed.applied + desired);
			placed.applied = desired;
		}
		placed.y = widget->y();
	}
	for (auto i = 0; i != int(_appear.size()); ++i) {
		if (_appear[i] && progress(i) > 0.) {
			base::take(_appear[i])();
		}
	}
	if (!_animation.animating()) {
		_placed.clear();
		_appear.clear();
	}
	if (_updated) {
		_updated();
	}
}

IntroCrossfade::IntroCrossfade(QPixmap was, QPixmap now)
: _was(std::move(was))
, _now(std::move(now)) {
}

void IntroCrossfade::start(bool back, Fn<void()> updated) {
	_back = back;
	_animation.start(
		std::move(updated),
		0.,
		1.,
		st::lumenIntroFadeDuration,
		anim::easeOutCubic);
}

bool IntroCrossfade::animating() const {
	return _animation.animating();
}

void IntroCrossfade::paint(QPainter &p, QRect area) const {
	const auto progress = _animation.value(1.);
	const auto direction = _back ? -1 : 1;
	const auto distance = st::lumenIntroFadeShift;
	paintOne(
		p,
		_was,
		area,
		1. - progress,
		1. - kCrossfadeScale * progress,
		-direction * int(base::SafeRound(distance * progress)));
	paintOne(
		p,
		_now,
		area,
		progress,
		1. - kCrossfadeScale * (1. - progress),
		direction * int(base::SafeRound(distance * (1. - progress))));
}

void IntroCrossfade::paintOne(
		QPainter &p,
		const QPixmap &pixmap,
		QRect area,
		float64 opacity,
		float64 scale,
		int shift) const {
	if (pixmap.isNull() || opacity <= 0.) {
		return;
	}
	const auto size = QSizeF(pixmap.size()) / pixmap.devicePixelRatio();
	const auto center = QRectF(area).center();

	p.save();
	p.setOpacity(opacity);
	p.setRenderHint(QPainter::SmoothPixmapTransform);
	p.translate(center.x() + shift, center.y());
	p.scale(scale, scale);
	p.drawPixmap(
		QRectF(QPointF(-size.width() / 2., -size.height() / 2.), size),
		pixmap,
		QRectF(QPointF(), QSizeF(pixmap.size())));
	p.restore();
}

} // namespace Lumen
