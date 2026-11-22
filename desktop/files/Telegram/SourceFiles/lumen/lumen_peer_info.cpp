/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#include "lumen/lumen_peer_info.h"

#include "base/unixtime.h"
#include "data/data_changes.h"
#include "data/data_channel.h"
#include "data/data_peer.h"
#include "data/data_user.h"
#include "lang/lang_keys.h"
#include "main/main_session.h"
#include "ui/image/image_location.h"

namespace Lumen {
namespace {

// WHY: Telegram does not expose registration dates; user ids grow roughly in order,
// so the month is interpolated between reference points, the same table as the web version.
struct RegistrationAnchor {
	uint64 id = 0;
	int year = 0;
	int month = 0;
};

constexpr auto kRegistrationAnchors = std::array<RegistrationAnchor, 20>{ {
	{ 0ULL, 2013, 8 },
	{ 100'000'000ULL, 2015, 1 },
	{ 200'000'000ULL, 2016, 4 },
	{ 400'000'000ULL, 2017, 7 },
	{ 700'000'000ULL, 2018, 10 },
	{ 1'000'000'000ULL, 2019, 12 },
	{ 1'300'000'000ULL, 2020, 7 },
	{ 1'600'000'000ULL, 2021, 1 },
	{ 1'638'932'794ULL, 2021, 2 },
	{ 1'900'000'000ULL, 2021, 7 },
	{ 2'147'483'647ULL, 2022, 2 },
	{ 5'000'000'000ULL, 2022, 3 },
	{ 5'500'000'000ULL, 2022, 10 },
	{ 6'000'000'000ULL, 2023, 2 },
	{ 6'500'000'000ULL, 2023, 8 },
	{ 7'000'000'000ULL, 2024, 1 },
	{ 7'500'000'000ULL, 2024, 8 },
	{ 8'000'000'000ULL, 2025, 1 },
	{ 8'500'000'000ULL, 2025, 7 },
	{ 9'000'000'000ULL, 2026, 1 },
} };

[[nodiscard]] qint64 AnchorTime(const RegistrationAnchor &anchor) {
	return QDateTime(
		QDate(anchor.year, anchor.month, 1),
		QTime(0, 0),
		Qt::UTC).toSecsSinceEpoch();
}

[[nodiscard]] QString DcLocation(int dcId) {
	switch (dcId) {
	case 1:
	case 3: return u"Miami, US"_q;
	case 2:
	case 4: return u"Amsterdam, NL"_q;
	case 5: return u"Singapore, SG"_q;
	}
	return QString();
}

[[nodiscard]] int PeerDcId(not_null<PeerData*> peer) {
	const auto location = peer->userpicLocation();
	const auto storage = std::get_if<StorageFileLocation>(
		&location.file().data);
	return storage ? storage->dcId() : 0;
}

[[nodiscard]] QString Label(not_null<PeerData*> peer) {
	const auto dcId = PeerDcId(peer);
	const auto location = DcLocation(dcId);
	return location.isEmpty()
		? tr::lng_lumen_profile_id(tr::now)
		: tr::lng_lumen_profile_id_dc(
			tr::now,
			lt_dc,
			QString::number(dcId),
			lt_location,
			location);
}

} // namespace

QString PeerIdText(not_null<PeerData*> peer) {
	const auto id = peer->id;
	if (peerIsUser(id)) {
		return QString::number(peerToUser(id).bare);
	} else if (peerIsChannel(id)) {
		return u"-100"_q + QString::number(peerToChannel(id).bare);
	}
	return u"-"_q + QString::number(peerToChat(id).bare);
}

QString RegistrationEstimateText(not_null<UserData*> user) {
	const auto id = peerToUser(user->id).bare;
	if (!id) {
		return QString();
	}
	const auto &anchors = kRegistrationAnchors;
	auto i = 1;
	while (i + 1 < int(anchors.size()) && id > anchors[i].id) {
		++i;
	}
	const auto &from = anchors[i - 1];
	const auto &to = anchors[i];
	const auto part = double(qint64(id) - qint64(from.id))
		/ double(to.id - from.id);
	const auto time = std::min(
		AnchorTime(from) + qint64(part * (AnchorTime(to) - AnchorTime(from))),
		qint64(base::unixtime::now()));
	const auto date = QDateTime::fromSecsSinceEpoch(time, Qt::UTC).date();
	return QString(QChar(0x2248)) + u' '
		+ langMonthOfYearFull(date.month(), date.year());
}

QString JoinDateText(not_null<ChannelData*> channel) {
	// For a member Telegram sends the join date here, for others the creation date.
	if (!channel->amIn() || !channel->date) {
		return QString();
	}
	// Always with the year, unlike langDayOfMonthFull() that drops it for this year.
	const auto date = base::unixtime::parse(channel->date).date();
	return tr::lng_month_day_year(
		tr::now,
		lt_month,
		Lang::MonthDay(date.month())(tr::now),
		lt_day,
		QString::number(date.day()),
		lt_year,
		QString::number(date.year()));
}

rpl::producer<QString> PeerIdLabel(not_null<PeerData*> peer) {
	return peer->session().changes().peerFlagsValue(
		peer,
		Data::PeerUpdate::Flag::Photo
	) | rpl::map([=](const Data::PeerUpdate &) {
		return Label(peer);
	});
}

} // namespace Lumen
