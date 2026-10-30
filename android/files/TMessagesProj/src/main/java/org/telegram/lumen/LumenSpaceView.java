/*
 * This file is part of LumenGram, a modification of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */
package org.telegram.lumen;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

// The space background of the login screens: drifting aurora spots in the theme colors and,
// in dark themes, slowly moving twinkling stars. A port of the web version's AuthSpaceBackground.
public class LumenSpaceView extends View {

    private static final long FRAME_INTERVAL_MS = 50;
    private static final float DRIFT_PERIOD_MS = 30000f;
    private static final float FULL_TURN = (float) (Math.PI * 2);
    private static final float AURORA_RADIUS = 0.45f;
    private static final float AURORA_DARK_ALPHA = 0.32f;
    private static final float AURORA_LIGHT_ALPHA = 0.18f;
    private static final int STARS_COUNT = 140;
    private static final long STARS_SEED = 20261020L;
    private static final float STAR_MIN_SIZE = 0.6f;
    private static final float STAR_MAX_SIZE = 1.8f;
    private static final float STAR_ALPHA = 0.85f;
    private static final float STAR_MIN_ALPHA = 0.35f;
    private static final float STARS_DRIFT = 0.035f;

    // x, y, driftX, driftY, speedX, speedY, shift, alpha
    private static final float[][] SPOTS = {
        {0.22f, 0.28f, 0.16f, 0.14f, 1, 1, 0f, 1f},
        {0.78f, 0.36f, 0.14f, 0.18f, 1, 2, 2.1f, 0.9f},
        {0.5f, 0.82f, 0.22f, 0.1f, 2, 1, 4.2f, 0.8f},
    };

    private static float[][] stars;

    private final Paint spotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final long startedAt = SystemClock.elapsedRealtime();
    private boolean attached;

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            invalidate();
            if (attached) {
                AndroidUtilities.runOnUIThread(this, FRAME_INTERVAL_MS);
            }
        }
    };

    public LumenSpaceView(Context context) {
        super(context);
        starPaint.setColor(Color.WHITE);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        frame.run();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attached = false;
        AndroidUtilities.cancelRunOnUIThread(frame);
    }

    private static float[][] stars() {
        if (stars == null) {
            long seed = STARS_SEED;
            stars = new float[STARS_COUNT][];
            for (int i = 0; i < STARS_COUNT; i++) {
                final float[] values = new float[7];
                for (int j = 0; j < values.length; j++) {
                    seed = (seed * 1664525L + 1013904223L) % 4294967296L;
                    values[j] = seed / 4294967296f;
                }
                // x, y, size, alpha, twinkle speed, phase shift, depth
                final float depth = values[0];
                stars[i] = new float[] {
                    values[1],
                    values[2],
                    STAR_MIN_SIZE + (STAR_MAX_SIZE - STAR_MIN_SIZE) * depth,
                    STAR_MIN_ALPHA + (1 - STAR_MIN_ALPHA) * values[3],
                    1 + (int) (values[4] * 4),
                    values[5] * FULL_TURN,
                    depth,
                };
            }
        }
        return stars;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final int width = getWidth();
        final int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        final int background = Theme.getColor(Theme.key_windowBackgroundWhite);
        final boolean dark = ColorUtils.calculateLuminance(background) < 0.2f;
        final int[] colors = {
            Theme.getColor(Theme.key_featuredStickers_addButton),
            Theme.getColor(Theme.key_chats_actionBackground),
            Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4),
        };
        canvas.drawColor(background);

        final float elapsed = SystemClock.elapsedRealtime() - startedAt;
        final float phase = (elapsed % DRIFT_PERIOD_MS) / DRIFT_PERIOD_MS * FULL_TURN;
        final float radius = Math.max(width, height) * AURORA_RADIUS;
        final float alpha = dark ? AURORA_DARK_ALPHA : AURORA_LIGHT_ALPHA;
        for (int i = 0; i < SPOTS.length; i++) {
            final float[] spot = SPOTS[i];
            final float x = width * (spot[0] + spot[2] * (float) Math.sin(spot[4] * phase + spot[6]));
            final float y = height * (spot[1] + spot[3] * (float) Math.cos(spot[5] * phase + spot[6]));
            final int color = colors[i];
            spotPaint.setShader(new RadialGradient(x, y, radius,
                ColorUtils.setAlphaComponent(color, (int) (255 * alpha * spot[7])),
                ColorUtils.setAlphaComponent(color, 0), Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, width, height, spotPaint);
        }

        if (!dark) {
            return;
        }
        final float drift = elapsed / DRIFT_PERIOD_MS;
        final float density = AndroidUtilities.density;
        for (float[] star : stars()) {
            final float twinkle = 0.6f + 0.4f * (float) Math.sin(star[4] * phase + star[5]);
            float x = star[0] - STARS_DRIFT * star[6] * drift;
            x -= (float) Math.floor(x);
            starPaint.setAlpha((int) (255 * STAR_ALPHA * star[3] * twinkle));
            canvas.drawCircle(x * width, star[1] * height, star[2] * density, starPaint);
        }
    }
}
