/*
 * This file is part of LumenGram, a modification of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */
package org.telegram.lumen;

import android.view.View;

import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

public class LumenSettingsActivity extends UniversalFragment implements NotificationCenter.NotificationCenterDelegate {

    public static final int SETTINGS_ITEM_ID = 900;

    private static final int RELAY_AUTO = 1;
    private static final int RELAY_ALWAYS = 2;
    private static final int RELAY_OFF = 3;
    private static final int GHOST = 10;
    private static final int GHOST_ONLINE = 11;
    private static final int GHOST_READ = 12;
    private static final int GHOST_STORIES = 13;
    private static final int GHOST_TYPING = 14;
    private static final int GHOST_INTERACT = 15;
    private static final int SHOW_SECONDS = 20;
    private static final int HIDE_SHARE = 21;
    private static final int SERVICE_TIME = 22;
    private static final int PLAIN_REPLIES = 23;
    private static final int SAVE_DELETED = 24;
    private static final int SAVE_EDITS = 25;
    private static final int AUTO_REPLY = 26;
    private static final int AUTO_REPLY_FOCUS = 27;
    private static final int AUTO_REPLY_TEXT = 28;
    private static final int LOCAL_TRANSCRIBE = 29;
    private static final int HIDE_STORIES = 30;
    private static final int DND_STOP = 40;
    private static final int DND_PRESET = 41;
    private static final int DND_CUSTOM = 46; // After the 5 presets
    private static final int DND_REPLY = 47;
    private static final int FULLSCREEN_EFFECTS = 48;
    private static final int SQUARE_AVATARS = 31;
    private static final int FOLDER_COUNTERS = 32;
    private static final int SIMILAR_CHANNELS = 33;
    private static final int LIQUID_GLASS = 34;
    private static final int FULL_BANNER = 35;
    private static final int STREAMER = 50;
    private static final int PEER_ID = 51;
    private static final int PROFILE = 60;
    private static final int TOOLS = 61;
    private static final int FEED = 62;
    private static final int WEBSITE = 70;
    private static final int DONATE = 71;
    private static final int NEWS = 72;
    private static final int NEW_FEATURES = 73;

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxySettingsChanged);
        getNotificationCenter().addObserver(this, NotificationCenter.didUpdateConnectionState);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.proxySettingsChanged);
        getNotificationCenter().removeObserver(this, NotificationCenter.didUpdateConnectionState);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        update();
    }

    @Override
    protected CharSequence getTitle() {
        return "LumenGram";
    }

    private static String t(String key) {
        return LumenLang.get(key);
    }

    private UItem check(int id, String key, String pref) {
        return UItem.asCheck(id, t(key)).setChecked(LumenConfig.getBool(pref));
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asShadow(t("SettingsAbout")));

        items.add(UItem.asHeader(t("ConnectionTitle")));
        final String mode = LumenConfig.relayMode();
        items.add(UItem.asRadio(RELAY_AUTO, t("RelayAuto")).setChecked(LumenConfig.RELAY_AUTO.equals(mode)));
        items.add(UItem.asRadio(RELAY_ALWAYS, t("RelayAlways")).setChecked(LumenConfig.RELAY_ALWAYS.equals(mode)));
        items.add(UItem.asRadio(RELAY_OFF, t("RelayOff")).setChecked(LumenConfig.RELAY_OFF.equals(mode)));
        final String modeAbout = LumenConfig.RELAY_ALWAYS.equals(mode) ? "RelayAlwaysAbout"
            : LumenConfig.RELAY_OFF.equals(mode) ? "RelayOffAbout" : "RelayAutoAbout";
        items.add(UItem.asShadow(t(modeAbout) + ".\n\n" + routeStatus() + "\n\n" + (LumenConnection.hasServer()
            ? LumenLang.format("RelayAbout", "host", LumenConnection.serverHost())
            : t("RelayNoServer"))));

        items.add(UItem.asHeader(t("GhostTitle")));
        items.add(check(GHOST, "GhostMode", LumenConfig.GHOST_ENABLED));
        if (LumenConfig.getBool(LumenConfig.GHOST_ENABLED)) {
            items.add(check(GHOST_ONLINE, "GhostHideOnline", LumenConfig.GHOST_HIDE_ONLINE));
            items.add(check(GHOST_READ, "GhostNoRead", LumenConfig.GHOST_NO_READ));
            items.add(check(GHOST_STORIES, "GhostNoStories", LumenConfig.GHOST_NO_STORIES));
            items.add(check(GHOST_TYPING, "GhostNoTyping", LumenConfig.GHOST_NO_TYPING));
            items.add(check(GHOST_INTERACT, "GhostReadOnInteract", LumenConfig.GHOST_READ_ON_INTERACT));
        }
        items.add(UItem.asShadow(t("GhostAbout")));

        items.add(UItem.asHeader(t("MessagesTitle")));
        items.add(check(SHOW_SECONDS, "ShowSeconds", LumenConfig.SHOW_SECONDS));
        items.add(check(HIDE_SHARE, "HideShareButton", LumenConfig.HIDE_SHARE_BUTTON));
        items.add(check(SERVICE_TIME, "ServiceTime", LumenConfig.SERVICE_TIME));
        items.add(check(PLAIN_REPLIES, "PlainReplies", LumenConfig.PLAIN_REPLIES));
        items.add(UItem.asCheck(SAVE_DELETED, t("SaveDeleted")).setChecked(LumenMessageHistory.saveDeleted()));
        items.add(UItem.asCheck(SAVE_EDITS, t("SaveEdits")).setChecked(LumenMessageHistory.saveEdits()));
        items.add(UItem.asShadow(t("SaveHistoryAbout")));

        items.add(UItem.asCheck(FULLSCREEN_EFFECTS, t("FullscreenEffects")).setChecked(LumenFullscreenEffect.enabled()));
        items.add(UItem.asShadow(t("FullscreenEffectsAbout")));

        items.add(UItem.asCheck(LOCAL_TRANSCRIBE, t("LocalTranscribe")).setChecked(LumenTranscribe.enabled()));
        items.add(UItem.asShadow(t("LocalTranscribeAbout")));

        items.add(UItem.asHeader(t("DndTitle")));
        if (LumenDnd.active()) {
            items.add(UItem.asButton(DND_STOP, LumenDnd.statusLine(), t("DndStop")));
        } else {
            for (int i = 0; i < LumenDnd.PRESETS.length; i++) {
                items.add(UItem.asButton(DND_PRESET + i, dndPreset(i)));
            }
            items.add(UItem.asButton(DND_CUSTOM, t("DndCustom")));
        }
        items.add(UItem.asCheck(DND_REPLY, t("DndReplyToggle")).setChecked(LumenAutoReply.dndReply()));
        items.add(UItem.asShadow(t("DndAbout")));

        items.add(UItem.asHeader(t("AutoReplyTitle")));
        items.add(check(AUTO_REPLY, "AutoReply", LumenAutoReply.ENABLED));
        if (LumenAutoReply.enabled()) {
            items.add(check(AUTO_REPLY_FOCUS, "AutoReplyFocusOnly", LumenAutoReply.FOCUS_ONLY));
            items.add(UItem.asButton(AUTO_REPLY_TEXT, t("AutoReplyText"), LumenAutoReply.text()));
        }
        items.add(UItem.asShadow(t("AutoReplyAbout")));

        items.add(UItem.asHeader(t("ChatsTitle")));
        items.add(check(HIDE_STORIES, "HideStories", LumenConfig.HIDE_STORIES));
        items.add(check(SQUARE_AVATARS, "SquareAvatars", LumenConfig.SQUARE_AVATARS));
        items.add(check(FOLDER_COUNTERS, "HideFolderCounters", LumenConfig.HIDE_FOLDER_COUNTERS));
        items.add(check(SIMILAR_CHANNELS, "HideSimilarChannels", LumenConfig.HIDE_SIMILAR_CHANNELS));
        items.add(UItem.asCheck(FULL_BANNER, t("FullBanner")).setChecked(LumenProfiles.fullBanner()));
        // Telegram's own liquid glass (a refraction shader), it needs Android 13 and a fast enough phone
        if (isLiquidGlassSupported()) {
            // The saved choice, not the current state: power saving turns it off below a battery level
            items.add(UItem.asCheck(LIQUID_GLASS, t("LiquidGlass")).setChecked(LiteMode.isEnabledSetting(LiteMode.FLAG_LIQUID_GLASS)));
            String about = t("LiquidGlassAbout") + " " + t("LiquidGlassRestart");
            if (LiteMode.isPowerSaverApplied()) {
                about += "\n\n" + t("LiquidGlassPowerSaver").replace("{level}", String.valueOf(LiteMode.getPowerSaverLevel()));
            }
            items.add(UItem.asShadow(about));
        } else {
            items.add(UItem.asShadow(null));
        }

        items.add(UItem.asHeader(t("PrivacyTitle")));
        items.add(check(STREAMER, "StreamerMode", LumenConfig.STREAMER_MODE));
        items.add(check(PEER_ID, "ShowPeerId", LumenConfig.SHOW_PEER_ID));
        items.add(UItem.asShadow(t("StreamerAbout")));

        items.add(UItem.asButton(PROFILE, R.drawable.msg_openprofile, t("ProfileTitle")));
        items.add(UItem.asButton(TOOLS, R.drawable.msg_edit, t("ToolsTitle")));
        items.add(UItem.asButton(NEW_FEATURES, R.drawable.msg_settings, t("FeaturesTitle")));
        items.add(UItem.asButton(FEED, R.drawable.msg_channel, t("FeedTitle")));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(t("LinksTitle")));
        items.add(UItem.asButton(WEBSITE, R.drawable.msg_language, t("Website")));
        items.add(UItem.asButton(DONATE, R.drawable.msg_gift_premium, t("SupportProject")));
        items.add(UItem.asButton(NEWS, R.drawable.msg_channel, t("NewsSubscribe")));
        items.add(UItem.asShadow(null));
    }

    private String routeStatus() {
        final int state = LumenConnection.connectionState();
        final String route = t(LumenConnection.isActive() ? "RouteRelay" : "RouteDirect");
        final String status;
        if (state == ConnectionsManager.ConnectionStateConnected || state == ConnectionsManager.ConnectionStateUpdating) {
            status = t("RouteActive");
        } else if (state == ConnectionsManager.ConnectionStateWaitingForNetwork) {
            status = t("RouteOffline");
        } else {
            status = t("RouteConnecting");
        }
        return route + " · " + status;
    }

    private static boolean isLiquidGlassSupported() {
        return android.os.Build.VERSION.SDK_INT >= 33 && SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_AVERAGE;
    }

    private void toggle(String pref) {
        LumenConfig.setBool(pref, !LumenConfig.getBool(pref));
        LumenUi.settingsChanged();
        update();
    }

    private static String dndPreset(int index) {
        return LumenDnd.PRESETS[index][0] + " " + t(LumenDnd.PRESETS[index][1]);
    }

    // Sleep starts at 8:00 in the morning, the rest an hour from now
    private void pickDndTime(String status, boolean sleep) {
        if (getParentActivity() == null) {
            return;
        }
        final java.util.Calendar inHour = java.util.Calendar.getInstance();
        inHour.add(java.util.Calendar.HOUR_OF_DAY, 1);
        final int hour = sleep ? 8 : inHour.get(java.util.Calendar.HOUR_OF_DAY);
        final int minute = 0;
        final int dialogTheme = Theme.isCurrentThemeDark() ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert;
        final android.app.TimePickerDialog dialog = new android.app.TimePickerDialog(getParentActivity(), dialogTheme, (picker, h, m) -> {
            LumenDnd.start(status, LumenDnd.nextTime(h, m), this::onDndSaved);
            update();
        }, hour, minute, true);
        dialog.setTitle(t("DndUntilPick"));
        dialog.show();
    }

    private void onDndSaved(org.json.JSONObject result, String error) {
        if (error != null) {
            LumenDialogs.error(this, error);
        }
        update();
    }

    private void update() {
        if (listView != null) {
            listView.adapter.update(true);
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == NEW_FEATURES) {
            presentFragment(new LumenNewFeaturesActivity());
            return;
        }
        if (item.id >= DND_PRESET && item.id < DND_CUSTOM) {
            pickDndTime(dndPreset(item.id - DND_PRESET), item.id == DND_PRESET);
            return;
        }
        switch (item.id) {
            case DND_STOP:
                LumenDnd.stop(this::onDndSaved);
                update();
                break;
            case DND_CUSTOM:
                LumenDialogs.input(this, t("DndCustom"), null, t("DndCustomHint"), "", false, text -> {
                    if (!text.isEmpty()) {
                        pickDndTime(text, false);
                    }
                });
                break;
            case FULLSCREEN_EFFECTS:
                LumenConfig.prefs().edit().putBoolean(LumenFullscreenEffect.ENABLED, !LumenFullscreenEffect.enabled()).apply();
                update();
                break;
            case DND_REPLY:
                LumenConfig.prefs().edit().putBoolean(LumenAutoReply.DND_REPLY, !LumenAutoReply.dndReply()).apply();
                update();
                break;
            case RELAY_AUTO:
                LumenConnection.setMode(LumenConfig.RELAY_AUTO);
                update();
                break;
            case RELAY_ALWAYS:
                LumenConnection.setMode(LumenConfig.RELAY_ALWAYS);
                update();
                break;
            case RELAY_OFF:
                LumenConnection.setMode(LumenConfig.RELAY_OFF);
                update();
                break;
            case GHOST:
                if (!LumenConfig.getBool(LumenConfig.GHOST_ENABLED) && !LumenConfig.getBool(LumenConfig.GHOST_RISK_ACKNOWLEDGED)) {
                    showGhostWarning();
                } else {
                    toggle(LumenConfig.GHOST_ENABLED);
                    LumenGhost.onSettingsChanged();
                }
                break;
            case GHOST_ONLINE:
                toggle(LumenConfig.GHOST_HIDE_ONLINE);
                LumenGhost.onSettingsChanged();
                break;
            case GHOST_READ:
                toggle(LumenConfig.GHOST_NO_READ);
                break;
            case GHOST_STORIES:
                toggle(LumenConfig.GHOST_NO_STORIES);
                break;
            case GHOST_TYPING:
                toggle(LumenConfig.GHOST_NO_TYPING);
                break;
            case GHOST_INTERACT:
                toggle(LumenConfig.GHOST_READ_ON_INTERACT);
                break;
            case SHOW_SECONDS:
                toggle(LumenConfig.SHOW_SECONDS);
                break;
            case HIDE_SHARE:
                toggle(LumenConfig.HIDE_SHARE_BUTTON);
                break;
            case SERVICE_TIME:
                toggle(LumenConfig.SERVICE_TIME);
                break;
            case PLAIN_REPLIES:
                toggle(LumenConfig.PLAIN_REPLIES);
                break;
            case SAVE_DELETED:
                LumenConfig.setBool(LumenMessageHistory.SAVE_DELETED, !LumenMessageHistory.saveDeleted());
                update();
                break;
            case LOCAL_TRANSCRIBE:
                LumenConfig.setBool(LumenTranscribe.ENABLED, !LumenTranscribe.enabled());
                update();
                break;
            case AUTO_REPLY:
                toggle(LumenAutoReply.ENABLED);
                break;
            case AUTO_REPLY_FOCUS:
                toggle(LumenAutoReply.FOCUS_ONLY);
                break;
            case AUTO_REPLY_TEXT:
                LumenDialogs.input(this, t("AutoReplyText"), null, t("AutoReplyDefault"), LumenAutoReply.text(), true, text -> {
                    LumenAutoReply.setText(text);
                    update();
                });
                break;
            case SAVE_EDITS:
                LumenConfig.setBool(LumenMessageHistory.SAVE_EDITS, !LumenMessageHistory.saveEdits());
                update();
                break;
            case FULL_BANNER:
                LumenConfig.setBool(LumenProfiles.FULL_BANNER, !LumenProfiles.fullBanner());
                update();
                break;
            case SIMILAR_CHANNELS:
                toggle(LumenConfig.HIDE_SIMILAR_CHANNELS);
                break;
            case LIQUID_GLASS: {
                final boolean enable = !LiteMode.isEnabledSetting(LiteMode.FLAG_LIQUID_GLASS);
                LiteMode.toggleFlag(LiteMode.FLAG_LIQUID_GLASS, enable);
                if (enable) {
                    // The glass is drawn over the blurred content, so it needs the blur too
                    LiteMode.toggleFlag(LiteMode.FLAG_CHAT_BLUR, true);
                }
                update();
                break;
            }
            case HIDE_STORIES:
                toggle(LumenConfig.HIDE_STORIES);
                break;
            case SQUARE_AVATARS:
                toggle(LumenConfig.SQUARE_AVATARS);
                break;
            case FOLDER_COUNTERS:
                toggle(LumenConfig.HIDE_FOLDER_COUNTERS);
                break;
            case STREAMER:
                toggle(LumenConfig.STREAMER_MODE);
                break;
            case PEER_ID:
                toggle(LumenConfig.SHOW_PEER_ID);
                break;
            case PROFILE:
                presentFragment(new LumenProfileActivity());
                break;
            case TOOLS:
                presentFragment(new LumenToolsActivity());
                break;
            case FEED:
                presentFragment(new LumenFeedActivity());
                break;
            case WEBSITE:
                Browser.openUrl(getParentActivity(), "https://tunexa.ru/docs/lumengram");
                break;
            case DONATE:
                Browser.openUrl(getParentActivity(), "https://tunexa.ru/lumengram/donate");
                break;
            case NEWS:
                Browser.openUrl(getParentActivity(), "https://t.me/lumengram_bot?start=subscribe");
                break;
        }
    }

    private void showGhostWarning() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
        builder.setTitle(t("GhostWarningTitle"));
        builder.setMessage(t("GhostWarning"));
        builder.setPositiveButton(t("GhostWarningConfirm"), (dialog, which) -> {
            LumenConfig.setBool(LumenConfig.GHOST_RISK_ACKNOWLEDGED, true);
            toggle(LumenConfig.GHOST_ENABLED);
            LumenGhost.onSettingsChanged();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        final AlertDialog dialog = builder.create();
        showDialog(dialog);
        final android.widget.TextView button = (android.widget.TextView) dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
