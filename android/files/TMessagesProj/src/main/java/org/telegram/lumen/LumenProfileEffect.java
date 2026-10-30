/*
 * This file is part of LumenGram, a modification of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */
package org.telegram.lumen;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;

import org.telegram.messenger.AndroidUtilities;

// The live layer over the profile header, the same particles as the web client's LumenProfileEffect.
public final class LumenProfileEffect {

    public static final long FRAME_MS = 33;
    private static final float FULL_TURN = (float) (Math.PI * 2);
    private static final int[] DEFAULT_COLORS = {0xFF8774E1, 0xFFC4B5FD};
    private static final int[] CONFETTI_EXTRA = {0xFFF472B6, 0xFFFACC15, 0xFF34D399, 0xFF60A5FA};

    private static class Particle {
        float x, y, size, speed, phase, spin;
        int color;
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final long startedAt = SystemClock.elapsedRealtime();
    private String effect;
    private int[] colors;
    private Particle[] particles;

    private static int count(String effect) {
        switch (effect) {
            case "stars": return 46;
            case "snow": return 54;
            case "sparks": return 34;
            case "confetti": return 42;
            case "aurora": return 3;
        }
        return 0;
    }

    // Returns true while there is something to draw, so the caller keeps invalidating.
    public boolean set(String effect, int[] accent) {
        final int[] palette = accent != null ? accent : DEFAULT_COLORS;
        if (effect == null) {
            this.effect = null;
            particles = null;
            return false;
        }
        if (effect.equals(this.effect) && java.util.Arrays.equals(palette, colors)) {
            return true;
        }
        this.effect = effect;
        colors = palette;
        int[] all = palette;
        if ("confetti".equals(effect)) {
            all = new int[palette.length + CONFETTI_EXTRA.length];
            System.arraycopy(palette, 0, all, 0, palette.length);
            System.arraycopy(CONFETTI_EXTRA, 0, all, palette.length, CONFETTI_EXTRA.length);
        }
        long seed = 20261024L;
        particles = new Particle[count(effect)];
        for (int i = 0; i < particles.length; i++) {
            final Particle p = new Particle();
            seed = (seed * 1664525L + 1013904223L) % 4294967296L;
            p.x = seed / 4294967296f;
            seed = (seed * 1664525L + 1013904223L) % 4294967296L;
            p.y = seed / 4294967296f;
            seed = (seed * 1664525L + 1013904223L) % 4294967296L;
            p.size = seed / 4294967296f;
            seed = (seed * 1664525L + 1013904223L) % 4294967296L;
            p.speed = 0.5f + seed / 4294967296f;
            seed = (seed * 1664525L + 1013904223L) % 4294967296L;
            p.phase = seed / 4294967296f * FULL_TURN;
            p.color = all[i % all.length];
            seed = (seed * 1664525L + 1013904223L) % 4294967296L;
            p.spin = seed / 4294967296f - 0.5f;
            particles[i] = p;
        }
        return true;
    }

    private static int withAlpha(int color, float alpha) {
        return (Math.round(Math.max(0, Math.min(1, alpha)) * 255) << 24) | (color & 0xFFFFFF);
    }

    public void draw(Canvas canvas, float width, float height) {
        if (particles == null || width <= 0 || height <= 0) {
            return;
        }
        final float seconds = (SystemClock.elapsedRealtime() - startedAt) / 1000f;
        final float ratio = AndroidUtilities.density;
        canvas.save();
        canvas.clipRect(0, 0, width, height);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        for (Particle p : particles) {
            switch (effect) {
                case "stars": {
                    final float alpha = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(seconds * p.speed * 2 + p.phase));
                    final float radius = (0.6f + p.size * 1.4f) * ratio;
                    paint.setColor(withAlpha(0xFFFFFF, alpha));
                    canvas.drawCircle(p.x * width, p.y * height, radius, paint);
                    if (p.size > 0.85f) {
                        paint.setStrokeWidth(ratio * 0.6f);
                        paint.setColor(withAlpha(0xFFFFFF, alpha * 0.6f));
                        canvas.drawLine(p.x * width - radius * 4, p.y * height, p.x * width + radius * 4, p.y * height, paint);
                        canvas.drawLine(p.x * width, p.y * height - radius * 4, p.x * width, p.y * height + radius * 4, paint);
                    }
                    break;
                }
                case "snow": {
                    final float y = (p.y + seconds * 0.05f * p.speed) % 1f;
                    final float x = p.x * width + (float) Math.sin(seconds * p.speed + p.phase) * 8 * ratio;
                    paint.setColor(withAlpha(0xFFFFFF, 0.5f + p.size * 0.5f));
                    canvas.drawCircle(x, y * height, (1 + p.size * 2.2f) * ratio, paint);
                    break;
                }
                case "sparks": {
                    final float progress = (p.y + seconds * 0.12f * p.speed) % 1f;
                    final float y = (1 - progress) * height;
                    final float x = p.x * width + (float) Math.sin(seconds * 2 * p.speed + p.phase) * 6 * ratio;
                    final float alpha = (float) Math.sin(progress * Math.PI);
                    final float radius = (1 + p.size * 1.6f) * ratio * 4;
                    if (alpha > 0.01f) {
                        paint.setShader(new RadialGradient(x, y, radius, withAlpha(p.color, alpha), withAlpha(p.color, 0), Shader.TileMode.CLAMP));
                        canvas.drawCircle(x, y, radius, paint);
                        paint.setShader(null);
                    }
                    break;
                }
                case "confetti": {
                    final float y = ((p.y + seconds * 0.08f * p.speed) % 1.1f) - 0.05f;
                    final float x = p.x * width + (float) Math.sin(seconds * p.speed + p.phase) * 10 * ratio;
                    final float w = (3 + p.size * 4) * ratio;
                    final float h = w / 2 * (0.4f + Math.abs((float) Math.sin(seconds * 3 + p.phase)));
                    canvas.save();
                    canvas.translate(x, y * height);
                    canvas.rotate((float) Math.toDegrees(seconds * p.spin * 6 + p.phase));
                    paint.setColor(withAlpha(p.color, 0.9f));
                    canvas.drawRect(-w / 2, -w / 4, w / 2, -w / 4 + h, paint);
                    canvas.restore();
                    break;
                }
                case "aurora": {
                    final float x = width * (0.2f + p.x * 0.6f + 0.15f * (float) Math.sin(seconds * 0.2f * p.speed + p.phase));
                    final float y = height * (0.3f + p.y * 0.4f + 0.15f * (float) Math.cos(seconds * 0.17f * p.speed + p.phase));
                    final float radius = Math.max(width, height) * (0.35f + p.size * 0.25f);
                    paint.setShader(new RadialGradient(x, y, radius, withAlpha(p.color, 0.45f), withAlpha(p.color, 0), Shader.TileMode.CLAMP));
                    canvas.drawRect(0, 0, width, height, paint);
                    paint.setShader(null);
                    break;
                }
            }
        }
        canvas.restore();
    }
}
