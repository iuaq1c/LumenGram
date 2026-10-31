/*
 * This file is part of LumenGram, a modification of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */
package org.telegram.lumen;

import android.os.SystemClock;

import org.json.JSONObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LaunchActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;

// Focus mode, quiet hours and screen time.
public final class LumenFocus {

    public static final String FOCUS_UNTIL = "focusUntil";
    public static final String QUIET_ENABLED = "quietEnabled";
    public static final String QUIET_FROM = "quietFrom";
    public static final String QUIET_TO = "quietTo";
    public static final String QUIET_ALLOW_PINNED = "quietAllowPinned";
    public static final String SCREEN_TIME_LIMIT = "screenTimeLimitMinutes";
    private static final String PREF_SCREEN_TIME = "screenTime";
    private static final String PREF_LIMIT_SHOWN = "screenTimeLimitShown";
    private static final int KEEP_DAYS = 14;

    private static long openedDialogId;
    private static long openedAt;

    private LumenFocus() {
    }

    // Focus mode: until the timer ends only pinned chats and chats with a color label are shown.
    public static boolean focusActive() {
        return System.currentTimeMillis() < LumenConfig.getLong(FOCUS_UNTIL, 0);
    }

    public static long focusLeftMs() {
        return Math.max(0, LumenConfig.getLong(FOCUS_UNTIL, 0) - System.currentTimeMillis());
    }

    public static void startFocus(int minutes) {
        LumenConfig.setLong(FOCUS_UNTIL, System.currentTimeMillis() + minutes * 60_000L);
        LumenUi.settingsChanged();
    }

    public static void stopFocus() {
        LumenConfig.setLong(FOCUS_UNTIL, 0);
        LumenUi.settingsChanged();
    }

    private static boolean isPinned(int account, long dialogId) {
        final TLRPC.Dialog dialog = MessagesController.getInstance(account).dialogs_dict.get(dialogId);
        return dialog != null && dialog.pinned;
    }

    private static boolean allowedInFocus(int account, long dialogId) {
        return isPinned(account, dialogId) || LumenChatMeta.label(dialogId) >= 0;
    }

    public static ArrayList<TLRPC.Dialog> filter(int account, ArrayList<TLRPC.Dialog> dialogs) {
        if (!focusActive() || dialogs == null) {
            return dialogs;
        }
        final ArrayList<TLRPC.Dialog> result = new ArrayList<>();
        for (TLRPC.Dialog dialog : dialogs) {
            if (!(dialog instanceof TLRPC.TL_dialogFolder) && allowedInFocus(account, dialog.id)) {
                result.add(dialog);
            }
        }
        return result;
    }

    // Quiet hours, in minutes since midnight; the range may cross midnight.
    public static boolean quietNow() {
        if (!LumenConfig.getBool(QUIET_ENABLED)) {
            return false;
        }
        final int from = LumenConfig.getInt(QUIET_FROM, 23 * 60);
        final int to = LumenConfig.getInt(QUIET_TO, 8 * 60);
        final Calendar calendar = Calendar.getInstance();
        final int now = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
        if (from == to) {
            return false;
        }
        return from < to ? now >= from && now < to : now >= from || now < to;
    }

    // Called by NotificationsController for every new message.
    public static boolean silences(int account, long dialogId) {
        if (focusActive() && !allowedInFocus(account, dialogId)) {
            return true;
        }
        return quietNow() && !(LumenConfig.getBool(QUIET_ALLOW_PINNED) && isPinned(account, dialogId));
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    public static long openedDialogId() {
        return openedDialogId;
    }

    public static void chatOpened(long dialogId) {
        chatClosed();
        openedDialogId = dialogId;
        openedAt = SystemClock.elapsedRealtime();
    }

    public static void chatClosed() {
        if (openedDialogId == 0) {
            return;
        }
        final long seconds = (SystemClock.elapsedRealtime() - openedAt) / 1000;
        final long dialogId = openedDialogId;
        openedDialogId = 0;
        if (seconds <= 0 || seconds > 12 * 60 * 60) {
            return;
        }
        try {
            final JSONObject all = new JSONObject(LumenConfig.getString(PREF_SCREEN_TIME, "{}"));
            final String day = today();
            final JSONObject todayData = all.optJSONObject(day) != null ? all.optJSONObject(day) : new JSONObject();
            todayData.put(String.valueOf(dialogId), todayData.optLong(String.valueOf(dialogId)) + seconds);
            all.put(day, todayData);
            final ArrayList<String> days = new ArrayList<>();
            for (Iterator<String> it = all.keys(); it.hasNext(); ) {
                days.add(it.next());
            }
            java.util.Collections.sort(days);
            while (days.size() > KEEP_DAYS) {
                all.remove(days.remove(0));
            }
            LumenConfig.setString(PREF_SCREEN_TIME, all.toString());
        } catch (Exception e) {
            FileLog.e(e);
        }
        checkLimit();
    }

    // Seconds spent today in each chat.
    public static HashMap<Long, Long> screenTimeToday() {
        final HashMap<Long, Long> result = new HashMap<>();
        try {
            final JSONObject data = new JSONObject(LumenConfig.getString(PREF_SCREEN_TIME, "{}")).optJSONObject(today());
            if (data != null) {
                for (Iterator<String> it = data.keys(); it.hasNext(); ) {
                    final String key = it.next();
                    result.put(Long.parseLong(key), data.optLong(key));
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return result;
    }

    public static long totalToday() {
        long total = 0;
        for (long seconds : screenTimeToday().values()) {
            total += seconds;
        }
        return total;
    }

    private static void checkLimit() {
        final int limit = LumenConfig.getInt(SCREEN_TIME_LIMIT, 0);
        if (limit <= 0 || today().equals(LumenConfig.getString(PREF_LIMIT_SHOWN, ""))) {
            return;
        }
        final long total = totalToday();
        if (total < limit * 60L) {
            return;
        }
        LumenConfig.setString(PREF_LIMIT_SHOWN, today());
        final BaseFragment fragment = LaunchActivity.getLastFragment();
        LumenDialogs.toast(fragment, LumenLang.format("LimitReached", "time", formatDuration(total)));
    }

    public static String formatDuration(long seconds) {
        if (seconds < 60) {
            return LumenLang.format("DurationSeconds", "count", seconds);
        }
        final long minutes = seconds / 60;
        if (minutes < 60) {
            return LumenLang.format("DurationMinutes", "count", minutes);
        }
        return LumenLang.format("DurationHours", "hours", minutes / 60, "minutes", minutes % 60);
    }
}
