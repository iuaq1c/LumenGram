/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#include "lumen/lumen_settings_section.h"

#include "core/application.h"
#include "core/click_handler_types.h"
#include "core/file_utilities.h"
#include "core/version.h"
#include "lang/lang_keys.h"
#include "lumen/lumen_app_icon.h"
#include "lumen/lumen_auto_reply.h"
#include "lumen/lumen_bookmarks.h"
#include "lumen/lumen_branding.h"
#include "lumen/lumen_connection.h"
#include "lumen/lumen_ghost.h"
#include "lumen/lumen_glass.h"
#include "lumen/lumen_message_history.h"
#include "lumen/lumen_profile_account.h"
#include "lumen/lumen_profile_ui.h"
#include "lumen/lumen_settings.h"
#include "lumen/lumen_transcribe.h"
#include "lumen/lumen_templates.h"
#include "lumen/lumen_undo_send.h"
#include "mainwindow.h"
#include "settings/sections/settings_main.h"
#include "settings/settings_builder.h"
#include "settings/settings_common_session.h"
#include "ui/boxes/confirm_box.h"
#include "ui/boxes/single_choice_box.h"
#include "ui/layers/generic_box.h"
#include "ui/painter.h"
#include "ui/widgets/buttons.h"
#include "ui/widgets/fields/input_field.h"
#include "ui/wrap/vertical_layout.h"
#include "ui/rp_widget.h"
#include "window/window_session_controller.h"
#include "styles/style_boxes.h"
#include "styles/style_layers.h"
#include "styles/style_menu_icons.h"
#include "styles/style_settings.h"

namespace Settings {
namespace {

using namespace Builder;
using Lumen::GhostOption;

constexpr auto kBubbleRadiusOptions = std::array{ -1, 0, 4, 8, 12, 16 };
constexpr auto kLogoSize = 84;
constexpr auto kLogoTop = 20;
constexpr auto kTitleSkip = 14;
constexpr auto kAboutSkip = 4;
constexpr auto kBottomSkip = 16;
constexpr auto kIconColumns = 6;
constexpr auto kIconMaxSize = 48;
constexpr auto kIconRing = 2;
constexpr auto kIconRingGap = 3;

class Header final : public Ui::RpWidget {
public:
	explicit Header(QWidget *parent);

protected:
	void paintEvent(QPaintEvent *e) override;

private:
	const QImage _logo;
	const QString _about;

};

Header::Header(QWidget *parent)
: RpWidget(parent)
, _logo(Lumen::AppIconPreview(Lumen::AppIcon()))
, _about(tr::lng_lumen_settings_about(
	tr::now,
	lt_version,
	QString::fromLatin1(AppVersionStr))) {
	resize(width(), style::ConvertScale(kLogoTop + kLogoSize + kTitleSkip)
		+ st::boxTitle.style.font->height
		+ style::ConvertScale(kAboutSkip)
		+ st::normalFont->height
		+ style::ConvertScale(kBottomSkip));
}

void Header::paintEvent(QPaintEvent *e) {
	auto p = QPainter(this);
	p.setRenderHint(QPainter::SmoothPixmapTransform);
	const auto size = style::ConvertScale(kLogoSize);
	const auto logoTop = style::ConvertScale(kLogoTop);
	p.drawImage(QRect((width() - size) / 2, logoTop, size, size), _logo);

	const auto &titleFont = st::boxTitle.style.font;
	const auto titleTop = logoTop + size + style::ConvertScale(kTitleSkip);
	p.setPen(st::windowBoldFg);
	p.setFont(titleFont);
	p.drawText(
		QRect(0, titleTop, width(), titleFont->height),
		Lumen::kAppDisplayName.utf16(),
		style::al_top);

	const auto aboutTop = titleTop
		+ titleFont->height
		+ style::ConvertScale(kAboutSkip);
	p.setPen(st::windowSubTextFg);
	p.setFont(st::normalFont);
	p.drawText(
		QRect(0, aboutTop, width(), st::normalFont->height),
		_about,
		style::al_top);
}

class AppIconPicker final : public Ui::RpWidget {
public:
	explicit AppIconPicker(QWidget *parent);

protected:
	int resizeGetHeight(int newWidth) override;
	void paintEvent(QPaintEvent *e) override;
	void mouseMoveEvent(QMouseEvent *e) override;
	void mousePressEvent(QMouseEvent *e) override;

private:
	[[nodiscard]] int cellSize(int width) const;
	[[nodiscard]] QRect iconRect(int index) const;
	[[nodiscard]] int iconAt(QPoint point) const;

	std::vector<QImage> _icons;

};

AppIconPicker::AppIconPicker(QWidget *parent)
: RpWidget(parent) {
	for (const auto &id : Lumen::AppIcons()) {
		_icons.push_back(Lumen::AppIconPreview(id));
	}
	setMouseTracking(true);
}

int AppIconPicker::cellSize(int width) const {
	const auto padding = st::boxRowPadding;
	return (width - padding.left() - padding.right()) / kIconColumns;
}

int AppIconPicker::resizeGetHeight(int newWidth) {
	const auto rows = (int(_icons.size()) + kIconColumns - 1) / kIconColumns;
	return rows * cellSize(newWidth);
}

QRect AppIconPicker::iconRect(int index) const {
	const auto cell = cellSize(width());
	const auto ring = style::ConvertScale(kIconRing + kIconRingGap);
	const auto size = std::min(
		cell - 2 * ring,
		style::ConvertScale(kIconMaxSize));
	const auto column = index % kIconColumns;
	const auto row = index / kIconColumns;
	return QRect(
		st::boxRowPadding.left() + column * cell + (cell - size) / 2,
		row * cell + (cell - size) / 2,
		size,
		size);
}

int AppIconPicker::iconAt(QPoint point) const {
	for (auto i = 0; i != int(_icons.size()); ++i) {
		if (iconRect(i).contains(point)) {
			return i;
		}
	}
	return -1;
}

void AppIconPicker::paintEvent(QPaintEvent *e) {
	auto p = QPainter(this);
	PainterHighQualityEnabler hq(p);
	const auto &ids = Lumen::AppIcons();
	const auto chosen = Lumen::AppIcon();
	for (auto i = 0; i != int(_icons.size()); ++i) {
		const auto rect = iconRect(i);
		p.drawImage(rect, _icons[i]);
		if (ids[i] == chosen) {
			const auto ring = style::ConvertScale(kIconRing);
			const auto gap = style::ConvertScale(kIconRingGap) + ring / 2.;
			auto pen = st::activeButtonBg->p;
			pen.setWidthF(ring);
			p.setPen(pen);
			p.setBrush(Qt::NoBrush);
			p.drawEllipse(QRectF(rect).marginsAdded({ gap, gap, gap, gap }));
		}
	}
}

void AppIconPicker::mouseMoveEvent(QMouseEvent *e) {
	setCursor((iconAt(e->pos()) >= 0)
		? style::cur_pointer
		: style::cur_default);
}

void AppIconPicker::mousePressEvent(QMouseEvent *e) {
	if (e->button() != Qt::LeftButton) {
		return;
	}
	const auto index = iconAt(e->pos());
	if (index >= 0) {
		Lumen::SetAppIcon(Lumen::AppIcons()[index]);
		update();
	}
}

void AddAppIconPicker(SectionBuilder &builder) {
	builder.addSkip();
	builder.addSubsectionTitle({
		.id = u"lumengram/app-icon"_q,
		.title = tr::lng_lumen_app_icon(),
		.keywords = { u"icon"_q, u"logo"_q, u"taskbar"_q, u"tray"_q },
	});
	builder.add([](const WidgetContext &ctx) {
		return SectionBuilder::WidgetToAdd{
			.widget = object_ptr<AppIconPicker>(ctx.container),
		};
	});
	builder.addSkip();
	builder.addDividerText(tr::lng_lumen_app_icon_about());
}

void AddGhostOption(
		SectionBuilder &builder,
		GhostOption option,
		QString id,
		rpl::producer<QString> title,
		QStringList keywords) {
	const auto button = builder.addButton({
		.id = std::move(id),
		.title = std::move(title),
		.st = &st::settingsButtonNoIcon,
		.toggled = rpl::single(Lumen::GhostOptionEnabled(option)),
		.keywords = std::move(keywords),
	});
	if (!button) {
		return;
	}
	button->toggledValue(
	) | rpl::filter([=](bool enabled) {
		return (enabled != Lumen::GhostOptionEnabled(option));
	}) | rpl::on_next([=](bool enabled) {
		Lumen::SetGhostOptionEnabled(option, enabled);
	}, button->lifetime());
}

[[nodiscard]] QString ConnectionModeText(Lumen::ConnectionMode mode) {
	switch (mode) {
	case Lumen::ConnectionMode::Auto:
		return tr::lng_lumen_connection_auto(tr::now);
	case Lumen::ConnectionMode::Always:
		return tr::lng_lumen_connection_always(tr::now);
	case Lumen::ConnectionMode::Off:
		return tr::lng_lumen_connection_off(tr::now);
	}
	Unexpected("Mode in ConnectionModeText.");
}

void AddConnectionOptions(SectionBuilder &builder) {
	const auto controller = builder.controller();
	builder.addSubsectionTitle({
		.id = u"lumengram/connection"_q,
		.title = tr::lng_lumen_connection_title(),
		.keywords = { u"proxy"_q, u"vpn"_q, u"connection"_q },
	});
	builder.addButton({
		.id = u"lumengram/connection-mode"_q,
		.title = tr::lng_lumen_connection_server(),
		.icon = { &st::menuIconNetwork },
		.label = Lumen::ConnectionModeValue(
		) | rpl::map(ConnectionModeText),
		.onClick = [=] {
			using Mode = Lumen::ConnectionMode;
			const auto modes = std::vector<Mode>{
				Mode::Auto,
				Mode::Always,
				Mode::Off,
			};
			auto options = std::vector<QString>();
			for (const auto mode : modes) {
				options.push_back(ConnectionModeText(mode));
			}
			const auto selected = int(ranges::find(
				modes,
				Lumen::GetConnectionMode()) - begin(modes));
			controller->show(Box([=](not_null<Ui::GenericBox*> box) {
				SingleChoiceBox(box, {
					.title = tr::lng_lumen_connection_server(),
					.options = options,
					.initialSelection = selected,
					.callback = [=](int index) {
						Lumen::SetConnectionMode(modes[index]);
					},
				});
			}));
		},
		.keywords = { u"proxy"_q, u"mtproxy"_q, u"vpn"_q, u"blocked"_q },
	});
	builder.addSkip();
	builder.addDividerText(tr::lng_lumen_connection_about());
	builder.addSkip();
}

void AddGhostToggle(SectionBuilder &builder) {
	const auto controller = builder.controller();
	const auto button = builder.addButton({
		.id = u"lumengram/ghost-mode"_q,
		.title = tr::lng_lumen_ghost_mode(),
		.icon = { &st::menuIconStealth },
		.keywords = { u"ghost"_q, u"invisible"_q, u"stealth"_q },
	});
	if (button && controller) {
		Lumen::SetupGhostToggle(button, controller->uiShow());
	}
}

void AddPrivacyOptions(SectionBuilder &builder) {
	builder.addSubsectionTitle({
		.id = u"lumengram/privacy"_q,
		.title = tr::lng_lumen_privacy_title(),
		.keywords = { u"privacy"_q, u"streamer"_q },
	});
	const auto streamer = builder.addButton({
		.id = u"lumengram/streamer"_q,
		.title = tr::lng_lumen_streamer_mode(),
		.icon = { &st::menuIconLock },
		.toggled = Lumen::StreamerModeValue(),
		.keywords = { u"streamer"_q, u"capture"_q, u"screenshot"_q },
	});
	if (streamer) {
		streamer->toggledValue(
		) | rpl::filter([](bool enabled) {
			return (enabled != Lumen::StreamerMode());
		}) | rpl::on_next([](bool enabled) {
			Lumen::SetStreamerMode(enabled);
		}, streamer->lifetime());
	}
	const auto ids = builder.addButton({
		.id = u"lumengram/peer-id"_q,
		.title = tr::lng_lumen_show_peer_id(),
		.icon = { &st::menuIconInfo },
		.toggled = rpl::single(Lumen::ShowPeerId()),
		.keywords = { u"id"_q, u"dc"_q, u"datacenter"_q },
	});
	if (ids) {
		ids->toggledValue(
		) | rpl::filter([](bool enabled) {
			return (enabled != Lumen::ShowPeerId());
		}) | rpl::on_next([](bool enabled) {
			Lumen::SetShowPeerId(enabled);
		}, ids->lifetime());
	}
	builder.addSkip();
	builder.addDividerText(tr::lng_lumen_streamer_about());
}

[[nodiscard]] QString BubbleRadiusText(int radius) {
	return (radius < 0)
		? tr::lng_lumen_bubble_radius_default(tr::now)
		: tr::lng_lumen_bubble_radius_px(
			tr::now,
			lt_radius,
			QString::number(radius));
}

void AddBubbleRadius(SectionBuilder &builder) {
	const auto controller = builder.controller();
	const auto radius = std::make_shared<rpl::variable<int>>(
		Lumen::BubbleRadius());
	builder.addButton({
		.id = u"lumengram/bubble-radius"_q,
		.title = tr::lng_lumen_bubble_radius(),
		.icon = { &st::menuIconChatBubble },
		.label = radius->value() | rpl::map(BubbleRadiusText),
		.onClick = [=] {
			auto options = std::vector<QString>();
			for (const auto value : kBubbleRadiusOptions) {
				options.push_back(BubbleRadiusText(value));
			}
			const auto current = ranges::find(
				kBubbleRadiusOptions,
				radius->current());
			const auto selected = (current != end(kBubbleRadiusOptions))
				? int(current - begin(kBubbleRadiusOptions))
				: 0;
			controller->show(Box([=](not_null<Ui::GenericBox*> box) {
				const auto save = [=](int index) {
					const auto value = kBubbleRadiusOptions[index];
					if (value == radius->current()) {
						return;
					}
					*radius = value;
					Lumen::SetBubbleRadius(value);
					controller->show(Ui::MakeConfirmBox({
						.text = tr::lng_settings_need_restart(),
						.confirmed = [] { Core::Restart(); },
						.confirmText = tr::lng_settings_restart_now(),
						.cancelText = tr::lng_settings_restart_later(),
					}));
				};
				SingleChoiceBox(box, {
					.title = tr::lng_lumen_bubble_radius(),
					.options = options,
					.initialSelection = selected,
					.callback = save,
				});
			}));
		},
		.keywords = { u"radius"_q, u"corners"_q, u"bubble"_q },
	});
}

void AddMessageToggle(
		SectionBuilder &builder,
		QString id,
		rpl::producer<QString> title,
		const style::icon *icon,
		Fn<bool()> value,
		Fn<void(bool)> change,
		QStringList keywords) {
	const auto button = builder.addButton({
		.id = std::move(id),
		.title = std::move(title),
		.icon = { icon },
		.toggled = rpl::single(value()),
		.keywords = std::move(keywords),
	});
	if (!button) {
		return;
	}
	button->toggledValue(
	) | rpl::filter([=](bool enabled) {
		return (enabled != value());
	}) | rpl::on_next([=](bool enabled) {
		change(enabled);
	}, button->lifetime());
}

void AddMessageOptions(SectionBuilder &builder) {
	builder.addSkip();
	builder.addSubsectionTitle({
		.id = u"lumengram/messages"_q,
		.title = tr::lng_lumen_messages_title(),
		.keywords = { u"messages"_q, u"bubbles"_q, u"appearance"_q },
	});
	AddBubbleRadius(builder);
	AddMessageToggle(
		builder,
		u"lumengram/undo-send"_q,
		tr::lng_lumen_undo_send(),
		&st::menuIconRestore,
		Lumen::UndoSendEnabled,
		Lumen::SetUndoSendEnabled,
		{ u"undo"_q, u"cancel"_q, u"send"_q });
	AddMessageToggle(
		builder,
		u"lumengram/show-seconds"_q,
		tr::lng_lumen_show_seconds(),
		&st::menuIconTimer,
		Lumen::ShowSeconds,
		Lumen::SetShowSeconds,
		{ u"seconds"_q, u"time"_q, u"clock"_q });
	AddMessageToggle(
		builder,
		u"lumengram/service-time"_q,
		tr::lng_lumen_service_time(),
		&st::menuIconSchedule,
		Lumen::ServiceTime,
		Lumen::SetServiceTime,
		{ u"service"_q, u"time"_q, u"joined"_q });
	AddMessageToggle(
		builder,
		u"lumengram/plain-replies"_q,
		tr::lng_lumen_plain_replies(),
		&st::menuIconReply,
		Lumen::PlainReplies,
		Lumen::SetPlainReplies,
		{ u"reply"_q, u"quote"_q, u"color"_q });
	AddMessageToggle(
		builder,
		u"lumengram/bubble-tail"_q,
		tr::lng_lumen_hide_bubble_tail(),
		&st::menuIconPalette,
		Lumen::HideBubbleTail,
		Lumen::SetHideBubbleTail,
		{ u"tail"_q, u"bubble"_q });
	AddMessageToggle(
		builder,
		u"lumengram/share-button"_q,
		tr::lng_lumen_hide_share_button(),
		&st::menuIconShare,
		Lumen::HideShareButton,
		Lumen::SetHideShareButton,
		{ u"share"_q, u"forward"_q, u"button"_q });
	AddMessageToggle(
		builder,
		u"lumengram/save-deleted"_q,
		tr::lng_lumen_save_deleted(),
		&st::menuIconDelete,
		Lumen::SaveDeletedMessages,
		Lumen::SetSaveDeletedMessages,
		{ u"deleted"_q, u"anti-recall"_q, u"history"_q });
	AddMessageToggle(
		builder,
		u"lumengram/save-edits"_q,
		tr::lng_lumen_save_edits(),
		&st::menuIconEdit,
		Lumen::SaveEditHistory,
		Lumen::SetSaveEditHistory,
		{ u"edited"_q, u"edits"_q, u"history"_q });
	AddMessageToggle(
		builder,
		u"lumengram/local-transcribe"_q,
		tr::lng_lumen_local_transcribe(),
		&st::menuIconTranslate,
		Lumen::LocalTranscribeEnabled,
		Lumen::SetLocalTranscribeEnabled,
		{ u"voice"_q, u"transcribe"_q, u"speech"_q });
	builder.addSkip();
	builder.addDividerText(tr::lng_lumen_messages_about());
	builder.addSkip();
	builder.addDividerText(tr::lng_lumen_local_transcribe_about());
}

void ShowAutoReplyTextBox(not_null<Window::SessionController*> controller) {
	controller->show(Box([=](not_null<Ui::GenericBox*> box) {
		box->setTitle(tr::lng_lumen_auto_reply_text());
		const auto field = box->addRow(object_ptr<Ui::InputField>(
			box,
			st::defaultInputField,
			Ui::InputField::Mode::MultiLine,
			tr::lng_lumen_auto_reply_text(),
			Lumen::AutoReplyText()));
		field->setMaxLength(1024);
		box->setFocusCallback([=] { field->setFocusFast(); });
		const auto save = [=] {
			Lumen::SetAutoReplyText(field->getLastText().trimmed());
			box->closeBox();
		};
		box->addButton(tr::lng_settings_save(), save);
		box->addButton(tr::lng_cancel(), [=] { box->closeBox(); });
	}));
}

void AddAutoReplyOptions(SectionBuilder &builder) {
	const auto controller = builder.controller();
	builder.addSkip();
	builder.addSubsectionTitle({
		.id = u"lumengram/auto-reply"_q,
		.title = tr::lng_lumen_auto_reply_title(),
		.keywords = { u"auto"_q, u"reply"_q, u"away"_q },
	});
	AddMessageToggle(
		builder,
		u"lumengram/auto-reply-enabled"_q,
		tr::lng_lumen_auto_reply(),
		&st::menuIconReply,
		Lumen::AutoReplyEnabled,
		Lumen::SetAutoReplyEnabled,
		{ u"auto"_q, u"reply"_q, u"away"_q });
	if (controller) {
		builder.addButton({
			.id = u"lumengram/auto-reply-text"_q,
			.title = tr::lng_lumen_auto_reply_text(),
			.icon = { &st::menuIconEdit },
			.onClick = [=] { ShowAutoReplyTextBox(controller); },
			.keywords = { u"auto"_q, u"reply"_q, u"text"_q },
		});
	}
	builder.addSkip();
	builder.addDividerText(tr::lng_lumen_auto_reply_about());
}

void AddChatsOptions(SectionBuilder &builder) {
	const auto controller = builder.controller();
	builder.addSkip();
	builder.addSubsectionTitle({
		.id = u"lumengram/chats"_q,
		.title = tr::lng_lumen_chats_title(),
		.keywords = { u"chats"_q, u"stories"_q, u"folders"_q },
	});
	AddMessageToggle(
		builder,
		u"lumengram/full-banner"_q,
		tr::lng_lumen_full_banner(),
		&st::menuIconProfile,
		Lumen::FullProfileBanner,
		Lumen::SetFullProfileBanner,
		{ u"banner"_q, u"profile"_q, u"background"_q });
	AddMessageToggle(
		builder,
		u"lumengram/hide-stories"_q,
		tr::lng_lumen_hide_stories(),
		&st::menuIconStoriesArchive,
		Lumen::HideStories,
		Lumen::SetHideStories,
		{ u"stories"_q, u"hide"_q });
	AddMessageToggle(
		builder,
		u"lumengram/square-userpics"_q,
		tr::lng_lumen_square_userpics(),
		&st::menuIconProfile,
		Lumen::SquareUserpicsSaved,
		[=](bool square) {
			Lumen::SetSquareUserpics(square);
			if (controller) {
				controller->show(Ui::MakeConfirmBox({
					.text = tr::lng_settings_need_restart(),
					.confirmed = [] { Core::Restart(); },
					.confirmText = tr::lng_settings_restart_now(),
					.cancelText = tr::lng_settings_restart_later(),
				}));
			}
		},
		{ u"avatar"_q, u"userpic"_q, u"square"_q, u"corners"_q });
	AddMessageToggle(
		builder,
		u"lumengram/folder-counters"_q,
		tr::lng_lumen_hide_folder_counters(),
		&st::menuIconShowInFolder,
		Lumen::HideFolderCounters,
		Lumen::SetHideFolderCounters,
		{ u"folders"_q, u"counters"_q, u"unread"_q });
	AddMessageToggle(
		builder,
		u"lumengram/similar-channels"_q,
		tr::lng_lumen_hide_similar_channels(),
		&st::menuIconChannel,
		Lumen::HideSimilarChannels,
		Lumen::SetHideSimilarChannels,
		{ u"similar"_q, u"channels"_q, u"recommendations"_q });
	AddMessageToggle(
		builder,
		u"lumengram/liquid-glass"_q,
		tr::lng_lumen_liquid_glass(),
		&st::menuIconPalette,
		Lumen::LiquidGlass,
		Lumen::SetLiquidGlass,
		{ u"glass"_q, u"blur"_q, u"liquid"_q });
	builder.addSkip();
	builder.addDividerText(tr::lng_lumen_chats_about());
}

[[nodiscard]] QString ProfileErrorText(const QString &error) {
	if (error == u"LOGIN_TIMEOUT"_q) {
		return tr::lng_lumen_profile_login_timeout(tr::now);
	} else if (error == u"BAD_IMAGE"_q) {
		return tr::lng_lumen_banner_bad_image(tr::now);
	} else if (error.isEmpty() || error == u"BOT_NOT_FOUND"_q) {
		return tr::lng_lumen_profile_error(tr::now);
	}
	return error;
}

void ShowLastfmBox(
		not_null<Window::SessionController*> controller,
		Fn<void(QString)> submit) {
	controller->show(Box([=](not_null<Ui::GenericBox*> box) {
		box->setTitle(tr::lng_lumen_music_lastfm());
		const auto field = box->addRow(object_ptr<Ui::InputField>(
			box,
			st::defaultInputField,
			tr::lng_lumen_music_lastfm_user()));
		field->setMaxLength(15);
		box->setFocusCallback([=] { field->setFocusFast(); });
		const auto save = [=] {
			const auto value = field->getLastText().trimmed();
			if (value.isEmpty()) {
				field->showError();
				return;
			}
			box->closeBox();
			submit(value);
		};
		field->submits() | rpl::on_next(save, field->lifetime());
		box->addButton(tr::lng_lumen_music_connect(), save);
		box->addButton(tr::lng_cancel(), [=] { box->closeBox(); });
	}));
}

void AddProfileOptions(SectionBuilder &builder) {
	const auto controller = builder.controller();
	if (!controller) {
		return;
	}
	const auto session = &controller->session();
	const auto show = controller->uiShow();
	const auto toast = [=](QString text) {
		show->showToast(text);
	};
	const auto failed = [=](QString error) {
		toast(ProfileErrorText(error));
	};
	const auto loggedIn = [=] {
		return Lumen::ProfileLoggedInValue(session);
	};
	const auto stateValue = [=] {
		return rpl::combine(
			Lumen::ProfileLoggedInValue(session),
			Lumen::ProfileStateValue(session));
	};
	Lumen::RefreshProfileState(session);

	builder.addSubsectionTitle({
		.id = u"lumengram/profile"_q,
		.title = tr::lng_lumen_profile_title(),
		.keywords = { u"banner"_q, u"spotify"_q, u"last.fm"_q },
	});
	builder.addButton({
		.id = u"lumengram/profile-login"_q,
		.title = tr::lng_lumen_profile_login(),
		.icon = { &st::menuIconProfile },
		.onClick = [=] {
			toast(tr::lng_lumen_profile_logging_in(tr::now));
			Lumen::LoginProfile(session, [=] {
				toast(tr::lng_lumen_profile_logged_in(tr::now));
			}, failed);
		},
		.keywords = { u"login"_q, u"profile"_q },
		.shown = loggedIn() | rpl::map(!rpl::mappers::_1),
	});
	builder.addButton({
		.id = u"lumengram/banner-upload"_q,
		.title = tr::lng_lumen_banner_upload(),
		.icon = { &st::menuIconPhotoSet },
		.onClick = [=] {
			FileDialog::GetOpenPath(
				controller->widget().get(),
				tr::lng_choose_image(tr::now),
				FileDialog::ImagesFilter(),
				[=](FileDialog::OpenResult &&result) {
					if (result.paths.isEmpty()) {
						return;
					}
					Lumen::UploadBanner(session, result.paths.front(), [=] {
						toast(tr::lng_lumen_banner_uploaded(tr::now));
					}, failed);
				});
		},
		.keywords = { u"banner"_q, u"cover"_q },
		.shown = stateValue() | rpl::map([](bool logged, auto state) {
			return logged && !state.banned;
		}),
	});
	builder.addButton({
		.id = u"lumengram/banner-delete"_q,
		.title = tr::lng_lumen_banner_delete(),
		.icon = { &st::menuIconDelete },
		.onClick = [=] {
			Lumen::DeleteBanner(session, [] {}, failed);
		},
		.shown = stateValue() | rpl::map([](bool logged, auto state) {
			return logged && state.hasBanner;
		}),
	});
	builder.addButton({
		.id = u"lumengram/music-spotify"_q,
		.title = tr::lng_lumen_music_spotify(),
		.icon = { &st::menuIconLink },
		.onClick = [=] {
			Lumen::ConnectSpotify(session, [=] {
				toast(tr::lng_lumen_music_spotify_opened(tr::now));
			}, failed);
		},
		.keywords = { u"spotify"_q, u"music"_q },
		.shown = stateValue() | rpl::map([](bool logged, auto state) {
			return logged
				&& state.spotifyAvailable
				&& state.musicProvider.isEmpty();
		}),
	});
	builder.addButton({
		.id = u"lumengram/music-lastfm"_q,
		.title = tr::lng_lumen_music_lastfm(),
		.icon = { &st::menuIconLink },
		.onClick = [=] {
			ShowLastfmBox(controller, [=](QString username) {
				Lumen::ConnectLastfm(session, username, [=] {
					toast(tr::lng_lumen_music_connected(tr::now));
				}, failed);
			});
		},
		.keywords = { u"last.fm"_q, u"music"_q },
		.shown = stateValue() | rpl::map([](bool logged, auto state) {
			return logged
				&& state.lastfmAvailable
				&& state.musicProvider.isEmpty();
		}),
	});
	builder.addButton({
		.id = u"lumengram/music-disconnect"_q,
		.title = tr::lng_lumen_music_disconnect(),
		.icon = { &st::menuIconDelete },
		.label = Lumen::ProfileStateValue(
			session
		) | rpl::map([](const Lumen::ProfileState &state) {
			return (state.musicProvider == u"lastfm"_q)
				? (u"Last.fm "_q + state.lastfmUser)
				: (state.musicProvider == u"spotify"_q)
				? u"Spotify"_q
				: QString();
		}),
		.onClick = [=] {
			Lumen::DisconnectMusic(session, [] {}, failed);
		},
		.shown = stateValue() | rpl::map([](bool logged, auto state) {
			return logged && !state.musicProvider.isEmpty();
		}),
	});
	builder.addButton({
		.id = u"lumengram/profile-logout"_q,
		.title = tr::lng_lumen_profile_logout(),
		.icon = { &st::menuIconLeave },
		.onClick = [=] { Lumen::LogoutProfile(session); },
		.shown = loggedIn(),
	});
	builder.addSkip();
	builder.addDividerText(tr::lng_lumen_profile_about());
	builder.addSkip();
}

void AddTools(SectionBuilder &builder) {
	const auto controller = builder.controller();
	if (!controller) {
		return;
	}
	builder.addSubsectionTitle({
		.id = u"lumengram/tools"_q,
		.title = tr::lng_lumen_tools_title(),
		.keywords = { u"tools"_q, u"bookmarks"_q },
	});
	builder.addButton({
		.id = u"lumengram/bookmarks"_q,
		.title = tr::lng_lumen_bookmarks(),
		.icon = { &st::menuIconFave },
		.label = Lumen::BookmarksCountValue(
			&controller->session()
		) | rpl::map([](int count) {
			return count ? QString::number(count) : QString();
		}),
		.onClick = [=] { Lumen::ShowBookmarks(controller); },
		.keywords = { u"bookmarks"_q, u"saved"_q, u"favorite"_q },
	});
	builder.addButton({
		.id = u"lumengram/templates"_q,
		.title = tr::lng_lumen_templates(),
		.icon = { &st::menuIconEdit },
		.label = Lumen::TemplatesCountValue(
		) | rpl::map([](int count) {
			return count ? QString::number(count) : QString();
		}),
		.onClick = [=] { Lumen::ShowTemplates(controller); },
		.keywords = { u"templates"_q, u"snippets"_q, u"quick"_q },
	});
	builder.addSkip();
	builder.addDividerText(tr::lng_lumen_tools_about());
	builder.addSkip();
}

void AddUrlScheme(SectionBuilder &builder) {
	const auto controller = builder.controller();
	builder.addButton({
		.id = u"lumengram/url-scheme"_q,
		.title = tr::lng_lumen_url_scheme(),
		.icon = { &st::menuIconLink },
		.onClick = [=] {
			Core::Application::RegisterUrlScheme();
			if (controller) {
				controller->uiShow()->showToast(
					tr::lng_lumen_url_scheme_done(tr::now));
			}
		},
		.keywords = { u"tg"_q, u"links"_q, u"default"_q },
	});
}

void AddLinks(SectionBuilder &builder) {
	builder.addSkip();
	builder.addSubsectionTitle({
		.id = u"lumengram/links"_q,
		.title = tr::lng_lumen_links_title(),
		.keywords = { u"website"_q, u"docs"_q },
	});
	builder.addButton({
		.id = u"lumengram/website"_q,
		.title = tr::lng_lumen_settings_website(),
		.icon = { &st::menuIconLink },
		.label = rpl::single(Lumen::kWebsiteLabel.utf16()),
		.onClick = [] { UrlClickHandler::Open(Lumen::kWebsite.utf16()); },
		.keywords = { u"website"_q, u"docs"_q, u"tunexa"_q },
	});
}

void BuildLumengramSection(SectionBuilder &builder) {
	builder.add([](const WidgetContext &ctx) {
		return SectionBuilder::WidgetToAdd{
			.widget = object_ptr<Header>(ctx.container),
		};
	});
	builder.addDivider();
	builder.addSkip();
	AddConnectionOptions(builder);
	builder.addSubsectionTitle({
		.id = u"lumengram/ghost"_q,
		.title = tr::lng_lumen_ghost_title(),
		.keywords = { u"ghost"_q, u"privacy"_q },
	});
	AddGhostToggle(builder);
	AddGhostOption(
		builder,
		GhostOption::HideOnline,
		u"lumengram/ghost-online"_q,
		tr::lng_lumen_ghost_hide_online(),
		{ u"online"_q, u"status"_q, u"last seen"_q });
	AddGhostOption(
		builder,
		GhostOption::NoReadMessages,
		u"lumengram/ghost-read"_q,
		tr::lng_lumen_ghost_no_read(),
		{ u"read"_q, u"receipts"_q, u"checks"_q });
	AddGhostOption(
		builder,
		GhostOption::ReadOnInteract,
		u"lumengram/ghost-read-on-interact"_q,
		tr::lng_lumen_ghost_read_on_interact(),
		{ u"read"_q, u"reply"_q, u"interact"_q });
	AddGhostOption(
		builder,
		GhostOption::NoReadStories,
		u"lumengram/ghost-stories"_q,
		tr::lng_lumen_ghost_no_stories(),
		{ u"stories"_q, u"views"_q });
	AddGhostOption(
		builder,
		GhostOption::NoTyping,
		u"lumengram/ghost-typing"_q,
		tr::lng_lumen_ghost_no_typing(),
		{ u"typing"_q, u"recording"_q });
	builder.addSkip();
	builder.addDividerText(tr::lng_lumen_ghost_about());
	builder.addSkip();
	AddPrivacyOptions(builder);
	AddMessageOptions(builder);
	AddAutoReplyOptions(builder);
	AddChatsOptions(builder);
	AddAppIconPicker(builder);
	AddProfileOptions(builder);
	AddTools(builder);
	AddUrlScheme(builder);
	AddLinks(builder);
}

class Lumengram : public Section<Lumengram> {
public:
	Lumengram(
		QWidget *parent,
		not_null<Window::SessionController*> controller);

	[[nodiscard]] rpl::producer<QString> title() override;

private:
	void setupContent();

};

Lumengram::Lumengram(
	QWidget *parent,
	not_null<Window::SessionController*> controller)
: Section(parent, controller) {
	setupContent();
}

rpl::producer<QString> Lumengram::title() {
	return tr::lng_lumen_settings_title();
}

void Lumengram::setupContent() {
	const auto content = Ui::CreateChild<Ui::VerticalLayout>(this);

	const SectionBuildMethod buildMethod = [](
			not_null<Ui::VerticalLayout*> container,
			not_null<Window::SessionController*> controller,
			Fn<void(Type)> showOther,
			rpl::producer<> showFinished) {
		auto builder = SectionBuilder(WidgetContext{
			.container = container,
			.controller = controller,
			.showOther = std::move(showOther),
			.isPaused = Window::PausedIn(
				controller,
				Window::GifPauseReason::Layer),
		});
		BuildLumengramSection(builder);
	};

	build(content, buildMethod);
	Ui::ResizeFitChild(this, content);
}

const auto kMeta = BuildHelper({
	.id = Lumengram::Id(),
	.parentId = MainId(),
	.title = &tr::lng_lumen_settings_title,
	.icon = &st::menuIconStar,
}, [](SectionBuilder &builder) {
	BuildLumengramSection(builder);
});

} // namespace

Type LumengramId() {
	return Lumengram::Id();
}

} // namespace Settings
