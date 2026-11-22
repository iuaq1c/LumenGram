/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#pragma once

namespace Lumen {

// How LumenGram may use its own MTProxy servers.
enum class ConnectionMode {
	Auto, // Only when Telegram is unreachable and no proxy is set.
	Always,
	Off,
};

[[nodiscard]] ConnectionMode GetConnectionMode();
[[nodiscard]] rpl::producer<ConnectionMode> ConnectionModeValue();
void SetConnectionMode(ConnectionMode mode);

// True while the selected proxy is a LumenGram server.
[[nodiscard]] bool LumenProxyActive();
[[nodiscard]] bool LumenProxyAvailable();

// Called for the main DC connection state of every account.
void HandleConnectionState(int32 state);

} // namespace Lumen
