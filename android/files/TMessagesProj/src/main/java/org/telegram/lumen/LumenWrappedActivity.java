/*
 * This file is part of LumenGram, a modification of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */
package org.telegram.lumen;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.net.Uri;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.FileProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.BaseFragment;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

// Full-screen "year in review" slides in the spirit of Spotify Wrapped, with a card to share at the end.
public class LumenWrappedActivity extends BaseFragment {

    private static final int[][] GRADIENTS = {
        {0xFF2B1D6B, 0xFF8774E1},
        {0xFF0F3D5E, 0xFF2EC4B6},
        {0xFF5B1A4A, 0xFFE15A97},
        {0xFF1F2A44, 0xFF5B8CFF},
        {0xFF4A2511, 0xFFF29D38},
        {0xFF101B3B, 0xFF6C4AB6},
        {0xFF1B1530, 0xFF8774E1},
    };
    private static final long COUNTER_MS = 900;
    private static final int SHARE_WIDTH = 1080;
    private static final int SHARE_HEIGHT = 1920;

    private static class Slide {
        String kicker;
        String big;
        String title;
        ArrayList<String> lines = new ArrayList<>();
        int counter = -1;
        boolean share;
    }

    private LumenWrapped collector;
    private final ArrayList<Slide> slides = new ArrayList<>();
    private WrappedView view;
    private int current;
    private int progressDone;
    private int progressTotal = 1;

    @Override
    public View createView(Context context) {
        actionBar.setAddToContainer(false);
        view = new WrappedView(context);
        fragmentView = view;
        collector = new LumenWrapped(currentAccount, (done, total) -> {
            progressDone = done;
            progressTotal = Math.max(1, total);
            view.invalidate();
        }, result -> {
            buildSlides(result);
            view.slideStartedAt = SystemClock.elapsedRealtime();
            view.invalidate();
        });
        collector.start();
        return fragmentView;
    }

    @Override
    public void onFragmentDestroy() {
        if (collector != null) {
            collector.cancel();
        }
        super.onFragmentDestroy();
    }

    @Override
    public boolean isLightStatusBar() {
        return false;
    }

    private static String t(String key) {
        return LumenLang.get(key);
    }

    private void buildSlides(LumenWrapped.Result result) {
        slides.clear();
        Slide slide = new Slide();
        slide.kicker = t("WrappedKicker");
        slide.big = String.valueOf(result.year);
        slide.title = t("WrappedIntro");
        slide.lines.add(t("WrappedTapNext"));
        slides.add(slide);

        slide = new Slide();
        slide.kicker = t("WrappedMessagesKicker");
        slide.counter = result.totalMessages;
        slide.title = t("WrappedMessages");
        slide.lines.add(LumenLang.format("WrappedInChats", "count", result.chats));
        slides.add(slide);

        if (!result.top.isEmpty()) {
            slide = new Slide();
            slide.kicker = t("WrappedPersonKicker");
            slide.big = result.top.get(0).name;
            slide.title = LumenLang.format("WrappedMessagesCount", "count", number(result.top.get(0).count));
            for (int i = 1; i < result.top.size(); i++) {
                final LumenWrapped.Person person = result.top.get(i);
                slide.lines.add((i + 1) + ". " + person.name + " · " + number(person.count));
            }
            slides.add(slide);
        }

        slide = new Slide();
        slide.kicker = t("WrappedMediaKicker");
        slide.lines.add("🎙  " + LumenLang.format("WrappedVoices", "count", number(result.voices)));
        slide.lines.add("⭕  " + LumenLang.format("WrappedRounds", "count", number(result.rounds)));
        slide.lines.add("📷  " + LumenLang.format("WrappedPhotos", "count", number(result.photos)));
        slide.title = t("WrappedMediaAbout");
        slides.add(slide);

        if (result.topWord != null) {
            slide = new Slide();
            slide.kicker = t("WrappedWordKicker");
            slide.big = "«" + result.topWord + "»";
            slide.title = LumenLang.format("WrappedWordCount", "count", result.topWordCount);
            if (!result.topEmoji.isEmpty()) {
                slide.lines.add(t("WrappedEmoji") + "  " + android.text.TextUtils.join(" ", result.topEmoji));
            }
            slides.add(slide);
        }

        if (result.busiestHour >= 0) {
            slide = new Slide();
            slide.kicker = t("WrappedTimeKicker");
            slide.big = t(chronotype(result.busiestHour, result.nightPercent));
            slide.title = LumenLang.format("WrappedBusiestHour", "hour", String.format(java.util.Locale.US, "%02d:00", result.busiestHour));
            slide.lines.add(LumenLang.format("WrappedNight", "percent", result.nightPercent));
            slides.add(slide);
        }

        slide = new Slide();
        slide.kicker = LumenLang.format("WrappedSummaryKicker", "year", result.year);
        slide.big = LumenLang.format("WrappedSummaryMessages", "count", number(result.totalMessages));
        if (!result.top.isEmpty()) {
            slide.lines.add("❤️  " + result.top.get(0).name);
        }
        if (result.topWord != null) {
            slide.lines.add("💬  «" + result.topWord + "»");
        }
        if (!result.topEmoji.isEmpty()) {
            slide.lines.add("✨  " + result.topEmoji.get(0));
        }
        if (result.busiestHour >= 0) {
            slide.lines.add("🕐  " + t(chronotype(result.busiestHour, result.nightPercent)));
        }
        slide.title = t("WrappedMadeIn");
        slide.share = true;
        slides.add(slide);
    }

    private static String number(int value) {
        return String.format(java.util.Locale.getDefault(), "%,d", value);
    }

    private static String chronotype(int busiestHour, int nightPercent) {
        if (nightPercent >= 15 || busiestHour < 5) {
            return "WrappedOwl";
        } else if (busiestHour < 12) {
            return "WrappedLark";
        } else if (busiestHour < 18) {
            return "WrappedDay";
        }
        return "WrappedEvening";
    }

    private void share() {
        try {
            final Bitmap bitmap = Bitmap.createBitmap(SHARE_WIDTH, SHARE_HEIGHT, Bitmap.Config.ARGB_8888);
            view.drawSlide(new Canvas(bitmap), slides.get(slides.size() - 1), slides.size() - 1, SHARE_WIDTH, SHARE_HEIGHT, 1f, false);
            final File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "cache");
            dir.mkdirs();
            final File file = new File(dir, "lumengram_wrapped.png");
            try (FileOutputStream out = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            final Uri uri = FileProvider.getUriForFile(getParentActivity(), ApplicationLoader.getApplicationId() + ".provider", file);
            final Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("image/png");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.putExtra(Intent.EXTRA_TEXT, t("WrappedShareText"));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getParentActivity().startActivity(Intent.createChooser(intent, t("WrappedShare")));
        } catch (Exception e) {
            FileLog.e(e);
            LumenDialogs.error(this, null);
        }
    }

    private class WrappedView extends View {

        private final Paint backgroundPaint = new Paint();
        private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint buttonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint kickerPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint bigPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint linePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final RectF buttonRect = new RectF();
        long slideStartedAt;

        WrappedView(Context context) {
            super(context);
            kickerPaint.setColor(0xCCFFFFFF);
            kickerPaint.setTypeface(AndroidUtilities.bold());
            bigPaint.setColor(Color.WHITE);
            bigPaint.setTypeface(AndroidUtilities.bold());
            titlePaint.setColor(Color.WHITE);
            linePaint.setColor(0xE6FFFFFF);
            buttonPaint.setColor(Color.WHITE);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final int width = getWidth();
            final int height = getHeight();
            if (slides.isEmpty()) {
                drawLoading(canvas, width, height);
                return;
            }
            final float appear = Math.min(1f, (SystemClock.elapsedRealtime() - slideStartedAt) / (float) COUNTER_MS);
            drawSlide(canvas, slides.get(current), current, width, height, appear, true);
            if (appear < 1f) {
                invalidate();
            }
        }

        private void drawLoading(Canvas canvas, int width, int height) {
            paintBackground(canvas, 0, width, height);
            titlePaint.setTextSize(AndroidUtilities.dp(20));
            final String text = LumenLang.format("WrappedLoading", "percent", progressDone * 100 / progressTotal);
            final StaticLayout layout = layout(text, titlePaint, width - AndroidUtilities.dp(64));
            canvas.save();
            canvas.translate(AndroidUtilities.dp(32), height / 2f - layout.getHeight() / 2f);
            layout.draw(canvas);
            canvas.restore();
            final float barTop = height / 2f + layout.getHeight() + AndroidUtilities.dp(16);
            barPaint.setColor(0x40FFFFFF);
            canvas.drawRoundRect(AndroidUtilities.dp(32), barTop, width - AndroidUtilities.dp(32), barTop + AndroidUtilities.dp(4), AndroidUtilities.dp(2), AndroidUtilities.dp(2), barPaint);
            barPaint.setColor(Color.WHITE);
            final float filled = (width - AndroidUtilities.dp(64)) * progressDone / (float) progressTotal;
            canvas.drawRoundRect(AndroidUtilities.dp(32), barTop, AndroidUtilities.dp(32) + filled, barTop + AndroidUtilities.dp(4), AndroidUtilities.dp(2), AndroidUtilities.dp(2), barPaint);
        }

        private void paintBackground(Canvas canvas, int index, int width, int height) {
            final int[] colors = GRADIENTS[index % GRADIENTS.length];
            backgroundPaint.setShader(new LinearGradient(0, 0, width * 0.4f, height, colors[0], colors[1], Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, width, height, backgroundPaint);
        }

        private StaticLayout layout(CharSequence text, TextPaint paint, int width) {
            return StaticLayout.Builder.obtain(text, 0, text.length(), paint, Math.max(1, width))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build();
        }

        // `scale` keeps the shared picture the same as the screen, `withChrome` adds progress bars and the button
        void drawSlide(Canvas canvas, Slide slide, int index, int width, int height, float appear, boolean withChrome) {
            final float scale = width / (float) AndroidUtilities.dp(400);
            final int side = (int) (AndroidUtilities.dp(28) * scale);
            final int textWidth = width - side * 2;
            paintBackground(canvas, index, width, height);

            if (withChrome) {
                final float gap = AndroidUtilities.dp(4);
                final float barWidth = (width - side * 2 - gap * (slides.size() - 1)) / slides.size();
                final float top = AndroidUtilities.statusBarHeight + AndroidUtilities.dp(10);
                for (int i = 0; i < slides.size(); i++) {
                    barPaint.setColor(i <= current ? Color.WHITE : 0x40FFFFFF);
                    final float left = side + i * (barWidth + gap);
                    canvas.drawRoundRect(left, top, left + barWidth, top + AndroidUtilities.dp(3), AndroidUtilities.dp(2), AndroidUtilities.dp(2), barPaint);
                }
            }

            kickerPaint.setTextSize(AndroidUtilities.dp(16) * scale);
            bigPaint.setTextSize(AndroidUtilities.dp(slide.counter >= 0 || (slide.big != null && slide.big.length() <= 6) ? 72 : 40) * scale);
            titlePaint.setTextSize(AndroidUtilities.dp(22) * scale);
            linePaint.setTextSize(AndroidUtilities.dp(18) * scale);

            final ArrayList<StaticLayout> blocks = new ArrayList<>();
            final ArrayList<Integer> gaps = new ArrayList<>();
            blocks.add(layout(slide.kicker.toUpperCase(), kickerPaint, textWidth));
            gaps.add((int) (AndroidUtilities.dp(12) * scale));
            String big = slide.big;
            if (slide.counter >= 0) {
                big = String.format(java.util.Locale.getDefault(), "%,d", (int) (slide.counter * easeOut(appear)));
            }
            // Without a big value the lines are the main content and come before the title
            final boolean hasBig = big != null;
            if (hasBig) {
                blocks.add(layout(big, bigPaint, textWidth));
                gaps.add((int) (AndroidUtilities.dp(12) * scale));
            } else {
                for (String line : slide.lines) {
                    blocks.add(layout(line, titlePaint, textWidth));
                    gaps.add((int) (AndroidUtilities.dp(14) * scale));
                }
            }
            if (slide.title != null) {
                blocks.add(layout(slide.title, titlePaint, textWidth));
                gaps.add((int) (AndroidUtilities.dp(20) * scale));
            }
            if (hasBig) {
                for (String line : slide.lines) {
                    blocks.add(layout(line, linePaint, textWidth));
                    gaps.add((int) (AndroidUtilities.dp(10) * scale));
                }
            }

            int total = 0;
            for (int i = 0; i < blocks.size(); i++) {
                total += blocks.get(i).getHeight() + gaps.get(i);
            }
            float y = height / 2f - total / 2f;
            final float slideIn = (1f - easeOut(appear)) * AndroidUtilities.dp(24) * scale;
            canvas.save();
            canvas.translate(side, y + slideIn);
            for (int i = 0; i < blocks.size(); i++) {
                blocks.get(i).draw(canvas);
                canvas.translate(0, blocks.get(i).getHeight() + gaps.get(i));
            }
            canvas.restore();

            if (slide.share && withChrome) {
                final float buttonHeight = AndroidUtilities.dp(52);
                buttonRect.set(side, height - AndroidUtilities.dp(48) - buttonHeight, width - side, height - AndroidUtilities.dp(48));
                canvas.drawRoundRect(buttonRect, buttonHeight / 2, buttonHeight / 2, buttonPaint);
                final TextPaint buttonText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                buttonText.setColor(GRADIENTS[index % GRADIENTS.length][0]);
                buttonText.setTypeface(AndroidUtilities.bold());
                buttonText.setTextSize(AndroidUtilities.dp(17));
                buttonText.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(t("WrappedShare"), buttonRect.centerX(), buttonRect.centerY() + AndroidUtilities.dp(6), buttonText);
            } else if (!withChrome) {
                final TextPaint brand = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                brand.setColor(0xB3FFFFFF);
                brand.setTextSize(AndroidUtilities.dp(14) * scale);
                canvas.drawText("tunexa.ru/docs/lumengram", side, height - side, brand);
            }
        }

        private float easeOut(float value) {
            return 1f - (1f - value) * (1f - value) * (1f - value);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() != MotionEvent.ACTION_UP || slides.isEmpty()) {
                return true;
            }
            if (slides.get(current).share && buttonRect.contains(event.getX(), event.getY())) {
                share();
                return true;
            }
            if (event.getX() < getWidth() * 0.3f) {
                current = Math.max(0, current - 1);
            } else if (current < slides.size() - 1) {
                current++;
            } else {
                finishFragment();
                return true;
            }
            slideStartedAt = SystemClock.elapsedRealtime();
            invalidate();
            return true;
        }
    }
}
