package org.telegram.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.ui.Components.ForegroundDetector;

public class LauncherIconController {
    // WHY: disabling the alias the app was started from closes its task at once,
    // so a picked icon waits until the app goes to background.
    private static LauncherIcon pendingIcon;
    private static boolean listening;

    public static void tryFixLauncherIconIfNeeded() {
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (isEnabled(icon)) {
                return;
            }
        }

        setIcon(LauncherIcon.DEFAULT);
    }

    public static boolean isEnabled(LauncherIcon icon) {
        if (pendingIcon != null) {
            return icon == pendingIcon;
        }
        Context ctx = ApplicationLoader.applicationContext;
        int i = ctx.getPackageManager().getComponentEnabledSetting(icon.getComponentName(ctx));
        return i == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || i == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && icon == LauncherIcon.DEFAULT;
    }

    public static void setIcon(LauncherIcon icon) {
        final ForegroundDetector detector = ForegroundDetector.getInstance();
        if (detector == null || detector.isBackground()) {
            pendingIcon = null;
            applyIcon(icon);
            return;
        }
        pendingIcon = icon;
        if (!listening) {
            listening = true;
            detector.addListener(new ForegroundDetector.Listener() {
                @Override
                public void onBecameForeground() {
                }

                @Override
                public void onBecameBackground() {
                    final LauncherIcon pending = pendingIcon;
                    if (pending != null) {
                        pendingIcon = null;
                        applyIcon(pending);
                    }
                }
            });
        }
    }

    private static void applyIcon(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        PackageManager pm = ctx.getPackageManager();
        for (LauncherIcon i : LauncherIcon.values()) {
            pm.setComponentEnabledSetting(i.getComponentName(ctx), i == icon ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        }
    }

    public enum LauncherIcon {
        DEFAULT("DefaultIcon", R.mipmap.lumen_icon_classic_bg, R.drawable.lumen_icon_empty, "AppIconClassic"),
        NIGHT("LumenNightIcon", R.mipmap.lumen_icon_night_bg, R.drawable.lumen_icon_empty, "AppIconNight"),
        LIGHT("LumenLightIcon", R.mipmap.lumen_icon_light_bg, R.drawable.lumen_icon_empty, "AppIconLight"),
        SUNSET("LumenSunsetIcon", R.mipmap.lumen_icon_sunset_bg, R.drawable.lumen_icon_empty, "AppIconSunset"),
        AURORA("LumenAuroraIcon", R.mipmap.lumen_icon_aurora_bg, R.drawable.lumen_icon_empty, "AppIconAurora"),
        OCEAN("LumenOceanIcon", R.mipmap.lumen_icon_ocean_bg, R.drawable.lumen_icon_empty, "AppIconOcean"),
        SAKURA("LumenSakuraIcon", R.mipmap.lumen_icon_sakura_bg, R.drawable.lumen_icon_empty, "AppIconSakura"),
        GOLD("LumenGoldIcon", R.mipmap.lumen_icon_gold_bg, R.drawable.lumen_icon_empty, "AppIconGold"),
        GLASS("LumenGlassIcon", R.mipmap.lumen_icon_glass_bg, R.drawable.lumen_icon_empty, "AppIconGlass"),
        LINE("LumenLineIcon", R.mipmap.lumen_icon_line_bg, R.drawable.lumen_icon_empty, "AppIconLine"),
        NEON("LumenNeonIcon", R.mipmap.lumen_icon_neon_bg, R.drawable.lumen_icon_empty, "AppIconNeon"),
        PIXEL("LumenPixelIcon", R.mipmap.lumen_icon_pixel_bg, R.drawable.lumen_icon_empty, "AppIconPixel");

        public final String key;
        public final int background;
        public final int foreground;
        // A LumenLang key, since these names are not in Telegram's strings.
        public final String title;
        public final boolean premium;

        private ComponentName componentName;

        public ComponentName getComponentName(Context ctx) {
            if (componentName == null) {
                componentName = new ComponentName(ctx.getPackageName(), "org.telegram.messenger." + key);
            }
            return componentName;
        }

        LauncherIcon(String key, int background, int foreground, String title) {
            this(key, background, foreground, title, false);
        }

        LauncherIcon(String key, int background, int foreground, String title, boolean premium) {
            this.key = key;
            this.background = background;
            this.foreground = foreground;
            this.title = title;
            this.premium = premium;
        }
    }
}
