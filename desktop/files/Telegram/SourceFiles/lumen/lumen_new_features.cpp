#include "lumen/lumen_new_features.h"

#include "api/api_blocked_peers.h"
#include "apiwrap.h"
#include "base/unixtime.h"
#include "core/application.h"
#include "core/core_settings.h"
#include "data/data_peer.h"
#include "data/data_session.h"
#include "data/data_user.h"
#include "history/history.h"
#include "history/history_item.h"
#include "lang/lang_keys.h"
#include "main/main_account.h"
#include "main/main_domain.h"
#include "main/main_session.h"
#include "settings/settings_common.h"
#include "ui/layers/generic_box.h"
#include "ui/widgets/buttons.h"
#include "ui/widgets/labels.h"
#include "ui/wrap/vertical_layout.h"
#include "window/window_session_controller.h"
#include "styles/style_boxes.h"
#include "styles/style_layers.h"
#include "styles/style_settings.h"

#include <QtCore/QJsonArray>
#include <QtCore/QJsonDocument>
#include <QtCore/QJsonObject>
#include <QtCore/QRegularExpression>
#include <QtNetwork/QNetworkInformation>

namespace Lumen {
namespace {

constexpr auto kMaxLog = 200;
constexpr auto kMaxAge = TimeId(120);
constexpr auto kBoostMultiplier = 2;

[[nodiscard]] rpl::event_stream<> &Changes() {
	static auto result = rpl::event_stream<>();
	return result;
}

[[nodiscard]] std::string Pref(std::string_view key) {
	return "lumen/features/" + std::string(key);
}

[[nodiscard]] std::string LogPref(uint64 account) {
	return Pref("threat_log_") + std::to_string(account);
}

[[nodiscard]] QJsonArray &Log(uint64 account) {
	static auto cache = std::map<uint64, QJsonArray>();
	const auto found = cache.find(account);
	if (found != cache.end()) {
		return found->second;
	}
	return cache.emplace(account, QJsonDocument::fromJson(
		Core::App().settings().readPref<QByteArray>(LogPref(account))
	).array()).first->second;
}

void SaveLog(uint64 account) {
	Core::App().settings().writePref<QByteArray>(LogPref(account),
		QJsonDocument(Log(account)).toJson(QJsonDocument::Compact));
	Core::App().saveSettingsDelayed();
}

[[nodiscard]] bool Matches(const QJsonObject &entry, not_null<const HistoryItem*> item) {
	return entry.value(u"peer"_q).toString().toULongLong() == item->history()->peer->id.value
		&& entry.value(u"message"_q).toString().toLongLong() == item->id.bare;
}

void RefreshItems() {
	for (const auto &[index, account] : Core::App().domain().accounts()) {
		if (const auto session = account->maybeSession()) {
			for (const auto &value : Log(session->uniqueId())) {
				const auto entry = value.toObject();
				if (const auto item = session->data().message(
					PeerId(entry.value(u"peer"_q).toString().toULongLong()),
					MsgId(entry.value(u"message"_q).toString().toLongLong()))) {
					session->data().requestItemResize(item);
				}
			}
		}
	}
}

void HandleThreat(not_null<HistoryItem*> item) {
	if (!FeatureEnabled("threats") || item->out() || !item->isRegular() || item->isService()
		|| base::unixtime::now() - item->date() > kMaxAge) {
		return;
	}
	const auto peer = item->history()->peer;
	const auto user = peer->asUser();
	if (!user || user->isSelf() || user->isBot() || user->isServiceUser() || user->isInaccessible()
		|| (user->isContact() && FeatureEnabled("trust_contacts", true))) {
		return;
	}
	const auto except = FeatureText("exceptions").split(QRegularExpression(u"[\\s,;]+"_q));
	if (except.contains(QString::number(peerToUser(user->id).bare))) {
		return;
	}
	auto &log = Log(item->history()->session().uniqueId());
	for (const auto &value : log) {
		if (Matches(value.toObject(), item)) {
			return;
		}
	}
	const auto text = item->originalText().text.normalized(QString::NormalizationForm_KC).toLower();
	const auto phrases = FeatureText("phrases").split('\n');
	for (auto i = 0; i < std::min(int(phrases.size()), kMaxLog); ++i) {
		const auto phrase = phrases[i].trimmed().normalized(QString::NormalizationForm_KC).toLower();
		if (phrase.isEmpty()) {
			continue;
		}
		const auto matches = FeatureMode("match")
			? text.contains(phrase)
			: QRegularExpression(u"(^|[^\\p{L}\\p{N}_])"_q + QRegularExpression::escape(phrase)
				+ u"($|[^\\p{L}\\p{N}_])"_q, QRegularExpression::UseUnicodePropertiesOption).match(text).hasMatch();
		if (!matches) {
			continue;
		}
		log.prepend(QJsonObject{
			{ u"peer"_q, QString::number(peer->id.value) },
			{ u"message"_q, QString::number(item->id.bare) },
			{ u"phrase"_q, phrases[i].trimmed() },
			{ u"name"_q, peer->name() },
			{ u"hidden"_q, true },
		});
		while (log.size() > kMaxLog) {
			log.removeLast();
		}
		SaveLog(item->history()->session().uniqueId());
		if (FeatureMode("threat_mode")) {
			peer->session().api().blockedPeers().block(peer);
		}
		peer->owner().requestItemResize(item);
		break;
	}
}

[[nodiscard]] int TransferMode(bool upload) {
	const auto mode = FeatureMode(upload ? "upload" : "download");
	if (!mode) {
		return 0;
	}
	static const auto loaded = QNetworkInformation::loadDefaultBackend();
	const auto network = loaded ? QNetworkInformation::instance() : nullptr;
	const auto unmetered = network && (network->transportMedium() == QNetworkInformation::TransportMedium::WiFi
		|| network->transportMedium() == QNetworkInformation::TransportMedium::Ethernet);
	if (FeatureEnabled("wifi_only") && !unmetered) {
		return 0;
	}
	return (mode == 1) ? (unmetered ? 2 : 0) : mode;
}

} // namespace

bool FeatureEnabled(std::string_view key, bool fallback) {
	return Core::App().settings().readPref<bool>(Pref(key), fallback);
}

void SetFeatureEnabled(std::string_view key, bool enabled) {
	Core::App().settings().writePref<bool>(Pref(key), enabled);
	Core::App().saveSettingsDelayed();
	RefreshItems();
	Changes().fire({});
}

QString FeatureText(std::string_view key) {
	return QString::fromUtf8(Core::App().settings().readPref<QByteArray>(Pref(key)));
}

void SetFeatureText(std::string_view key, const QString &value) {
	Core::App().settings().writePref<QByteArray>(Pref(key), value.left(40000).toUtf8());
	Core::App().saveSettingsDelayed();
	Changes().fire({});
}

int FeatureMode(std::string_view key) {
	return FeatureText(key).toInt();
}

void SetFeatureMode(std::string_view key, int value) {
	SetFeatureText(key, QString::number(value));
}

rpl::producer<> FeatureChanges() {
	return Changes().events();
}

rpl::producer<bool> FeatureEnabledValue(std::string key, bool fallback) {
	return rpl::single(rpl::empty) | rpl::then(FeatureChanges()) | rpl::map([=] {
		return FeatureEnabled(key, fallback);
	});
}

int TransferRequestLimit(int standard, bool upload) {
	const auto mode = TransferMode(upload);
	return (mode == 2) ? standard * kBoostMultiplier : (mode == 3) ? standard / (upload ? 2 : 4) : standard;
}

int TransferSessionLimit(int standard, bool upload) {
	return TransferMode(upload) == 3 ? 1 : standard;
}

void SetupThreatProtection(not_null<Main::Session*> session) {
	session->data().newItemAdded() | rpl::on_next(HandleThreat, session->lifetime());
}

bool IsThreatHidden(not_null<const HistoryItem*> item) {
	if (!FeatureEnabled("threats")) {
		return false;
	}
	for (const auto &value : Log(item->history()->session().uniqueId())) {
		const auto entry = value.toObject();
		if (Matches(entry, item)) {
			return entry.value(u"hidden"_q).toBool();
		}
	}
	return false;
}

void ShowThreatLog(not_null<Window::SessionController*> controller) {
	const auto session = &controller->session();
	const auto account = session->uniqueId();
	controller->show(Box([=](not_null<Ui::GenericBox*> box) {
		box->setTitle(tr::lng_lumen_threat_log());
		box->setWidth(st::boxWideWidth);
		const auto container = box->verticalLayout();
		for (const auto &value : Log(account)) {
			const auto entry = value.toObject();
			const auto peerId = PeerId(entry.value(u"peer"_q).toString().toULongLong());
			const auto msgId = MsgId(entry.value(u"message"_q).toString().toLongLong());
			Settings::AddButtonWithLabel(container,
				rpl::single(entry.value(u"name"_q).toString()),
				rpl::single(entry.value(u"phrase"_q).toString()),
				st::settingsButtonNoIcon)->addClickHandler([=] {
				controller->show(Box([=](not_null<Ui::GenericBox*> details) {
					details->setTitle(tr::lng_lumen_threat_log());
					details->addRow(object_ptr<Ui::FlatLabel>(details,
						entry.value(u"phrase"_q).toString(), st::boxLabel));
					if (const auto peer = session->data().peerLoaded(peerId)) {
						const auto blocked = peer->isBlocked();
						details->addButton(blocked ? tr::lng_lumen_threat_unblock()
							: tr::lng_lumen_threat_block(), [=] {
							if (blocked) session->api().blockedPeers().unblock(peer);
							else session->api().blockedPeers().block(peer);
							details->closeBox();
						});
					}
					details->addButton(tr::lng_lumen_threat_reveal(), [=] {
						auto &log = Log(account);
						for (auto i = 0; i < log.size(); ++i) {
							auto saved = log[i].toObject();
							if (saved.value(u"peer"_q) == entry.value(u"peer"_q)
								&& saved.value(u"message"_q) == entry.value(u"message"_q)) {
								saved.insert(u"hidden"_q, false);
								log[i] = saved;
							}
						}
						SaveLog(account);
						RefreshItems();
						details->closeBox();
						controller->showPeerHistory(peerId, Window::SectionShow::Way::ClearStack, msgId);
					});
					details->addButton(tr::lng_close(), [=] { details->closeBox(); });
				}));
			});
		}
		box->addButton(tr::lng_close(), [=] { box->closeBox(); });
		box->addLeftButton(tr::lng_lumen_threat_clear(), [=] {
			RefreshItems();
			Log(account) = QJsonArray();
			SaveLog(account);
			box->closeBox();
		});
	}));
}

} // namespace Lumen
