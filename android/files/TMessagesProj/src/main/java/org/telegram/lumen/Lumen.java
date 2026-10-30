/*
 * This file is part of LumenGram, a modification of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */
package org.telegram.lumen;

public final class Lumen {

    private Lumen() {
    }

    // Called from LaunchActivity.onResume, every module starts only once.
    public static void start() {
        LumenConnection.start();
        LumenBadges.start();
        LumenProfiles.start();
    }
}
