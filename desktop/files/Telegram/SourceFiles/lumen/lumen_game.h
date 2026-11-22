/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#pragma once

class PeerData;

namespace Window {
class SessionController;
} // namespace Window

namespace Lumen {

// Tic-tac-toe in a private chat. Every move is a message with a tunexa.ru
// link holding the whole board: LumenGram opens it as a game, other apps
// open a page that explains it.
[[nodiscard]] bool CanStartGame(not_null<PeerData*> peer);
void StartGame(
	not_null<Window::SessionController*> controller,
	not_null<PeerData*> peer);

// Opens a game link instead of the browser, returns true for game links.
[[nodiscard]] bool HandleGameUrl(const QString &url, const QVariant &context);

} // namespace Lumen
