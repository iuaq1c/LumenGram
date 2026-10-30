/*
 * This file is part of LumenGram, a modification of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */
package org.telegram.lumen;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ChatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

// Saved deleted messages and edit history, like AyuGram: kept on this device only.
public final class LumenMessageHistory {

    public static final String SAVE_DELETED = "shouldSaveDeleted";
    public static final String SAVE_EDITS = "shouldSaveEdits";

    // An updateInterfaces mask bit Telegram does not use, so open chats redraw the deleted mark.
    public static final int UPDATE_MASK_DELETED = 1 << 28;

    public static final int OPTION_EDIT_HISTORY = 904;

    private static final String PREF_DELETED = "deleted";
    private static final int MAX_DELETED = 5000;
    private static final int MAX_VERSIONS = 20;

    private static Set<String> deleted;

    private LumenMessageHistory() {
    }

    private static String t(String key) {
        return LumenLang.get(key);
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("lumengram_history", Context.MODE_PRIVATE);
    }

    public static boolean saveDeleted() {
        return LumenConfig.prefs().getBoolean(SAVE_DELETED, true);
    }

    public static boolean saveEdits() {
        return LumenConfig.prefs().getBoolean(SAVE_EDITS, true);
    }

    // Message ids are per account, and inside a channel also per channel.
    private static String key(int account, long channelDialogId, int messageId) {
        return account + ":" + channelDialogId + ":" + messageId;
    }

    private static long channelDialogId(TLRPC.Message message) {
        return message.peer_id != null && message.peer_id.channel_id != 0 ? -message.peer_id.channel_id : 0;
    }

    private static synchronized Set<String> deletedSet() {
        if (deleted == null) {
            deleted = new HashSet<>(prefs().getStringSet(PREF_DELETED, new HashSet<>()));
        }
        return deleted;
    }

    // Called from the updates handler: true when the deletion is kept as a mark instead.
    public static boolean keepDeleted(int account, long channelDialogId, ArrayList<Integer> messageIds) {
        if (!saveDeleted() || messageIds == null || messageIds.isEmpty()) {
            return false;
        }
        synchronized (LumenMessageHistory.class) {
            final Set<String> set = deletedSet();
            for (int i = 0; i < messageIds.size(); i++) {
                set.add(key(account, channelDialogId, messageIds.get(i)));
            }
            if (set.size() > MAX_DELETED) {
                set.clear();
                for (int i = 0; i < messageIds.size(); i++) {
                    set.add(key(account, channelDialogId, messageIds.get(i)));
                }
            }
            prefs().edit().putStringSet(PREF_DELETED, new HashSet<>(set)).apply();
        }
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(account)
            .postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_DELETED));
        return true;
    }

    public static boolean isDeleted(MessageObject message) {
        if (message == null || message.messageOwner == null || message.getId() <= 0) {
            return false;
        }
        final Set<String> set = deletedSet();
        return !set.isEmpty() && set.contains(key(message.currentAccount, channelDialogId(message.messageOwner), message.getId()));
    }

    public static CharSequence deletedMark() {
        return t("DeletedMark");
    }

    private static String editsKey(int account, long dialogId, int messageId) {
        return "edits_" + account + "_" + dialogId + "_" + messageId;
    }

    // Called from the updates handler before an edit replaces the stored message.
    public static void rememberEdit(int account, TLRPC.Message edited) {
        if (!saveEdits() || edited == null || edited.id <= 0) {
            return;
        }
        try {
            final long dialogId = edited.dialog_id;
            final TLRPC.Message old = MessagesStorage.getInstance(account).getMessage(dialogId, edited.id);
            if (old == null || TextUtils.equals(old.message, edited.message)) {
                return;
            }
            addVersion(account, dialogId, edited.id, old.message, old.edit_date != 0 ? old.edit_date : old.date);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    // Own edits change the stored message before the server confirms them, so catch them here.
    public static void rememberOwnEdit(MessageObject message) {
        if (!saveEdits() || message == null || message.getId() <= 0 || message.editingMessage == null
            || TextUtils.equals(message.previousMessage, message.editingMessage)) {
            return;
        }
        final TLRPC.Message owner = message.messageOwner;
        addVersion(message.currentAccount, message.getDialogId(), message.getId(), message.previousMessage,
            owner.edit_date != 0 ? owner.edit_date : owner.date);
    }

    private static void addVersion(int account, long dialogId, int messageId, String text, long date) {
        if (TextUtils.isEmpty(text)) {
            return;
        }
        try {
            final String key = editsKey(account, dialogId, messageId);
            final JSONArray versions = new JSONArray(prefs().getString(key, "[]"));
            final JSONObject version = new JSONObject();
            version.put("text", text);
            version.put("date", date);
            versions.put(version);
            while (versions.length() > MAX_VERSIONS) {
                versions.remove(0);
            }
            prefs().edit().putString(key, versions.toString()).apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static boolean hasEditHistory(MessageObject message) {
        return message != null && message.getId() > 0
            && prefs().contains(editsKey(message.currentAccount, message.getDialogId(), message.getId()));
    }

    public static void addOptions(MessageObject message, ArrayList<CharSequence> items, ArrayList<Integer> options, ArrayList<Integer> icons) {
        if (hasEditHistory(message)) {
            items.add(t("EditHistory"));
            options.add(OPTION_EDIT_HISTORY);
            icons.add(R.drawable.msg_edit);
        }
    }

    private static String fullDate(long seconds) {
        return new SimpleDateFormat("d MMMM yyyy, HH:mm:ss", LocaleController.getInstance().getCurrentLocale())
            .format(new Date(seconds * 1000));
    }

    public static void showEditHistory(ChatActivity chat, MessageObject message) {
        final StringBuilder text = new StringBuilder();
        try {
            final JSONArray versions = new JSONArray(prefs().getString(editsKey(message.currentAccount, message.getDialogId(), message.getId()), "[]"));
            for (int i = 0; i < versions.length(); i++) {
                final JSONObject version = versions.getJSONObject(i);
                text.append(fullDate(version.optLong("date"))).append('\n').append(version.optString("text")).append("\n\n");
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        final TLRPC.Message owner = message.messageOwner;
        final long date = owner.edit_date != 0 ? owner.edit_date : owner.date;
        text.append(LumenLang.format("EditHistoryCurrent", "date", fullDate(date))).append('\n').append(owner.message);
        final String history = text.toString().trim();
        final AlertDialog.Builder builder = new AlertDialog.Builder(chat.getParentActivity(), chat.getResourceProvider());
        builder.setTitle(t("EditHistory"));
        builder.setMessage(history);
        builder.setPositiveButton(t("DetailsCopy"), (dialog, which) -> {
            AndroidUtilities.addToClipboard(history);
            LumenDialogs.toast(chat, t("DetailsCopied"));
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Close), null);
        chat.showDialog(builder.create());
    }
}
