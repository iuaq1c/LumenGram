/*
 * This file is part of LumenGram, a modification of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */
package org.telegram.lumen;

import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.utils.proxy.ProxySettings;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

// Connection through the LumenGram MTProxy for networks where Telegram is blocked.
// Never touches a proxy the user set up: it only switches its own proxy on and off.
public final class LumenConnection {

    private static final String[] LIST_URLS = {
        "https://tunexa.ru/api/lumengram/proxies.json",
        "https://tg.tunexa.ru/lumengram/proxies.json",
    };
    private static final long UNREACHABLE_TIMEOUT = 8000;
    private static final long REFRESH_INTERVAL = 6 * 60 * 60 * 1000L;
    private static final String PREF_LIST = "proxyList";
    private static final String PREF_LIST_TIME = "proxyListTime";
    private static final String PREF_APPLIED = "proxyApplied";

    private static final ArrayList<ProxySettings> proxies = new ArrayList<>();
    private static boolean started;
    private static final Runnable unreachableCheck = LumenConnection::onUnreachable;

    private LumenConnection() {
    }

    public static void start() {
        if (started) {
            return;
        }
        started = true;
        parseList(LumenConfig.getString(PREF_LIST, ""));
        NotificationCenter.getGlobalInstance().addObserver((id, account, args) -> onStateChanged(),
            NotificationCenter.proxySettingsChanged);
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter.getInstance(a).addObserver((id, acc, args) -> onStateChanged(),
                NotificationCenter.didUpdateConnectionState);
        }
        applyMode();
        if (System.currentTimeMillis() - LumenConfig.getLong(PREF_LIST_TIME, 0) > REFRESH_INTERVAL || proxies.isEmpty()) {
            refreshList();
        }
    }

    public static boolean hasServer() {
        return !proxies.isEmpty();
    }

    public static String serverHost() {
        return proxies.isEmpty() ? "" : proxies.get(0).getAddress();
    }

    // True while Telegram traffic goes through the LumenGram proxy.
    public static boolean isActive() {
        final ProxySettings ours = ourProxy();
        return ours != null
            && SharedConfig.isProxyEnabled()
            && SharedConfig.currentProxy != null
            && ours.equals(SharedConfig.currentProxy.settings);
    }

    public static int connectionState() {
        return ConnectionsManager.getInstance(UserConfig.selectedAccount).getConnectionState();
    }

    public static void setMode(String mode) {
        LumenConfig.setString(LumenConfig.RELAY_MODE, mode);
        applyMode();
    }

    private static ProxySettings ourProxy() {
        return proxies.isEmpty() ? null : proxies.get(0);
    }

    private static boolean userProxyEnabled() {
        return SharedConfig.isProxyEnabled() && !isActive();
    }

    private static void applyMode() {
        final String mode = LumenConfig.relayMode();
        if (LumenConfig.RELAY_OFF.equals(mode)) {
            release();
        } else if (LumenConfig.RELAY_ALWAYS.equals(mode)) {
            apply();
        } else {
            onStateChanged();
        }
    }

    private static void onStateChanged() {
        AndroidUtilities.cancelRunOnUIThread(unreachableCheck);
        if (!LumenConfig.RELAY_AUTO.equals(LumenConfig.relayMode()) || isActive() || userProxyEnabled()) {
            return;
        }
        final int state = connectionState();
        if (state == ConnectionsManager.ConnectionStateConnecting) {
            AndroidUtilities.runOnUIThread(unreachableCheck, UNREACHABLE_TIMEOUT);
        }
    }

    private static void onUnreachable() {
        if (LumenConfig.RELAY_AUTO.equals(LumenConfig.relayMode())
            && connectionState() == ConnectionsManager.ConnectionStateConnecting
            && !userProxyEnabled()) {
            apply();
        }
    }

    private static void apply() {
        final ProxySettings settings = ourProxy();
        if (settings == null || isActive() || userProxyEnabled()) {
            return;
        }
        final SharedConfig.ProxyInfo info = SharedConfig.addProxy(new SharedConfig.ProxyInfo(settings));
        SharedConfig.currentProxy = info;
        final SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
        settings.toSharedPreferences(editor);
        editor.putBoolean("proxy_enabled", true);
        editor.commit();
        LumenConfig.setBool(PREF_APPLIED, true);
        ConnectionsManager.setProxySettings(true, settings);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
    }

    private static void release() {
        if (!LumenConfig.getBool(PREF_APPLIED)) {
            return;
        }
        LumenConfig.setBool(PREF_APPLIED, false);
        if (!isActive()) {
            return;
        }
        final SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
        editor.putBoolean("proxy_enabled", false);
        editor.commit();
        ConnectionsManager.setProxySettings(false, null);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
    }

    private static void refreshList() {
        Utilities.globalQueue.postRunnable(() -> {
            for (String url : LIST_URLS) {
                try {
                    final String text = new String(LumenServer.download(url), StandardCharsets.UTF_8);
                    if (parseListSafe(text)) {
                        AndroidUtilities.runOnUIThread(() -> {
                            parseList(text);
                            LumenConfig.setString(PREF_LIST, text);
                            LumenConfig.setLong(PREF_LIST_TIME, System.currentTimeMillis());
                            applyMode();
                        });
                        return;
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        });
    }

    private static boolean parseListSafe(String text) {
        return !readList(text).isEmpty();
    }

    private static void parseList(String text) {
        final ArrayList<ProxySettings> list = readList(text);
        if (!list.isEmpty()) {
            proxies.clear();
            proxies.addAll(list);
        }
    }

    private static ArrayList<ProxySettings> readList(String text) {
        final ArrayList<ProxySettings> result = new ArrayList<>();
        if (TextUtils.isEmpty(text)) {
            return result;
        }
        try {
            final JSONArray list = new JSONObject(text).optJSONArray("proxies");
            for (int i = 0; list != null && i < list.length(); i++) {
                final JSONObject entry = list.getJSONObject(i);
                final String server = entry.optString("server", "").trim();
                final int port = entry.optInt("port", 0);
                final String secret = entry.optString("secret", "").trim();
                if (server.isEmpty() || port <= 0 || port > 65535 || !secret.matches("[0-9a-fA-F]{32,}")) {
                    continue;
                }
                final ProxySettings settings = ProxySettings.builder()
                    .setType(ProxySettings.Type.MTPROTO)
                    .setAddress(server)
                    .setPort(port)
                    .setSecret(secret)
                    .build();
                if (settings.isValid()) {
                    result.add(settings);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return result;
    }
}
