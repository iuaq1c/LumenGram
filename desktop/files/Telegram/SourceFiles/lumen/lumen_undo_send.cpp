/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#include "lumen/lumen_undo_send.h"

#include "api/api_common.h"
#include "base/call_delayed.h"
#include "core/application.h"
#include "core/core_settings.h"
#include "data/data_changes.h"
#include "data/data_drafts.h"
#include "data/data_forum_topic.h"
#include "data/data_peer.h"
#include "history/history.h"
#include "lang/lang_keys.h"
#include "main/main_session.h"
#include "ui/toast/toast.h"
#include "window/window_session_controller.h"

namespace Lumen {
namespace {

constexpr auto kUndoSendPref = "lumen/undo_send"_cs;
constexpr auto kUndoDelay = crl::time(5000);

bool Sending = false;

void RestoreDraft(const Api::MessageToSend &message) {
	const auto history = message.action.history;
	const auto &replyTo = message.action.replyTo;
	const auto topicRootId = replyTo.topicRootId;
	const auto monoforumPeerId = replyTo.monoforumPeerId;
	const auto text = message.textWithTags;
	const auto cursor = MessageCursor{
		int(text.text.size()),
		int(text.text.size()),
		Ui::kQFixedMax,
	};
	history->setLocalDraft(std::make_unique<Data::Draft>(
		text,
		replyTo,
		SuggestOptions(),
		cursor,
		message.webPage));
	history->clearLocalEditDraft(topicRootId, monoforumPeerId);
	const auto topic = topicRootId
		? history->peer->forumTopicFor(topicRootId)
		: nullptr;
	history->session().changes().entryUpdated(
		topic ? static_cast<Data::Thread*>(topic) : history.get(),
		Data::EntryUpdate::Flag::LocalDraftSet);
}

} // namespace

bool UndoSendEnabled() {
	return Core::App().settings().readPref<bool>(kUndoSendPref);
}

void SetUndoSendEnabled(bool enabled) {
	Core::App().settings().writePref<bool>(kUndoSendPref, enabled);
	Core::App().saveSettingsDelayed();
}

bool DelaySend(const Api::MessageToSend &message, Fn<void()> send) {
	if (Sending
		|| !UndoSendEnabled()
		|| message.action.options.scheduled
		|| message.textWithTags.text.trimmed().isEmpty()) {
		return false;
	}
	const auto history = message.action.history;
	const auto session = &history->session();
	const auto window = session->tryResolveWindow(history->peer);
	if (!window) {
		return false;
	}
	const auto cancelled = std::make_shared<bool>(false);
	const auto copy = std::make_shared<Api::MessageToSend>(message);
	auto text = tr::marked(tr::lng_lumen_undo_send_toast(tr::now));
	text.append(u"  "_q).append(tr::link(
		tr::lng_lumen_undo_send_cancel(tr::now),
		u"internal:lumen_undo_send"_q));
	const auto toast = std::make_shared<base::weak_ptr<Ui::Toast::Instance>>();
	*toast = window->uiShow()->showToast(Ui::Toast::Config{
		.text = std::move(text),
		.filter = [=](const ClickHandlerPtr &, Qt::MouseButton) {
			if (!*cancelled) {
				*cancelled = true;
				RestoreDraft(*copy);
				if (const auto strong = toast->get()) {
					strong->hideAnimated();
				}
			}
			return false;
		},
		.duration = kUndoDelay,
	});
	base::call_delayed(kUndoDelay, session, [=] {
		if (*cancelled) {
			return;
		}
		Sending = true;
		send();
		Sending = false;
	});
	return true;
}

} // namespace Lumen
