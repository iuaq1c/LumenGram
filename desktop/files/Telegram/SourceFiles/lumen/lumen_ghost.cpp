/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#include "lumen/lumen_ghost.h"

#include "apiwrap.h"
#include "data/data_channel.h"
#include "data/data_peer.h"
#include "history/history.h"
#include "history/history_item.h"
#include "lang/lang_keys.h"
#include "lumen/lumen_settings.h"
#include "main/main_session.h"
#include "ui/boxes/confirm_box.h"
#include "ui/layers/generic_box.h"
#include "ui/layers/show.h"
#include "ui/widgets/buttons.h"

namespace Lumen {
namespace {

constexpr auto kOfflineThrottle = crl::time(2000);

void SendOffline(not_null<Main::Session*> session) {
	static auto lastSent = base::flat_map<not_null<Main::Session*>, crl::time>();
	const auto now = crl::now();
	auto &last = lastSent[session];
	if (last && now - last < kOfflineThrottle) {
		return;
	}
	last = now;
	session->api().request(MTPaccount_UpdateStatus(MTP_bool(true))).send();
}

void SendReadHistory(not_null<History*> history) {
	const auto last = history->lastServerMessage();
	if (!last) {
		return;
	}
	const auto till = last->id;
	const auto peer = history->peer;
	const auto api = &history->session().api();
	if (const auto channel = peer->asChannel()) {
		api->request(MTPchannels_ReadHistory(
			channel->inputChannel(),
			MTP_int(till)
		)).send();
	} else {
		api->request(MTPmessages_ReadHistory(
			peer->input(),
			MTP_int(till)
		)).done([=](const MTPmessages_AffectedMessages &result) {
			api->applyAffectedMessages(peer, result);
		}).send();
	}
}

} // namespace

void SetupGhostToggle(
		not_null<Ui::SettingsButton*> button,
		std::shared_ptr<Ui::Show> show) {
	const auto reverts = button->lifetime().make_state<
		rpl::event_stream<bool>>();
	button->toggleOn(rpl::merge(
		GhostEnabledValue(),
		reverts->events()));
	button->toggledChanges(
	) | rpl::filter([=](bool enabled) {
		return (enabled != GhostEnabled());
	}) | rpl::on_next([=](bool enabled) {
		if (!enabled || GhostRiskAcknowledged()) {
			SetGhostEnabled(enabled);
			return;
		}
		show->showBox(Ui::MakeConfirmBox({
			.text = tr::lng_lumen_ghost_warning(),
			.confirmed = [=](Fn<void()> close) {
				SetGhostRiskAcknowledged();
				SetGhostEnabled(true);
				close();
			},
			.cancelled = [=](Fn<void()> close) {
				reverts->fire(false);
				close();
			},
			.confirmText = tr::lng_lumen_ghost_warning_confirm(),
			.title = tr::lng_lumen_ghost_warning_title(),
		}));
	}, button->lifetime());
}

void MessageSent(not_null<History*> history) {
	if (GhostActive(GhostOption::HideOnline)) {
		SendOffline(&history->session());
	}
	if (GhostActive(GhostOption::NoReadMessages)
		&& GhostOptionEnabled(GhostOption::ReadOnInteract)) {
		SendReadHistory(history);
	}
}

void SelfMarkedOnline(not_null<Main::Session*> session) {
	if (GhostActive(GhostOption::HideOnline)) {
		SendOffline(session);
	}
}

} // namespace Lumen
