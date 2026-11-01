/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#pragma once

namespace Data {
struct ColorProfileSet;
} // namespace Data

class PeerData;

namespace Ui {
class RpWidget;
template <typename Widget>
class SlideWrap;
} // namespace Ui

namespace Lumen {

// Dark colors for a profile top bar under a LumenGram banner.
[[nodiscard]] Data::ColorProfileSet BannerColorProfile();

// Center-cropped banner with a shade, so white text stays readable.
void PaintProfileBanner(QPainter &p, QRect rect, const QImage &banner);

// The banner stretched under the whole profile, not only under the photo.
[[nodiscard]] bool FullProfileBanner();
void SetFullProfileBanner(bool enabled);

// The banner over the whole profile area, `full` is in painter coordinates,
// and a shade of `bg` below `shadeTop` that keeps the profile readable.
void PaintFullProfileBanner(
	QPainter &p,
	QRect full,
	const QImage &banner,
	int shadeTop,
	QColor bg);

// The track the user is listening to right now, hidden without one.
[[nodiscard]] object_ptr<Ui::SlideWrap<Ui::RpWidget>> CreateNowPlaying(
	not_null<QWidget*> parent,
	not_null<PeerData*> peer);

// Calls done with the banner of the peer, or a null image without one.
void WatchProfileBanner(
	not_null<PeerData*> peer,
	Fn<void(QImage)> done,
	rpl::lifetime &lifetime);

} // namespace Lumen
