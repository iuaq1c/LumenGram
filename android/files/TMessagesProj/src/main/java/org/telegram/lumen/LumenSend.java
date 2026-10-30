/*
 * This file is part of LumenGram, a modification of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */
package org.telegram.lumen;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;

// Hooks into SendMessagesHelper.sendMessage: reply templates, undo send and ghost mode after sending.
public final class LumenSend {

    private static final long UNDO_DELAY = 5000;
    private static final String PREF_TEMPLATES = "templates";

    public static class Template {
        public String key;
        public String text;
    }

    private static boolean sendingNow;

    private LumenSend() {
    }

    // Returns true when LumenGram took the message over and will send it itself.
    public static boolean intercept(int account, SendMessagesHelper.SendMessageParams params) {
        if (sendingNow || params == null || params.retryMessageObject != null) {
            return false;
        }
        final boolean textOnly = !TextUtils.isEmpty(params.message)
            && params.photo == null && params.document == null && params.location == null
            && params.poll == null && params.game == null && params.invoice == null && params.todo == null
            && params.path == null && params.user == null && params.sendingStory == null;
        if (textOnly) {
            applyTemplate(params);
        }
        if (!textOnly || params.scheduleDate != 0 || !LumenConfig.getBool(LumenConfig.UNDO_SEND)
            || params.quick_reply_shortcut != null) {
            LumenGhost.onMessageSent(account, params.peer);
            return false;
        }
        final BaseFragment fragment = LaunchActivity.getLastFragment();
        if (fragment == null || fragment.getParentActivity() == null) {
            return false;
        }
        final boolean[] cancelled = new boolean[1];
        final Runnable send = () -> {
            if (cancelled[0]) {
                return;
            }
            cancelled[0] = true;
            sendingNow = true;
            try {
                SendMessagesHelper.getInstance(account).sendMessage(params);
            } finally {
                sendingNow = false;
            }
            LumenGhost.onMessageSent(account, params.peer);
        };
        final Bulletin bulletin = BulletinFactory.of(fragment).createSimpleBulletin(R.raw.chats_infotip,
            LumenLang.get("Sending"), LumenLang.get("Undo"), () -> {
                if (cancelled[0]) {
                    return;
                }
                cancelled[0] = true;
                AndroidUtilities.cancelRunOnUIThread(send);
                restoreText(params);
            });
        bulletin.setDuration((int) UNDO_DELAY).show();
        AndroidUtilities.runOnUIThread(send, UNDO_DELAY);
        return true;
    }

    private static void restoreText(SendMessagesHelper.SendMessageParams params) {
        final BaseFragment fragment = LaunchActivity.getLastFragment();
        if (fragment instanceof ChatActivity) {
            final ChatActivity chat = (ChatActivity) fragment;
            if (chat.getDialogId() == params.peer && chat.getChatActivityEnterView() != null) {
                chat.getChatActivityEnterView().setFieldText(params.message);
                LumenDialogs.toast(fragment, LumenLang.get("SendCancelled"));
                return;
            }
        }
        if (fragment != null) {
            LumenDialogs.toast(fragment, LumenLang.get("SendCancelled"));
        }
    }

    private static void applyTemplate(SendMessagesHelper.SendMessageParams params) {
        final String text = params.message.trim();
        if (!text.startsWith(";") || text.length() < 2 || text.contains(" ")) {
            return;
        }
        final String key = text.substring(1).toLowerCase();
        for (Template template : templates()) {
            if (template.key.equals(key)) {
                params.message = template.text;
                params.entities = null;
                return;
            }
        }
    }

    public static ArrayList<Template> templates() {
        final ArrayList<Template> result = new ArrayList<>();
        try {
            final JSONArray list = new JSONArray(LumenConfig.getString(PREF_TEMPLATES, "[]"));
            for (int i = 0; i < list.length(); i++) {
                final JSONObject entry = list.getJSONObject(i);
                final Template template = new Template();
                template.key = entry.optString("key", "");
                template.text = entry.optString("text", "");
                if (!template.key.isEmpty() && !template.text.isEmpty()) {
                    result.add(template);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return result;
    }

    public static void saveTemplates(ArrayList<Template> templates) {
        final JSONArray list = new JSONArray();
        try {
            for (Template template : templates) {
                list.put(new JSONObject().put("key", template.key).put("text", template.text));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        LumenConfig.setString(PREF_TEMPLATES, list.toString());
    }

    public static String normalizeKey(String key) {
        return key.trim().replaceFirst("^;", "").replaceAll("\\s+", "").toLowerCase();
    }
}
