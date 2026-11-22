/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#pragma once

#include "base/timer.h"

namespace Lumen {

// Palette-colored aurora with twinkling stars behind the login screens.
class IntroBackground final {
public:
	explicit IntroBackground(Fn<void()> update);

	void paint(QPainter &p, QRect rect);

	void paintPulse(QPainter &p, QRect button, int radius);
	void setPulseActive(bool active);

private:
	struct Star {
		float64 x = 0.;
		float64 y = 0.;
		float64 size = 0.;
		float64 alpha = 0.;
		float64 twinkle = 0.;
		float64 shift = 0.;
		float64 depth = 0.;
	};

	void refreshColors();
	void refreshAnimated();
	void prepareFrame(QSize size);
	void paintStars(QPainter &p, QRect rect) const;
	[[nodiscard]] float64 phase() const;

	const Fn<void()> _update;
	base::Timer _timer;

	QColor _base;
	QColor _starColor;
	std::array<QColor, 3> _spots;
	std::vector<Star> _stars;
	QImage _frame;
	crl::time _started = 0;
	crl::time _pulseStarted = 0;
	bool _animated = false;
	bool _pulseActive = false;
	bool _dark = false;

	rpl::lifetime _lifetime;

};

} // namespace Lumen
