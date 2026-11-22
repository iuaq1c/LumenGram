/*
 * This file is part of LumenGram, a modification of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */
package org.telegram.lumen;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_keyboard;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.RecyclerListView;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

// Message context menu tools: details, bookmarks, a picture of the message and reading it aloud.
public final class LumenMessageTools {

    public static final int OPTION_DETAILS = 900;
    public static final int OPTION_BOOKMARK = 901;
    public static final int OPTION_SCREENSHOT = 902;
    public static final int OPTION_READ_ALOUD = 903;

    private static final String PREF_BOOKMARKS = "bookmarks";
    private static final int MAX_BOOKMARKS = 500;

    public static class Bookmark {
        public long dialogId;
        public int messageId;
        public String text;
        public long addedAt;
    }

    private static TextToSpeech tts;
    private static boolean ttsReady;
    private static String pendingSpeech;

    private LumenMessageTools() {
    }

    private static String t(String key) {
        return LumenLang.get(key);
    }

    // Adds the LumenGram items to the message menu of ChatActivity.
    public static void addOptions(MessageObject message, ArrayList<CharSequence> items, ArrayList<Integer> options, ArrayList<Integer> icons) {
        if (message == null || message.getId() <= 0 || message.isSponsored()) {
            return;
        }
        items.add(t("MessageDetails"));
        options.add(OPTION_DETAILS);
        icons.add(R.drawable.msg_info);
        final boolean bookmarked = isBookmarked(message.getDialogId(), message.getId());
        items.add(t(bookmarked ? "BookmarkRemove" : "BookmarkAdd"));
        options.add(OPTION_BOOKMARK);
        icons.add(bookmarked ? R.drawable.msg_unfave : R.drawable.msg_fave);
        items.add(t("Screenshot"));
        options.add(OPTION_SCREENSHOT);
        icons.add(R.drawable.msg_screencast);
        if (!TextUtils.isEmpty(textOf(message))) {
            final boolean speaking = tts != null && tts.isSpeaking();
            items.add(t(speaking ? "StopReading" : "ReadAloud"));
            options.add(OPTION_READ_ALOUD);
            icons.add(R.drawable.msg_voice_speaker);
        }
    }

    // Returns true when the option belongs to LumenGram and was handled.
    public static boolean process(ChatActivity chat, int option, MessageObject message) {
        switch (option) {
            case OPTION_DETAILS:
                showDetails(chat, message);
                return true;
            case OPTION_BOOKMARK:
                toggleBookmark(chat, message);
                return true;
            case OPTION_SCREENSHOT:
                screenshot(chat, message);
                return true;
            case OPTION_READ_ALOUD:
                readAloud(message);
                return true;
        }
        return false;
    }

    private static CharSequence textOf(MessageObject message) {
        if (message.type == MessageObject.TYPE_TEXT) {
            return message.messageText;
        }
        return message.caption;
    }

    private static String fullDate(long seconds) {
        return new SimpleDateFormat("d MMMM yyyy, HH:mm:ss", LocaleController.getInstance().getCurrentLocale())
            .format(new Date(seconds * 1000));
    }

    // Title and link of the sticker set the sticker in the message belongs to.
    private static String stickerSetText(MessageObject message) {
        final TLRPC.Document document = message.getDocument();
        if (document == null || !MessageObject.isStickerDocument(document) && !MessageObject.isAnimatedStickerDocument(document, true)) {
            return null;
        }
        final TLRPC.InputStickerSet input = MessageObject.getInputStickerSet(document);
        if (input == null) {
            return null;
        }
        final TLRPC.TL_messages_stickerSet set = MediaDataController.getInstance(message.currentAccount).getStickerSet(input, true);
        String title = null;
        String shortName = input.short_name;
        if (set != null && set.set != null) {
            title = set.set.title;
            shortName = set.set.short_name;
        }
        final String link = TextUtils.isEmpty(shortName) ? null : "https://t.me/addstickers/" + shortName;
        if (title == null) {
            return link;
        }
        return link == null ? title : title + " · " + link;
    }

    // Callback data is usually text, otherwise show it as hex.
    private static String callbackDataText(byte[] data) {
        final String text = new String(data, java.nio.charset.StandardCharsets.UTF_8);
        boolean printable = java.util.Arrays.equals(text.getBytes(java.nio.charset.StandardCharsets.UTF_8), data);
        for (int i = 0; printable && i < text.length(); i++) {
            printable = !Character.isISOControl(text.charAt(i));
        }
        if (printable) {
            return text;
        }
        final StringBuilder hex = new StringBuilder("0x");
        for (byte b : data) {
            hex.append(String.format(Locale.US, "%02x", b));
        }
        return hex.toString();
    }

    // Appends the send time to a service message text if enabled.
    public static CharSequence withServiceTime(MessageObject message, CharSequence text) {
        if (message == null || TextUtils.isEmpty(text) || message.getId() <= 0 || message.messageOwner == null || message.messageOwner.date == 0
            || !LumenConfig.getBool(LumenConfig.SERVICE_TIME)) {
            return text;
        }
        final long millis = (long) message.messageOwner.date * 1000;
        final String time = LumenUi.showSeconds()
            ? LocaleController.getInstance().getFormatterDayWithSeconds().format(millis)
            : LocaleController.getInstance().getFormatterDay().format(millis);
        return new android.text.SpannableStringBuilder(text).append(" · ").append(time);
    }

    private static void showDetails(ChatActivity chat, MessageObject message) {
        final StringBuilder text = new StringBuilder();
        text.append(t("DetailsMessageId")).append(": ").append(message.getId()).append('\n');
        text.append(t("DetailsChatId")).append(": ").append(message.getDialogId()).append('\n');
        final long senderId = message.getSenderId();
        if (senderId != 0) {
            text.append(t("DetailsSenderId")).append(": ").append(senderId).append('\n');
        }
        text.append(t("DetailsSent")).append(": ").append(fullDate(message.messageOwner.date)).append('\n');
        if (message.messageOwner.edit_date != 0) {
            text.append(t("DetailsEdited")).append(": ").append(fullDate(message.messageOwner.edit_date)).append('\n');
        }
        if (message.messageOwner.fwd_from != null && message.messageOwner.fwd_from.date != 0) {
            text.append(t("DetailsForwarded")).append(": ").append(fullDate(message.messageOwner.fwd_from.date)).append('\n');
        }
        final String stickerSet = stickerSetText(message);
        if (stickerSet != null) {
            text.append(t("DetailsStickerSet")).append(": ").append(stickerSet).append('\n');
        }
        if (message.messageOwner.reply_markup instanceof TLRPC.TL_replyInlineMarkup) {
            for (TL_keyboard.KeyboardInlineButtonRow row : ((TLRPC.TL_replyInlineMarkup) message.messageOwner.reply_markup).rows) {
                for (TL_keyboard.KeyboardInlineButton button : row.buttons) {
                    if (button.type instanceof TL_keyboard.TL_inlineButtonTypeCallback && ((TL_keyboard.TL_inlineButtonTypeCallback) button.type).data != null) {
                        text.append(t("DetailsButton")).append(" «").append(button.text).append("»: ").append(callbackDataText(((TL_keyboard.TL_inlineButtonTypeCallback) button.type).data)).append('\n');
                    }
                }
            }
        }
        final String details = text.toString().trim();
        final AlertDialog.Builder builder = new AlertDialog.Builder(chat.getParentActivity(), chat.getResourceProvider());
        builder.setTitle(t("DetailsTitle"));
        builder.setMessage(details);
        builder.setPositiveButton(t("DetailsCopy"), (dialog, which) -> {
            AndroidUtilities.addToClipboard(details);
            LumenDialogs.toast(chat, t("DetailsCopied"));
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Close), null);
        chat.showDialog(builder.create());
    }

    public static ArrayList<Bookmark> bookmarks() {
        final ArrayList<Bookmark> result = new ArrayList<>();
        try {
            final JSONArray list = new JSONArray(LumenConfig.getString(PREF_BOOKMARKS, "[]"));
            for (int i = 0; i < list.length(); i++) {
                final JSONObject entry = list.getJSONObject(i);
                final Bookmark bookmark = new Bookmark();
                bookmark.dialogId = entry.optLong("chatId");
                bookmark.messageId = entry.optInt("messageId");
                bookmark.text = entry.optString("text", "");
                bookmark.addedAt = entry.optLong("addedAt");
                result.add(bookmark);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return result;
    }

    public static void saveBookmarks(ArrayList<Bookmark> bookmarks) {
        final JSONArray list = new JSONArray();
        try {
            for (Bookmark bookmark : bookmarks) {
                list.put(new JSONObject()
                    .put("chatId", bookmark.dialogId)
                    .put("messageId", bookmark.messageId)
                    .put("text", bookmark.text)
                    .put("addedAt", bookmark.addedAt));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        LumenConfig.setString(PREF_BOOKMARKS, list.toString());
    }

    public static boolean isBookmarked(long dialogId, int messageId) {
        for (Bookmark bookmark : bookmarks()) {
            if (bookmark.dialogId == dialogId && bookmark.messageId == messageId) {
                return true;
            }
        }
        return false;
    }

    private static String preview(MessageObject message) {
        if (!TextUtils.isEmpty(textOf(message))) {
            final String text = textOf(message).toString().replace('\n', ' ').trim();
            return text.length() > 120 ? text.substring(0, 120) + "…" : text;
        }
        if (message.isPhoto()) {
            return t("MediaPhoto");
        } else if (message.isVideo()) {
            return t("MediaVideo");
        } else if (message.isVoice()) {
            return t("MediaVoice");
        } else if (message.isSticker() || message.isAnimatedSticker()) {
            return t("MediaSticker");
        } else if (message.getDocument() != null) {
            return t("MediaFile");
        }
        return t("MediaOther");
    }

    private static void toggleBookmark(ChatActivity chat, MessageObject message) {
        final ArrayList<Bookmark> list = bookmarks();
        for (int i = 0; i < list.size(); i++) {
            final Bookmark bookmark = list.get(i);
            if (bookmark.dialogId == message.getDialogId() && bookmark.messageId == message.getId()) {
                list.remove(i);
                saveBookmarks(list);
                LumenDialogs.toast(chat, t("BookmarkRemoved"));
                return;
            }
        }
        final Bookmark bookmark = new Bookmark();
        bookmark.dialogId = message.getDialogId();
        bookmark.messageId = message.getId();
        bookmark.text = preview(message);
        bookmark.addedAt = System.currentTimeMillis();
        list.add(0, bookmark);
        while (list.size() > MAX_BOOKMARKS) {
            list.remove(list.size() - 1);
        }
        saveBookmarks(list);
        LumenDialogs.toast(chat, t("BookmarkAdded"));
    }

    // Draws the message cell on the chat background color and saves it to the gallery.
    private static void screenshot(ChatActivity chat, MessageObject message) {
        final RecyclerListView list = chat.getChatListView();
        ChatMessageCell cell = null;
        for (int i = 0; list != null && i < list.getChildCount(); i++) {
            final View child = list.getChildAt(i);
            if (child instanceof ChatMessageCell && ((ChatMessageCell) child).getMessageObject() != null
                && ((ChatMessageCell) child).getMessageObject().getId() == message.getId()) {
                cell = (ChatMessageCell) child;
                break;
            }
        }
        if (cell == null || cell.getWidth() <= 0 || cell.getHeight() <= 0) {
            LumenDialogs.error(chat, null);
            return;
        }
        final int padding = AndroidUtilities.dp(12);
        final Bitmap bitmap;
        try {
            bitmap = Bitmap.createBitmap(cell.getWidth() + padding * 2, cell.getHeight() + padding * 2, Bitmap.Config.ARGB_8888);
        } catch (Throwable e) {
            FileLog.e(e);
            LumenDialogs.error(chat, null);
            return;
        }
        final Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundGray, chat.getResourceProvider()));
        canvas.translate(padding, padding);
        cell.draw(canvas);
        Utilities.globalQueue.postRunnable(() -> {
            try {
                saveToGallery(bitmap, "LumenGram_" + System.currentTimeMillis() + ".png");
                AndroidUtilities.runOnUIThread(() -> LumenDialogs.toast(chat, t("ScreenshotSavedOnly")));
            } catch (Exception e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> LumenDialogs.error(chat, null));
            } finally {
                bitmap.recycle();
            }
        });
    }

    // Pictures/LumenGram through MediaStore; Telegram's saveFile refuses files from the app's own folders.
    private static void saveToGallery(Bitmap bitmap, String name) throws Exception {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            final android.content.ContentResolver resolver = ApplicationLoader.applicationContext.getContentResolver();
            final android.content.ContentValues values = new android.content.ContentValues();
            values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name);
            values.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/LumenGram");
            final android.net.Uri uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new Exception("MediaStore insert failed");
            }
            try (java.io.OutputStream out = resolver.openOutputStream(uri)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
        } else {
            final File dir = new File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "LumenGram");
            dir.mkdirs();
            final File file = new File(dir, name);
            try (FileOutputStream out = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            AndroidUtilities.addMediaToGallery(file.getAbsolutePath());
        }
    }

    private static void readAloud(MessageObject message) {
        if (tts != null && tts.isSpeaking()) {
            tts.stop();
            return;
        }
        final CharSequence messageText = textOf(message);
        if (TextUtils.isEmpty(messageText)) {
            return;
        }
        final String text = messageText.toString();
        if (tts == null) {
            pendingSpeech = text;
            tts = new TextToSpeech(ApplicationLoader.applicationContext, status -> {
                ttsReady = status == TextToSpeech.SUCCESS;
                if (ttsReady && pendingSpeech != null) {
                    speak(pendingSpeech);
                    pendingSpeech = null;
                }
            });
        } else if (ttsReady) {
            speak(text);
        }
    }

    private static void speak(String text) {
        final Locale locale = LumenLang.isRussian() ? new Locale("ru") : Locale.getDefault();
        try {
            tts.setLanguage(locale);
        } catch (Exception ignore) {
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "lumen");
    }
}
