/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#pragma once

#include "ui/effects/animations.h"

#include <QtCore/QPointer>

namespace Lumen {

struct IntroEntranceGroup {
	std::vector<QPointer<QWidget>> widgets;
	Fn<void()> appear;
};

class IntroEntrance final {
public:
	explicit IntroEntrance(Fn<void()> updated);

	void start(std::vector<IntroEntranceGroup> groups, int extraGroups);
	void finish();

	[[nodiscard]] int shift(int index) const;
	[[nodiscard]] bool animating() const;

private:
	struct Placed {
		QPointer<QWidget> widget;
		int index = 0;
		int y = 0;
		int applied = 0;
	};

	void apply();
	[[nodiscard]] float64 progress(int index) const;

	const Fn<void()> _updated;
	Ui::Animations::Simple _animation;
	std::vector<Placed> _placed;
	std::vector<Fn<void()>> _appear;
	int _groups = 0;

};

class IntroCrossfade final {
public:
	IntroCrossfade(QPixmap was, QPixmap now);

	void start(bool back, Fn<void()> updated);
	void paint(QPainter &p, QRect area) const;

	[[nodiscard]] bool animating() const;

private:
	void paintOne(
		QPainter &p,
		const QPixmap &pixmap,
		QRect area,
		float64 opacity,
		float64 scale,
		int shift) const;

	QPixmap _was;
	QPixmap _now;
	Ui::Animations::Simple _animation;
	bool _back = false;

};

} // namespace Lumen
