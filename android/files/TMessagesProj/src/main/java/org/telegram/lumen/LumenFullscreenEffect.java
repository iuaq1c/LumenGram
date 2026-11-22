/*
 * This file is part of LumenGram, a modification of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */
package org.telegram.lumen;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;

import java.util.ArrayList;
import java.util.Random;

// A message made of one of these emoji fills the screen with it for a moment, in chats opened in LumenGram.
public class LumenFullscreenEffect {

    public static final String ENABLED = "fullscreenEffects";

    private static final String[] TRIGGERS = {"❤️", "❤", "🔥", "🎉", "🎆", "🎊", "✨", "😂", "🥳", "💜", "👍", "😍", "🎄", "❄️"};
    private static final long DURATION_MS = 2600;
    private static final int PARTICLES = 34;

    public static boolean enabled() {
        return LumenConfig.prefs().getBoolean(ENABLED, true);
    }

    // The emoji when the message is exactly one trigger emoji, otherwise null
    public static String trigger(MessageObject message) {
        if (message == null || message.messageOwner == null || TextUtils.isEmpty(message.messageOwner.message)) {
            return null;
        }
        final String text = message.messageOwner.message.trim();
        for (String trigger : TRIGGERS) {
            if (text.equals(trigger)) {
                return trigger;
            }
        }
        return null;
    }

    public static void play(Activity activity, String emoji) {
        if (activity == null || emoji == null || !enabled()) {
            return;
        }
        final ViewGroup root = (ViewGroup) activity.getWindow().getDecorView();
        final EffectView view = new EffectView(activity, emoji);
        root.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        AndroidUtilities.runOnUIThread(() -> root.removeView(view), DURATION_MS + 100);
    }

    private static class Particle {
        float x;
        float y;
        float speedX;
        float speedY;
        float size;
        float rotation;
        float spin;
        long delay;
    }

    private static class EffectView extends View {

        private final String emoji;
        private final boolean burst;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ArrayList<Particle> particles = new ArrayList<>();
        private final long startedAt = SystemClock.elapsedRealtime();

        EffectView(Context context, String emoji) {
            super(context);
            this.emoji = emoji;
            // Fireworks and party emoji burst from the middle, the rest float up from the bottom
            burst = "🎆".equals(emoji) || "🎉".equals(emoji) || "🎊".equals(emoji) || "🥳".equals(emoji) || "✨".equals(emoji);
            setClickable(false);
        }

        private void seed(int width, int height) {
            final Random random = new Random();
            for (int i = 0; i < PARTICLES; i++) {
                final Particle particle = new Particle();
                particle.size = AndroidUtilities.dp(22 + random.nextInt(30));
                particle.rotation = random.nextFloat() * 40 - 20;
                particle.spin = random.nextFloat() * 120 - 60;
                if (burst) {
                    final double angle = random.nextDouble() * Math.PI * 2;
                    final float speed = AndroidUtilities.dp(160 + random.nextInt(320));
                    particle.x = width / 2f;
                    particle.y = height * 0.45f;
                    particle.speedX = (float) Math.cos(angle) * speed;
                    particle.speedY = (float) Math.sin(angle) * speed - AndroidUtilities.dp(200);
                    particle.delay = random.nextInt(250);
                } else {
                    particle.x = random.nextFloat() * width;
                    particle.y = height + particle.size;
                    particle.speedX = random.nextFloat() * AndroidUtilities.dp(60) - AndroidUtilities.dp(30);
                    particle.speedY = -AndroidUtilities.dp(380 + random.nextInt(320));
                    particle.delay = random.nextInt(900);
                }
                particles.add(particle);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (particles.isEmpty()) {
                seed(getWidth(), getHeight());
            }
            final long elapsed = SystemClock.elapsedRealtime() - startedAt;
            final float gravity = burst ? AndroidUtilities.dp(520) : 0;
            for (Particle particle : particles) {
                final float t = (elapsed - particle.delay) / 1000f;
                if (t < 0) {
                    continue;
                }
                final float x = particle.x + particle.speedX * t;
                final float y = particle.y + particle.speedY * t + gravity * t * t / 2f;
                final float life = (elapsed - particle.delay) / (float) (DURATION_MS - particle.delay);
                paint.setAlpha((int) (255 * Math.max(0f, Math.min(1f, (1f - life) * 2.5f))));
                paint.setTextSize(particle.size);
                canvas.save();
                canvas.rotate(particle.rotation + particle.spin * t, x, y);
                canvas.drawText(emoji, x - particle.size / 2f, y, paint);
                canvas.restore();
            }
            if (elapsed < DURATION_MS) {
                invalidate();
            }
        }
    }
}
