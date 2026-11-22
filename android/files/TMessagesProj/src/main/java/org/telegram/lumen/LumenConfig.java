/*
 * This file is part of LumenGram, a modification of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */
package org.telegram.lumen;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

// LumenGram settings, kept on this device. Keys match the web client's LumenSettings fields.
public final class LumenConfig {

    public static final String GHOST_ENABLED = "isGhostEnabled";
    public static final String GHOST_RISK_ACKNOWLEDGED = "isGhostRiskAcknowledged";
    public static final String GHOST_HIDE_ONLINE = "ghost_hideOnline";
    public static final String GHOST_NO_READ = "ghost_noReadMessages";
    public static final String GHOST_NO_STORIES = "ghost_noReadStories";
    public static final String GHOST_NO_TYPING = "ghost_noTyping";
    public static final String GHOST_READ_ON_INTERACT = "ghost_readOnInteract";

    public static final String SHOW_SECONDS = "shouldShowSeconds";
    public static final String HIDE_STORIES = "shouldHideStories";
    public static final String SQUARE_AVATARS = "shouldUseSquareAvatars";
    public static final String HIDE_FOLDER_COUNTERS = "shouldHideFolderCounters";
    public static final String SHOW_PEER_ID = "shouldShowPeerId";
    public static final String STREAMER_MODE = "isStreamerMode";
    public static final String HIDE_SHARE_BUTTON = "shouldHideShareButton";
    public static final String UNDO_SEND = "isUndoSendEnabled";
    public static final String BLUR_MEDIA = "shouldBlurMedia";
    public static final String PLAIN_REPLIES = "shouldUsePlainReplies";
    public static final String SERVICE_TIME = "shouldShowServiceTime";
    public static final String HIDE_SIMILAR_CHANNELS = "shouldHideSimilarChannels";

    public static final String RELAY_MODE = "relayMode";
    public static final String RELAY_AUTO = "auto";
    public static final String RELAY_ALWAYS = "always";
    public static final String RELAY_OFF = "off";

    private LumenConfig() {
    }

    public static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("lumengram", Context.MODE_PRIVATE);
    }

    public static boolean getBool(String key) {
        return prefs().getBoolean(key, false);
    }

    public static void setBool(String key, boolean value) {
        prefs().edit().putBoolean(key, value).apply();
    }

    public static String getString(String key, String def) {
        return prefs().getString(key, def);
    }

    public static void setString(String key, String value) {
        if (value == null) {
            prefs().edit().remove(key).apply();
        } else {
            prefs().edit().putString(key, value).apply();
        }
    }

    public static int getInt(String key, int def) {
        return prefs().getInt(key, def);
    }

    public static void setInt(String key, int value) {
        prefs().edit().putInt(key, value).apply();
    }

    public static long getLong(String key, long def) {
        return prefs().getLong(key, def);
    }

    public static void setLong(String key, long value) {
        prefs().edit().putLong(key, value).apply();
    }

    // Ghost options only work while ghost mode itself is on.
    public static boolean ghost(String option) {
        return getBool(GHOST_ENABLED) && getBool(option);
    }

    public static String relayMode() {
        return getString(RELAY_MODE, RELAY_AUTO);
    }
}
