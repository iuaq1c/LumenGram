/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#pragma once

namespace Lumen {

// Alternative app icons for the window, taskbar and tray. "classic" is the bundled logo.
[[nodiscard]] const std::vector<QString> &AppIcons();
[[nodiscard]] QString AppIcon();
void SetAppIcon(const QString &id);

// The full-circle picture of an icon, for previews.
[[nodiscard]] QImage AppIconPreview(const QString &id);

// The chosen icon in place of Window::Logo() / LogoNoMargin(), or nullptr for classic.
[[nodiscard]] const QImage *CustomAppLogo(bool noMargin);

// Bumped on every change, so icon caches know when to drop scaled copies.
[[nodiscard]] int AppIconGeneration();

} // namespace Lumen
