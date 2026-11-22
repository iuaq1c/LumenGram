package org.telegram.lumen;

import android.text.TextUtils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

// "Do not disturb" with a status like "Сплю до 8:00": notifications are muted, the status
// is shown in the LumenGram profile and the auto-reply answers with it until the set time.
public class LumenDnd {

    public static final String TEXT = "dndText";
    public static final String UNTIL = "dndUntil";

    // Presets: emoji and the lang key of the status text
    public static final String[][] PRESETS = {
        {"😴", "DndSleep"},
        {"📚", "DndStudy"},
        {"💼", "DndWork"},
        {"🚗", "DndDriving"},
        {"🎮", "DndGaming"},
    };

    public static boolean active() {
        return System.currentTimeMillis() < until() && !TextUtils.isEmpty(text());
    }

    public static String text() {
        return LumenConfig.prefs().getString(TEXT, null);
    }

    // Local time in ms when the status ends
    public static long until() {
        return LumenConfig.prefs().getLong(UNTIL, 0);
    }

    public static String formatTime(long ms) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ms));
    }

    // "Сплю до 08:00"
    public static String statusLine() {
        return LumenLang.format("DndUntil", "status", text(), "time", formatTime(until()));
    }

    // The next moment the clock shows hour:minute, today or tomorrow
    public static long nextTime(int hour, int minute) {
        final Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }
        return calendar.getTimeInMillis();
    }

    public static void start(String text, long untilMs, LumenServer.Callback<org.json.JSONObject> callback) {
        LumenConfig.prefs().edit().putString(TEXT, text).putLong(UNTIL, untilMs).apply();
        LumenUi.settingsChanged();
        publish(text, untilMs, callback);
    }

    public static void stop(LumenServer.Callback<org.json.JSONObject> callback) {
        LumenConfig.prefs().edit().remove(TEXT).remove(UNTIL).apply();
        LumenUi.settingsChanged();
        publish("", 0, callback);
    }

    // Shows the status to other LumenGram users, when the profile is logged in
    private static void publish(String text, long untilMs, LumenServer.Callback<org.json.JSONObject> callback) {
        if (!LumenProfiles.isLoggedIn()) {
            if (callback != null) {
                callback.run(null, null);
            }
            return;
        }
        final HashMap<String, String> fields = new HashMap<>();
        fields.put("text", text);
        fields.put("until", String.valueOf(untilMs / 1000));
        LumenServer.post("/api/lumengram/away", fields, (result, error) -> {
            LumenProfiles.refreshNow();
            if (callback != null) {
                callback.run(result, error);
            }
        });
    }
}
