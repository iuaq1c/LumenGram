package org.telegram.lumen;

import org.telegram.messenger.ApplicationLoader;

public final class LumenTransfers {
    private LumenTransfers() { }

    public static int requests(int standard, boolean upload) {
        String mode = LumenConfig.getString(upload ? "mediaUploadMode" : "mediaDownloadMode", "off");
        if (LumenConfig.getBool("isMediaBoostWifiOnly") && !ApplicationLoader.isConnectedToWiFi()) return standard;
        if ("auto".equals(mode)) mode = ApplicationLoader.isConnectedToWiFi() && !ApplicationLoader.isConnectionSlow()
            ? "fast" : "off";
        if ("eco".equals(mode)) return 1;
        if ("fast".equals(mode)) return Math.max(standard, Math.min(32, standard * 2));
        return standard;
    }
}
