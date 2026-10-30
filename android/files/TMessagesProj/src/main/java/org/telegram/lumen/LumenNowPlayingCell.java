/*
 * This file is part of LumenGram, a modification of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */
package org.telegram.lumen;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

import java.util.Locale;

import static org.telegram.messenger.AndroidUtilities.dp;

// "Now playing" row in a profile: cover, track, artist and, for Spotify, the progress of the track.
public class LumenNowPlayingCell extends View {

    private final Theme.ResourcesProvider resourcesProvider;
    private final ImageReceiver cover = new ImageReceiver(this);
    private final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint subtitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint timePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint placeholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Drawable musicIcon;

    private LumenProfiles.Track track;
    private boolean attached;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            invalidate();
            if (attached && track != null && track.duration > 0) {
                AndroidUtilities.runOnUIThread(this, 1000);
            }
        }
    };

    public LumenNowPlayingCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        cover.setRoundRadius(dp(8));
        titlePaint.setTextSize(dp(16));
        titlePaint.setTypeface(AndroidUtilities.bold());
        subtitlePaint.setTextSize(dp(13));
        timePaint.setTextSize(dp(12));
        timePaint.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MONO));
        musicIcon = context.getResources().getDrawable(R.drawable.files_music).mutate();
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
    }

    public void setTrack(LumenProfiles.Track track) {
        this.track = track;
        if (track != null && track.cover != null) {
            cover.setImage(track.cover, "96_96", null, "jpg", 0);
        } else {
            cover.clearImage();
        }
        AndroidUtilities.cancelRunOnUIThread(tick);
        if (attached) {
            tick.run();
        }
        invalidate();
    }

    public LumenProfiles.Track getTrack() {
        return track;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        cover.onAttachedToWindow();
        tick.run();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attached = false;
        cover.onDetachedFromWindow();
        AndroidUtilities.cancelRunOnUIThread(tick);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(76), MeasureSpec.EXACTLY));
    }

    private static String time(long seconds) {
        seconds = Math.max(0, seconds);
        return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (track == null) {
            return;
        }
        final int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
        final int grayColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider);
        final int accentColor = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
        titlePaint.setColor(textColor);
        subtitlePaint.setColor(grayColor);
        timePaint.setColor(grayColor);

        final int left = dp(21);
        final int size = dp(48);
        final int top = (getMeasuredHeight() - size) / 2;
        if (track.cover != null && cover.hasImageSet()) {
            cover.setImageCoords(left, top, size, size);
            cover.draw(canvas);
        } else {
            placeholderPaint.setColor(Theme.multAlpha(accentColor, 0.15f));
            rect.set(left, top, left + size, top + size);
            canvas.drawRoundRect(rect, dp(8), dp(8), placeholderPaint);
            musicIcon.setColorFilter(new android.graphics.PorterDuffColorFilter(accentColor, android.graphics.PorterDuff.Mode.SRC_IN));
            final int iconSize = dp(24);
            musicIcon.setBounds(left + (size - iconSize) / 2, top + (size - iconSize) / 2, left + (size + iconSize) / 2, top + (size + iconSize) / 2);
            musicIcon.draw(canvas);
        }

        final int textLeft = left + size + dp(14);
        final int textWidth = getMeasuredWidth() - textLeft - dp(21);
        final CharSequence title = TextUtils.ellipsize(track.title, titlePaint, textWidth, TextUtils.TruncateAt.END);
        final String provider = track.spotify ? "Spotify" : "Last.fm";
        final String subtitleText;
        if (track.duration > 0 || track.spotify) {
            subtitleText = TextUtils.isEmpty(track.artist) ? provider : track.artist + " · " + provider;
        } else {
            subtitleText = (TextUtils.isEmpty(track.artist) ? "" : track.artist + " · ")
                + LumenLang.format("NowPlayingVia", "provider", provider);
        }
        final CharSequence subtitle = TextUtils.ellipsize(subtitleText, subtitlePaint, textWidth, TextUtils.TruncateAt.END);

        if (track.duration > 0) {
            canvas.drawText(title, 0, title.length(), textLeft, top + dp(14), titlePaint);
            canvas.drawText(subtitle, 0, subtitle.length(), textLeft, top + dp(31), subtitlePaint);
            final long elapsed = Math.min(track.duration, Math.max(0, (System.currentTimeMillis() - track.startedAt) / 1000));
            final String times = time(elapsed) + " / " + time(track.duration);
            final float timesWidth = timePaint.measureText(times);
            final float barTop = top + dp(42);
            final float barRight = textLeft + textWidth - timesWidth - dp(10);
            trackPaint.setColor(Theme.multAlpha(grayColor, 0.25f));
            fillPaint.setColor(accentColor);
            rect.set(textLeft, barTop, barRight, barTop + dp(3));
            canvas.drawRoundRect(rect, dp(1.5f), dp(1.5f), trackPaint);
            rect.right = textLeft + (barRight - textLeft) * (elapsed / (float) track.duration);
            canvas.drawRoundRect(rect, dp(1.5f), dp(1.5f), fillPaint);
            canvas.drawText(times, textLeft + textWidth - timesWidth, barTop + dp(5), timePaint);
        } else {
            canvas.drawText(title, 0, title.length(), textLeft, top + dp(20), titlePaint);
            canvas.drawText(subtitle, 0, subtitle.length(), textLeft, top + dp(39), subtitlePaint);
        }
    }
}
