/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#include "lumen/lumen_profile_ui.h"

#include "base/timer.h"
#include "data/data_peer.h"
#include "data/data_peer_colors.h"
#include "lumen/lumen_server.h"
#include "ui/basic_click_handlers.h"
#include "ui/painter.h"
#include "ui/widgets/buttons.h"
#include "ui/wrap/slide_wrap.h"
#include "styles/style_widgets.h"

#include <QtGui/QPainterPath>

namespace Lumen {
namespace {

constexpr auto kCoverSize = 48;
constexpr auto kCoverRadius = 6;
constexpr auto kPaddingLeft = 23;
constexpr auto kPaddingRight = 20;
constexpr auto kPaddingTop = 8;
constexpr auto kCoverSkip = 14;
constexpr auto kBarHeight = 3;
constexpr auto kBarSkip = 6;
constexpr auto kTimeSkip = 8;
constexpr auto kProgressTick = crl::time(1000);

[[nodiscard]] QString FormatTime(int seconds) {
	seconds = std::max(seconds, 0);
	return QString::number(seconds / 60)
		+ ':'
		+ QString::number(seconds % 60).rightJustified(2, '0');
}

void DrawCropped(QPainter &p, QRect rect, const QImage &image) {
	const auto source = QSizeF(image.size());
	const auto scale = std::max(
		rect.width() / source.width(),
		rect.height() / source.height());
	const auto part = QSizeF(rect.width() / scale, rect.height() / scale);
	p.drawImage(
		QRectF(rect),
		image,
		QRectF(
			QPointF(
				(source.width() - part.width()) / 2.,
				(source.height() - part.height()) / 2.),
			part));
}

class NowPlayingWidget final : public Ui::RippleButton {
public:
	NowPlayingWidget(QWidget *parent, uint64 userId);

	[[nodiscard]] rpl::producer<bool> hasTrackValue() const;

private:
	void paintEvent(QPaintEvent *e) override;
	void refresh();
	void paintCover(QPainter &p, QRect rect);

	const uint64 _userId = 0;
	std::optional<NowPlaying> _track;
	QString _coverUrl;
	QImage _cover;
	base::Timer _tick;
	rpl::variable<bool> _hasTrack = false;
	const std::shared_ptr<bool> _alive = std::make_shared<bool>(true);

};

NowPlayingWidget::NowPlayingWidget(QWidget *parent, uint64 userId)
: RippleButton(parent, st::defaultRippleAnimation)
, _userId(userId)
, _tick([=] { update(); }) {
	resize(width(), style::ConvertScale(kCoverSize + 2 * kPaddingTop));
	setClickedCallback([=] {
		if (_track && !_track->url.isEmpty()) {
			UrlClickHandler::Open(_track->url);
		}
	});
	lifetime().add([alive = _alive] { *alive = false; });
	ProfilesChanges() | rpl::on_next([=] {
		refresh();
	}, lifetime());
	refresh();
}

rpl::producer<bool> NowPlayingWidget::hasTrackValue() const {
	return _hasTrack.value();
}

void NowPlayingWidget::refresh() {
	_track = CurrentTrack(_userId);
	_hasTrack = _track.has_value();
	setCursor((_track && !_track->url.isEmpty())
		? style::cur_pointer
		: style::cur_default);
	if (_track && _track->duration > 0) {
		if (!_tick.isActive()) {
			_tick.callEach(kProgressTick);
		}
	} else {
		_tick.cancel();
	}
	const auto url = _track ? _track->cover : QString();
	if (url != _coverUrl) {
		_coverUrl = url;
		_cover = QImage();
		if (!url.isEmpty()) {
			const auto alive = _alive;
			LoadImage(url, [=](QImage image) {
				if (*alive && _coverUrl == url) {
					_cover = std::move(image);
					update();
				}
			});
		}
	}
	update();
}

void NowPlayingWidget::paintCover(QPainter &p, QRect rect) {
	auto hq = PainterHighQualityEnabler(p);
	auto path = QPainterPath();
	const auto radius = style::ConvertScale(kCoverRadius);
	path.addRoundedRect(rect, radius, radius);
	p.save();
	p.setClipPath(path);
	if (!_cover.isNull()) {
		DrawCropped(p, rect, _cover);
	} else {
		p.fillRect(rect, st::windowBgOver);
		p.setPen(st::windowSubTextFg);
		p.setFont(st::semiboldFont);
		p.drawText(rect, QString(QChar(0x266B)), style::al_center);
	}
	p.restore();
}

void NowPlayingWidget::paintEvent(QPaintEvent *e) {
	auto p = QPainter(this);
	paintRipple(p, 0, 0);
	if (!_track) {
		return;
	}
	const auto cover = style::ConvertScale(kCoverSize);
	const auto left = style::ConvertScale(kPaddingLeft);
	paintCover(p, QRect(left, (height() - cover) / 2, cover, cover));

	const auto textLeft = left + cover + style::ConvertScale(kCoverSkip);
	const auto textWidth = width()
		- textLeft
		- style::ConvertScale(kPaddingRight);
	if (textWidth <= 0) {
		return;
	}
	const auto &titleFont = st::semiboldFont;
	const auto &subFont = st::normalFont;
	const auto hasProgress = (_track->duration > 0);
	const auto barSkip = style::ConvertScale(kBarSkip);
	const auto blockHeight = titleFont->height
		+ subFont->height
		+ (hasProgress ? (barSkip + subFont->height) : 0);
	auto y = (height() - blockHeight) / 2;

	p.setPen(st::windowFg);
	p.setFont(titleFont);
	p.drawText(
		textLeft,
		y + titleFont->ascent,
		titleFont->elided(_track->title, textWidth));
	y += titleFont->height;

	const auto provider = _track->spotify ? u"Spotify"_q : u"Last.fm"_q;
	const auto subtitle = _track->artist.isEmpty()
		? provider
		: (_track->artist + u" · "_q + provider);
	p.setPen(st::windowSubTextFg);
	p.setFont(subFont);
	p.drawText(
		textLeft,
		y + subFont->ascent,
		subFont->elided(subtitle, textWidth));
	y += subFont->height;
	if (!hasProgress) {
		return;
	}

	const auto elapsed = std::clamp(
		int((QDateTime::currentMSecsSinceEpoch() - _track->startedAt) / 1000),
		0,
		_track->duration);
	const auto time = FormatTime(elapsed)
		+ u" / "_q
		+ FormatTime(_track->duration);
	const auto timeWidth = subFont->width(time);
	const auto barWidth = textWidth
		- timeWidth
		- style::ConvertScale(kTimeSkip);
	y += barSkip;
	if (barWidth > 0) {
		const auto bar = style::ConvertScale(kBarHeight);
		const auto barTop = y + (subFont->height - bar) / 2;
		const auto filled = barWidth * elapsed / _track->duration;
		auto hq = PainterHighQualityEnabler(p);
		p.setPen(Qt::NoPen);
		p.setBrush(st::windowBgOver);
		p.drawRoundedRect(
			QRect(textLeft, barTop, barWidth, bar),
			bar / 2.,
			bar / 2.);
		if (filled > 0) {
			p.setBrush(st::windowSubTextFg);
			p.drawRoundedRect(
				QRect(textLeft, barTop, filled, bar),
				bar / 2.,
				bar / 2.);
		}
	}
	p.setPen(st::windowSubTextFg);
	p.drawText(textLeft + textWidth - timeWidth, y + subFont->ascent, time);
}

} // namespace

Data::ColorProfileSet BannerColorProfile() {
	return Data::ColorProfileSet{
		.palette = { QColor(0x87, 0x74, 0xe1) },
		.bg = { QColor(0x2b, 0x23, 0x50), QColor(0x15, 0x11, 0x1f) },
		.story = { QColor(0x87, 0x74, 0xe1), QColor(0xa9, 0x96, 0xff) },
	};
}

void PaintProfileBanner(QPainter &p, QRect rect, const QImage &banner) {
	if (banner.isNull() || rect.isEmpty()) {
		return;
	}
	p.save();
	p.setRenderHint(QPainter::SmoothPixmapTransform);
	DrawCropped(p, rect, banner);
	auto shade = QLinearGradient(rect.topLeft(), rect.bottomLeft());
	shade.setColorAt(0., QColor(0, 0, 0, 40));
	shade.setColorAt(1., QColor(0, 0, 0, 150));
	p.fillRect(rect, shade);
	p.restore();
}

object_ptr<Ui::SlideWrap<Ui::RpWidget>> CreateNowPlaying(
		not_null<QWidget*> parent,
		not_null<PeerData*> peer) {
	const auto userId = peer->isUser() ? peerToUser(peer->id).bare : 0;
	auto widget = object_ptr<NowPlayingWidget>(parent.get(), userId);
	const auto raw = widget.data();
	auto result = object_ptr<Ui::SlideWrap<Ui::RpWidget>>(
		parent.get(),
		std::move(widget));
	result->toggleOn(userId
		? raw->hasTrackValue()
		: rpl::producer<bool>(rpl::single(false)));
	result->finishAnimating();
	return result;
}

void WatchProfileBanner(
		not_null<PeerData*> peer,
		Fn<void(QImage)> done,
		rpl::lifetime &lifetime) {
	if (!peer->isUser()) {
		return;
	}
	const auto userId = peerToUser(peer->id).bare;
	const auto shown = std::make_shared<QString>();
	const auto alive = std::make_shared<bool>(true);
	lifetime.add([=] { *alive = false; });
	const auto refresh = [=] {
		const auto url = BannerUrl(userId);
		if (url == *shown) {
			return;
		}
		*shown = url;
		if (url.isEmpty()) {
			done(QImage());
			return;
		}
		LoadImage(url, [=](QImage image) {
			if (*alive && *shown == url) {
				done(std::move(image));
			}
		});
	};
	ProfilesChanges() | rpl::on_next(refresh, lifetime);
	refresh();
}

} // namespace Lumen
