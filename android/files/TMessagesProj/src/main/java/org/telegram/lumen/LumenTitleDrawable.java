/*
 * This file is part of LumenGram, a modification of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */
package org.telegram.lumen;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;

import org.telegram.messenger.AndroidUtilities;

// The app name drawn as text in place of Telegram's logo picture above the chat list.
// It is white, so the logo view's MULTIPLY color filter paints it in the header text color.
public class LumenTitleDrawable extends Drawable {

    private static final int TEXT_SIZE_DP = 20;
    private static final int HEIGHT_DP = 22;

    private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final String text;

    public LumenTitleDrawable(String text) {
        this.text = text;
        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(AndroidUtilities.dp(TEXT_SIZE_DP));
        paint.setTypeface(AndroidUtilities.bold());
    }

    @Override
    public void draw(Canvas canvas) {
        final android.graphics.Rect bounds = getBounds();
        final Paint.FontMetrics metrics = paint.getFontMetrics();
        final float baseline = bounds.top + (bounds.height() - (metrics.descent - metrics.ascent)) / 2f - metrics.ascent;
        canvas.drawText(text, bounds.left, baseline, paint);
    }

    @Override
    public int getIntrinsicWidth() {
        return (int) Math.ceil(paint.measureText(text));
    }

    @Override
    public int getIntrinsicHeight() {
        return AndroidUtilities.dp(HEIGHT_DP);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
