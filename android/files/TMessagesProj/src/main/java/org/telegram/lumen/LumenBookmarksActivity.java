/*
 * This file is part of LumenGram, a modification of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */
package org.telegram.lumen;

import android.os.Bundle;
import android.view.View;

import org.telegram.messenger.DialogObject;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

public class LumenBookmarksActivity extends UniversalFragment {

    private static final int FIRST = 1000;

    private ArrayList<LumenMessageTools.Bookmark> bookmarks = new ArrayList<>();

    @Override
    protected CharSequence getTitle() {
        return LumenLang.get("BookmarksTitle");
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listView != null) {
            listView.adapter.update(false);
        }
    }

    private String chatName(long dialogId) {
        if (DialogObject.isUserDialog(dialogId)) {
            final TLRPC.User user = getMessagesController().getUser(dialogId);
            return user != null ? UserObject.getUserName(user) : String.valueOf(dialogId);
        }
        final TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
        return chat != null ? chat.title : String.valueOf(dialogId);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        bookmarks = LumenMessageTools.bookmarks();
        if (bookmarks.isEmpty()) {
            items.add(UItem.asShadow(LumenLang.get("BookmarksEmpty")));
            return;
        }
        for (int i = 0; i < bookmarks.size(); i++) {
            final LumenMessageTools.Bookmark bookmark = bookmarks.get(i);
            items.add(UItem.asButton(FIRST + i, bookmark.text, chatName(bookmark.dialogId)));
        }
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        final int index = item.id - FIRST;
        if (index < 0 || index >= bookmarks.size()) {
            return;
        }
        final LumenMessageTools.Bookmark bookmark = bookmarks.get(index);
        final Bundle args = new Bundle();
        if (DialogObject.isUserDialog(bookmark.dialogId)) {
            args.putLong("user_id", bookmark.dialogId);
        } else {
            args.putLong("chat_id", -bookmark.dialogId);
        }
        args.putInt("message_id", bookmark.messageId);
        presentFragment(new ChatActivity(args));
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        final int index = item.id - FIRST;
        if (index < 0 || index >= bookmarks.size()) {
            return false;
        }
        LumenDialogs.confirm(this, LumenLang.get("BookmarksTitle"), LumenLang.get("BookmarkRemove") + "?",
            LumenLang.get("Remove"), true, () -> {
                final ArrayList<LumenMessageTools.Bookmark> list = LumenMessageTools.bookmarks();
                if (index < list.size()) {
                    list.remove(index);
                    LumenMessageTools.saveBookmarks(list);
                }
                listView.adapter.update(true);
            });
        return true;
    }
}
