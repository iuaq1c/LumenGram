/*
 * This file is part of LumenGram, a modification of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */
package org.telegram.lumen;

import android.text.TextUtils;

import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// The "LumenGram" item in a chat's long-press menu: label, local name, note and statistics.
public final class LumenChatActions {

    private static final int STATS_PAGES = 10;
    private static final int STATS_PAGE_SIZE = 100;
    private static final Pattern WORD_RE = Pattern.compile("[\\p{L}\\p{N}]{4,}");
    private static final Pattern EMOJI_RE = Pattern.compile("[\\x{1F300}-\\x{1FAFF}\\x{2600}-\\x{27BF}]");

    private LumenChatActions() {
    }

    private static String t(String key) {
        return LumenLang.get(key);
    }

    public static void show(BaseFragment fragment, long dialogId) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        final String[] items = {t("ChatLabel"), t("RenameLocal"), t("ChatNote"), t("ChatStats"), t("JumpToStart")};
        final AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider());
        builder.setTitle("LumenGram");
        builder.setItems(items, (dialog, which) -> {
            switch (which) {
                case 0:
                    chooseLabel(fragment, dialogId);
                    break;
                case 1:
                    LumenDialogs.input(fragment, t("RenameTitle"), t("RenameAbout"), realName(fragment, dialogId),
                        LumenChatMeta.localName(dialogId), false, name -> LumenChatMeta.setLocalName(dialogId, name));
                    break;
                case 2:
                    LumenDialogs.input(fragment, t("NoteTitle"), t("NoteAbout"), t("NoteTitle"),
                        LumenChatMeta.note(dialogId), true, note -> LumenChatMeta.setNote(dialogId, note));
                    break;
                case 3:
                    showStats(fragment, dialogId);
                    break;
                case 4:
                    jumpToStart(fragment, dialogId);
                    break;
            }
        });
        fragment.showDialog(builder.create());
    }

    // offset_id = 1 with add_offset = -1 returns the oldest message of the chat.
    private static void jumpToStart(BaseFragment fragment, long dialogId) {
        if (DialogObject.isEncryptedDialog(dialogId)) {
            return;
        }
        final TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
        req.peer = fragment.getMessagesController().getInputPeer(dialogId);
        req.offset_id = 1;
        req.add_offset = -1;
        req.limit = 1;
        fragment.getConnectionsManager().sendRequest(req, (response, error) -> org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
            if (!(response instanceof TLRPC.messages_Messages)) {
                return;
            }
            final TLRPC.messages_Messages result = (TLRPC.messages_Messages) response;
            if (result.messages.isEmpty()) {
                return;
            }
            final android.os.Bundle args = new android.os.Bundle();
            if (DialogObject.isUserDialog(dialogId)) {
                args.putLong("user_id", dialogId);
            } else {
                args.putLong("chat_id", -dialogId);
            }
            args.putInt("message_id", result.messages.get(0).id);
            fragment.presentFragment(new org.telegram.ui.ChatActivity(args));
        }));
    }

    private static String realName(BaseFragment fragment, long dialogId) {
        final MessagesController controller = fragment.getMessagesController();
        if (DialogObject.isUserDialog(dialogId)) {
            final TLRPC.User user = controller.getUser(dialogId);
            return user != null ? org.telegram.messenger.ContactsController.formatName(user.first_name, user.last_name) : "";
        }
        final TLRPC.Chat chat = controller.getChat(-dialogId);
        return chat != null ? chat.title : "";
    }

    private static void chooseLabel(BaseFragment fragment, long dialogId) {
        final String[] items = new String[LumenChatMeta.LABEL_KEYS.length + 1];
        items[0] = LumenChatMeta.labelTitle(-1);
        for (int i = 0; i < LumenChatMeta.LABEL_KEYS.length; i++) {
            items[i + 1] = "●  " + LumenChatMeta.labelTitle(i);
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider());
        builder.setTitle(t("ChatLabel"));
        builder.setItems(items, (dialog, which) -> LumenChatMeta.setLabel(dialogId, which - 1));
        fragment.showDialog(builder.create());
    }

    // Statistics from the last messages of the chat, loaded straight from Telegram.
    private static void showStats(BaseFragment fragment, long dialogId) {
        final AlertDialog progress = new AlertDialog(fragment.getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(true);
        fragment.showDialog(progress);
        loadPage(fragment, dialogId, 0, 0, new ArrayList<>(), messages -> {
            progress.dismiss();
            final AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider());
            builder.setTitle(t("ChatStats"));
            builder.setMessage(messages.isEmpty() ? t("StatsEmpty") : buildStats(fragment, messages));
            builder.setPositiveButton(org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.OK), null);
            fragment.showDialog(builder.create());
        });
    }

    private interface Done {
        void run(ArrayList<TLRPC.Message> messages);
    }

    private static void loadPage(BaseFragment fragment, long dialogId, int offsetId, int page, ArrayList<TLRPC.Message> collected, Done done) {
        final TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
        req.peer = fragment.getMessagesController().getInputPeer(dialogId);
        req.offset_id = offsetId;
        req.limit = STATS_PAGE_SIZE;
        fragment.getConnectionsManager().sendRequest(req, (response, error) -> org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof TLRPC.messages_Messages) {
                final TLRPC.messages_Messages result = (TLRPC.messages_Messages) response;
                fragment.getMessagesController().putUsers(result.users, false);
                fragment.getMessagesController().putChats(result.chats, false);
                int lastId = 0;
                for (TLRPC.Message message : result.messages) {
                    if (!(message instanceof TLRPC.TL_messageEmpty)) {
                        collected.add(message);
                        lastId = message.id;
                    }
                }
                if (lastId != 0 && result.messages.size() >= STATS_PAGE_SIZE && page + 1 < STATS_PAGES) {
                    loadPage(fragment, dialogId, lastId, page + 1, collected, done);
                    return;
                }
            }
            done.run(collected);
        }), ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    private static String buildStats(BaseFragment fragment, ArrayList<TLRPC.Message> messages) {
        final HashMap<Long, Integer> bySender = new HashMap<>();
        final int[] byHour = new int[24];
        final HashMap<String, Integer> words = new HashMap<>();
        final HashMap<String, Integer> emoji = new HashMap<>();
        final java.util.Calendar calendar = java.util.Calendar.getInstance();
        long replyTotal = 0;
        int replyCount = 0;
        TLRPC.Message newer = null;
        for (TLRPC.Message message : messages) {
            final long sender = MessageObject.getPeerId(message.from_id != null ? message.from_id : message.peer_id);
            increment(bySender, sender);
            calendar.setTimeInMillis(message.date * 1000L);
            byHour[calendar.get(java.util.Calendar.HOUR_OF_DAY)]++;
            if (!TextUtils.isEmpty(message.message)) {
                final Matcher wordMatcher = WORD_RE.matcher(message.message.toLowerCase(Locale.ROOT));
                while (wordMatcher.find()) {
                    increment(words, wordMatcher.group());
                }
                final Matcher emojiMatcher = EMOJI_RE.matcher(message.message);
                while (emojiMatcher.find()) {
                    increment(emoji, emojiMatcher.group());
                }
            }
            // Messages come newest first: a sender change means "newer" answered "message".
            if (newer != null) {
                final long newerSender = MessageObject.getPeerId(newer.from_id != null ? newer.from_id : newer.peer_id);
                final int gap = newer.date - message.date;
                if (newerSender != sender && gap > 0 && gap < 6 * 60 * 60) {
                    replyTotal += gap;
                    replyCount++;
                }
            }
            newer = message;
        }

        final StringBuilder text = new StringBuilder();
        text.append(LumenLang.format("StatsBased", "count", messages.size())).append("\n\n");
        text.append(t("StatsMessages")).append('\n');
        for (Map.Entry<Long, Integer> entry : top(bySender, 8)) {
            text.append("• ").append(senderName(fragment, entry.getKey())).append(" — ").append(entry.getValue()).append('\n');
        }
        int peakHour = 0;
        for (int i = 1; i < 24; i++) {
            if (byHour[i] > byHour[peakHour]) {
                peakHour = i;
            }
        }
        text.append('\n').append(t("StatsByHour")).append(": ")
            .append(String.format(Locale.US, "%02d:00–%02d:00", peakHour, (peakHour + 1) % 24)).append('\n');
        if (!words.isEmpty()) {
            text.append('\n').append(t("StatsTopWords")).append(": ");
            appendTop(text, words, 8);
        }
        if (!emoji.isEmpty()) {
            text.append('\n').append(t("StatsTopEmoji")).append(": ");
            appendTop(text, emoji, 8);
        }
        if (replyCount > 0) {
            text.append('\n').append(t("StatsAvgReply")).append(": ").append(LumenFocus.formatDuration(replyTotal / replyCount));
        }
        return text.toString().trim();
    }

    private static <K> void increment(HashMap<K, Integer> map, K key) {
        final Integer value = map.get(key);
        map.put(key, value == null ? 1 : value + 1);
    }

    private static <K> ArrayList<Map.Entry<K, Integer>> top(HashMap<K, Integer> map, int limit) {
        final ArrayList<Map.Entry<K, Integer>> list = new ArrayList<>(map.entrySet());
        Collections.sort(list, (a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return new ArrayList<>(list.subList(0, Math.min(limit, list.size())));
    }

    private static void appendTop(StringBuilder text, HashMap<String, Integer> map, int limit) {
        boolean first = true;
        for (Map.Entry<String, Integer> entry : top(map, limit)) {
            if (!first) {
                text.append(", ");
            }
            text.append(entry.getKey()).append(" (").append(entry.getValue()).append(')');
            first = false;
        }
        text.append('\n');
    }

    private static String senderName(BaseFragment fragment, long peerId) {
        if (peerId > 0) {
            final TLRPC.User user = fragment.getMessagesController().getUser(peerId);
            return user != null ? UserObject.getUserName(user) : String.valueOf(peerId);
        }
        final TLRPC.Chat chat = fragment.getMessagesController().getChat(-peerId);
        return chat != null ? chat.title : String.valueOf(peerId);
    }
}
