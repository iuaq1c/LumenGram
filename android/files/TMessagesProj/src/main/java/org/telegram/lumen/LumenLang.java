/*
 * This file is part of LumenGram, a modification of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */
package org.telegram.lumen;

import org.telegram.messenger.LocaleController;

import java.util.Locale;

public final class LumenLang {

    private LumenLang() {
    }

    public static boolean isRussian() {
        try {
            final Locale locale = LocaleController.getInstance().getCurrentLocale();
            final String language = locale != null ? locale.getLanguage() : "";
            return "ru".equals(language) || "uk".equals(language) || "be".equals(language);
        } catch (Exception e) {
            return false;
        }
    }

    public static String get(String key) {
        String value = (isRussian() ? LumenLangData.RU : LumenLangData.EN).get(key);
        if (value == null) {
            value = LumenLangData.EN.get(key);
        }
        return value != null ? value : key;
    }

    // Replaces {name} placeholders, the same format the web client uses.
    public static String format(String key, Object... pairs) {
        String value = get(key);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            value = value.replace("{" + pairs[i] + "}", String.valueOf(pairs[i + 1]));
        }
        return value;
    }
}
