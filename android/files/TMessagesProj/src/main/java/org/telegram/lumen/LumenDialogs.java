/*
 * This file is part of LumenGram, a modification of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */
package org.telegram.lumen;

import android.content.Context;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

public final class LumenDialogs {

    private LumenDialogs() {
    }

    public static void toast(BaseFragment fragment, CharSequence text) {
        if (fragment != null && fragment.getParentActivity() != null) {
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.info, text).show();
        }
    }

    public static void error(BaseFragment fragment, String error) {
        toast(fragment, error == null || error.isEmpty() ? LocaleController.getString(R.string.ErrorOccurred) : error);
    }

    public static void confirm(BaseFragment fragment, String title, String text, String button, boolean red, Runnable onConfirm) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider());
        builder.setTitle(title);
        builder.setMessage(text);
        builder.setPositiveButton(button, (dialog, which) -> onConfirm.run());
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        final AlertDialog dialog = builder.create();
        fragment.showDialog(dialog);
        if (red) {
            final TextView positive = (TextView) dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (positive != null) {
                positive.setTextColor(Theme.getColor(Theme.key_text_RedBold));
            }
        }
    }

    // A one-field text dialog: empty input is passed on as an empty string.
    public static void input(BaseFragment fragment, String title, String about, String hint, String value, boolean multiline, Utilities.Callback<String> onDone) {
        final Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(context, fragment.getResourceProvider());
        builder.setTitle(title);
        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        if (about != null) {
            final TextView aboutView = new TextView(context);
            aboutView.setText(about);
            aboutView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            aboutView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, fragment.getResourceProvider()));
            layout.addView(aboutView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 4, 24, 4));
        }
        final EditTextBoldCursor edit = new EditTextBoldCursor(context);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        edit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, fragment.getResourceProvider()));
        edit.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint, fragment.getResourceProvider()));
        edit.setHint(hint);
        edit.setBackground(null);
        edit.setLineColors(
            Theme.getColor(Theme.key_dialogInputField, fragment.getResourceProvider()),
            Theme.getColor(Theme.key_dialogInputFieldActivated, fragment.getResourceProvider()),
            Theme.getColor(Theme.key_text_RedBold, fragment.getResourceProvider()));
        edit.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack, fragment.getResourceProvider()));
        edit.setCursorWidth(1.5f);
        edit.setCursorSize(AndroidUtilities.dp(20));
        edit.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        edit.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        if (multiline) {
            edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            edit.setMaxLines(6);
        } else {
            edit.setInputType(InputType.TYPE_CLASS_TEXT);
            edit.setSingleLine(true);
        }
        if (value != null) {
            edit.setText(value);
            edit.setSelection(edit.length());
        }
        layout.addView(edit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 6, 24, 0));
        final FrameLayout container = new FrameLayout(context);
        container.addView(layout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        builder.setView(container);
        builder.setPositiveButton(LumenLang.get("Save"), (dialog, which) -> onDone.run(edit.getText().toString().trim()));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            edit.requestFocus();
            AndroidUtilities.showKeyboard(edit);
        });
        fragment.showDialog(dialog);
    }
}
