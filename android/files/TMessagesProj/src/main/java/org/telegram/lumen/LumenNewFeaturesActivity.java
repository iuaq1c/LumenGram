package org.telegram.lumen;

import android.os.Bundle;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

public class LumenNewFeaturesActivity extends UniversalFragment {
    private static final int LISTEN = 1, PORTAL = 2, OPEN_LISTEN = 3, OPEN_PORTAL = 4,
        THREAT = 5, PHRASES = 6, MATCH = 7, MODE = 8, CONTACTS = 9, EXCEPTIONS = 10,
        DOWNLOAD = 11, UPLOAD = 12, WIFI = 13, CLEAR = 14, LOG_START = 1000;
    private static final String[] TRANSFER_MODES = {"off", "auto", "fast", "eco"};
    private static final String[] TRANSFER_KEYS = {"BoostOff", "BoostAuto", "BoostFast", "BoostEco"};

    @Override protected CharSequence getTitle() { return t("FeaturesTitle"); }
    private static String t(String key) { return LumenLang.get(key); }
    private void update() { if (listView != null) listView.adapter.update(true); }

    @Override protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final boolean listening = LumenConfig.prefs().getBoolean("isListeningEnabled", true);
        final boolean portal = LumenConfig.getBool("isPortalEnabled");
        items.add(UItem.asHeader(t("FeaturesTitle")));
        items.add(UItem.asCheck(LISTEN, t("ListenTitle")).setChecked(listening));
        items.add(UItem.asCheck(PORTAL, t("PortalTitle")).setChecked(portal));
        if (listening) items.add(UItem.asButton(OPEN_LISTEN, R.drawable.msg_link, t("ListenWebTitle")));
        if (portal) items.add(UItem.asButton(OPEN_PORTAL, R.drawable.msg_link, t("PortalTitle") + " · Web"));
        items.add(UItem.asShadow(t("FeaturesAbout")));

        items.add(UItem.asHeader(t("ThreatTitle")));
        items.add(UItem.asCheck(THREAT, t("ThreatEnable")).setChecked(LumenConfig.getBool(LumenThreats.ENABLED)));
        if (LumenConfig.getBool(LumenThreats.ENABLED)) {
            items.add(UItem.asButton(PHRASES, t("ThreatPhrases"), LumenConfig.getString(LumenThreats.PHRASES, "")));
            items.add(UItem.asButton(MATCH, t("ThreatMatch"), t("substring".equals(LumenConfig.getString(LumenThreats.MATCH, "word")) ? "ThreatSubstring" : "ThreatWhole")));
            items.add(UItem.asButton(MODE, t("ThreatMode"), t("block".equals(LumenConfig.getString(LumenThreats.MODE, "hide")) ? "ThreatBlock" : "ThreatHide")));
            items.add(UItem.asCheck(CONTACTS, t("ThreatTrustContacts")).setChecked(LumenConfig.prefs().getBoolean(LumenThreats.TRUST_CONTACTS, true)));
            items.add(UItem.asButton(EXCEPTIONS, t("ThreatExceptionId"), LumenConfig.getString(LumenThreats.EXCEPTIONS, "")));
        }
        items.add(UItem.asShadow(t("ThreatAbout")));
        items.add(UItem.asHeader(t("ThreatLog")));
        final JSONArray log = LumenThreats.log(currentAccount);
        for (int i = 0; i < log.length(); i++) {
            final JSONObject entry = log.optJSONObject(i);
            if (entry == null) continue;
            final TLRPC.User user = getMessagesController().getUser(entry.optLong("peer"));
            final String name = user == null ? String.valueOf(entry.optLong("peer")) : UserObject.getUserName(user);
            final String status = entry.optString("status");
            items.add(UItem.asButton(LOG_START + i, name, entry.optString("phrase") + " · "
                + t("blocked".equals(status) ? "ThreatBlocked" : "failed".equals(status) ? "ThreatFailed"
                : "unblocked".equals(status) ? "ThreatUnblocked" : "ThreatHidden")));
        }
        if (log.length() == 0) items.add(UItem.asShadow(t("ThreatLogEmpty")));
        else items.add(UItem.asButton(CLEAR, R.drawable.msg_delete, t("ThreatClearLog")));

        items.add(UItem.asHeader(t("BoostTitle")));
        items.add(UItem.asButton(DOWNLOAD, t("BoostDownload"), modeLabel("mediaDownloadMode")));
        items.add(UItem.asButton(UPLOAD, t("BoostUpload"), modeLabel("mediaUploadMode")));
        items.add(UItem.asCheck(WIFI, t("BoostWifiOnly")).setChecked(LumenConfig.getBool("isMediaBoostWifiOnly")));
        items.add(UItem.asShadow(t("BoostAbout")));
    }

    private String modeLabel(String key) {
        final String value = LumenConfig.getString(key, "off");
        for (int i = 0; i < TRANSFER_MODES.length; i++) if (TRANSFER_MODES[i].equals(value)) return t(TRANSFER_KEYS[i]);
        return t("BoostOff");
    }

    @Override protected void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case LISTEN: LumenConfig.setBool("isListeningEnabled", !LumenConfig.prefs().getBoolean("isListeningEnabled", true)); break;
            case PORTAL: toggle("isPortalEnabled"); break;
            case OPEN_LISTEN: Browser.openUrl(getParentActivity(), "https://tunexa.ru/lumengram/listen"); return;
            case OPEN_PORTAL: Browser.openUrl(getParentActivity(), "https://web.tunexa.ru/?listen=new&mode=portal"); return;
            case THREAT: toggle(LumenThreats.ENABLED); LumenThreats.refresh(); break;
            case CONTACTS: LumenConfig.setBool(LumenThreats.TRUST_CONTACTS, !LumenConfig.prefs().getBoolean(LumenThreats.TRUST_CONTACTS, true)); break;
            case WIFI: toggle("isMediaBoostWifiOnly"); break;
            case PHRASES: edit(LumenThreats.PHRASES, "ThreatPhrases"); return;
            case EXCEPTIONS: edit(LumenThreats.EXCEPTIONS, "ThreatExceptionId"); return;
            case MATCH: choose(LumenThreats.MATCH, new String[]{"word", "substring"}, new String[]{"ThreatWhole", "ThreatSubstring"}); return;
            case MODE: choose(LumenThreats.MODE, new String[]{"hide", "block"}, new String[]{"ThreatHide", "ThreatBlock"}); return;
            case DOWNLOAD: choose("mediaDownloadMode", TRANSFER_MODES, TRANSFER_KEYS); return;
            case UPLOAD: choose("mediaUploadMode", TRANSFER_MODES, TRANSFER_KEYS); return;
            case CLEAR:
                LumenDialogs.confirm(this, t("ThreatLog"), t("ThreatClearLog") + "?", t("ThreatClearLog"), true,
                    () -> { LumenThreats.clear(currentAccount); update(); });
                return;
            default:
                final JSONObject entry = LumenThreats.log(currentAccount).optJSONObject(item.id - LOG_START);
                if (entry != null) showEvent(entry);
                return;
        }
        update();
    }

    private void toggle(String key) { LumenConfig.setBool(key, !LumenConfig.getBool(key)); }
    @Override protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
    private void edit(String key, String title) {
        LumenDialogs.input(this, t(title), null, t(title), LumenConfig.getString(key, ""), true, value -> {
            LumenConfig.setString(key, value.length() > 40000 ? value.substring(0, 40000) : value); update();
        });
    }
    private void choose(String key, String[] values, String[] labels) {
        final CharSequence[] titles = new CharSequence[labels.length];
        for (int i = 0; i < labels.length; i++) titles[i] = t(labels[i]);
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider()).setItems(titles, (dialog, which) -> {
            LumenConfig.setString(key, values[which]); update();
        }).create());
    }
    private void showEvent(JSONObject entry) {
        final boolean blocked = "blocked".equals(entry.optString("status"));
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider()).setTitle(t("ThreatLog"))
            .setMessage(entry.optString("phrase"))
            .setPositiveButton(t(blocked ? "ThreatUnblock" : "ThreatBlock"), (dialog, which) ->
                LumenThreats.changeBlock(currentAccount, entry, !blocked, this::update))
            .setNeutralButton(t("ThreatReveal"), (dialog, which) -> {
                LumenThreats.reveal(currentAccount, entry);
                final Bundle args = new Bundle(); args.putLong("user_id", entry.optLong("peer"));
                args.putInt("message_id", entry.optInt("mid"));
                presentFragment(new ChatActivity(args));
            }).setNegativeButton(org.telegram.messenger.LocaleController.getString(R.string.Cancel), null).create());
    }
}
