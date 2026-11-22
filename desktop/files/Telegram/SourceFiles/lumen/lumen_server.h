/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#pragma once

class QJsonObject;

namespace Lumen {

// WHY: whole lists are downloaded, so tunexa.ru never learns whose
// profile is opened. Shown only in LumenGram.

struct NowPlaying {
	QString title;
	QString artist;
	QString url;
	QString cover;
	bool spotify = false;
	int duration = 0; // Seconds, Spotify only.
	qint64 startedAt = 0; // Local ms since epoch, Spotify only.
};

[[nodiscard]] bool IsSupporter(uint64 userId);
[[nodiscard]] QString BannerUrl(uint64 userId);
[[nodiscard]] std::optional<NowPlaying> CurrentTrack(uint64 userId);

// Fires when supporters, banners or tracks change.
[[nodiscard]] rpl::producer<> ProfilesChanges();

// Loads a banner or a cover picture once and keeps it in memory.
void LoadImage(const QString &url, Fn<void(QImage)> done);

void StartServerSync();
void RefreshProfilesNow();

// A plain form POST to tunexa.ru: the hosting firewall challenges multipart.
void ServerPost(
	const QString &path,
	const std::vector<std::pair<QString, QString>> &fields,
	Fn<void(QJsonObject)> done,
	Fn<void(QString)> fail);

} // namespace Lumen
