/*
 * This file is part of LumenGram, a modification of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */
package org.telegram.lumen;

import org.json.JSONArray;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.util.HashSet;

// Developer and supporter badges, the supporter list is synced from tunexa.ru.
public final class LumenBadges {

    public static final int NONE = 0;
    public static final int DEVELOPER = 1;
    public static final int SUPPORTER = 2;

    private static final long DEVELOPER_ID = 1638932794L;
    private static final String SUPPORTERS_URL = LumenServer.SERVER_URL + "/api/lumengram/supporters.json";
    private static final long REFRESH_MS = 30 * 60 * 1000L;
    private static final String PREF_SUPPORTERS = "supporterIds";

    private static HashSet<Long> supporters;
    private static boolean started;

    private LumenBadges() {
    }

    public static int kind(long userId) {
        if (userId == DEVELOPER_ID) {
            return DEVELOPER;
        }
        return supporters().contains(userId) ? SUPPORTER : NONE;
    }

    public static String title(int kind) {
        return LumenLang.get(kind == DEVELOPER ? "BadgeDeveloper" : "BadgeSupporter");
    }

    private static HashSet<Long> supporters() {
        if (supporters == null) {
            supporters = parse(LumenConfig.getString(PREF_SUPPORTERS, ""));
        }
        return supporters;
    }

    private static HashSet<Long> parse(String joined) {
        final HashSet<Long> result = new HashSet<>();
        for (String id : joined.split(",")) {
            if (id.matches("[1-9][0-9]{4,15}")) {
                result.add(Long.parseLong(id));
            }
        }
        return result;
    }

    public static void start() {
        if (started) {
            return;
        }
        started = true;
        refresh();
    }

    private static void refresh() {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                final JSONArray list = LumenServer.getJson(SUPPORTERS_URL).optJSONArray("supporters");
                if (list != null) {
                    final StringBuilder joined = new StringBuilder();
                    for (int i = 0; i < list.length(); i++) {
                        if (joined.length() > 0) {
                            joined.append(',');
                        }
                        joined.append(list.optString(i));
                    }
                    final String value = joined.toString();
                    AndroidUtilities.runOnUIThread(() -> {
                        LumenConfig.setString(PREF_SUPPORTERS, value);
                        supporters = parse(value);
                    });
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            AndroidUtilities.runOnUIThread(LumenBadges::refresh, REFRESH_MS);
        });
    }
}
