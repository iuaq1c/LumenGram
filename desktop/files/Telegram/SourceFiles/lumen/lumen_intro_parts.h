/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#pragma once

namespace style {
struct InputField;
} // namespace style

namespace Lumen {

void PaintIntroCover(QPainter &p, int width, int top);

[[nodiscard]] const style::InputField &IntroField(
	const style::InputField &st);

} // namespace Lumen
