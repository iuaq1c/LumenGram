/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#include "lumen/lumen_profile_account.h"

#include "apiwrap.h"
#include "base/call_delayed.h"
#include "base/random.h"
#include "core/application.h"
#include "core/core_settings.h"
#include "data/data_session.h"
#include "data/data_user.h"
#include "lumen/lumen_server.h"
#include "main/main_session.h"
#include "ui/basic_click_handlers.h"

#include <QtCore/QBuffer>
#include <QtCore/QJsonObject>

namespace Lumen {
namespace {

constexpr auto kLoginPollDelay = crl::time(1500);
constexpr auto kLoginPollTimes = 40;
constexpr auto kBannerWidth = 1200;
constexpr auto kBannerHeight = 400;
constexpr auto kBannerQuality = 90;

[[nodiscard]] std::string TokenPref(not_null<Main::Session*> session) {
	return "lumen/profile_token_" + std::to_string(session->uniqueId());
}

[[nodiscard]] QString Token(not_null<Main::Session*> session) {
	return QString::fromUtf8(Core::App().settings().readPref<QByteArray>(
		TokenPref(session)));
}

[[nodiscard]] rpl::event_stream<> &LoginChanges() {
	static auto result = rpl::event_stream<>();
	return result;
}

[[nodiscard]] base::flat_map<uint64, rpl::variable<ProfileState>> &States() {
	static auto result = base::flat_map<uint64, rpl::variable<ProfileState>>();
	return result;
}

void SetToken(not_null<Main::Session*> session, const QString &token) {
	Core::App().settings().writePref<QByteArray>(
		TokenPref(session),
		token.toUtf8());
	Core::App().saveSettingsDelayed();
	LoginChanges().fire({});
}

void Post(
		not_null<Main::Session*> session,
		const QString &path,
		std::vector<std::pair<QString, QString>> fields,
		Fn<void(QJsonObject)> done,
		Fail fail) {
	const auto token = Token(session);
	if (!token.isEmpty()) {
		fields.emplace_back(u"token"_q, token);
	}
	const auto weak = base::make_weak(session);
	ServerPost(path, fields, [=](QJsonObject result) {
		if (weak) {
			done(result);
		}
	}, [=](QString error) {
		if (const auto strong = weak.get()) {
			if (error.contains(u"заново"_q)) {
				SetToken(strong, QString());
			}
			fail(error);
		}
	});
}

void PollLogin(
		not_null<Main::Session*> session,
		QString code,
		QString check,
		int left,
		Done done,
		Fail fail) {
	if (left <= 0) {
		fail(u"LOGIN_TIMEOUT"_q);
		return;
	}
	base::call_delayed(kLoginPollDelay, session, [=] {
		Post(session, u"/api/lumengram/login/check"_q, {
			{ u"code"_q, code },
			{ u"check"_q, check },
		}, [=](QJsonObject result) {
			const auto token = result.value(u"token"_q).toString();
			if (token.isEmpty()) {
				PollLogin(session, code, check, left - 1, done, fail);
				return;
			}
			SetToken(session, token);
			RefreshProfileState(session);
			done();
		}, fail);
	});
}

void StartBot(
		not_null<Main::Session*> session,
		not_null<UserData*> bot,
		const QString &param,
		Fail fail) {
	session->api().request(MTPmessages_StartBot(
		bot->inputUser(),
		MTP_inputPeerEmpty(),
		MTP_long(base::RandomValue<uint64>()),
		MTP_string(param)
	)).done([=](const MTPUpdates &result) {
		session->api().applyUpdates(result);
	}).fail([=](const MTP::Error &error) {
		fail(error.type());
	}).send();
}

[[nodiscard]] QString PrepareBanner(const QString &path) {
	auto image = QImage(path);
	if (image.isNull()) {
		return QString();
	}
	const auto ratio = double(kBannerWidth) / kBannerHeight;
	auto part = QSize(image.width(), int(image.width() / ratio));
	if (part.height() > image.height()) {
		part = QSize(int(image.height() * ratio), image.height());
	}
	const auto cropped = image.copy(QRect(
		QPoint(
			(image.width() - part.width()) / 2,
			(image.height() - part.height()) / 2),
		part));
	const auto scaled = cropped.scaled(
		kBannerWidth,
		kBannerHeight,
		Qt::IgnoreAspectRatio,
		Qt::SmoothTransformation).convertToFormat(QImage::Format_RGB32);
	auto bytes = QByteArray();
	auto buffer = QBuffer(&bytes);
	buffer.open(QIODevice::WriteOnly);
	if (!scaled.save(&buffer, "JPG", kBannerQuality)) {
		return QString();
	}
	return QString::fromLatin1(bytes.toBase64());
}

} // namespace

bool ProfileLoggedIn(not_null<Main::Session*> session) {
	return !Token(session).isEmpty();
}

rpl::producer<bool> ProfileLoggedInValue(not_null<Main::Session*> session) {
	return rpl::single(rpl::empty) | rpl::then(
		LoginChanges().events()
	) | rpl::map([=] {
		return ProfileLoggedIn(session);
	}) | rpl::distinct_until_changed();
}

rpl::producer<ProfileState> ProfileStateValue(
		not_null<Main::Session*> session) {
	return States()[session->uniqueId()].value();
}

void LoginProfile(not_null<Main::Session*> session, Done done, Fail fail) {
	Post(session, u"/api/lumengram/login/start"_q, {}, [=](QJsonObject r) {
		const auto code = r.value(u"code"_q).toString();
		const auto check = r.value(u"check"_q).toString();
		const auto username = r.value(u"bot"_q).toString();
		if (code.isEmpty() || check.isEmpty() || username.isEmpty()) {
			fail(QString());
			return;
		}
		const auto start = [=](not_null<UserData*> bot) {
			StartBot(session, bot, u"login_"_q + code, fail);
			PollLogin(session, code, check, kLoginPollTimes, done, fail);
		};
		if (const auto peer = session->data().peerByUsername(username)) {
			if (const auto bot = peer->asUser()) {
				start(bot);
				return;
			}
		}
		session->api().request(MTPcontacts_ResolveUsername(
			MTP_flags(0),
			MTP_string(username),
			MTP_string()
		)).done([=](const MTPcontacts_ResolvedPeer &result) {
			session->data().processUsers(result.data().vusers());
			session->data().processChats(result.data().vchats());
			const auto peer = session->data().peerLoaded(
				peerFromMTP(result.data().vpeer()));
			if (const auto bot = peer ? peer->asUser() : nullptr) {
				start(bot);
			} else {
				fail(u"BOT_NOT_FOUND"_q);
			}
		}).fail([=](const MTP::Error &error) {
			fail(error.type());
		}).send();
	}, fail);
}

void LogoutProfile(not_null<Main::Session*> session) {
	Post(session, u"/api/lumengram/logout"_q, {}, [](QJsonObject) {
	}, [](QString) {
	});
	SetToken(session, QString());
	States()[session->uniqueId()] = ProfileState();
}

void RefreshProfileState(not_null<Main::Session*> session) {
	if (!ProfileLoggedIn(session)) {
		States()[session->uniqueId()] = ProfileState();
		return;
	}
	const auto key = session->uniqueId();
	Post(session, u"/api/lumengram/me"_q, {}, [=](QJsonObject r) {
		const auto music = r.value(u"music"_q).toObject();
		States()[key] = ProfileState{
			.hasBanner = r.value(u"banner"_q).isObject(),
			.banned = r.value(u"banned"_q).toBool(),
			.musicProvider = music.value(u"provider"_q).toString(),
			.lastfmUser = music.value(u"lastfm_user"_q).toString(),
			.spotifyAvailable = r.value(u"spotify_available"_q).toBool(),
			.lastfmAvailable = r.value(u"lastfm_available"_q).toBool(),
		};
	}, [](QString) {
	});
}

void UploadBanner(
		not_null<Main::Session*> session,
		const QString &path,
		Done done,
		Fail fail) {
	const auto data = PrepareBanner(path);
	if (data.isEmpty()) {
		fail(u"BAD_IMAGE"_q);
		return;
	}
	Post(session, u"/api/lumengram/banner"_q, {
		{ u"banner_data"_q, data },
	}, [=](QJsonObject) {
		RefreshProfilesNow();
		RefreshProfileState(session);
		done();
	}, fail);
}

void DeleteBanner(not_null<Main::Session*> session, Done done, Fail fail) {
	Post(session, u"/api/lumengram/banner/delete"_q, {}, [=](QJsonObject) {
		RefreshProfilesNow();
		RefreshProfileState(session);
		done();
	}, fail);
}

void ConnectLastfm(
		not_null<Main::Session*> session,
		const QString &username,
		Done done,
		Fail fail) {
	Post(session, u"/api/lumengram/lastfm"_q, {
		{ u"username"_q, username.trimmed() },
	}, [=](QJsonObject) {
		RefreshProfilesNow();
		RefreshProfileState(session);
		done();
	}, fail);
}

void ConnectSpotify(not_null<Main::Session*> session, Done done, Fail fail) {
	Post(session, u"/api/lumengram/spotify/start"_q, {}, [=](QJsonObject r) {
		const auto url = r.value(u"url"_q).toString();
		if (url.startsWith(u"https://accounts.spotify.com/"_q)) {
			UrlClickHandler::Open(url);
			done();
		} else {
			fail(QString());
		}
	}, fail);
}

void DisconnectMusic(not_null<Main::Session*> session, Done done, Fail fail) {
	Post(session, u"/api/lumengram/music/disconnect"_q, {}, [=](QJsonObject) {
		RefreshProfilesNow();
		RefreshProfileState(session);
		done();
	}, fail);
}

} // namespace Lumen
