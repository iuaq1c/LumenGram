package org.telegram.lumen;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashSet;

// Answers private messages with a set text while the user is away: always, or only in focus mode.
public class LumenAutoReply {

    public static final String ENABLED = "autoReplyEnabled";
    public static final String FOCUS_ONLY = "autoReplyFocusOnly";
    public static final String TEXT = "autoReplyText";

    // One answer per chat in this time, so a conversation does not get the same text over and over
    private static final long COOLDOWN_MS = 6 * 60 * 60 * 1000L;
    // Messages older than this come from catching up after being offline and are not answered
    private static final int MAX_MESSAGE_AGE_SECONDS = 120;

    public static boolean enabled() {
        return LumenConfig.getBool(ENABLED);
    }

    public static boolean focusOnly() {
        return LumenConfig.getBool(FOCUS_ONLY);
    }

    public static final String DND_REPLY = "dndAutoReply";

    public static boolean dndReply() {
        return LumenConfig.prefs().getBoolean(DND_REPLY, true);
    }

    public static String text() {
        if (LumenDnd.active() && dndReply()) {
            return LumenLang.format("DndReply", "status", LumenDnd.statusLine());
        }
        final String text = LumenConfig.prefs().getString(TEXT, null);
        return TextUtils.isEmpty(text) ? LumenLang.get("AutoReplyDefault") : text;
    }

    public static void setText(String text) {
        LumenConfig.prefs().edit().putString(TEXT, text).apply();
    }

    public static boolean active() {
        return enabled() && (!focusOnly() || LumenFocus.focusActive()) || LumenDnd.active() && dndReply();
    }

    public static void process(int account, ArrayList<MessageObject> messages) {
        if (messages == null || !active()) {
            return;
        }
        final int now = ConnectionsManager.getInstance(account).getCurrentTime();
        final long selfId = UserConfig.getInstance(account).getClientUserId();
        final HashSet<Long> answered = new HashSet<>();
        for (MessageObject message : messages) {
            if (message == null || message.messageOwner == null || message.isOutOwner()
                || message.messageOwner.action != null && !(message.messageOwner.action instanceof TLRPC.TL_messageActionEmpty)
                || now - message.messageOwner.date > MAX_MESSAGE_AGE_SECONDS) {
                continue;
            }
            final long dialogId = message.getDialogId();
            if (!DialogObject.isUserDialog(dialogId) || dialogId == selfId || answered.contains(dialogId)
                || dialogId == LumenFocus.openedDialogId()) {
                continue;
            }
            final TLRPC.User user = MessagesController.getInstance(account).getUser(dialogId);
            if (user == null || user.bot || user.self || user.deleted || UserObject.isService(dialogId)) {
                continue;
            }
            if (!takeCooldown(account, dialogId)) {
                continue;
            }
            answered.add(dialogId);
            SendMessagesHelper.getInstance(account).sendMessage(SendMessagesHelper.SendMessageParams.of(
                text(), dialogId, null, null, null, false, null, null, null, true, 0, 0, null, false));
        }
    }

    private static boolean takeCooldown(int account, long dialogId) {
        final SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences("lumengram_autoreply", Context.MODE_PRIVATE);
        final String key = account + "_" + dialogId;
        final long now = System.currentTimeMillis();
        if (now - prefs.getLong(key, 0) < COOLDOWN_MS) {
            return false;
        }
        prefs.edit().putLong(key, now).apply();
        return true;
    }
}
