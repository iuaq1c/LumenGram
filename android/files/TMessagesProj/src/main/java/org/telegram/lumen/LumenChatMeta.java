/*
 * This file is part of LumenGram, a modification of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */
package org.telegram.lumen;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextUtils;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.tgnet.TLRPC;

import java.util.HashMap;
import java.util.Iterator;

// Local names, notes and color labels of chats, kept only on this device.
public final class LumenChatMeta {

    public static final String[] LABEL_KEYS = {"red", "orange", "yellow", "green", "blue", "purple"};
    public static final int[] LABEL_COLORS = {0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047, 0xFF1E88E5, 0xFF8E24AA};
    private static final String[] LABEL_TITLES = {"LabelRed", "LabelOrange", "LabelYellow", "LabelGreen", "LabelBlue", "LabelPurple"};

    private static final String PREF_LABELS = "chatLabels";
    private static final String PREF_NAMES = "localNames";
    private static final String PREF_NOTES = "chatNotes";

    private static HashMap<Long, String> labels;
    private static HashMap<Long, String> names;
    private static HashMap<Long, String> notes;
    private static final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF labelRect = new RectF();

    private LumenChatMeta() {
    }

    private static HashMap<Long, String> load(String pref) {
        final HashMap<Long, String> result = new HashMap<>();
        try {
            final JSONObject object = new JSONObject(LumenConfig.getString(pref, "{}"));
            for (Iterator<String> it = object.keys(); it.hasNext(); ) {
                final String key = it.next();
                result.put(Long.parseLong(key), object.getString(key));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return result;
    }

    private static void save(String pref, HashMap<Long, String> map) {
        final JSONObject object = new JSONObject();
        try {
            for (HashMap.Entry<Long, String> entry : map.entrySet()) {
                object.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        LumenConfig.setString(pref, object.toString());
    }

    private static HashMap<Long, String> labels() {
        if (labels == null) {
            labels = load(PREF_LABELS);
        }
        return labels;
    }

    private static HashMap<Long, String> names() {
        if (names == null) {
            names = load(PREF_NAMES);
        }
        return names;
    }

    private static HashMap<Long, String> notes() {
        if (notes == null) {
            notes = load(PREF_NOTES);
        }
        return notes;
    }

    // Index into LABEL_KEYS, or -1 when the chat has no label.
    public static int label(long dialogId) {
        final String key = labels().get(dialogId);
        if (key == null) {
            return -1;
        }
        for (int i = 0; i < LABEL_KEYS.length; i++) {
            if (LABEL_KEYS[i].equals(key)) {
                return i;
            }
        }
        return -1;
    }

    public static String labelTitle(int index) {
        return LumenLang.get(index >= 0 && index < LABEL_TITLES.length ? LABEL_TITLES[index] : "LabelNone");
    }

    public static void setLabel(long dialogId, int index) {
        if (index < 0 || index >= LABEL_KEYS.length) {
            labels().remove(dialogId);
        } else {
            labels().put(dialogId, LABEL_KEYS[index]);
        }
        save(PREF_LABELS, labels());
        LumenUi.settingsChanged();
    }

    // Peer ID as stored by the web client: user ID for users, negative ID for groups and channels.
    public static String localName(long dialogId) {
        if (names == null && !LumenConfig.prefs().contains(PREF_NAMES)) {
            return null;
        }
        final String name = names().get(dialogId);
        return TextUtils.isEmpty(name) ? null : name;
    }

    public static String userName(TLRPC.User user) {
        return user != null ? localName(user.id) : null;
    }

    public static String chatTitle(TLRPC.Chat chat) {
        if (chat == null) {
            return "";
        }
        final String local = localName(-chat.id);
        return local != null ? local : chat.title;
    }

    public static void setLocalName(long dialogId, String name) {
        if (TextUtils.isEmpty(name)) {
            names().remove(dialogId);
        } else {
            names().put(dialogId, name.length() > 64 ? name.substring(0, 64) : name);
        }
        save(PREF_NAMES, names());
        LumenUi.settingsChanged();
    }

    public static String note(long dialogId) {
        final String note = notes().get(dialogId);
        return TextUtils.isEmpty(note) ? null : note;
    }

    public static void setNote(long dialogId, String note) {
        if (TextUtils.isEmpty(note)) {
            notes().remove(dialogId);
        } else {
            notes().put(dialogId, note.length() > 1000 ? note.substring(0, 1000) : note);
        }
        save(PREF_NOTES, notes());
        LumenUi.settingsChanged();
    }

    // A thin colored stripe at the start edge of the chat row.
    public static void drawLabel(Canvas canvas, long dialogId, int height, boolean rtl, int width) {
        final int index = label(dialogId);
        if (index < 0) {
            return;
        }
        labelPaint.setColor(LABEL_COLORS[index]);
        final float stripe = AndroidUtilities.dp(4);
        final float inset = AndroidUtilities.dp(10);
        if (rtl) {
            labelRect.set(width - stripe, inset, width + stripe, height - inset);
        } else {
            labelRect.set(-stripe, inset, stripe, height - inset);
        }
        canvas.drawRoundRect(labelRect, stripe, stripe, labelPaint);
    }
}
