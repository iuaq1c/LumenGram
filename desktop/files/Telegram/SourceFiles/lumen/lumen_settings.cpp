/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#include "lumen/lumen_settings.h"

#include "api/api_updates.h"
#include "core/application.h"
#include "core/core_settings.h"
#include "main/main_account.h"
#include "main/main_domain.h"
#include "main/main_session.h"
#include "ui/chat/chat_style_radius.h"

namespace Lumen {
namespace {

constexpr auto kGhostEnabledPref = "lumen/ghost_enabled"_cs;
constexpr auto kGhostRiskPref = "lumen/ghost_risk_acknowledged"_cs;
constexpr auto kStreamerModePref = "lumen/streamer_mode"_cs;
constexpr auto kShowPeerIdPref = "lumen/show_peer_id"_cs;
constexpr auto kHideBubbleTailPref = "lumen/hide_bubble_tail"_cs;
constexpr auto kHideShareButtonPref = "lumen/hide_share_button"_cs;
constexpr auto kBubbleRadiusPref = "lumen/bubble_radius"_cs;
constexpr auto kShowSecondsPref = "lumen/show_seconds"_cs;
constexpr auto kHideStoriesPref = "lumen/hide_stories"_cs;
constexpr auto kSquareUserpicsPref = "lumen/square_userpics"_cs;
constexpr auto kHideFolderCountersPref = "lumen/hide_folder_counters"_cs;
constexpr auto kPlainRepliesPref = "lumen/plain_replies"_cs;
constexpr auto kServiceTimePref = "lumen/service_time"_cs;
constexpr auto kHideSimilarChannelsPref = "lumen/hide_similar_channels"_cs;

struct AppearanceCache {
	bool loaded = false;
	bool hideBubbleTail = false;
	bool hideShareButton = false;
	bool showSeconds = false;
	bool hideFolderCounters = false;
	bool squareUserpics = false;
	bool plainReplies = false;
	bool serviceTime = false;
	bool hideSimilarChannels = false;
	rpl::variable<bool> hideStories = false;
};

[[nodiscard]] AppearanceCache &Appearance() {
	static auto result = AppearanceCache();
	if (!result.loaded) {
		auto &settings = Core::App().settings();
		result.hideBubbleTail = settings.readPref<bool>(kHideBubbleTailPref);
		result.hideShareButton = settings.readPref<bool>(
			kHideShareButtonPref);
		result.showSeconds = settings.readPref<bool>(kShowSecondsPref);
		result.hideFolderCounters = settings.readPref<bool>(
			kHideFolderCountersPref);
		result.squareUserpics = settings.readPref<bool>(
			kSquareUserpicsPref);
		result.hideStories = settings.readPref<bool>(kHideStoriesPref);
		result.plainReplies = settings.readPref<bool>(kPlainRepliesPref);
		result.serviceTime = settings.readPref<bool>(kServiceTimePref);
		result.hideSimilarChannels = settings.readPref<bool>(
			kHideSimilarChannelsPref);
		result.loaded = true;
	}
	return result;
}

[[nodiscard]] std::string_view OptionPref(GhostOption option) {
	switch (option) {
	case GhostOption::HideOnline: return "lumen/ghost_hide_online";
	case GhostOption::NoReadMessages: return "lumen/ghost_no_read";
	case GhostOption::NoReadStories: return "lumen/ghost_no_stories";
	case GhostOption::NoTyping: return "lumen/ghost_no_typing";
	case GhostOption::ReadOnInteract: return "lumen/ghost_read_on_interact";
	}
	Unexpected("Option in Lumen::OptionPref.");
}

[[nodiscard]] rpl::event_stream<> &Changes() {
	static auto result = rpl::event_stream<>();
	return result;
}

[[nodiscard]] rpl::event_stream<> &OtherChanges() {
	static auto result = rpl::event_stream<>();
	return result;
}

void Saved() {
	Core::App().saveSettingsDelayed();
	Changes().fire({});
	for (const auto &[index, account] : Core::App().domain().accounts()) {
		if (const auto session = account->maybeSession()) {
			session->updates().updateOnline();
		}
	}
}

} // namespace

bool GhostEnabled() {
	return Core::App().settings().readPref<bool>(kGhostEnabledPref);
}

rpl::producer<bool> GhostEnabledValue() {
	return rpl::single(GhostEnabled()) | rpl::then(
		Changes().events() | rpl::map([] { return GhostEnabled(); })
	) | rpl::distinct_until_changed();
}

void SetGhostEnabled(bool enabled) {
	if (GhostEnabled() != enabled) {
		Core::App().settings().writePref<bool>(kGhostEnabledPref, enabled);
		Saved();
	}
}

bool GhostOptionEnabled(GhostOption option) {
	return Core::App().settings().readPref<bool>(OptionPref(option), true);
}

void SetGhostOptionEnabled(GhostOption option, bool enabled) {
	if (GhostOptionEnabled(option) != enabled) {
		Core::App().settings().writePref<bool>(OptionPref(option), enabled);
		Saved();
	}
}

bool GhostActive(GhostOption option) {
	return GhostEnabled() && GhostOptionEnabled(option);
}

rpl::producer<> GhostChanges() {
	return Changes().events();
}

bool GhostRiskAcknowledged() {
	return Core::App().settings().readPref<bool>(kGhostRiskPref);
}

void SetGhostRiskAcknowledged() {
	Core::App().settings().writePref<bool>(kGhostRiskPref, true);
	Core::App().saveSettingsDelayed();
}

bool StreamerMode() {
	return Core::App().settings().readPref<bool>(kStreamerModePref);
}

rpl::producer<bool> StreamerModeValue() {
	return rpl::single(StreamerMode()) | rpl::then(
		OtherChanges().events() | rpl::map([] { return StreamerMode(); })
	) | rpl::distinct_until_changed();
}

void SetStreamerMode(bool enabled) {
	if (StreamerMode() != enabled) {
		Core::App().settings().writePref<bool>(kStreamerModePref, enabled);
		Core::App().saveSettingsDelayed();
		OtherChanges().fire({});
	}
}

bool ShowPeerId() {
	return Core::App().settings().readPref<bool>(kShowPeerIdPref, true);
}

bool HideBubbleTail() {
	return Appearance().hideBubbleTail;
}

void SetHideBubbleTail(bool hide) {
	Appearance().hideBubbleTail = hide;
	Core::App().settings().writePref<bool>(kHideBubbleTailPref, hide);
	Core::App().saveSettingsDelayed();
}

bool HideShareButton() {
	return Appearance().hideShareButton;
}

void SetHideShareButton(bool hide) {
	Appearance().hideShareButton = hide;
	Core::App().settings().writePref<bool>(kHideShareButtonPref, hide);
	Core::App().saveSettingsDelayed();
}

bool ShowSeconds() {
	return Appearance().showSeconds;
}

void SetShowSeconds(bool show) {
	Appearance().showSeconds = show;
	Core::App().settings().writePref<bool>(kShowSecondsPref, show);
	Core::App().saveSettingsDelayed();
}

QString FormatMessageTime(QTime time) {
	const auto locale = QLocale();
	if (!ShowSeconds()) {
		return locale.toString(time, QLocale::ShortFormat);
	}
	auto format = locale.timeFormat(QLocale::ShortFormat);
	if (!format.contains(u"ss"_q)) {
		format.replace(u"mm"_q, u"mm:ss"_q);
	}
	return locale.toString(time, format);
}

bool HideStories() {
	return Appearance().hideStories.current();
}

rpl::producer<bool> HideStoriesValue() {
	return Appearance().hideStories.value();
}

void SetHideStories(bool hide) {
	Appearance().hideStories = hide;
	Core::App().settings().writePref<bool>(kHideStoriesPref, hide);
	Core::App().saveSettingsDelayed();
}

bool SquareUserpics() {
	static const auto result = Appearance().squareUserpics;
	return result;
}

bool SquareUserpicsSaved() {
	return Appearance().squareUserpics;
}

void SetSquareUserpics(bool square) {
	Appearance().squareUserpics = square;
	Core::App().settings().writePref<bool>(kSquareUserpicsPref, square);
	Core::App().saveSettingsDelayed();
}

bool HideFolderCounters() {
	return Appearance().hideFolderCounters;
}

void SetHideFolderCounters(bool hide) {
	Appearance().hideFolderCounters = hide;
	Core::App().settings().writePref<bool>(kHideFolderCountersPref, hide);
	Core::App().saveSettingsDelayed();
}

bool PlainReplies() {
	return Appearance().plainReplies;
}

void SetPlainReplies(bool plain) {
	Appearance().plainReplies = plain;
	Core::App().settings().writePref<bool>(kPlainRepliesPref, plain);
	Core::App().saveSettingsDelayed();
}

bool ServiceTime() {
	return Appearance().serviceTime;
}

void SetServiceTime(bool show) {
	Appearance().serviceTime = show;
	Core::App().settings().writePref<bool>(kServiceTimePref, show);
	Core::App().saveSettingsDelayed();
}

bool HideSimilarChannels() {
	return Appearance().hideSimilarChannels;
}

void SetHideSimilarChannels(bool hide) {
	Appearance().hideSimilarChannels = hide;
	Core::App().settings().writePref<bool>(kHideSimilarChannelsPref, hide);
	Core::App().saveSettingsDelayed();
}

int BubbleRadius() {
	const auto value = Core::App().settings().readPref<QByteArray>(
		kBubbleRadiusPref);
	auto ok = false;
	const auto result = value.toInt(&ok);
	return (ok && result >= 0) ? result : kDefaultBubbleRadius;
}

void SetBubbleRadius(int radius) {
	Core::App().settings().writePref<QByteArray>(
		kBubbleRadiusPref,
		(radius >= 0) ? QByteArray::number(radius) : QByteArray());
	Core::App().saveSettingsDelayed();
}

void ApplyAppearance() {
	if (const auto radius = BubbleRadius(); radius >= 0) {
		Ui::SetBubbleRadiusLargeOverride(radius);
	}
}

void SetShowPeerId(bool enabled) {
	if (ShowPeerId() != enabled) {
		Core::App().settings().writePref<bool>(kShowPeerIdPref, enabled);
		Core::App().saveSettingsDelayed();
		OtherChanges().fire({});
	}
}

} // namespace Lumen
