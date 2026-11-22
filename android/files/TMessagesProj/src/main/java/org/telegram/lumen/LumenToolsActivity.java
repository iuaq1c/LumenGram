/*
 * This file is part of LumenGram, a modification of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */
package org.telegram.lumen;

import android.app.TimePickerDialog;
import android.view.View;

import org.telegram.messenger.DialogObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class LumenToolsActivity extends UniversalFragment {

    private static final int UNDO_SEND = 1;
    private static final int TEMPLATE_ADD = 2;
    private static final int SUPPORT = 3;
    private static final int BOOKMARKS = 4;
    private static final int FOCUS = 5;
    private static final int QUIET = 6;
    private static final int QUIET_FROM = 7;
    private static final int QUIET_TO = 8;
    private static final int QUIET_PINNED = 9;
    private static final int LIMIT = 10;
    private static final int BLUR = 11;
    private static final int VOICE_SEARCH = 12;
    private static final int WRAPPED = 13;
    private static final int LISTENING = 14;
    private static final int TEMPLATE_FIRST = 1000;

    private static final int[] FOCUS_MINUTES = {25, 50, 90};
    private static final int[] LIMIT_MINUTES = {0, 30, 60, 120, 180};

    private final Runnable focusTick = new Runnable() {
        @Override
        public void run() {
            update();
            if (LumenFocus.focusActive()) {
                org.telegram.messenger.AndroidUtilities.runOnUIThread(this, 30_000);
            }
        }
    };

    @Override
    protected CharSequence getTitle() {
        return LumenLang.get("ToolsTitle");
    }

    private static String t(String key) {
        return LumenLang.get(key);
    }

    @Override
    public void onFragmentDestroy() {
        org.telegram.messenger.AndroidUtilities.cancelRunOnUIThread(focusTick);
        super.onFragmentDestroy();
    }

    private void update() {
        if (listView != null) {
            listView.adapter.update(true);
        }
    }

    private static String clock(int minutes) {
        return String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60);
    }

    private String chatName(long dialogId) {
        if (DialogObject.isUserDialog(dialogId)) {
            final TLRPC.User user = getMessagesController().getUser(dialogId);
            return user != null ? UserObject.getUserName(user) : String.valueOf(dialogId);
        }
        final TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
        return chat != null ? LumenChatMeta.chatTitle(chat) : String.valueOf(dialogId);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(t("ToolsTitle")));
        items.add(UItem.asCheck(UNDO_SEND, t("UndoSend")).setChecked(LumenConfig.getBool(LumenConfig.UNDO_SEND)));
        items.add(UItem.asButton(BOOKMARKS, R.drawable.msg_fave, t("BookmarksTitle"), String.valueOf(LumenMessageTools.bookmarks().size())));
        items.add(UItem.asButton(VOICE_SEARCH, R.drawable.msg_voice_unmuted, t("VoiceSearchTitle")));
        if (LumenConfig.prefs().getBoolean("isListeningEnabled", true)) {
            items.add(UItem.asButton(LISTENING, R.drawable.msg_link, t("ListenWebTitle")));
        }
        items.add(UItem.asButton(WRAPPED, R.drawable.msg_stats, LumenLang.format("WrappedTitle", "year", java.util.Calendar.getInstance().get(java.util.Calendar.YEAR))).accent());
        items.add(UItem.asShadow(t("UndoSendAbout")));

        items.add(UItem.asHeader(t("TemplatesTitle")));
        final ArrayList<LumenSend.Template> templates = LumenSend.templates();
        for (int i = 0; i < templates.size(); i++) {
            final LumenSend.Template template = templates.get(i);
            items.add(UItem.asButton(TEMPLATE_FIRST + i, ";" + template.key, template.text));
        }
        items.add(UItem.asButton(TEMPLATE_ADD, R.drawable.msg_add, t("TemplateAdd")).accent());
        items.add(UItem.asShadow(t("TemplatesAbout")));

        items.add(UItem.asHeader(t("FocusMode")));
        if (LumenFocus.focusActive()) {
            final long left = LumenFocus.focusLeftMs() / 1000;
            items.add(UItem.asButton(FOCUS, R.drawable.msg_mute, t("FocusStop"),
                LumenLang.format("FocusLeft", "time", LumenFocus.formatDuration(left))).red());
            org.telegram.messenger.AndroidUtilities.cancelRunOnUIThread(focusTick);
            org.telegram.messenger.AndroidUtilities.runOnUIThread(focusTick, 30_000);
        } else {
            items.add(UItem.asButton(FOCUS, R.drawable.msg_mute, t("FocusMode")).accent());
        }
        items.add(UItem.asShadow(t("FocusAbout")));

        items.add(UItem.asHeader(t("QuietTitle")));
        final boolean quiet = LumenConfig.getBool(LumenFocus.QUIET_ENABLED);
        items.add(UItem.asCheck(QUIET, t("QuietEnable")).setChecked(quiet));
        if (quiet) {
            items.add(UItem.asButton(QUIET_FROM, t("QuietFrom"), clock(LumenConfig.getInt(LumenFocus.QUIET_FROM, 23 * 60))));
            items.add(UItem.asButton(QUIET_TO, t("QuietTo"), clock(LumenConfig.getInt(LumenFocus.QUIET_TO, 8 * 60))));
            items.add(UItem.asCheck(QUIET_PINNED, t("QuietAllowPinned")).setChecked(LumenConfig.getBool(LumenFocus.QUIET_ALLOW_PINNED)));
        }
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(t("ScreenTimeTitle")));
        final HashMap<Long, Long> today = LumenFocus.screenTimeToday();
        if (today.isEmpty()) {
            items.add(UItem.asShadow(t("ScreenTimeEmpty")));
        } else {
            final ArrayList<Map.Entry<Long, Long>> list = new ArrayList<>(today.entrySet());
            Collections.sort(list, (a, b) -> Long.compare(b.getValue(), a.getValue()));
            items.add(UItem.asButton(2000, t("ScreenTimeTotal"), LumenFocus.formatDuration(LumenFocus.totalToday())));
            for (int i = 0; i < Math.min(5, list.size()); i++) {
                items.add(UItem.asButton(2001 + i, chatName(list.get(i).getKey()), LumenFocus.formatDuration(list.get(i).getValue())));
            }
        }
        final int limit = LumenConfig.getInt(LumenFocus.SCREEN_TIME_LIMIT, 0);
        items.add(UItem.asButton(LIMIT, t("ScreenTimeLimit"), limit == 0 ? t("LimitOff") : LumenFocus.formatDuration(limit * 60L)));
        items.add(UItem.asShadow(null));

        items.add(UItem.asCheck(BLUR, t("BlurMedia")).setChecked(LumenConfig.getBool(LumenConfig.BLUR_MEDIA)));
        items.add(UItem.asShadow(t("BlurMediaAbout")));

        items.add(UItem.asButton(SUPPORT, R.drawable.msg_input_like, t("SupportProject")));
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case UNDO_SEND:
                LumenConfig.setBool(LumenConfig.UNDO_SEND, !LumenConfig.getBool(LumenConfig.UNDO_SEND));
                update();
                return;
            case BOOKMARKS:
                presentFragment(new LumenBookmarksActivity());
                return;
            case VOICE_SEARCH:
                presentFragment(new LumenVoiceSearchActivity());
                return;
            case LISTENING:
                Browser.openUrl(getParentActivity(), "https://tunexa.ru/lumengram/listen");
                return;
            case WRAPPED:
                presentFragment(new LumenWrappedActivity());
                return;
            case TEMPLATE_ADD:
                editTemplate(-1);
                return;
            case FOCUS:
                if (LumenFocus.focusActive()) {
                    LumenFocus.stopFocus();
                    update();
                } else {
                    chooseFocus();
                }
                return;
            case QUIET:
                LumenConfig.setBool(LumenFocus.QUIET_ENABLED, !LumenConfig.getBool(LumenFocus.QUIET_ENABLED));
                update();
                return;
            case QUIET_FROM:
                pickTime(LumenFocus.QUIET_FROM, 23 * 60);
                return;
            case QUIET_TO:
                pickTime(LumenFocus.QUIET_TO, 8 * 60);
                return;
            case QUIET_PINNED:
                LumenConfig.setBool(LumenFocus.QUIET_ALLOW_PINNED, !LumenConfig.getBool(LumenFocus.QUIET_ALLOW_PINNED));
                update();
                return;
            case LIMIT:
                chooseLimit();
                return;
            case BLUR:
                LumenConfig.setBool(LumenConfig.BLUR_MEDIA, !LumenConfig.getBool(LumenConfig.BLUR_MEDIA));
                LumenUi.settingsChanged();
                update();
                return;
            case SUPPORT:
                Browser.openUrl(getParentActivity(), "https://tunexa.ru/lumengram/donate");
                return;
        }
        if (item.id >= TEMPLATE_FIRST && item.id < 2000) {
            editTemplate(item.id - TEMPLATE_FIRST);
        }
    }

    private void chooseFocus() {
        final String[] items = new String[FOCUS_MINUTES.length];
        for (int i = 0; i < FOCUS_MINUTES.length; i++) {
            items[i] = LumenLang.format("FocusStart", "count", FOCUS_MINUTES[i]);
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
        builder.setTitle(t("FocusMode"));
        builder.setItems(items, (dialog, which) -> {
            LumenFocus.startFocus(FOCUS_MINUTES[which]);
            update();
        });
        showDialog(builder.create());
    }

    private void chooseLimit() {
        final String[] items = new String[LIMIT_MINUTES.length];
        for (int i = 0; i < LIMIT_MINUTES.length; i++) {
            items[i] = LIMIT_MINUTES[i] == 0 ? t("LimitOff") : LumenFocus.formatDuration(LIMIT_MINUTES[i] * 60L);
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
        builder.setTitle(t("ScreenTimeLimit"));
        builder.setItems(items, (dialog, which) -> {
            LumenConfig.setInt(LumenFocus.SCREEN_TIME_LIMIT, LIMIT_MINUTES[which]);
            update();
        });
        showDialog(builder.create());
    }

    private void pickTime(String pref, int def) {
        final int value = LumenConfig.getInt(pref, def);
        new TimePickerDialog(getParentActivity(), (picker, hour, minute) -> {
            LumenConfig.setInt(pref, hour * 60 + minute);
            update();
        }, value / 60, value % 60, true).show();
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (item.id < TEMPLATE_FIRST || item.id >= 2000) {
            return false;
        }
        final int index = item.id - TEMPLATE_FIRST;
        LumenDialogs.confirm(this, t("TemplatesTitle"), t("Remove") + "?", t("Remove"), true, () -> {
            final ArrayList<LumenSend.Template> templates = LumenSend.templates();
            if (index < templates.size()) {
                templates.remove(index);
                LumenSend.saveTemplates(templates);
                update();
            }
        });
        return true;
    }

    // Asks for the key first, then the text: two short dialogs are easier on a phone than one big form.
    private void editTemplate(int index) {
        final ArrayList<LumenSend.Template> templates = LumenSend.templates();
        final LumenSend.Template current = index >= 0 && index < templates.size() ? templates.get(index) : null;
        LumenDialogs.input(this, t("TemplateKey"), t("TemplatesAbout"), "hello", current != null ? current.key : null, false, rawKey -> {
            final String key = LumenSend.normalizeKey(rawKey);
            if (key.isEmpty()) {
                return;
            }
            LumenDialogs.input(this, t("TemplateText"), null, t("TemplateText"), current != null ? current.text : null, true, text -> {
                final ArrayList<LumenSend.Template> list = LumenSend.templates();
                if (current != null && index < list.size()) {
                    list.remove(index);
                }
                for (int i = list.size() - 1; i >= 0; i--) {
                    if (list.get(i).key.equals(key)) {
                        list.remove(i);
                    }
                }
                if (!text.isEmpty()) {
                    final LumenSend.Template template = new LumenSend.Template();
                    template.key = key;
                    template.text = text;
                    list.add(template);
                }
                LumenSend.saveTemplates(list);
                update();
            });
        });
    }
}
