/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#include "lumen/lumen_connection.h"

#include "base/timer.h"
#include "core/application.h"
#include "core/core_settings.h"
#include "mtproto/facade.h"
#include "mtproto/mtproto_proxy_data.h"

#include <QtCore/QJsonArray>
#include <QtCore/QJsonDocument>
#include <QtCore/QJsonObject>
#include <QtNetwork/QNetworkAccessManager>
#include <QtNetwork/QNetworkReply>

namespace Lumen {
namespace {

constexpr auto kConnectionModePref = "lumen/connection_mode"_cs;
constexpr auto kProxyListPref = "lumen/proxy_list"_cs;
constexpr auto kAppliedProxyPref = "lumen/proxy_applied"_cs;

constexpr auto kUnreachableTimeout = 8 * crl::time(1000);
constexpr auto kRefreshInterval = 6 * 60 * 60 * crl::time(1000);
constexpr auto kMaxListSize = 64 * 1024;

// Static files published by the LumenGram server kit, tried in order.
[[nodiscard]] QString ProxyListUrl(int index) {
	switch (index) {
	case 0: return u"https://tunexa.ru/api/lumengram/proxies.json"_q;
	case 1: return u"https://tg.tunexa.ru/lumengram/proxies.json"_q;
	}
	return QString();
}

struct State {
	bool started = false;
	int32 lastState = MTP::DisconnectedState;
	std::vector<MTP::ProxyData> proxies;
	base::Timer unreachableTimer;
	base::Timer refreshTimer;
	std::unique_ptr<QNetworkAccessManager> manager;
	rpl::event_stream<ConnectionMode> modeChanges;
};

[[nodiscard]] State &Current() {
	// Never destroyed: timers and the network manager must not outlive Qt.
	static const auto result = new State();
	return *result;
}

[[nodiscard]] std::vector<MTP::ProxyData> ParseList(
		const QByteArray &bytes) {
	auto result = std::vector<MTP::ProxyData>();
	const auto document = QJsonDocument::fromJson(bytes);
	const auto list = document.object().value(u"proxies"_q).toArray();
	for (const auto &value : list) {
		const auto entry = value.toObject();
		auto proxy = MTP::ProxyData();
		proxy.type = MTP::ProxyData::Type::Mtproto;
		proxy.host = entry.value(u"server"_q).toString().trimmed();
		proxy.port = uint32(entry.value(u"port"_q).toInt());
		proxy.password = entry.value(u"secret"_q).toString().trimmed();
		if (proxy.status() == MTP::ProxyData::Status::Valid) {
			result.push_back(std::move(proxy));
		}
	}
	return result;
}

[[nodiscard]] QString ProxyKey(const MTP::ProxyData &proxy) {
	return proxy.host + ':' + QString::number(proxy.port);
}

[[nodiscard]] QString AppliedKey() {
	return QString::fromUtf8(Core::App().settings().readPref<QByteArray>(
		kAppliedProxyPref));
}

void SetAppliedKey(const QString &key) {
	Core::App().settings().writePref<QByteArray>(
		kAppliedProxyPref,
		key.toUtf8());
	Core::App().saveSettingsDelayed();
}

[[nodiscard]] bool UserProxyEnabled() {
	const auto &proxy = Core::App().settings().proxy();
	return proxy.isEnabled() && !LumenProxyActive();
}

void Apply() {
	auto &state = Current();
	if (state.proxies.empty() || UserProxyEnabled()) {
		return;
	}
	const auto proxy = state.proxies.front();
	auto &settings = Core::App().settings().proxy();
	if (LumenProxyActive() && settings.selected() == proxy) {
		return;
	}
	if (settings.indexInList(proxy) < 0) {
		settings.addToList(proxy);
	}
	SetAppliedKey(ProxyKey(proxy));
	Core::App().setCurrentProxy(proxy, MTP::ProxyData::Settings::Enabled);
	Core::App().saveSettingsDelayed();
}

void Release() {
	if (!LumenProxyActive()) {
		return;
	}
	const auto selected = Core::App().settings().proxy().selected();
	SetAppliedKey(QString());
	Core::App().setCurrentProxy(selected, MTP::ProxyData::Settings::System);
	Core::App().saveSettingsDelayed();
}

void ListUpdated() {
	const auto mode = GetConnectionMode();
	if (mode == ConnectionMode::Always) {
		Apply();
	} else if (mode == ConnectionMode::Auto && LumenProxyActive()) {
		// Keep using the server, but follow a changed address or secret.
		Apply();
	}
}

void Refresh(int index) {
	const auto url = ProxyListUrl(index);
	if (url.isEmpty()) {
		return;
	}
	auto &state = Current();
	if (!state.manager) {
		state.manager = std::make_unique<QNetworkAccessManager>();
	}
	auto request = QNetworkRequest(QUrl(url));
	request.setAttribute(
		QNetworkRequest::RedirectPolicyAttribute,
		QNetworkRequest::NoLessSafeRedirectPolicy);
	request.setTransferTimeout(30 * 1000);
	const auto reply = state.manager->get(request);
	QObject::connect(reply, &QNetworkReply::finished, [=] {
		reply->deleteLater();
		if (reply->error() != QNetworkReply::NoError) {
			LOG(("Lumen Proxy: list request failed, %1."
				).arg(reply->errorString()));
			Refresh(index + 1);
			return;
		}
		const auto bytes = reply->read(kMaxListSize);
		auto proxies = ParseList(bytes);
		if (proxies.empty()) {
			LOG(("Lumen Proxy: list is empty or invalid, %1.").arg(url));
			Refresh(index + 1);
			return;
		}
		Current().proxies = std::move(proxies);
		Core::App().settings().writePref<QByteArray>(kProxyListPref, bytes);
		Core::App().saveSettingsDelayed();
		ListUpdated();
	});
}

void UnreachableTimeout() {
	if (Current().lastState != MTP::ConnectedState
		&& GetConnectionMode() == ConnectionMode::Auto
		&& !Core::App().settings().proxy().isEnabled()) {
		LOG(("Lumen Proxy: Telegram is unreachable, using LumenGram server."));
		Apply();
	}
}

void Start() {
	auto &state = Current();
	if (state.started) {
		return;
	}
	state.started = true;
	state.proxies = ParseList(Core::App().settings().readPref<QByteArray>(
		kProxyListPref));
	state.unreachableTimer.setCallback(UnreachableTimeout);
	state.refreshTimer.setCallback([] { Refresh(0); });
	state.refreshTimer.callEach(kRefreshInterval);
	Refresh(0);
	if (GetConnectionMode() == ConnectionMode::Always) {
		Apply();
	} else if (GetConnectionMode() == ConnectionMode::Off) {
		Release();
	}
}

} // namespace

ConnectionMode GetConnectionMode() {
	const auto value = Core::App().settings().readPref<QByteArray>(
		kConnectionModePref);
	return (value == "always")
		? ConnectionMode::Always
		: (value == "off")
		? ConnectionMode::Off
		: ConnectionMode::Auto;
}

rpl::producer<ConnectionMode> ConnectionModeValue() {
	return rpl::single(
		GetConnectionMode()
	) | rpl::then(Current().modeChanges.events());
}

void SetConnectionMode(ConnectionMode mode) {
	if (GetConnectionMode() == mode) {
		return;
	}
	Core::App().settings().writePref<QByteArray>(
		kConnectionModePref,
		(mode == ConnectionMode::Always)
			? QByteArray("always")
			: (mode == ConnectionMode::Off)
			? QByteArray("off")
			: QByteArray());
	Core::App().saveSettingsDelayed();
	Start();
	if (mode == ConnectionMode::Always) {
		Apply();
	} else if (mode == ConnectionMode::Off) {
		Release();
	}
	Current().modeChanges.fire_copy(mode);
}

bool LumenProxyActive() {
	const auto &settings = Core::App().settings().proxy();
	const auto key = AppliedKey();
	return settings.isEnabled()
		&& !key.isEmpty()
		&& (ProxyKey(settings.selected()) == key);
}

bool LumenProxyAvailable() {
	return !Current().proxies.empty();
}

void HandleConnectionState(int32 state) {
	Start();
	auto &current = Current();
	current.lastState = state;
	if (state == MTP::ConnectedState) {
		current.unreachableTimer.cancel();
	} else if (!current.unreachableTimer.isActive()
		&& GetConnectionMode() == ConnectionMode::Auto
		&& !Core::App().settings().proxy().isEnabled()) {
		current.unreachableTimer.callOnce(kUnreachableTimeout);
	}
}

} // namespace Lumen
