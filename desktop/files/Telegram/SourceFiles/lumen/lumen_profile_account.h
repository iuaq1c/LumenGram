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

// Session on the LumenGram profiles server, for the banner and music.
struct ProfileState {
	bool hasBanner = false;
	bool banned = false;
	QString musicProvider;
	QString lastfmUser;
	bool spotifyAvailable = false;
	bool lastfmAvailable = false;

	friend inline bool operator==(
		const ProfileState &,
		const ProfileState &) = default;
};

using Done = Fn<void()>;
using Fail = Fn<void(QString)>;

[[nodiscard]] bool ProfileLoggedIn(not_null<Main::Session*> session);
[[nodiscard]] rpl::producer<bool> ProfileLoggedInValue(
	not_null<Main::Session*> session);
[[nodiscard]] rpl::producer<ProfileState> ProfileStateValue(
	not_null<Main::Session*> session);

void LoginProfile(not_null<Main::Session*> session, Done done, Fail fail);
void LogoutProfile(not_null<Main::Session*> session);
void RefreshProfileState(not_null<Main::Session*> session);

void UploadBanner(
	not_null<Main::Session*> session,
	const QString &path,
	Done done,
	Fail fail);
void DeleteBanner(not_null<Main::Session*> session, Done done, Fail fail);

// "Do not disturb" status for other LumenGram users, an empty text clears it.
void SaveAway(
	not_null<Main::Session*> session,
	const QString &text,
	qint64 until,
	Done done,
	Fail fail);

void ConnectLastfm(
	not_null<Main::Session*> session,
	const QString &username,
	Done done,
	Fail fail);
void ConnectSpotify(not_null<Main::Session*> session, Done done, Fail fail);
void DisconnectMusic(not_null<Main::Session*> session, Done done, Fail fail);

} // namespace Lumen
