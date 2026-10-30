/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#include "lumen/lumen_feed.h"

#include "apiwrap.h"
#include "base/unixtime.h"
#include "data/data_document.h"
#include "data/data_document_media.h"
#include "data/data_file_origin.h"
#include "data/data_media_types.h"
#include "data/data_peer.h"
#include "data/data_photo.h"
#include "data/data_photo_media.h"
#include "data/data_session.h"
#include "history/history.h"
#include "history/history_item.h"
#include "lang/lang_keys.h"
#include "lang/lang_tag.h"
#include "main/main_session.h"
#include "settings/settings_common.h"
#include "ui/image/image.h"
#include "ui/layers/generic_box.h"
#include "ui/painter.h"
#include "ui/text/text.h"
#include "ui/text/text_options.h"
#include "ui/text/text_utilities.h"
#include "ui/widgets/buttons.h"
#include "ui/widgets/labels.h"
#include "ui/wrap/vertical_layout.h"
#include "ui/userpic_view.h"
#include "window/window_session_controller.h"
#include "styles/style_boxes.h"
#include "styles/style_layers.h"
#include "styles/style_settings.h"

namespace Lumen {
namespace {

constexpr auto kPageSize = 30;
constexpr auto kFeedDays = 30;
constexpr auto kPadding = 12;
constexpr auto kUserpicSize = 36;
constexpr auto kSkip = 8;
constexpr auto kMaxTextLines = 12;
constexpr auto kPhotoMaxHeight = 240;
constexpr auto kCardSkip = 8;

// Spoiler text would be drawn open in a plain card, so it is covered.
[[nodiscard]] TextWithEntities HideSpoilers(TextWithEntities text) {
	for (auto i = text.entities.begin(); i != text.entities.end();) {
		if (i->type() != EntityType::Spoiler) {
			++i;
			continue;
		}
		for (auto j = i->offset(); j != i->offset() + i->length(); ++j) {
			if (j < text.text.size() && text.text[j] != QChar('\n')) {
				text.text[j] = QChar(0x2592);
			}
		}
		i = text.entities.erase(i);
	}
	return text;
}

class FeedPost final : public Ui::AbstractButton {
public:
	FeedPost(QWidget *parent, not_null<HistoryItem*> item);

protected:
	int resizeGetHeight(int newWidth) override;
	void paintEvent(QPaintEvent *e) override;

private:
	[[nodiscard]] Image *photoImage() const;
	[[nodiscard]] int textHeight(int width) const;
	[[nodiscard]] int photoHeight(int width) const;

	const not_null<HistoryItem*> _item;
	const not_null<PeerData*> _peer;
	Ui::PeerUserpicView _userpic;
	Ui::Text::String _name;
	Ui::Text::String _text;
	QString _date;
	QString _views;
	bool _spoiler = false;
	std::shared_ptr<Data::PhotoMedia> _photoMedia;
	std::shared_ptr<Data::DocumentMedia> _documentMedia;

};

FeedPost::FeedPost(QWidget *parent, not_null<HistoryItem*> item)
: AbstractButton(parent)
, _item(item)
, _peer(item->history()->peer) {
	_name.setText(st::semiboldTextStyle, _peer->name());
	_text.setMarkedText(
		st::defaultTextStyle,
		HideSpoilers(item->originalText()),
		Ui::ItemTextDefaultOptions());
	_date = langDateTime(base::unixtime::parse(item->date()));
	if (const auto views = item->viewsCount(); views > 0) {
		_views = QString::fromUtf8("\xF0\x9F\x91\x81 ")
			+ Lang::FormatCountToShort(views).string;
	}
	if (const auto media = item->media()) {
		_spoiler = media->hasSpoiler();
		if (const auto photo = media->photo()) {
			_photoMedia = photo->createMediaView();
			if (!_spoiler) {
				_photoMedia->wanted(
					Data::PhotoSize::Thumbnail,
					item->fullId());
			}
		} else if (const auto document = media->document()) {
			if (document->hasThumbnail()) {
				_documentMedia = document->createMediaView();
				if (!_spoiler) {
					_documentMedia->thumbnailWanted(item->fullId());
				}
			}
		}
	}
	item->history()->session().downloaderTaskFinished(
	) | rpl::on_next([=] {
		update();
	}, lifetime());
	setCursor(style::cur_pointer);
}

Image *FeedPost::photoImage() const {
	// WHY: spoilers and hidden media show only the tiny inline preview,
	// which is blurry by itself when scaled up.
	if (_photoMedia) {
		if (!_spoiler) {
			if (const auto image = _photoMedia->image(
					Data::PhotoSize::Thumbnail)) {
				return image;
			}
		}
		return _photoMedia->thumbnailInline();
	} else if (_documentMedia) {
		if (!_spoiler) {
			if (const auto image = _documentMedia->thumbnail()) {
				return image;
			}
		}
		return _documentMedia->thumbnailInline();
	}
	return nullptr;
}

int FeedPost::textHeight(int width) const {
	if (_text.isEmpty()) {
		return 0;
	}
	return std::min(
		_text.countHeight(width),
		st::defaultTextStyle.font->height * kMaxTextLines);
}

int FeedPost::photoHeight(int width) const {
	if (!_photoMedia && !_documentMedia) {
		return 0;
	}
	return std::min(width * 9 / 16, style::ConvertScale(kPhotoMaxHeight));
}

int FeedPost::resizeGetHeight(int newWidth) {
	const auto padding = style::ConvertScale(kPadding);
	const auto skip = style::ConvertScale(kSkip);
	const auto inner = newWidth - 2 * padding;
	auto result = padding + style::ConvertScale(kUserpicSize);
	if (const auto text = textHeight(inner)) {
		result += skip + text;
	}
	if (const auto photo = photoHeight(inner)) {
		result += skip + photo;
	}
	if (!_views.isEmpty()) {
		result += skip + st::normalFont->height;
	}
	return result + padding + style::ConvertScale(kCardSkip);
}

void FeedPost::paintEvent(QPaintEvent *e) {
	auto p = Painter(this);
	PainterHighQualityEnabler hq(p);

	const auto padding = style::ConvertScale(kPadding);
	const auto skip = style::ConvertScale(kSkip);
	const auto card = rect().marginsRemoved(
		{ 0, 0, 0, style::ConvertScale(kCardSkip) });
	p.setPen(Qt::NoPen);
	p.setBrush(isOver() ? st::windowBgRipple : st::windowBgOver);
	p.drawRoundedRect(card, st::boxRadius, st::boxRadius);

	const auto inner = width() - 2 * padding;
	const auto userpic = style::ConvertScale(kUserpicSize);
	_peer->paintUserpicLeft(p, _userpic, padding, padding, width(), userpic);

	const auto titleLeft = padding + userpic + skip;
	const auto titleWidth = width() - titleLeft - padding;
	p.setPen(st::windowBoldFg);
	_name.drawLeftElided(
		p,
		titleLeft,
		padding,
		titleWidth,
		width());
	p.setPen(st::windowSubTextFg);
	p.setFont(st::normalFont);
	p.drawTextLeft(
		titleLeft,
		padding + userpic - st::normalFont->height,
		width(),
		_date);

	auto top = padding + userpic;
	if (const auto text = textHeight(inner)) {
		top += skip;
		p.setPen(st::windowFg);
		_text.draw(p, {
			.position = { padding, top },
			.outerWidth = width(),
			.availableWidth = inner,
			.elisionLines = kMaxTextLines,
		});
		top += text;
	}
	if (const auto photo = photoHeight(inner)) {
		top += skip;
		const auto target = QRect(padding, top, inner, photo);
		if (const auto image = photoImage()) {
			const auto source = image->original();
			const auto scaled = source.size().scaled(
				target.size(),
				Qt::KeepAspectRatioByExpanding);
			const auto from = QRect(
				(scaled.width() - target.width()) / 2,
				(scaled.height() - target.height()) / 2,
				target.width(),
				target.height());
			p.save();
			auto path = QPainterPath();
			path.addRoundedRect(target, st::boxRadius, st::boxRadius);
			p.setClipPath(path);
			p.drawImage(
				target,
				source.scaled(
					scaled,
					Qt::IgnoreAspectRatio,
					Qt::SmoothTransformation),
				from);
			p.restore();
		} else {
			p.setBrush(st::windowBgRipple);
			p.drawRoundedRect(target, st::boxRadius, st::boxRadius);
		}
		top += photo;
	}
	if (!_views.isEmpty()) {
		top += skip;
		p.setPen(st::windowSubTextFg);
		p.setFont(st::normalFont);
		p.drawTextRight(padding, top, width(), _views);
	}
}

struct FeedState {
	int nextRate = 0;
	PeerData *nextPeer = nullptr;
	MsgId nextId = 0;
	MessageGroupId lastGroup;
	bool loading = false;
	bool finished = false;
	int shown = 0;
	mtpRequestId requestId = 0;
};

} // namespace

void ShowFeed(not_null<Window::SessionController*> controller) {
	const auto session = &controller->session();
	controller->show(Box([=](not_null<Ui::GenericBox*> box) {
		box->setTitle(tr::lng_lumen_feed());
		box->setWidth(st::boxWideWidth);
		const auto container = box->verticalLayout();
		const auto state = box->lifetime().make_state<FeedState>();
		const auto list = container->add(
			object_ptr<Ui::VerticalLayout>(container),
			style::margins(
				st::boxRowPadding.left(),
				0,
				st::boxRowPadding.right(),
				0));
		const auto footer = container->add(
			object_ptr<Ui::VerticalLayout>(container));

		const auto load = std::make_shared<Fn<void()>>();
		const auto refreshFooter = [=] {
			footer->clear();
			if (state->loading) {
				footer->add(
					object_ptr<Ui::FlatLabel>(
						footer,
						tr::lng_contacts_loading(),
						st::boxDividerLabel),
					st::boxPadding);
			} else if (state->finished) {
				footer->add(
					object_ptr<Ui::FlatLabel>(
						footer,
						(state->shown
							? tr::lng_lumen_feed_end()
							: tr::lng_lumen_feed_empty()),
						st::boxDividerLabel),
					st::boxPadding);
			} else {
				Settings::AddButtonWithIcon(
					footer,
					tr::lng_lumen_feed_more(),
					st::settingsButtonNoIcon
				)->addClickHandler([=] { (*load)(); });
			}
			footer->resizeToWidth(st::boxWideWidth);
		};

		const auto received = [=](const MTPmessages_Messages &result) {
			state->requestId = 0;
			state->loading = false;
			auto added = 0;
			const auto process = [&](const MTPVector<MTPMessage> &messages) {
				auto last = (const MTPMessage*)nullptr;
				for (const auto &message : messages.v) {
					last = &message;
					const auto peerId = PeerFromMessage(message);
					if (!peerIsChannel(peerId)
						|| !session->data().peerLoaded(peerId)
						|| !DateFromMessage(message)) {
						continue;
					}
					const auto item = session->data().addNewMessage(
						message,
						MessageFlags(),
						NewMessageType::Existing);
					if (!item || item->isService()) {
						continue;
					}
					// An album comes as one message per photo: keep one card
					const auto group = item->groupId();
					if (group && group == state->lastGroup) {
						continue;
					}
					state->lastGroup = group;
					const auto post = list->add(
						object_ptr<FeedPost>(list, item));
					post->setClickedCallback([=] {
						const auto peer = item->history()->peer;
						const auto id = item->id;
						box->closeBox();
						controller->showPeerHistory(
							peer,
							Window::SectionShow::Way::ClearStack,
							id);
					});
					++added;
				}
				if (last) {
					state->nextPeer = session->data().peerLoaded(
						PeerFromMessage(*last));
					state->nextId = IdFromMessage(*last);
				}
				return int(messages.v.size());
			};
			result.match([&](const MTPDmessages_messages &data) {
				session->data().processUsers(data.vusers());
				session->data().processChats(data.vchats());
				process(data.vmessages());
				state->finished = true;
			}, [&](const MTPDmessages_messagesSlice &data) {
				session->data().processUsers(data.vusers());
				session->data().processChats(data.vchats());
				const auto count = process(data.vmessages());
				const auto next = data.vnext_rate();
				if (!count || !next || next->v == state->nextRate) {
					state->finished = !count || !next;
				}
				if (next) {
					state->nextRate = next->v;
				}
			}, [&](const MTPDmessages_channelMessages &data) {
				session->data().processUsers(data.vusers());
				session->data().processChats(data.vchats());
				process(data.vmessages());
				state->finished = true;
			}, [&](const MTPDmessages_messagesNotModified &) {
				state->finished = true;
			});
			state->shown += added;
			list->resizeToWidth(
				st::boxWideWidth
					- st::boxRowPadding.left()
					- st::boxRowPadding.right());
			refreshFooter();
		};

		*load = [=] {
			if (state->loading || state->finished) {
				return;
			}
			state->loading = true;
			refreshFooter();
			const auto now = base::unixtime::now();
			using Flag = MTPmessages_SearchGlobal::Flag;
			state->requestId = session->api().request(
				MTPmessages_SearchGlobal(
					MTP_flags(Flag::f_broadcasts_only),
					MTP_int(0), // folder_id
					MTPInputChannel(),
					MTP_string(),
					MTP_inputMessagesFilterEmpty(),
					MTP_int(now - kFeedDays * 24 * 60 * 60),
					MTP_int(now + 60),
					MTP_int(state->nextRate),
					(state->nextPeer
						? state->nextPeer->input()
						: MTP_inputPeerEmpty()),
					MTP_int(state->nextId.bare),
					MTP_int(kPageSize))
			).done(received).fail([=] {
				state->requestId = 0;
				state->loading = false;
				state->finished = true;
				refreshFooter();
			}).send();
		};

		box->addButton(tr::lng_close(), [=] { box->closeBox(); });
		box->boxClosing() | rpl::on_next([=] {
			session->api().request(base::take(state->requestId)).cancel();
		}, box->lifetime());
		(*load)();
	}));
}

} // namespace Lumen
