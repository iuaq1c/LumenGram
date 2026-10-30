/*
 * This file is part of LumenGram, a modification of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */
package org.telegram.lumen;

import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;

// Opt-in ghost mode, the user accepted the risk warning before any of this is active.
public final class LumenGhost {

    private LumenGhost() {
    }

    public static boolean hideOnline() {
        return LumenConfig.ghost(LumenConfig.GHOST_HIDE_ONLINE);
    }

    public static boolean blockTyping() {
        return LumenConfig.ghost(LumenConfig.GHOST_NO_TYPING);
    }

    public static boolean blockRead() {
        return LumenConfig.ghost(LumenConfig.GHOST_NO_READ);
    }

    public static boolean blockStories() {
        return LumenConfig.ghost(LumenConfig.GHOST_NO_STORIES);
    }

    // Applies a hidden online status right away instead of waiting for the next status update.
    public static void onSettingsChanged() {
        if (!hideOnline()) {
            return;
        }
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                sendOffline(a);
            }
        }
    }

    // Sending a message shows you online, so go offline again right after it.
    public static void onMessageSent(int account, long dialogId) {
        if (hideOnline()) {
            sendOffline(account);
        }
        if (blockRead() && LumenConfig.ghost(LumenConfig.GHOST_READ_ON_INTERACT)) {
            markRead(account, dialogId);
        }
    }

    private static void sendOffline(int account) {
        final TL_account.updateStatus req = new TL_account.updateStatus();
        req.offline = true;
        ConnectionsManager.getInstance(account).sendRequest(req, null);
    }

    // Replying marks the chat as read for the sender, like in the web version.
    private static void markRead(int account, long dialogId) {
        if (dialogId == 0 || DialogObject.isEncryptedDialog(dialogId)) {
            return;
        }
        final MessagesController controller = MessagesController.getInstance(account);
        final TLRPC.Dialog dialog = controller.dialogs_dict.get(dialogId);
        if (dialog == null || dialog.top_message == 0) {
            return;
        }
        final TLRPC.InputPeer peer = controller.getInputPeer(dialogId);
        final TLObject req;
        if (peer instanceof TLRPC.TL_inputPeerChannel) {
            final TLRPC.TL_channels_readHistory request = new TLRPC.TL_channels_readHistory();
            request.channel = controller.getInputChannel(-dialogId);
            request.max_id = dialog.top_message;
            req = request;
        } else {
            final TLRPC.TL_messages_readHistory request = new TLRPC.TL_messages_readHistory();
            request.peer = peer;
            request.max_id = dialog.top_message;
            req = request;
        }
        ConnectionsManager.getInstance(account).sendRequest(req, null);
    }
}
