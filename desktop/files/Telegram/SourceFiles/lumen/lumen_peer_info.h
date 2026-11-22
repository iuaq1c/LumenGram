/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#pragma once

class PeerData;
class UserData;
class ChannelData;

namespace Lumen {

[[nodiscard]] QString PeerIdText(not_null<PeerData*> peer);
[[nodiscard]] rpl::producer<QString> PeerIdLabel(not_null<PeerData*> peer);

// "≈ June 2021", estimated from the user id, or empty.
[[nodiscard]] QString RegistrationEstimateText(not_null<UserData*> user);

// "6 December 2024" for a channel or supergroup the user is in, or empty.
[[nodiscard]] QString JoinDateText(not_null<ChannelData*> channel);

} // namespace Lumen
