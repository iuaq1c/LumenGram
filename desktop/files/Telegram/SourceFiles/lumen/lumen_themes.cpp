/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#include "lumen/lumen_themes.h"

#include "window/themes/window_theme.h"
#include "window/themes/window_themes_embedded.h"

namespace Lumen {

bool IsLumenTheme(Window::Theme::EmbeddedType type) {
	using Type = Window::Theme::EmbeddedType;
	return (type == Type::LumenLight)
		|| (type == Type::LumenDark)
		|| (type == Type::LumenBlack);
}

void ApplyDefaultTheme() {
	using namespace Window::Theme;
	using Type = EmbeddedType;
	const auto type = CurrentEmbeddedType();
	if (!type || IsLumenTheme(*type)) {
		return;
	}
	switch (*type) {
	case Type::Default:
		ToggleNightMode(NightThemePath());
		break;
	case Type::DayBlue:
		ApplyDefaultWithPath(DayThemePath());
		break;
	case Type::Night:
	case Type::NightGreen:
		ApplyDefaultWithPath(NightThemePath());
		break;
	default:
		return;
	}
	KeepApplied();
}

} // namespace Lumen
