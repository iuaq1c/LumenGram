/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#pragma once

namespace Lumen {

enum class GhostOption {
	HideOnline,
	NoReadMessages,
	NoReadStories,
	NoTyping,
	ReadOnInteract,
};

[[nodiscard]] bool GhostEnabled();
[[nodiscard]] rpl::producer<bool> GhostEnabledValue();
void SetGhostEnabled(bool enabled);

[[nodiscard]] bool GhostOptionEnabled(GhostOption option);
void SetGhostOptionEnabled(GhostOption option, bool enabled);

[[nodiscard]] bool GhostActive(GhostOption option);
[[nodiscard]] rpl::producer<> GhostChanges();

[[nodiscard]] bool GhostRiskAcknowledged();
void SetGhostRiskAcknowledged();

[[nodiscard]] bool StreamerMode();
[[nodiscard]] rpl::producer<bool> StreamerModeValue();
void SetStreamerMode(bool enabled);

[[nodiscard]] bool ShowPeerId();
void SetShowPeerId(bool enabled);

[[nodiscard]] bool HideBubbleTail();
void SetHideBubbleTail(bool hide);

[[nodiscard]] bool HideShareButton();
void SetHideShareButton(bool hide);

inline constexpr auto kDefaultBubbleRadius = -1;
[[nodiscard]] int BubbleRadius();
void SetBubbleRadius(int radius);

[[nodiscard]] bool ShowSeconds();
void SetShowSeconds(bool show);
[[nodiscard]] QString FormatMessageTime(QTime time);

[[nodiscard]] bool HideStories();
[[nodiscard]] rpl::producer<bool> HideStoriesValue();
void SetHideStories(bool hide);

[[nodiscard]] bool SquareUserpics();
[[nodiscard]] bool SquareUserpicsSaved();
void SetSquareUserpics(bool square);

[[nodiscard]] bool HideFolderCounters();
void SetHideFolderCounters(bool hide);

[[nodiscard]] bool PlainReplies();
void SetPlainReplies(bool plain);

[[nodiscard]] bool ServiceTime();
void SetServiceTime(bool show);

[[nodiscard]] bool HideSimilarChannels();
void SetHideSimilarChannels(bool hide);

void ApplyAppearance();

} // namespace Lumen
