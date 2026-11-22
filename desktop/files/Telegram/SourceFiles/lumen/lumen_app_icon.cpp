/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#include "lumen/lumen_app_icon.h"

#include "core/application.h"
#include "core/core_settings.h"
#include "main/main_domain.h"
#include "tray.h"

namespace Lumen {
namespace {

constexpr auto kAppIconPref = "lumen/app_icon"_cs;
constexpr auto kClassic = "classic"_cs;
constexpr auto kLogoSide = 256;
constexpr auto kLogoMargin = 7;

struct LoadedIcon {
	QString id;
	QImage noMargin;
	QImage withMargin;
};

[[nodiscard]] std::optional<QString> &Chosen() {
	static auto result = std::optional<QString>();
	return result;
}

[[nodiscard]] LoadedIcon &Loaded() {
	static auto result = LoadedIcon();
	return result;
}

int &Generation() {
	static auto result = 0;
	return result;
}

[[nodiscard]] QString ResourcePath(const QString &id) {
	return (id == kClassic.utf16())
		? u":/gui/art/logo_256_no_margin.png"_q
		: (u":/gui/art/lumen_icons/"_q + id + u".png"_q);
}

// Same 7px transparent border as logo_256.png, which the tray and window icons expect.
[[nodiscard]] QImage AddMargin(const QImage &image) {
	auto result = QImage(
		QSize(kLogoSide, kLogoSide),
		QImage::Format_ARGB32_Premultiplied);
	result.fill(Qt::transparent);
	auto p = QPainter(&result);
	p.setRenderHint(QPainter::SmoothPixmapTransform);
	const auto side = kLogoSide - 2 * kLogoMargin;
	p.drawImage(QRect(kLogoMargin, kLogoMargin, side, side), image);
	p.end();
	return result;
}

void Apply() {
	++Generation();
	Core::App().refreshApplicationIcon();
	Core::App().tray().updateIconCounters();
	Core::App().domain().notifyUnreadBadgeChanged();
}

} // namespace

const std::vector<QString> &AppIcons() {
	static const auto result = std::vector<QString>{
		kClassic.utf16(),
		u"night"_q,
		u"light"_q,
		u"sunset"_q,
		u"aurora"_q,
		u"ocean"_q,
		u"sakura"_q,
		u"gold"_q,
		u"glass"_q,
		u"line"_q,
		u"neon"_q,
		u"pixel"_q,
	};
	return result;
}

QString AppIcon() {
	auto &chosen = Chosen();
	if (!chosen) {
		const auto saved = QString::fromUtf8(
			Core::App().settings().readPref<QByteArray>(kAppIconPref));
		chosen = ranges::contains(AppIcons(), saved)
			? saved
			: kClassic.utf16();
	}
	return *chosen;
}

void SetAppIcon(const QString &id) {
	if (!ranges::contains(AppIcons(), id) || id == AppIcon()) {
		return;
	}
	Chosen() = id;
	Core::App().settings().writePref<QByteArray>(kAppIconPref, id.toUtf8());
	Core::App().saveSettingsDelayed();
	Apply();
}

QImage AppIconPreview(const QString &id) {
	return QImage(ResourcePath(id));
}

const QImage *CustomAppLogo(bool noMargin) {
	// The crash report window asks for the logo before settings exist.
	if (!Core::IsAppLaunched()) {
		return nullptr;
	}
	const auto id = AppIcon();
	if (id == kClassic.utf16()) {
		return nullptr;
	}
	auto &loaded = Loaded();
	if (loaded.id != id) {
		auto image = QImage(ResourcePath(id));
		if (image.isNull()) {
			return nullptr;
		}
		loaded.withMargin = AddMargin(image);
		loaded.noMargin = std::move(image);
		loaded.id = id;
	}
	return noMargin ? &loaded.noMargin : &loaded.withMargin;
}

int AppIconGeneration() {
	return Generation();
}

} // namespace Lumen
