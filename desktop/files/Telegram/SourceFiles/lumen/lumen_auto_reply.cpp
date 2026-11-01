/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#include "lumen/lumen_auto_reply.h"

#include "api/api_common.h"
#include "apiwrap.h"
#include "base/unixtime.h"
#include "core/application.h"
#include "core/core_settings.h"
#include "data/data_peer.h"
#include "data/data_session.h"
#include "data/data_user.h"
#include "history/history.h"
#include "history/history_item.h"
#include "lang/lang_keys.h"
#include "main/main_session.h"
#include "window/window_controller.h"
#include "window/window_session_controller.h"
#include "mainwindow.h"

namespace Lumen {
namespace {

constexpr auto kEnabledPref = "lumen/auto_reply"_cs;
constexpr auto kTextPref = "lumen/auto_reply_text"_cs;

// One answer per chat in this time, so a conversation does not get
// the same text over and over.
constexpr auto kCooldown = 6 * 60 * 60 * crl::time(1000);

// Messages older than this come from catching up after being offline.
constexpr auto kMaxMessageAge = TimeId(120);

[[nodiscard]] rpl::variable<bool> &Enabled() {
	static auto result = rpl::variable<bool>(
		Core::App().settings().readPref<bool>(kEnabledPref, false));
	return result;
}

[[nodiscard]] base::flat_map<PeerId, crl::time> &AnsweredAt() {
	static auto result = base::flat_map<PeerId, crl::time>();
	return result;
}

[[nodiscard]] bool IsChatOpenAndVisible(not_null<PeerData*> peer) {
	const auto window = Core::App().activeWindow();
	if (!window || !window->widget()->isActiveWindow()) {
		return false;
	}
	const auto controller = window->sessionController();
	return controller
		&& (controller->activeChatCurrent().peer() == peer);
}

[[nodiscard]] bool TakeCooldown(PeerId peerId) {
	const auto now = crl::now();
	auto &answered = AnsweredAt();
	const auto i = answered.find(peerId);
	if (i != end(answered) && now - i->second < kCooldown) {
		return false;
	}
	answered[peerId] = now;
	return true;
}

void HandleNewItem(not_null<HistoryItem*> item) {
	if (!AutoReplyEnabled()
		|| item->out()
		|| !item->isRegular()
		|| item->isService()
		|| (base::unixtime::now() - item->date() > kMaxMessageAge)) {
		return;
	}
	const auto history = item->history();
	const auto user = history->peer->asUser();
	if (!user
		|| user->isSelf()
		|| user->isBot()
		|| user->isServiceUser()
		|| user->isInaccessible()
		|| IsChatOpenAndVisible(user)
		|| !TakeCooldown(user->id)) {
		return;
	}
	auto message = Api::MessageToSend(Api::SendAction(history));
	message.textWithTags = { AutoReplyText() };
	history->session().api().sendMessage(std::move(message));
}

} // namespace

bool AutoReplyEnabled() {
	return Enabled().current();
}

void SetAutoReplyEnabled(bool enabled) {
	Enabled() = enabled;
	Core::App().settings().writePref<bool>(kEnabledPref, enabled);
	Core::App().saveSettingsDelayed();
}

rpl::producer<bool> AutoReplyEnabledValue() {
	return Enabled().value();
}

QString AutoReplyText() {
	const auto text = QString::fromUtf8(
		Core::App().settings().readPref<QByteArray>(kTextPref));
	return text.isEmpty() ? tr::lng_lumen_auto_reply_default(tr::now) : text;
}

void SetAutoReplyText(const QString &text) {
	Core::App().settings().writePref<QByteArray>(kTextPref, text.toUtf8());
	Core::App().saveSettingsDelayed();
}

void SetupAutoReply(not_null<Main::Session*> session) {
	session->data().newItemAdded(
	) | rpl::on_next(HandleNewItem, session->lifetime());
}

} // namespace Lumen
