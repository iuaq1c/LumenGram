/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#pragma once

namespace Window::Theme {
enum class EmbeddedType;
} // namespace Window::Theme

namespace Lumen {

[[nodiscard]] bool IsLumenTheme(Window::Theme::EmbeddedType type);

void ApplyDefaultTheme();

} // namespace Lumen
