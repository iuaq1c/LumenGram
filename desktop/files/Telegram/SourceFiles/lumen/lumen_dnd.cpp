/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#include "lumen/lumen_dnd.h"

#include "base/timer.h"
#include "core/application.h"
#include "core/core_settings.h"
#include "lang/lang_keys.h"
#include "lumen/lumen_profile_account.h"
#include "main/main_session.h"

namespace Lumen {
namespace {

constexpr auto kTextPref = "lumen/dnd_text"_cs;
constexpr auto kUntilPref = "lumen/dnd_until"_cs;
constexpr auto kAutoReplyPref = "lumen/dnd_auto_reply"_cs;

[[nodiscard]] rpl::variable<bool> &Active() {
	static auto result = rpl::variable<bool>(false);
	return result;
}

[[nodiscard]] base::Timer &EndTimer() {
	static auto result = base::Timer([] { Active() = DndActive(); });
	return result;
}

[[nodiscard]] qint64 UntilMs() {
	return QString::fromUtf8(
		Core::App().settings().readPref<QByteArray>(kUntilPref)).toLongLong();
}

void Save(const QString &text, qint64 untilMs) {
	auto &settings = Core::App().settings();
	settings.writePref<QByteArray>(kTextPref, text.toUtf8());
	settings.writePref<QByteArray>(
		kUntilPref,
		QByteArray::number(untilMs));
	Core::App().saveSettingsDelayed();
	Active() = DndActive();
	const auto left = untilMs - QDateTime::currentMSecsSinceEpoch();
	if (left > 0) {
		EndTimer().callOnce(left + 1000);
	}
}

} // namespace

bool DndActive() {
	return !DndText().isEmpty()
		&& UntilMs() > QDateTime::currentMSecsSinceEpoch();
}

rpl::producer<bool> DndActiveValue() {
	Active() = DndActive();
	return Active().value();
}

QString DndText() {
	return QString::fromUtf8(
		Core::App().settings().readPref<QByteArray>(kTextPref));
}

QDateTime DndUntil() {
	return QDateTime::fromMSecsSinceEpoch(UntilMs());
}

QString DndStatusLine() {
	return tr::lng_lumen_dnd_until(
		tr::now,
		lt_status,
		DndText(),
		lt_time,
		QLocale().toString(DndUntil().time(), QLocale::ShortFormat));
}

bool DndAutoReply() {
	return Core::App().settings().readPref<bool>(kAutoReplyPref, true);
}

void SetDndAutoReply(bool enabled) {
	Core::App().settings().writePref<bool>(kAutoReplyPref, enabled);
	Core::App().saveSettingsDelayed();
}

std::vector<QString> DndPresets() {
	return {
		QString::fromUtf8("\xF0\x9F\x98\xB4 ") + tr::lng_lumen_dnd_sleep(tr::now),
		QString::fromUtf8("\xF0\x9F\x93\x9A ") + tr::lng_lumen_dnd_study(tr::now),
		QString::fromUtf8("\xF0\x9F\x92\xBC ") + tr::lng_lumen_dnd_work(tr::now),
		QString::fromUtf8("\xF0\x9F\x9A\x97 ") + tr::lng_lumen_dnd_driving(tr::now),
		QString::fromUtf8("\xF0\x9F\x8E\xAE ") + tr::lng_lumen_dnd_gaming(tr::now),
	};
}

void StartDnd(
		not_null<Main::Session*> session,
		const QString &text,
		QTime time,
		Fn<void(QString)> fail) {
	auto until = QDateTime(QDate::currentDate(), time);
	if (until <= QDateTime::currentDateTime()) {
		until = until.addDays(1);
	}
	Save(text, until.toMSecsSinceEpoch());
	if (ProfileLoggedIn(session)) {
		SaveAway(session, text, until.toSecsSinceEpoch(), [] {}, fail);
	}
}

void StopDnd(not_null<Main::Session*> session) {
	Save(QString(), 0);
	if (ProfileLoggedIn(session)) {
		SaveAway(session, QString(), 0, [] {}, [](QString) {});
	}
}

} // namespace Lumen
