/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#pragma once

namespace Main {
class Session;
} // namespace Main

namespace Lumen {

[[nodiscard]] bool AutoReplyEnabled();
void SetAutoReplyEnabled(bool enabled);
[[nodiscard]] rpl::producer<bool> AutoReplyEnabledValue();

// The custom text, or the default one when none is set.
[[nodiscard]] QString AutoReplyText();
void SetAutoReplyText(const QString &text);

// Answers new private messages from people while the auto-reply is on.
void SetupAutoReply(not_null<Main::Session*> session);

} // namespace Lumen
