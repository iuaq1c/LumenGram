/*
 * This file is part of LumenGram, a modification of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */
package org.telegram.lumen;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ReplacementSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.TextStyleSpan;

import java.util.ArrayList;

// Posts from every channel the user follows in one list, newest first, like the web feed.
// messages.searchGlobal with an empty query and broadcasts_only returns exactly that.
public class LumenFeedActivity extends BaseFragment {

    private static final int PAGE_SIZE = 30;
    private static final int FEED_DAYS = 30;
    private static final int LOAD_AHEAD = 5;
    private static final int MENU_REFRESH = 1;
    private static final int VIEW_POST = 0;
    private static final int VIEW_FOOTER = 1;

    private final ArrayList<MessageObject> posts = new ArrayList<>();
    private int nextRate;
    private TLRPC.InputPeer nextPeer;
    private int nextId;
    private boolean loading;
    private boolean endReached;
    private int requestId;
    private long lastGroupId;
    private int generation;

    private RecyclerListView listView;
    private LinearLayoutManager layoutManager;
    private Adapter adapter;

    private static String t(String key) {
        return LumenLang.get(key);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(t("FeedTitle"));
        actionBar.createMenu().addItem(MENU_REFRESH, R.drawable.msg_retry).setContentDescription(t("FeedRefresh"));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_REFRESH) {
                    reload();
                }
            }
        });

        final FrameLayout contentView = new FrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context));
        listView.setAdapter(adapter = new Adapter());
        listView.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));
        listView.setClipToPadding(false);
        listView.setOnItemClickListener((view, position) -> {
            if (position >= 0 && position < posts.size()) {
                openPost(posts.get(position));
            }
        });
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (layoutManager.findLastVisibleItemPosition() >= posts.size() - LOAD_AHEAD) {
                    load();
                }
            }
        });
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        load();
        return fragmentView = contentView;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        cancelRequest();
    }

    private void cancelRequest() {
        if (requestId != 0) {
            getConnectionsManager().cancelRequest(requestId, true);
            requestId = 0;
        }
    }

    private void reload() {
        cancelRequest();
        generation++;
        posts.clear();
        nextRate = 0;
        nextPeer = null;
        nextId = 0;
        lastGroupId = 0;
        loading = false;
        endReached = false;
        adapter.notifyDataSetChanged();
        load();
    }

    private void load() {
        if (loading || endReached) {
            return;
        }
        loading = true;
        final int now = getConnectionsManager().getCurrentTime();
        final TLRPC.TL_messages_searchGlobal req = new TLRPC.TL_messages_searchGlobal();
        req.broadcasts_only = true;
        req.q = "";
        req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
        req.min_date = now - FEED_DAYS * 24 * 60 * 60;
        req.max_date = now + 60;
        req.offset_rate = nextRate;
        req.offset_peer = nextPeer != null ? nextPeer : new TLRPC.TL_inputPeerEmpty();
        req.offset_id = nextId;
        req.limit = PAGE_SIZE;
        final int requestGeneration = generation;
        requestId = getConnectionsManager().sendRequest(req, (response, error) -> {
            final ArrayList<MessageObject> parsed = new ArrayList<>();
            if (response instanceof TLRPC.messages_Messages) {
                final TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                for (TLRPC.Message message : res.messages) {
                    if (message instanceof TLRPC.TL_messageService || !(message.peer_id instanceof TLRPC.TL_peerChannel)) {
                        continue;
                    }
                    parsed.add(new MessageObject(currentAccount, message, false, true));
                }
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (requestGeneration != generation) {
                    return;
                }
                requestId = 0;
                loading = false;
                if (!(response instanceof TLRPC.messages_Messages)) {
                    endReached = true;
                    adapter.notifyDataSetChanged();
                    return;
                }
                final TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                getMessagesController().putUsers(res.users, false);
                getMessagesController().putChats(res.chats, false);
                if (res.messages.isEmpty()) {
                    endReached = true;
                } else {
                    final TLRPC.Message last = res.messages.get(res.messages.size() - 1);
                    nextRate = res.next_rate;
                    nextPeer = getMessagesController().getInputPeer(MessageObject.getDialogId(last));
                    nextId = last.id;
                    endReached = res.messages.size() < PAGE_SIZE || !(res instanceof TLRPC.TL_messages_messagesSlice);
                }
                // An album comes as one message per photo: keep its first one, the one with the caption if any
                for (MessageObject post : parsed) {
                    final long groupId = post.messageOwner.grouped_id;
                    if (groupId != 0 && groupId == lastGroupId && !posts.isEmpty()) {
                        final MessageObject previous = posts.get(posts.size() - 1);
                        if (TextUtils.isEmpty(previous.caption) && !TextUtils.isEmpty(post.caption)) {
                            posts.set(posts.size() - 1, post);
                        }
                        continue;
                    }
                    lastGroupId = groupId;
                    posts.add(post);
                }
                adapter.notifyDataSetChanged();
            });
        }, ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    private void openPost(MessageObject post) {
        final Bundle args = new Bundle();
        args.putLong("chat_id", -post.getDialogId());
        args.putInt("message_id", post.getId());
        presentFragment(new ChatActivity(args));
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == VIEW_POST;
        }

        @Override
        public int getItemCount() {
            return posts.size() + 1;
        }

        @Override
        public int getItemViewType(int position) {
            return position < posts.size() ? VIEW_POST : VIEW_FOOTER;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            final View view;
            if (viewType == VIEW_POST) {
                view = new PostCell(parent.getContext());
            } else {
                final TextView footer = new TextView(parent.getContext());
                footer.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                footer.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                footer.setGravity(Gravity.CENTER);
                footer.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(20), AndroidUtilities.dp(24), AndroidUtilities.dp(28));
                view = footer;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == VIEW_POST) {
                ((PostCell) holder.itemView).set(posts.get(position));
            } else {
                final TextView footer = (TextView) holder.itemView;
                if (!endReached) {
                    footer.setText(LocaleController.getString(R.string.Loading));
                } else {
                    footer.setText(posts.isEmpty() ? t("FeedEmpty") : t("FeedEnd"));
                }
            }
        }
    }

    // Spoiler text is drawn by Telegram's own text layouts; a plain TextView would show it, so cover it.
    private static CharSequence hideSpoilers(CharSequence text) {
        if (!(text instanceof Spanned)) {
            return text;
        }
        final SpannableStringBuilder result = new SpannableStringBuilder(text);
        for (TextStyleSpan span : result.getSpans(0, result.length(), TextStyleSpan.class)) {
            if (!span.isSpoiler()) {
                continue;
            }
            final int start = result.getSpanStart(span);
            final int end = result.getSpanEnd(span);
            for (ReplacementSpan emoji : result.getSpans(start, end, ReplacementSpan.class)) {
                result.removeSpan(emoji);
            }
            for (int i = start; i < end; i++) {
                if (result.charAt(i) != '\n') {
                    result.replace(i, i + 1, "\u2592");
                }
            }
        }
        return result;
    }

    private class PostCell extends FrameLayout {

        private final AvatarDrawable avatarDrawable = new AvatarDrawable();
        private final BackupImageView avatarView;
        private final TextView nameView;
        private final TextView dateView;
        private final TextView textView;
        private final BackupImageView photoView;
        private final TextView viewsView;

        PostCell(Context context) {
            super(context);

            final LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(14), Theme.getColor(Theme.key_windowBackgroundWhite)));
            card.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(10));
            addView(card, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 8, 4, 8, 4));

            final LinearLayout header = new LinearLayout(context);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            card.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            avatarView = new BackupImageView(context);
            avatarView.setRoundRadius(AndroidUtilities.dp(18));
            header.addView(avatarView, LayoutHelper.createLinear(36, 36));

            final LinearLayout titles = new LinearLayout(context);
            titles.setOrientation(LinearLayout.VERTICAL);
            header.addView(titles, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 10, 0, 0, 0));

            nameView = new TextView(context);
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            nameView.setTypeface(AndroidUtilities.bold());
            nameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            nameView.setSingleLine(true);
            nameView.setEllipsize(TextUtils.TruncateAt.END);
            titles.addView(nameView);

            dateView = new TextView(context);
            dateView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            dateView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            titles.addView(dateView);

            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            textView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText));
            textView.setMaxLines(12);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setLineSpacing(AndroidUtilities.dp(2), 1f);
            card.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));

            photoView = new BackupImageView(context);
            photoView.setRoundRadius(AndroidUtilities.dp(10));
            card.addView(photoView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 200, 0, 10, 0, 0));

            viewsView = new TextView(context);
            viewsView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            viewsView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            viewsView.setCompoundDrawablePadding(AndroidUtilities.dp(4));
            viewsView.setGravity(Gravity.CENTER_VERTICAL);
            final Drawable eye = context.getResources().getDrawable(R.drawable.msg_views).mutate();
            eye.setColorFilter(new android.graphics.PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), android.graphics.PorterDuff.Mode.SRC_IN));
            eye.setBounds(0, 0, AndroidUtilities.dp(16), AndroidUtilities.dp(16));
            viewsView.setCompoundDrawables(eye, null, null, null);
            card.addView(viewsView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.END, 0, 8, 0, 0));
        }

        void set(MessageObject post) {
            final TLRPC.Chat chat = getMessagesController().getChat(-post.getDialogId());
            if (chat != null) {
                avatarDrawable.setInfo(currentAccount, chat);
                avatarView.setForUserOrChat(chat, avatarDrawable);
                nameView.setText(chat.title);
            } else {
                avatarView.setImageDrawable(avatarDrawable);
                nameView.setText(null);
            }
            dateView.setText(LocaleController.stringForMessageListDate(post.messageOwner.date));

            // Media posts show only their caption: messageText there is just "Photo" or "Album"
            final TLRPC.MessageMedia media = post.messageOwner.media;
            final boolean textPost = media == null
                || media instanceof TLRPC.TL_messageMediaEmpty
                || media instanceof TLRPC.TL_messageMediaWebPage;
            CharSequence text = post.caption;
            if (TextUtils.isEmpty(text) && textPost) {
                text = post.messageText;
            }
            textView.setText(hideSpoilers(text));
            textView.setVisibility(TextUtils.isEmpty(text) ? GONE : VISIBLE);

            final TLRPC.PhotoSize size = post.photoThumbs != null
                ? FileLoader.getClosestPhotoSizeWithSize(post.photoThumbs, AndroidUtilities.dp(360))
                : null;
            if (size != null && post.hasMediaSpoilers()) {
                // Spoilers and hidden 18+ media stay hidden here too: only the tiny preview, blurred
                final TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(post.photoThumbs, AndroidUtilities.dp(40));
                photoView.setVisibility(VISIBLE);
                photoView.setImage(ImageLocation.getForObject(thumb, post.photoThumbsObject), "360_200_b2", (Drawable) null, post);
            } else if (size != null) {
                final TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(post.photoThumbs, AndroidUtilities.dp(40));
                photoView.setVisibility(VISIBLE);
                photoView.setImage(
                    ImageLocation.getForObject(size, post.photoThumbsObject), "360_200",
                    ImageLocation.getForObject(thumb, post.photoThumbsObject), "360_200_b",
                    0, post);
            } else {
                photoView.setVisibility(GONE);
                photoView.clearImage();
            }

            final int views = post.messageOwner.views;
            viewsView.setText(views > 0 ? LocaleController.formatShortNumber(views, null) : "");
            viewsView.setVisibility(views > 0 ? VISIBLE : GONE);
        }
    }
}
