/*
 * This file is part of LumenGram, a modification of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */
package org.telegram.lumen;

import android.os.Bundle;
import android.view.View;

import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

// Search through the text of transcribed voice and round video messages.
public class LumenVoiceSearchActivity extends UniversalFragment {

    private static final int SEARCH = 1;
    private static final int FIRST = 1000;

    private String query = "";
    private ArrayList<LumenVoiceIndex.Entry> results = new ArrayList<>();

    @Override
    protected CharSequence getTitle() {
        return LumenLang.get("VoiceSearchTitle");
    }

    @Override
    public boolean onFragmentCreate() {
        results = LumenVoiceIndex.search(currentAccount, query);
        return super.onFragmentCreate();
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
        items.add(UItem.asButton(SEARCH, R.drawable.msg_search,
            query.isEmpty() ? LumenLang.get("VoiceSearchAction") : "«" + query + "»").accent());
        items.add(UItem.asShadow(LumenLang.get("VoiceSearchAbout")));
        if (results.isEmpty()) {
            items.add(UItem.asShadow(LumenLang.get(query.isEmpty() ? "VoiceSearchEmpty" : "VoiceSearchNothing")));
            return;
        }
        for (int i = 0; i < results.size(); i++) {
            final LumenVoiceIndex.Entry entry = results.get(i);
            final String when = LocaleController.formatDateChat(entry.date);
            items.add(UItem.asButton(FIRST + i, entry.text, chatName(entry.dialogId) + " · " + when));
        }
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == SEARCH) {
            LumenDialogs.input(this, LumenLang.get("VoiceSearchTitle"), null, LumenLang.get("VoiceSearchHint"), query, false, text -> {
                query = text;
                results = LumenVoiceIndex.search(currentAccount, query);
                listView.adapter.update(true);
            });
            return;
        }
        final int index = item.id - FIRST;
        if (index < 0 || index >= results.size()) {
            return;
        }
        final LumenVoiceIndex.Entry entry = results.get(index);
        final Bundle args = new Bundle();
        if (DialogObject.isUserDialog(entry.dialogId)) {
            args.putLong("user_id", entry.dialogId);
        } else {
            args.putLong("chat_id", -entry.dialogId);
        }
        args.putInt("message_id", entry.messageId);
        presentFragment(new ChatActivity(args));
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
