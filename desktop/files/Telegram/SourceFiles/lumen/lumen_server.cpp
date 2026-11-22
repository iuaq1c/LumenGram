/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#include "lumen/lumen_server.h"

#include "base/timer.h"
#include "base/unixtime.h"
#include "core/application.h"
#include "data/data_changes.h"
#include "data/data_peer.h"
#include "data/data_session.h"
#include "main/main_account.h"
#include "main/main_domain.h"
#include "main/main_session.h"

#include <QtCore/QJsonArray>
#include <QtCore/QJsonDocument>
#include <QtCore/QJsonObject>
#include <QtCore/QRegularExpression>
#include <QtNetwork/QNetworkAccessManager>
#include <QtNetwork/QNetworkReply>

namespace Lumen {
namespace {

constexpr auto kSupportersRefresh = 30 * 60 * crl::time(1000);
constexpr auto kProfilesRefresh = 60 * crl::time(1000);
constexpr auto kMaxListSize = 4 * 1024 * 1024;
constexpr auto kMaxImageSize = 8 * 1024 * 1024;
constexpr auto kMaxCachedImages = 64;

[[nodiscard]] QString SupportersUrl() {
	return u"https://tunexa.ru/api/lumengram/supporters.json"_q;
}

[[nodiscard]] QString ProfilesUrl() {
	return u"https://tunexa.ru/api/lumengram/profiles.json"_q;
}

struct State {
	bool started = false;
	base::flat_set<uint64> supporters;
	base::flat_map<uint64, QString> banners;
	base::flat_map<uint64, NowPlaying> music;
	base::flat_map<QString, QImage> images;
	base::flat_map<QString, std::vector<Fn<void(QImage)>>> loading;
	base::Timer supportersTimer;
	base::Timer profilesTimer;
	std::unique_ptr<QNetworkAccessManager> manager;
	rpl::event_stream<> changes;
};

[[nodiscard]] State &Current() {
	// Never destroyed: timers and the network manager must not outlive Qt.
	static const auto result = new State();
	return *result;
}

[[nodiscard]] QNetworkAccessManager &Manager() {
	auto &state = Current();
	if (!state.manager) {
		state.manager = std::make_unique<QNetworkAccessManager>();
	}
	return *state.manager;
}

[[nodiscard]] bool ValidId(const QString &value) {
	static const auto re = QRegularExpression(u"^[1-9][0-9]{4,15}$"_q);
	return re.match(value).hasMatch();
}

[[nodiscard]] bool ValidUrl(const QString &url, const QString &pattern) {
	return QRegularExpression(pattern).match(url).hasMatch();
}

void Get(const QString &url, int maxSize, Fn<void(QByteArray)> done) {
	auto request = QNetworkRequest(QUrl(url));
	request.setAttribute(
		QNetworkRequest::RedirectPolicyAttribute,
		QNetworkRequest::NoLessSafeRedirectPolicy);
	request.setTransferTimeout(30 * 1000);
	const auto reply = Manager().get(request);
	QObject::connect(reply, &QNetworkReply::finished, [=] {
		reply->deleteLater();
		if (reply->error() != QNetworkReply::NoError) {
			done(QByteArray());
			return;
		}
		done(reply->read(maxSize));
	});
}

void RepaintSupporters(const base::flat_set<uint64> &ids) {
	for (const auto &[index, account] : Core::App().domain().accounts()) {
		const auto session = account->maybeSession();
		if (!session) {
			continue;
		}
		for (const auto id : ids) {
			if (const auto peer = session->data().peerLoaded(
					peerFromUser(UserId(id)))) {
				session->changes().peerUpdated(
					peer,
					Data::PeerUpdate::Flag::Name);
			}
		}
	}
}

void RefreshSupporters() {
	Get(SupportersUrl(), kMaxListSize, [](QByteArray bytes) {
		if (bytes.isEmpty()) {
			return;
		}
		const auto list = QJsonDocument::fromJson(bytes).object().value(
			u"supporters"_q).toArray();
		auto ids = base::flat_set<uint64>();
		for (const auto &value : list) {
			const auto text = value.isString()
				? value.toString()
				: QString::number(qint64(value.toDouble()));
			if (ValidId(text)) {
				ids.emplace(text.toULongLong());
			}
		}
		auto &state = Current();
		if (ids == state.supporters) {
			return;
		}
		auto changed = ids;
		for (const auto id : state.supporters) {
			changed.emplace(id);
		}
		state.supporters = std::move(ids);
		RepaintSupporters(changed);
		state.changes.fire({});
	});
}

void RefreshProfiles() {
	Get(ProfilesUrl(), kMaxListSize, [](QByteArray bytes) {
		if (bytes.isEmpty()) {
			return;
		}
		const auto root = QJsonDocument::fromJson(bytes).object();
		// Server clock minus device clock, for the track progress.
		const auto serverNow = qint64(root.value(u"now"_q).toDouble());
		const auto shift = serverNow
			? (serverNow * 1000 - QDateTime::currentMSecsSinceEpoch())
			: 0;

		auto banners = base::flat_map<uint64, QString>();
		const auto bannersObject = root.value(u"banners"_q).toObject();
		for (auto i = bannersObject.begin(); i != bannersObject.end(); ++i) {
			const auto url = i.value().toString();
			if (ValidId(i.key()) && ValidUrl(
					url,
					u"^https://tunexa\\.ru/lumengram/banner/[a-f0-9]{32}\\.jpg$"_q)) {
				banners.emplace(i.key().toULongLong(), url);
			}
		}

		auto music = base::flat_map<uint64, NowPlaying>();
		const auto musicObject = root.value(u"music"_q).toObject();
		for (auto i = musicObject.begin(); i != musicObject.end(); ++i) {
			const auto track = i.value().toObject();
			auto entry = NowPlaying{
				.title = track.value(u"title"_q).toString().left(200),
				.artist = track.value(u"artist"_q).toString().left(200),
				.spotify = (track.value(u"provider"_q).toString()
					== u"spotify"_q),
			};
			if (!ValidId(i.key()) || entry.title.isEmpty()) {
				continue;
			}
			const auto url = track.value(u"url"_q).toString();
			if (ValidUrl(
					url,
					u"^https://(open\\.spotify\\.com|www\\.last\\.fm)/"_q)) {
				entry.url = url;
			}
			const auto cover = track.value(u"cover"_q).toString();
			if (ValidUrl(
					cover,
					u"^https://(i\\.scdn\\.co/image/"
					"|lastfm\\.freetls\\.fastly\\.net/i/)"_q)) {
				entry.cover = cover;
			}
			const auto duration = track.value(u"duration"_q).toInt();
			const auto at = qint64(track.value(u"at"_q).toDouble());
			if (duration > 0 && at > 0 && track.contains(u"progress"_q)) {
				const auto progress = track.value(u"progress"_q).toInt();
				entry.duration = duration;
				entry.startedAt = at * 1000 - shift - progress * 1000LL;
			}
			music.emplace(i.key().toULongLong(), std::move(entry));
		}

		auto &state = Current();
		state.banners = std::move(banners);
		state.music = std::move(music);
		state.changes.fire({});
	});
}

} // namespace

void StartServerSync() {
	auto &state = Current();
	if (state.started) {
		return;
	}
	state.started = true;
	state.supportersTimer.setCallback(RefreshSupporters);
	state.supportersTimer.callEach(kSupportersRefresh);
	state.profilesTimer.setCallback(RefreshProfiles);
	state.profilesTimer.callEach(kProfilesRefresh);
	RefreshSupporters();
	RefreshProfiles();
}

void RefreshProfilesNow() {
	StartServerSync();
	RefreshProfiles();
}

void ServerPost(
		const QString &path,
		const std::vector<std::pair<QString, QString>> &fields,
		Fn<void(QJsonObject)> done,
		Fn<void(QString)> fail) {
	auto body = QByteArray();
	for (const auto &[key, value] : fields) {
		if (!body.isEmpty()) {
			body.append('&');
		}
		body.append(QUrl::toPercentEncoding(key));
		body.append('=');
		body.append(QUrl::toPercentEncoding(value));
	}
	auto request = QNetworkRequest(QUrl(u"https://tunexa.ru"_q + path));
	request.setHeader(
		QNetworkRequest::ContentTypeHeader,
		u"application/x-www-form-urlencoded"_q);
	request.setTransferTimeout(60 * 1000);
	const auto reply = Manager().post(request, body);
	QObject::connect(reply, &QNetworkReply::finished, [=] {
		reply->deleteLater();
		const auto object = QJsonDocument::fromJson(
			reply->read(kMaxListSize)).object();
		if (object.value(u"ok"_q).toBool()) {
			done(object);
		} else {
			const auto error = object.value(u"error"_q).toString();
			fail(error.isEmpty() ? reply->errorString() : error);
		}
	});
}

bool IsSupporter(uint64 userId) {
	StartServerSync();
	return Current().supporters.contains(userId);
}

QString BannerUrl(uint64 userId) {
	StartServerSync();
	const auto &banners = Current().banners;
	const auto i = banners.find(userId);
	return (i != end(banners)) ? i->second : QString();
}

std::optional<NowPlaying> CurrentTrack(uint64 userId) {
	StartServerSync();
	const auto &music = Current().music;
	const auto i = music.find(userId);
	return (i != end(music))
		? std::make_optional(i->second)
		: std::nullopt;
}

rpl::producer<> ProfilesChanges() {
	StartServerSync();
	return Current().changes.events();
}

void LoadImage(const QString &url, Fn<void(QImage)> done) {
	auto &state = Current();
	if (const auto i = state.images.find(url); i != end(state.images)) {
		done(i->second);
		return;
	}
	auto &waiting = state.loading[url];
	waiting.push_back(std::move(done));
	if (waiting.size() > 1) {
		return;
	}
	Get(url, kMaxImageSize, [=](QByteArray bytes) {
		auto image = QImage::fromData(bytes);
		auto &state = Current();
		if (!image.isNull()) {
			if (state.images.size() >= kMaxCachedImages) {
				state.images.erase(begin(state.images));
			}
			state.images.emplace(url, image);
		}
		auto callbacks = base::take(state.loading[url]);
		state.loading.remove(url);
		for (const auto &callback : callbacks) {
			callback(image);
		}
	});
}

} // namespace Lumen
