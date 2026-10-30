/*
 * This file is part of LumenGram, a modification of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */
package org.telegram.lumen;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.View;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.io.InputStream;
import java.util.ArrayList;

public class LumenProfileActivity extends UniversalFragment {

    private static final int PICK_BANNER = 7310;

    private static final int LOGIN = 1;
    private static final int LOGOUT = 2;
    private static final int UPLOAD_BANNER = 3;
    private static final int DELETE_BANNER = 4;
    private static final int SPOTIFY = 5;
    private static final int LASTFM = 6;
    private static final int DISCONNECT = 7;
    private static final int STYLE_ACCENT = 10;
    private static final int STYLE_ACCENT2 = 11;
    private static final int STYLE_EFFECT = 12;
    private static final int STYLE_STATUS = 13;
    private static final int STYLE_BADGE = 14;
    private static final int STYLE_SAVE = 15;

    private static final int[] ACCENTS = {0xFF8774E1, 0xFF3B82F6, 0xFF06B6D4, 0xFF10B981, 0xFFF59E0B, 0xFFEF4444, 0xFFEC4899, 0xFF64748B};
    private static final String[] BADGES = {"🌙", "✨", "🔥", "💜", "🎧", "🎮", "⚡", "👑", "🌸", "🐾"};
    private static final String[] EFFECT_KEYS = {"EffectNone", "EffectStars", "EffectSnow", "EffectSparks", "EffectConfetti", "EffectAurora"};

    private boolean busy;
    private JSONObject state;
    // The style being edited, saved to the server only with the Save button.
    private LumenProfiles.Style draft;

    @Override
    public boolean onFragmentCreate() {
        loadState();
        return super.onFragmentCreate();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Coming back from the Spotify page in the browser.
        loadState();
    }

    @Override
    protected CharSequence getTitle() {
        return LumenLang.get("ProfileTitle");
    }

    private static String t(String key) {
        return LumenLang.get(key);
    }

    private void loadState() {
        LumenProfiles.me((result, error) -> {
            state = result;
            if (draft == null && result != null) {
                draft = LumenProfiles.parseStyle(result.optJSONObject("style"));
            }
            update();
        });
    }

    private void update() {
        if (listView != null) {
            listView.adapter.update(true);
        }
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (!LumenProfiles.isLoggedIn()) {
            items.add(UItem.asButton(LOGIN, R.drawable.msg_openprofile, t(busy ? "ProfileLoggingIn" : "ProfileLogin")).accent());
            items.add(UItem.asShadow(t("ProfileLoginAbout")));
            return;
        }
        final JSONObject banner = state != null ? state.optJSONObject("banner") : null;
        final boolean banned = state != null && state.optBoolean("banned");
        items.add(UItem.asHeader(t("BannerTitle")));
        if (banned) {
            items.add(UItem.asShadow(t("BannerBanned")));
        } else {
            items.add(UItem.asButton(UPLOAD_BANNER, R.drawable.msg_photos, t(banner != null ? "BannerChange" : "BannerUpload")).accent());
            if (banner != null) {
                items.add(UItem.asButton(DELETE_BANNER, R.drawable.msg_delete, t("BannerDelete")).red());
            }
            items.add(UItem.asShadow(t("BannerAbout")));
        }

        if (draft != null) {
            fillStyleItems(items, banned);
        }

        items.add(UItem.asHeader(t("MusicTitle")));
        final JSONObject music = state != null ? state.optJSONObject("music") : null;
        final boolean connected = music != null && music.optBoolean("connected");
        final String status;
        if (!connected) {
            status = t("MusicNotConnected");
        } else if ("spotify".equals(music.optString("provider"))) {
            status = t("MusicSpotifyConnected");
        } else {
            status = LumenLang.format("MusicLastfmConnected", "user", music.optString("lastfm_user", ""));
        }
        if (state == null || state.optBoolean("spotify_available", true)) {
            items.add(UItem.asButton(SPOTIFY, R.drawable.msg_link, t("MusicConnectSpotify")));
        }
        if (state == null || state.optBoolean("lastfm_available", true)) {
            items.add(UItem.asButton(LASTFM, R.drawable.msg_link, t("MusicConnectLastfm")));
        }
        if (connected) {
            items.add(UItem.asButton(DISCONNECT, R.drawable.msg_delete, t("MusicDisconnect")).red());
        }
        items.add(UItem.asShadow(status + "\n\n" + t("MusicAbout")));

        items.add(UItem.asButton(LOGOUT, R.drawable.msg_leave, t("ProfileLogout")).red());
        items.add(UItem.asShadow(null));
    }

    private static CharSequence colorValue(int color) {
        final android.text.SpannableString text = new android.text.SpannableString("●  " + LumenProfiles.colorText(color));
        text.setSpan(new android.text.style.ForegroundColorSpan(color), 0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    private static String effectTitle(String effect) {
        for (int i = 0; i < LumenProfiles.EFFECTS.length; i++) {
            if (LumenProfiles.EFFECTS[i].equals(effect)) {
                return t(EFFECT_KEYS[i]);
            }
        }
        return t("EffectNone");
    }

    private void fillStyleItems(ArrayList<UItem> items, boolean banned) {
        items.add(UItem.asHeader(t("StyleTitle")));
        final int[] accent = draft.accent;
        items.add(UItem.asButton(STYLE_ACCENT, R.drawable.msg_colors, t("AccentTitle"), accent != null ? colorValue(accent[0]) : t("AccentDefault")));
        if (accent != null) {
            items.add(UItem.asButton(STYLE_ACCENT2, R.drawable.msg_colors, t("AccentGradient"), accent.length > 1 ? colorValue(accent[1]) : t("EffectNone")));
        }
        items.add(UItem.asButton(STYLE_EFFECT, R.drawable.msg_premium_liststar, t("EffectTitle"), effectTitle(draft.effect)));
        if (!banned) {
            items.add(UItem.asButton(STYLE_STATUS, R.drawable.msg_edit, t("StatusTitle"), draft.status != null ? draft.status : ""));
            items.add(UItem.asButton(STYLE_BADGE, R.drawable.msg_emoji_cat, t("BadgeLabel"), draft.badge != null ? draft.badge : ""));
        }
        items.add(UItem.asButton(STYLE_SAVE, R.drawable.msg_check_s, t("StyleSave")).accent());
        items.add(UItem.asShadow(t("StyleAbout")));
    }

    private void pickColor(boolean second) {
        final CharSequence[] titles = new CharSequence[ACCENTS.length + 1];
        titles[0] = second ? t("EffectNone") : t("AccentDefault");
        for (int i = 0; i < ACCENTS.length; i++) {
            titles[i + 1] = colorValue(ACCENTS[i]);
        }
        final org.telegram.ui.ActionBar.AlertDialog.Builder builder = new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity(), getResourceProvider());
        builder.setTitle(t(second ? "AccentGradient" : "AccentTitle"));
        builder.setItems(titles, (dialog, which) -> {
            final int[] accent = draft.accent;
            if (!second) {
                if (which == 0) {
                    draft.accent = null;
                } else if (accent != null && accent.length > 1) {
                    draft.accent = new int[] {ACCENTS[which - 1], accent[1]};
                } else {
                    draft.accent = new int[] {ACCENTS[which - 1]};
                }
            } else if (accent != null) {
                draft.accent = which == 0 ? new int[] {accent[0]} : new int[] {accent[0], ACCENTS[which - 1]};
            }
            update();
        });
        showDialog(builder.create());
    }

    private void pickEffect() {
        final CharSequence[] titles = new CharSequence[EFFECT_KEYS.length];
        for (int i = 0; i < EFFECT_KEYS.length; i++) {
            titles[i] = t(EFFECT_KEYS[i]);
        }
        final org.telegram.ui.ActionBar.AlertDialog.Builder builder = new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity(), getResourceProvider());
        builder.setTitle(t("EffectTitle"));
        builder.setItems(titles, (dialog, which) -> {
            draft.effect = which == 0 ? null : LumenProfiles.EFFECTS[which];
            update();
        });
        showDialog(builder.create());
    }

    private void pickBadge() {
        final CharSequence[] titles = new CharSequence[BADGES.length + 2];
        titles[0] = t("EffectNone");
        System.arraycopy(BADGES, 0, titles, 1, BADGES.length);
        titles[titles.length - 1] = t("BadgeCustom");
        final org.telegram.ui.ActionBar.AlertDialog.Builder builder = new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity(), getResourceProvider());
        builder.setTitle(t("BadgeLabel"));
        builder.setItems(titles, (dialog, which) -> {
            if (which == titles.length - 1) {
                LumenDialogs.input(this, t("BadgeLabel"), null, t("BadgeLabel"), draft.badge, false, badge -> {
                    final String value = badge.trim();
                    draft.badge = value.isEmpty() || value.length() > 16 ? null : value;
                    update();
                });
                return;
            }
            draft.badge = which == 0 ? null : BADGES[which - 1];
            update();
        });
        showDialog(builder.create());
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (busy) {
            return;
        }
        switch (item.id) {
            case LOGIN:
                busy = true;
                update();
                LumenProfiles.login(currentAccount, (ok, error) -> {
                    busy = false;
                    if (ok == null || !ok) {
                        LumenDialogs.error(this, error);
                    }
                    loadState();
                });
                break;
            case LOGOUT:
                LumenProfiles.logout(() -> {
                    state = null;
                    update();
                });
                break;
            case UPLOAD_BANNER: {
                final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(intent, PICK_BANNER);
                break;
            }
            case DELETE_BANNER:
                LumenDialogs.confirm(this, t("BannerTitle"), t("BannerDelete") + "?", t("BannerDelete"), true, () ->
                    LumenProfiles.deleteBanner((result, error) -> {
                        if (error != null) {
                            LumenDialogs.error(this, error);
                        }
                        loadState();
                    }));
                break;
            case SPOTIFY:
                LumenProfiles.startSpotify((url, error) -> {
                    if (url == null) {
                        LumenDialogs.error(this, error);
                        return;
                    }
                    Browser.openUrl(getParentActivity(), url);
                    LumenDialogs.toast(this, t("MusicSpotifyPending"));
                });
                break;
            case LASTFM:
                LumenDialogs.input(this, t("MusicConnectLastfm"), null, t("MusicLastfmUser"), null, false, username -> {
                    if (username.isEmpty()) {
                        return;
                    }
                    LumenProfiles.connectLastfm(username, (result, error) -> {
                        if (error != null) {
                            LumenDialogs.error(this, error);
                        }
                        loadState();
                    });
                });
                break;
            case STYLE_ACCENT:
                pickColor(false);
                break;
            case STYLE_ACCENT2:
                pickColor(true);
                break;
            case STYLE_EFFECT:
                pickEffect();
                break;
            case STYLE_STATUS:
                LumenDialogs.input(this, t("StatusTitle"), t("StatusLabel"), t("StatusTitle"), draft.status, false, status -> {
                    final String value = status.trim();
                    draft.status = value.isEmpty() ? null : (value.length() > 70 ? value.substring(0, 70) : value);
                    update();
                });
                break;
            case STYLE_BADGE:
                pickBadge();
                break;
            case STYLE_SAVE:
                busy = true;
                LumenProfiles.saveStyle(draft, (result, error) -> {
                    busy = false;
                    LumenDialogs.toast(this, error != null ? error : t("StyleSaved"));
                    loadState();
                });
                break;
            case DISCONNECT:
                LumenProfiles.disconnectMusic((result, error) -> {
                    if (error != null) {
                        LumenDialogs.error(this, error);
                    }
                    loadState();
                });
                break;
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != PICK_BANNER || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        final Uri uri = data.getData();
        busy = true;
        Utilities.globalQueue.postRunnable(() -> {
            Bitmap bitmap = null;
            try (InputStream stream = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri)) {
                final BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 1;
                bitmap = BitmapFactory.decodeStream(stream, null, options);
            } catch (Exception e) {
                FileLog.e(e);
            }
            final Bitmap picked = bitmap;
            AndroidUtilities.runOnUIThread(() -> {
                if (picked == null) {
                    busy = false;
                    LumenDialogs.error(this, null);
                    return;
                }
                LumenProfiles.uploadBanner(picked, (result, error) -> {
                    busy = false;
                    LumenDialogs.toast(this, error != null ? error : t("BannerUploaded"));
                    loadState();
                });
            });
        });
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
