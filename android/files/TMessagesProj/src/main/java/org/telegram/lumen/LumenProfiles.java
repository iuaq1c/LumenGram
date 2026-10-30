/*
 * This file is part of LumenGram, a modification of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */
package org.telegram.lumen;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.Base64;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

// LumenGram profiles server: custom banners and "now playing", visible only in LumenGram.
// The whole list is downloaded at once, so the server never learns whose profile is opened.
public final class LumenProfiles {

    private static final String PROFILES_URL = LumenServer.SERVER_URL + "/api/lumengram/profiles.json";
    private static final long REFRESH_MS = 60 * 1000;
    private static final long LOGIN_POLL_MS = 1500;
    private static final int LOGIN_POLL_TIMES = 40;
    private static final int BANNER_WIDTH = 1200;
    private static final int BANNER_HEIGHT = 400;
    private static final int BANNER_QUALITY = 90;
    private static final Pattern ID_RE = Pattern.compile("^[1-9][0-9]{4,15}$");
    private static final Pattern BANNER_URL_RE = Pattern.compile("^https://tunexa\\.ru/lumengram/banner/[a-f0-9]{32}\\.jpg$");
    private static final Pattern TRACK_URL_RE = Pattern.compile("^https://(open\\.spotify\\.com|www\\.last\\.fm)/.*");
    private static final Pattern COVER_URL_RE = Pattern.compile("^https://(i\\.scdn\\.co/image/|lastfm\\.freetls\\.fastly\\.net/i/).*");

    public static class Track {
        public String title;
        public String artist;
        public String url;
        public String cover;
        public boolean spotify;
        // Spotify only: track length in seconds and the local time (ms) when it started playing.
        public long duration;
        public long startedAt;
    }

    // Profile style chosen on the LumenGram site or in the apps, the same fields as the web client's LumenStyle.
    public static class Style {
        public int[] accent;
        public String effect;
        public String status;
        public String badge;

        public boolean isEmpty() {
            return accent == null && effect == null && status == null && badge == null;
        }
    }

    public static final String[] EFFECTS = {"none", "stars", "snow", "sparks", "confetti", "aurora"};
    private static final Pattern COLOR_RE = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final int STATUS_MAX = 70;
    private static final int BADGE_MAX = 16;

    public interface Listener {
        void onProfilesUpdated();
    }

    private static HashMap<Long, String> banners = new HashMap<>();
    private static HashMap<Long, Track> music = new HashMap<>();
    private static HashMap<Long, Style> styles = new HashMap<>();
    private static final java.util.ArrayList<Listener> listeners = new java.util.ArrayList<>();
    private static boolean started;
    private static long lastRefresh;
    private static final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            refresh();
            AndroidUtilities.runOnUIThread(this, REFRESH_MS);
        }
    };

    private LumenProfiles() {
    }

    public static void start() {
        if (started) {
            return;
        }
        started = true;
        AndroidUtilities.runOnUIThread(refreshRunnable);
    }

    public static void addListener(Listener listener) {
        listeners.add(listener);
    }

    public static void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public static String banner(long userId) {
        return banners.get(userId);
    }

    public static Track track(long userId) {
        return music.get(userId);
    }

    public static Style style(long userId) {
        return styles.get(userId);
    }

    public static Style parseStyle(JSONObject entry) {
        final Style style = new Style();
        if (entry == null) {
            return style;
        }
        final org.json.JSONArray accent = entry.optJSONArray("accent");
        if (accent != null) {
            final java.util.ArrayList<Integer> colors = new java.util.ArrayList<>();
            for (int i = 0; i < accent.length() && colors.size() < 2; i++) {
                final String color = accent.optString(i, "");
                if (COLOR_RE.matcher(color).matches()) {
                    colors.add(0xFF000000 | Integer.parseInt(color.substring(1), 16));
                }
            }
            if (!colors.isEmpty()) {
                style.accent = new int[colors.size()];
                for (int i = 0; i < colors.size(); i++) {
                    style.accent[i] = colors.get(i);
                }
            }
        }
        final String effect = entry.optString("effect", "");
        for (int i = 1; i < EFFECTS.length; i++) {
            if (EFFECTS[i].equals(effect)) {
                style.effect = effect;
            }
        }
        final String status = entry.optString("status", "").trim();
        if (!status.isEmpty()) {
            style.status = status.length() > STATUS_MAX ? status.substring(0, STATUS_MAX) : status;
        }
        final String badge = entry.optString("badge", "").trim();
        if (!badge.isEmpty() && badge.length() <= BADGE_MAX) {
            style.badge = badge;
        }
        return style;
    }

    public static String colorText(int color) {
        return String.format(java.util.Locale.US, "#%06x", color & 0xFFFFFF);
    }

    public static void saveStyle(Style style, LumenServer.Callback<JSONObject> callback) {
        final HashMap<String, String> fields = new HashMap<>();
        final StringBuilder accent = new StringBuilder();
        if (style.accent != null) {
            for (int color : style.accent) {
                if (accent.length() > 0) {
                    accent.append(',');
                }
                accent.append(colorText(color));
            }
        }
        fields.put("accent", accent.toString());
        fields.put("effect", style.effect != null ? style.effect : "none");
        fields.put("status", style.status != null ? style.status : "");
        fields.put("badge", style.badge != null ? style.badge : "");
        LumenServer.post("/api/lumengram/style", fields, (result, error) -> {
            lastRefresh = 0;
            refresh();
            callback.run(result, error);
        });
    }

    private static String tokenKey() {
        return "profileToken_" + UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
    }

    public static String token() {
        return LumenConfig.getString(tokenKey(), null);
    }

    public static boolean isLoggedIn() {
        return token() != null;
    }

    private static void setToken(String token) {
        LumenConfig.setString(tokenKey(), token);
    }

    public static void refresh() {
        final long now = SystemClock.elapsedRealtime();
        if (now - lastRefresh < 5000) {
            return;
        }
        lastRefresh = now;
        Utilities.globalQueue.postRunnable(() -> {
            try {
                final JSONObject data = LumenServer.getJson(PROFILES_URL);
                final HashMap<Long, String> newBanners = new HashMap<>();
                final HashMap<Long, Track> newMusic = new HashMap<>();
                final HashMap<Long, Style> newStyles = new HashMap<>();
                parse(data, newBanners, newMusic);
                parseStyles(data, newStyles);
                AndroidUtilities.runOnUIThread(() -> {
                    banners = newBanners;
                    music = newMusic;
                    styles = newStyles;
                    for (Listener listener : new java.util.ArrayList<>(listeners)) {
                        listener.onProfilesUpdated();
                    }
                });
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private static void parse(JSONObject data, Map<Long, String> outBanners, Map<Long, Track> outMusic) {
        // Server clock minus device clock, so a wrong device clock doesn't break the progress.
        final long clockShift = data.has("now") ? data.optLong("now") * 1000 - System.currentTimeMillis() : 0;
        final JSONObject bannerList = data.optJSONObject("banners");
        if (bannerList != null) {
            for (Iterator<String> it = bannerList.keys(); it.hasNext(); ) {
                final String id = it.next();
                final String url = bannerList.optString(id, "");
                if (ID_RE.matcher(id).matches() && BANNER_URL_RE.matcher(url).matches()) {
                    outBanners.put(Long.parseLong(id), url);
                }
            }
        }
        final JSONObject musicList = data.optJSONObject("music");
        if (musicList != null) {
            for (Iterator<String> it = musicList.keys(); it.hasNext(); ) {
                final String id = it.next();
                final JSONObject entry = musicList.optJSONObject(id);
                if (!ID_RE.matcher(id).matches() || entry == null || entry.optString("title", "").isEmpty()) {
                    continue;
                }
                final Track track = new Track();
                track.title = limit(entry.optString("title", ""));
                track.artist = limit(entry.optString("artist", ""));
                final String url = entry.optString("url", "");
                track.url = TRACK_URL_RE.matcher(url).matches() ? url : null;
                final String cover = entry.optString("cover", "");
                track.cover = COVER_URL_RE.matcher(cover).matches() ? cover : null;
                track.spotify = "spotify".equals(entry.optString("provider"));
                final double duration = entry.optDouble("duration", 0);
                final double progress = entry.optDouble("progress", -1);
                final double at = entry.optDouble("at", -1);
                if (duration > 0 && progress >= 0 && at > 0) {
                    track.duration = (long) duration;
                    track.startedAt = (long) (at * 1000 - clockShift - progress * 1000);
                }
                outMusic.put(Long.parseLong(id), track);
            }
        }
    }

    private static void parseStyles(JSONObject data, Map<Long, Style> outStyles) {
        final JSONObject styleList = data.optJSONObject("styles");
        if (styleList == null) {
            return;
        }
        for (Iterator<String> it = styleList.keys(); it.hasNext(); ) {
            final String id = it.next();
            if (!ID_RE.matcher(id).matches()) {
                continue;
            }
            final Style style = parseStyle(styleList.optJSONObject(id));
            if (!style.isEmpty()) {
                outStyles.put(Long.parseLong(id), style);
            }
        }
    }

    private static String limit(String text) {
        return text.length() > 200 ? text.substring(0, 200) : text;
    }

    // Login through the LumenGram bot: the app starts the bot with a one-time code,
    // Telegram delivers it to tunexa.ru with the verified sender, and the app picks up the session.
    public static void login(int account, LumenServer.Callback<Boolean> callback) {
        LumenServer.post("/api/lumengram/login/start", null, (login, error) -> {
            if (login == null) {
                callback.run(false, error);
                return;
            }
            final String bot = login.optString("bot", "");
            final String code = login.optString("code", "");
            final String check = login.optString("check", "");
            final MessagesController controller = MessagesController.getInstance(account);
            controller.getUserNameResolver().resolve(bot, peerId -> {
                final TLRPC.User user = peerId != null ? controller.getUser(peerId) : null;
                if (user == null || !user.bot) {
                    callback.run(false, "BOT_NOT_FOUND");
                    return;
                }
                controller.sendBotStart(user, "login_" + code);
                pollLogin(code, check, 0, callback);
            });
        });
    }

    private static void pollLogin(String code, String check, int attempt, LumenServer.Callback<Boolean> callback) {
        if (attempt >= LOGIN_POLL_TIMES) {
            callback.run(false, LumenLang.get("ProfileLoginTimeout"));
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            final HashMap<String, String> fields = new HashMap<>();
            fields.put("code", code);
            fields.put("check", check);
            LumenServer.post("/api/lumengram/login/check", fields, (result, error) -> {
                final String token = result != null ? result.optString("token", "") : "";
                if (!token.isEmpty()) {
                    setToken(token);
                    callback.run(true, null);
                } else {
                    pollLogin(code, check, attempt + 1, callback);
                }
            });
        }, LOGIN_POLL_MS);
    }

    public static void logout(Runnable done) {
        LumenServer.post("/api/lumengram/logout", null, (result, error) -> {
            setToken(null);
            done.run();
        });
    }

    public static void me(LumenServer.Callback<JSONObject> callback) {
        if (!isLoggedIn()) {
            callback.run(null, null);
            return;
        }
        LumenServer.post("/api/lumengram/me", null, (result, error) -> {
            if (error != null && error.contains("заново")) {
                setToken(null);
            }
            callback.run(result, error);
        });
    }

    // Crops to 3:1 and shrinks the picture before sending, the server re-encodes it again.
    public static void uploadBanner(Bitmap source, LumenServer.Callback<JSONObject> callback) {
        Utilities.globalQueue.postRunnable(() -> {
            final String data;
            try {
                data = prepareBanner(source);
            } catch (Exception e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> callback.run(null, e.getMessage()));
                return;
            }
            final HashMap<String, String> fields = new HashMap<>();
            fields.put("banner_data", data);
            LumenServer.post("/api/lumengram/banner", fields, (result, error) -> {
                lastRefresh = 0;
                refresh();
                callback.run(result, error);
            });
        });
    }

    private static String prepareBanner(Bitmap bitmap) {
        final float ratio = BANNER_WIDTH / (float) BANNER_HEIGHT;
        int width = bitmap.getWidth();
        int height = Math.round(width / ratio);
        if (height > bitmap.getHeight()) {
            height = bitmap.getHeight();
            width = Math.round(height * ratio);
        }
        final int left = (bitmap.getWidth() - width) / 2;
        final int top = (bitmap.getHeight() - height) / 2;
        final Bitmap result = Bitmap.createBitmap(BANNER_WIDTH, BANNER_HEIGHT, Bitmap.Config.ARGB_8888);
        new Canvas(result).drawBitmap(bitmap, new Rect(left, top, left + width, top + height),
            new Rect(0, 0, BANNER_WIDTH, BANNER_HEIGHT), new Paint(Paint.FILTER_BITMAP_FLAG));
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        result.compress(Bitmap.CompressFormat.JPEG, BANNER_QUALITY, out);
        result.recycle();
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    public static void deleteBanner(LumenServer.Callback<JSONObject> callback) {
        LumenServer.post("/api/lumengram/banner/delete", null, (result, error) -> {
            lastRefresh = 0;
            refresh();
            callback.run(result, error);
        });
    }

    public static void reportBanner(long userId, LumenServer.Callback<JSONObject> callback) {
        final HashMap<String, String> fields = new HashMap<>();
        fields.put("user_id", String.valueOf(userId));
        LumenServer.post("/api/lumengram/banner/report", fields, callback);
    }

    public static void startSpotify(LumenServer.Callback<String> callback) {
        LumenServer.post("/api/lumengram/spotify/start", null, (result, error) ->
            callback.run(result != null ? result.optString("url", null) : null, error));
    }

    public static void connectLastfm(String username, LumenServer.Callback<JSONObject> callback) {
        final HashMap<String, String> fields = new HashMap<>();
        fields.put("username", username);
        LumenServer.post("/api/lumengram/lastfm", fields, (result, error) -> {
            lastRefresh = 0;
            refresh();
            callback.run(result, error);
        });
    }

    public static void disconnectMusic(LumenServer.Callback<JSONObject> callback) {
        LumenServer.post("/api/lumengram/music/disconnect", null, (result, error) -> {
            lastRefresh = 0;
            refresh();
            callback.run(result, error);
        });
    }
}
