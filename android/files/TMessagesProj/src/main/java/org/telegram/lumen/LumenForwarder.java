/*
 * This file is part of LumenGram, a modification of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * Re-upload forwarding is based on the AyuForward approach from AyuGram4A.
 */
package org.telegram.lumen;

import android.text.TextUtils;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;

/** Re-sends protected content as a new upload instead of using messages.forwardMessages. */
public final class LumenForwarder {
    private static volatile boolean dispatching;

    private LumenForwarder() {
    }

    public static boolean needsReupload(int account, ArrayList<MessageObject> messages) {
        if (!LumenConfig.ignoreContentRestrictions() || messages == null || messages.isEmpty()) {
            return false;
        }
        for (MessageObject message : messages) {
            if (message != null && message.messageOwner != null && message.messageOwner.noforwards) {
                return true;
            }
            if (message != null) {
                long dialogId = message.getDialogId();
                if (dialogId < 0) {
                    TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-dialogId);
                    if (chat != null && chat.noforwards) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean intercept(int account, ArrayList<MessageObject> messages, long peer,
                                    boolean hideCaption, boolean notify, int scheduleDate,
                                    int scheduleRepeatPeriod, MessageObject replyToTopMsg,
                                    long payStars, long monoForumPeerId) {
        if (dispatching || !needsReupload(account, messages)) {
            return false;
        }
        final ArrayList<MessageObject> copy = new ArrayList<>(messages);
        new Thread(() -> resend(account, copy, peer, hideCaption, notify, scheduleDate,
            scheduleRepeatPeriod, replyToTopMsg, payStars, monoForumPeerId), "Lumen protected forward").start();
        return true;
    }

    private static void resend(int account, ArrayList<MessageObject> messages, long peer,
                               boolean hideCaption, boolean notify, int scheduleDate,
                               int scheduleRepeatPeriod, MessageObject replyToTopMsg,
                               long payStars, long monoForumPeerId) {
        for (MessageObject message : messages) {
            try {
                resendOne(account, message, peer, hideCaption, notify, scheduleDate,
                    scheduleRepeatPeriod, replyToTopMsg, payStars, monoForumPeerId);
            } catch (Throwable error) {
                FileLog.e(error);
            }
        }
    }

    private static void resendOne(int account, MessageObject message, long peer,
                                  boolean hideCaption, boolean notify, int scheduleDate,
                                  int scheduleRepeatPeriod, MessageObject replyToTopMsg,
                                  long payStars, long monoForumPeerId) throws InterruptedException {
        if (message == null || message.messageOwner == null) {
            return;
        }
        final String text = hideCaption ? "" : (message.messageOwner.message == null ? "" : message.messageOwner.message);
        final ArrayList<TLRPC.MessageEntity> entities = hideCaption ? null : message.messageOwner.entities;
        final TLRPC.Document sourceDocument = message.getDocument();
        final TLRPC.Photo sourcePhoto = MessageObject.getPhoto(message.messageOwner);

        if (sourceDocument == null && sourcePhoto == null) {
            if (!TextUtils.isEmpty(text)) {
                AndroidUtilities.runOnUIThread(() -> sendText(account, text, entities, peer, notify,
                    scheduleDate, scheduleRepeatPeriod, replyToTopMsg, payStars, monoForumPeerId));
            }
            return;
        }

        final File path = FileLoader.getInstance(account).getPathToMessage(message.messageOwner);
        if (!path.exists() || path.length() == 0) {
            AndroidUtilities.runOnUIThread(() -> {
                if (sourceDocument != null) {
                    FileLoader.getInstance(account).loadFile(sourceDocument, message, FileLoader.PRIORITY_HIGH, 0);
                } else {
                    TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(sourcePhoto.sizes, AndroidUtilities.getPhotoSize());
                    FileLoader.getInstance(account).loadFile(ImageLocation.getForPhoto(size, sourcePhoto), message, null, FileLoader.PRIORITY_HIGH, 0);
                }
            });
            // FileLoader has no blocking API. Polling here is safe because this runs on our worker thread.
            for (int i = 0; i < 2400 && (!path.exists() || path.length() == 0); i++) {
                Thread.sleep(250);
            }
        }
        if (!path.exists() || path.length() == 0) {
            FileLog.e("Lumen protected forward: media download failed: " + path);
            return;
        }

        AndroidUtilities.runOnUIThread(() -> {
            SendMessagesHelper helper = SendMessagesHelper.getInstance(account);
            SendMessagesHelper.SendMessageParams params;
            if (sourceDocument != null) {
                TLRPC.TL_document document = new TLRPC.TL_document();
                document.file_reference = new byte[0];
                document.dc_id = Integer.MIN_VALUE;
                document.date = AccountInstance.getInstance(account).getConnectionsManager().getCurrentTime();
                document.mime_type = sourceDocument.mime_type;
                document.size = path.length();
                document.attributes = sourceDocument.attributes;
                document.file_name = sourceDocument.file_name;
                document.file_name_fixed = sourceDocument.file_name_fixed;
                document.localPath = path.getAbsolutePath();
                params = SendMessagesHelper.SendMessageParams.of(document, null, path.getAbsolutePath(), peer,
                    null, replyToTopMsg, text, entities, null, null, notify, scheduleDate,
                    scheduleRepeatPeriod, 0, message, null, false, message.hasMediaSpoilers());
            } else {
                TLRPC.TL_photo photo = helper.generatePhotoSizes(path.getAbsolutePath(), null);
                if (photo == null) {
                    return;
                }
                params = SendMessagesHelper.SendMessageParams.of(photo, path.getAbsolutePath(), peer,
                    null, replyToTopMsg, text, entities, null, null, notify, scheduleDate,
                    scheduleRepeatPeriod, 0, message, false, message.hasMediaSpoilers());
            }
            params.payStars = payStars;
            params.monoForumPeer = monoForumPeerId;
            dispatching = true;
            try {
                helper.sendMessage(params);
            } finally {
                dispatching = false;
            }
        });
    }

    private static void sendText(int account, String text, ArrayList<TLRPC.MessageEntity> entities,
                                 long peer, boolean notify, int scheduleDate, int scheduleRepeatPeriod,
                                 MessageObject replyToTopMsg, long payStars, long monoForumPeerId) {
        SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(text, peer,
            null, replyToTopMsg, null, true, entities, null, null, notify, scheduleDate,
            scheduleRepeatPeriod, null, false);
        params.payStars = payStars;
        params.monoForumPeer = monoForumPeerId;
        dispatching = true;
        try {
            SendMessagesHelper.getInstance(account).sendMessage(params);
        } finally {
            dispatching = false;
        }
    }
}
