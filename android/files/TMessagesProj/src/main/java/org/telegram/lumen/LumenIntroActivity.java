/*
 * This file is part of LumenGram, a modification of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */
package org.telegram.lumen;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LoginActivity;

import static org.telegram.messenger.AndroidUtilities.dp;

// Welcome screen of LumenGram, replaces Telegram's intro with the paper plane: the space
// background, the logo, the app name with the "unofficial client" note and a start button.
public class LumenIntroActivity extends BaseFragment {

    private boolean startPressed;
    private boolean onLogout;
    private final AnimatorSet loops = new AnimatorSet();

    public LumenIntroActivity setOnLogout() {
        onLogout = true;
        return this;
    }

    @Override
    public View createView(Context context) {
        actionBar.setAddToContainer(false);

        final FrameLayout root = new FrameLayout(context);
        root.addView(new LumenSpaceView(context), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        final LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        // The logo floats a few dp up, it must not be clipped by the column.
        column.setClipChildren(false);
        column.setClipToPadding(false);
        root.setClipChildren(false);
        root.addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 24, 0, 24, 96));

        final ImageView logo = new ImageView(context);
        logo.setImageResource(R.drawable.lumen_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        column.addView(logo, LayoutHelper.createLinear(132, 132, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 28));

        final TextView title = new TextView(context);
        title.setText("LumenGram");
        title.setTypeface(AndroidUtilities.bold());
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 30);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setGravity(Gravity.CENTER);
        column.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 12));

        final TextView about = new TextView(context);
        about.setText(LumenLang.get("IntroAbout"));
        about.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        about.setLineSpacing(dp(2), 1f);
        about.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
        about.setGravity(Gravity.CENTER);
        column.addView(about, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        final TextView start = new TextView(context);
        start.setText(LumenLang.get("IntroStart"));
        start.setTypeface(AndroidUtilities.bold());
        start.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        start.setTextColor(0xFFFFFFFF);
        start.setGravity(Gravity.CENTER);
        final GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] {
            Theme.getColor(Theme.key_featuredStickers_addButton),
            Theme.getColor(Theme.key_chats_actionBackground),
        });
        background.setCornerRadius(dp(12));
        start.setBackground(background);
        root.addView(start, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.BOTTOM, 16, 0, 16, 48));
        start.setOnClickListener(v -> {
            if (startPressed) {
                return;
            }
            startPressed = true;
            presentFragment(new LoginActivity(), true);
        });

        animate(logo, title, about, start);
        return fragmentView = root;
    }

    // Entrance: the logo, the texts and the button rise in one after another; then the logo floats
    // and the button breathes, like the desktop and web login.
    private void animate(View logo, View title, View about, View start) {
        final View[] views = {logo, title, about, start};
        final AnimatorSet entrance = new AnimatorSet();
        for (int i = 0; i < views.length; i++) {
            final View view = views[i];
            view.setAlpha(0f);
            view.setTranslationY(dp(18));
            final AnimatorSet set = new AnimatorSet();
            set.playTogether(
                ObjectAnimator.ofFloat(view, View.ALPHA, 1f),
                ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0f));
            set.setStartDelay(120L * i);
            set.setDuration(520);
            set.setInterpolator(new DecelerateInterpolator(2f));
            entrance.playTogether(set);
        }
        entrance.start();

        final ObjectAnimator floating = ObjectAnimator.ofFloat(logo, View.TRANSLATION_Y, 0f, -dp(6), 0f);
        floating.setDuration(4200);
        floating.setStartDelay(900);
        floating.setRepeatCount(ValueAnimator.INFINITE);
        final ValueAnimator pulse = ValueAnimator.ofFloat(0f, 1f);
        pulse.setDuration(2400);
        pulse.setStartDelay(900);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.addUpdateListener(a -> {
            final float scale = 1f + 0.025f * (float) Math.sin(Math.PI * 2 * (float) a.getAnimatedValue());
            start.setScaleX(scale);
            start.setScaleY(scale);
        });
        loops.playTogether(floating, pulse);
        loops.start();
    }

    @Override
    public void onFragmentDestroy() {
        loops.cancel();
        super.onFragmentDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        startPressed = false;
    }

    @Override
    public AnimatorSet onCustomTransitionAnimation(boolean isOpen, Runnable callback) {
        if (onLogout) {
            final AnimatorSet set = new AnimatorSet().setDuration(50);
            set.playTogether(ValueAnimator.ofFloat());
            return set;
        }
        return null;
    }

    @Override
    public boolean isLightStatusBar() {
        return ColorUtils.calculateLuminance(Theme.getColor(Theme.key_windowBackgroundWhite)) > 0.7f;
    }
}
