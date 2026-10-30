/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#pragma once

namespace Window {
class SessionController;
} // namespace Window

namespace Lumen {

// Liquid glass: bars show the chat background under them blurred and tinted.
[[nodiscard]] bool LiquidGlass();
void SetLiquidGlass(bool enabled);

// Fills `rect` of `widget`, a child of `section` that paints the background.
void PaintGlass(
	QPainter &p,
	not_null<Window::SessionController*> controller,
	not_null<QWidget*> section,
	not_null<QWidget*> widget,
	QRect rect,
	const QColor &tint);

} // namespace Lumen
