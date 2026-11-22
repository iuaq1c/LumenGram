/*
 * This file is part of LumenGram, a modification of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */
package org.telegram.lumen;

import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;

// "Year in review": counts of the user's own messages this year, collected with one search request per chat.
public class LumenWrapped {

    // Recent chats that are checked; older ones rarely change the top
    private static final int MAX_CHATS = 40;
    private static final int SAMPLE_PER_CHAT = 100;
    private static final int TOP_PEOPLE = 5;
    private static final int TOP_EMOJI = 5;
    // Chats where sent media is counted: the most active ones hold almost all of it
    private static final int MEDIA_CHATS = 10;
    private static final int MIN_WORD_LENGTH = 4;
    // Pause between requests, so Telegram does not slow the account down
    private static final long REQUEST_PAUSE_MS = 350;
    private static final int NIGHT_FROM_HOUR = 0;
    private static final int NIGHT_TO_HOUR = 5;

    private static final HashSet<String> STOP_WORDS = new HashSet<>(java.util.Arrays.asList(
        "что", "это", "как", "так", "вот", "был", "была", "было", "были", "меня", "тебя", "тебе", "мне",
        "если", "когда", "чтобы", "только", "уже", "еще", "ещё", "очень", "тоже", "просто", "потом", "сейчас",
        "есть", "нет", "там", "тут", "надо", "можно", "нужно", "будет", "буду", "него", "неё", "нее", "них",
        "который", "которая", "которые", "этот", "эта", "эти", "того", "тоже", "всё", "все", "всех", "себя",
        "типа", "вообще", "давай", "ладно", "хорошо", "щас", "короче", "кстати", "почему", "зачем", "where",
        "that", "this", "with", "have", "what", "from", "your", "just", "there", "they", "will", "would"));

    public static class Person {
        public long dialogId;
        public String name;
        public int count;
    }

    public static class Result {
        public int year;
        public int totalMessages;
        public int chats;
        public ArrayList<Person> top = new ArrayList<>();
        public int voices;
        public int rounds;
        public int photos;
        public String topWord;
        public int topWordCount;
        public ArrayList<String> topEmoji = new ArrayList<>();
        public int nightPercent;
        public int busiestHour = -1;
    }

    public interface Progress {
        void onProgress(int done, int total);
    }

    public interface Done {
        void onDone(Result result);
    }

    private final int account;
    private final Progress progress;
    private final Done done;
    private final Result result = new Result();
    private final ArrayList<TLRPC.Dialog> dialogs = new ArrayList<>();
    private final HashMap<String, Integer> words = new HashMap<>();
    private final HashMap<String, Integer> emoji = new HashMap<>();
    private final int[] hours = new int[24];
    private final ArrayList<Runnable> mediaRequests = new ArrayList<>();
    // Id of the last message before January 1 in each chat: Telegram refuses date limits
    // together with media filters, so the year is cut by message id instead
    private final HashMap<Long, Integer> yearBoundary = new HashMap<>();
    private int sampled;
    private int yearStart;
    private int index;
    private boolean cancelled;

    public LumenWrapped(int account, Progress progress, Done done) {
        this.account = account;
        this.progress = progress;
        this.done = done;
    }

    public void start() {
        final Calendar calendar = Calendar.getInstance();
        result.year = calendar.get(Calendar.YEAR);
        calendar.set(result.year, Calendar.JANUARY, 1, 0, 0, 0);
        yearStart = (int) (calendar.getTimeInMillis() / 1000);
        final MessagesController controller = MessagesController.getInstance(account);
        for (TLRPC.Dialog dialog : controller.getAllDialogs()) {
            if (dialogs.size() >= MAX_CHATS) {
                break;
            }
            if (isPersonalOrGroup(controller, dialog.id)) {
                dialogs.add(dialog);
            }
        }
        next();
    }

    public void cancel() {
        cancelled = true;
    }

    private boolean isPersonalOrGroup(MessagesController controller, long dialogId) {
        if (DialogObject.isUserDialog(dialogId)) {
            final TLRPC.User user = controller.getUser(dialogId);
            return user != null && !user.bot && !UserObject.isUserSelf(user) && !UserObject.isService(user.id);
        }
        final TLRPC.Chat chat = controller.getChat(-dialogId);
        return chat != null && (!org.telegram.messenger.ChatObject.isChannel(chat) || chat.megagroup);
    }

    private void next() {
        if (cancelled) {
            return;
        }
        if (index == dialogs.size()) {
            planMediaRequests();
        }
        progress.onProgress(index, dialogs.size() + Math.min(result.top.size(), MEDIA_CHATS) * 4);
        if (index < dialogs.size()) {
            countChat(dialogs.get(index++));
        } else if (index - dialogs.size() < mediaRequests.size()) {
            mediaRequests.get(index++ - dialogs.size()).run();
        } else {
            finish();
        }
    }

    // Voice, round and photo messages the user sent this year, in the most active chats
    private void planMediaRequests() {
        if (!mediaRequests.isEmpty()) {
            return;
        }
        Collections.sort(result.top, (a, b) -> Integer.compare(b.count, a.count));
        for (int i = 0; i < result.top.size() && i < MEDIA_CHATS; i++) {
            final long dialogId = result.top.get(i).dialogId;
            mediaRequests.add(() -> findYearBoundary(dialogId));
            mediaRequests.add(() -> countMine(dialogId, new TLRPC.TL_inputMessagesFilterVoice(), count -> result.voices += count));
            mediaRequests.add(() -> countMine(dialogId, new TLRPC.TL_inputMessagesFilterRoundVideo(), count -> result.rounds += count));
            mediaRequests.add(() -> countMine(dialogId, new TLRPC.TL_inputMessagesFilterPhotos(), count -> result.photos += count));
        }
    }

    private void schedule() {
        AndroidUtilities.runOnUIThread(this::next, REQUEST_PAUSE_MS);
    }

    private void countChat(TLRPC.Dialog dialog) {
        final MessagesController controller = MessagesController.getInstance(account);
        final TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
        req.peer = controller.getInputPeer(dialog.id);
        req.q = "";
        req.from_id = new TLRPC.TL_inputPeerSelf();
        req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
        req.min_date = yearStart;
        req.limit = SAMPLE_PER_CHAT;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof TLRPC.messages_Messages) {
                final TLRPC.messages_Messages messages = (TLRPC.messages_Messages) response;
                final int count = Math.max(messages.count, messages.messages.size());
                if (count > 0) {
                    result.totalMessages += count;
                    result.chats++;
                    final Person person = new Person();
                    person.dialogId = dialog.id;
                    person.name = chatName(controller, dialog.id);
                    person.count = count;
                    result.top.add(person);
                }
                for (TLRPC.Message message : messages.messages) {
                    if (message.date >= yearStart) {
                        sample(message);
                    }
                }
            }
            schedule();
        }));
    }

    private void findYearBoundary(long dialogId) {
        final TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
        req.peer = MessagesController.getInstance(account).getInputPeer(dialogId);
        req.offset_date = yearStart;
        req.limit = 1;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            int boundary = 0;
            if (response instanceof TLRPC.messages_Messages && !((TLRPC.messages_Messages) response).messages.isEmpty()) {
                boundary = ((TLRPC.messages_Messages) response).messages.get(0).id;
            }
            yearBoundary.put(dialogId, boundary);
            schedule();
        }));
    }

    private void countMine(long dialogId, TLRPC.MessagesFilter filter, Utilities.Callback<Integer> apply) {
        final TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
        req.peer = MessagesController.getInstance(account).getInputPeer(dialogId);
        req.q = "";
        req.from_id = new TLRPC.TL_inputPeerSelf();
        req.filter = filter;
        req.min_id = yearBoundary.getOrDefault(dialogId, 0);
        req.limit = 1;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof TLRPC.messages_Messages) {
                final TLRPC.messages_Messages messages = (TLRPC.messages_Messages) response;
                apply.run(Math.max(messages.count, messages.messages.size()));
            }
            schedule();
        }));
    }

    private static String chatName(MessagesController controller, long dialogId) {
        if (DialogObject.isUserDialog(dialogId)) {
            return UserObject.getUserName(controller.getUser(dialogId));
        }
        final TLRPC.Chat chat = controller.getChat(-dialogId);
        return chat != null ? chat.title : "";
    }

    private void sample(TLRPC.Message message) {
        sampled++;
        final Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(message.date * 1000L);
        hours[calendar.get(Calendar.HOUR_OF_DAY)]++;
        // Bot commands like /start are not words the person really uses
        if (TextUtils.isEmpty(message.message) || message.message.startsWith("/")) {
            return;
        }
        final String text = message.message;
        for (String word : text.toLowerCase(Locale.getDefault()).split("[^\\p{L}]+")) {
            if (word.length() >= MIN_WORD_LENGTH && !STOP_WORDS.contains(word)) {
                words.put(word, words.getOrDefault(word, 0) + 1);
            }
        }
        for (int i = 0; i < text.length(); ) {
            final int codePoint = text.codePointAt(i);
            if (isEmoji(codePoint)) {
                final String symbol = new String(Character.toChars(codePoint));
                emoji.put(symbol, emoji.getOrDefault(symbol, 0) + 1);
            }
            i += Character.charCount(codePoint);
        }
    }

    private static boolean isEmoji(int codePoint) {
        return codePoint >= 0x1F300 && codePoint <= 0x1FAFF || codePoint >= 0x2600 && codePoint <= 0x27BF;
    }

    private void finish() {
        Collections.sort(result.top, (a, b) -> Integer.compare(b.count, a.count));
        while (result.top.size() > TOP_PEOPLE) {
            result.top.remove(result.top.size() - 1);
        }
        for (Map.Entry<String, Integer> entry : words.entrySet()) {
            if (entry.getValue() > result.topWordCount) {
                result.topWord = entry.getKey();
                result.topWordCount = entry.getValue();
            }
        }
        final ArrayList<Map.Entry<String, Integer>> emojiList = new ArrayList<>(emoji.entrySet());
        Collections.sort(emojiList, (a, b) -> Integer.compare(b.getValue(), a.getValue()));
        for (int i = 0; i < emojiList.size() && i < TOP_EMOJI; i++) {
            result.topEmoji.add(emojiList.get(i).getKey());
        }
        int night = 0;
        int busiest = 0;
        for (int hour = 0; hour < 24; hour++) {
            if (hour >= NIGHT_FROM_HOUR && hour <= NIGHT_TO_HOUR) {
                night += hours[hour];
            }
            if (hours[hour] > hours[busiest]) {
                busiest = hour;
            }
        }
        result.nightPercent = sampled > 0 ? Math.round(night * 100f / sampled) : 0;
        result.busiestHour = sampled > 0 ? busiest : -1;
        done.onDone(result);
    }
}
