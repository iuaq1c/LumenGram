/*
 * This file is part of LumenGram, a modification of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */
package org.telegram.lumen;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;

// Small helpers for the interface tweaks, read by the hooks in Telegram's views.
public final class LumenUi {

    private LumenUi() {
    }

    // Redraws chat lists, chats and profiles after a LumenGram setting changed.
    public static void settingsChanged() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL);
                NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.dialogsNeedReload);
            }
        }
    }

    public static boolean showSeconds() {
        return LumenConfig.getBool(LumenConfig.SHOW_SECONDS);
    }

    public static boolean hideStories() {
        return LumenConfig.getBool(LumenConfig.HIDE_STORIES);
    }

    public static boolean hideShareButton() {
        return LumenConfig.getBool(LumenConfig.HIDE_SHARE_BUTTON);
    }

    public static boolean hideFolderCounters() {
        return LumenConfig.getBool(LumenConfig.HIDE_FOLDER_COUNTERS);
    }

    public static boolean streamerMode() {
        return LumenConfig.getBool(LumenConfig.STREAMER_MODE);
    }

    private static final android.graphics.Paint hiddenPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private static final android.graphics.RectF hiddenRect = new android.graphics.RectF();

    // Streamer mode: a soft placeholder bar instead of the message preview in the chat list.
    public static void drawHiddenPreview(android.graphics.Canvas canvas, float left, float top, int width, org.telegram.ui.ActionBar.Theme.ResourcesProvider resourcesProvider) {
        final int color = org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_chats_message, resourcesProvider);
        hiddenPaint.setColor(org.telegram.ui.ActionBar.Theme.multAlpha(color, 0.22f));
        final float barWidth = Math.min(width, AndroidUtilities.dp(170));
        hiddenRect.set(left, top + AndroidUtilities.dp(4), left + barWidth, top + AndroidUtilities.dp(14));
        canvas.drawRoundRect(hiddenRect, AndroidUtilities.dp(5), AndroidUtilities.dp(5), hiddenPaint);
    }

    // Formats a phone number, or hides it while streamer mode is on.
    public static String phone(String phone) {
        if (streamerMode()) {
            return "+•• ••• ••• •• ••";
        }
        return org.telegram.PhoneFormat.PhoneFormat.getInstance().format("+" + phone);
    }

    // Blur media: photos and videos get Telegram's own spoiler cover until tapped.
    public static boolean blurMedia(org.telegram.messenger.MessageObject message) {
        if (!LumenConfig.getBool(LumenConfig.BLUR_MEDIA) || message.messageOwner == null || message.messageOwner.media == null) {
            return false;
        }
        return message.messageOwner.media instanceof org.telegram.tgnet.TLRPC.TL_messageMediaPhoto
            || message.isVideo() || message.isGif();
    }

    public static boolean showPeerId() {
        return LumenConfig.getBool(LumenConfig.SHOW_PEER_ID);
    }

    // For a channel or supergroup the user is in, Telegram sends the join date in chat.date
    // (for anyone else it is the creation date), so no extra request is needed.
    public static int joinDate(org.telegram.tgnet.TLRPC.Chat chat) {
        if (chat == null || !org.telegram.messenger.ChatObject.isChannel(chat) || chat.left || chat.min) {
            return 0;
        }
        return chat.date;
    }

    public static String formatJoinDate(int date) {
        final java.text.SimpleDateFormat format = new java.text.SimpleDateFormat(
            "d MMMM yyyy", org.telegram.messenger.LocaleController.getInstance().getCurrentLocale());
        return format.format(new java.util.Date(date * 1000L));
    }

    // Avatar corner radius: a rounded square instead of a circle when the option is on.
    public static int avatarRadius(int sizeDp) {
        return LumenConfig.getBool(LumenConfig.SQUARE_AVATARS)
            ? AndroidUtilities.dp(Math.max(6, sizeDp * 0.3f))
            : AndroidUtilities.dp(sizeDp / 2f);
    }

    public static boolean squareAvatars() {
        return LumenConfig.getBool(LumenConfig.SQUARE_AVATARS);
    }
}
