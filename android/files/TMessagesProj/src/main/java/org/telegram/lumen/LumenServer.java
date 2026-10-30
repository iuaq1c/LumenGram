/*
 * This file is part of LumenGram, a modification of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */
package org.telegram.lumen;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

// Plain HTTPS calls to the LumenGram server on tunexa.ru, always off the main thread.
public final class LumenServer {

    public static final String SERVER_URL = "https://tunexa.ru";
    private static final int TIMEOUT_MS = 15000;
    private static final int MAX_RESPONSE = 4 * 1024 * 1024;

    public interface Callback<T> {
        void run(T result, String error);
    }

    private LumenServer() {
    }

    public static byte[] download(String url) throws Exception {
        final HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setUseCaches(false);
        try {
            if (connection.getResponseCode() != 200) {
                throw new Exception("HTTP " + connection.getResponseCode());
            }
            return readAll(connection.getInputStream());
        } finally {
            connection.disconnect();
        }
    }

    public static JSONObject getJson(String url) throws Exception {
        return new JSONObject(new String(download(url), StandardCharsets.UTF_8));
    }

    // A plain form body: the hosting firewall challenges multipart requests.
    public static JSONObject postForm(String path, Map<String, String> fields) throws Exception {
        final StringBuilder body = new StringBuilder();
        final String token = LumenProfiles.token();
        if (token != null) {
            body.append("token=").append(URLEncoder.encode(token, "UTF-8"));
        }
        if (fields != null) {
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                if (body.length() > 0) {
                    body.append('&');
                }
                body.append(URLEncoder.encode(entry.getKey(), "UTF-8"))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), "UTF-8"));
            }
        }
        final byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        final HttpURLConnection connection = (HttpURLConnection) new URL(SERVER_URL + path).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS * 2);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
        connection.setFixedLengthStreamingMode(bytes.length);
        try {
            try (OutputStream out = connection.getOutputStream()) {
                out.write(bytes);
            }
            final int code = connection.getResponseCode();
            final InputStream stream = code < 400 ? connection.getInputStream() : connection.getErrorStream();
            JSONObject data = null;
            if (stream != null) {
                try {
                    data = new JSONObject(new String(readAll(stream), StandardCharsets.UTF_8));
                } catch (Exception ignore) {
                }
            }
            if (data == null || !data.optBoolean("ok")) {
                final String error = data != null ? data.optString("error", "") : "";
                throw new Exception(error.isEmpty() ? "HTTP " + code : error);
            }
            return data;
        } finally {
            connection.disconnect();
        }
    }

    // Runs postForm on a background queue and reports back on the main thread.
    public static void post(String path, Map<String, String> fields, Callback<JSONObject> callback) {
        Utilities.globalQueue.postRunnable(() -> {
            JSONObject result = null;
            String error = null;
            try {
                result = postForm(path, fields);
            } catch (Exception e) {
                FileLog.e(e);
                error = e.getMessage() != null ? e.getMessage() : "ERROR";
            }
            final JSONObject finalResult = result;
            final String finalError = error;
            if (callback != null) {
                AndroidUtilities.runOnUIThread(() -> callback.run(finalResult, finalError));
            }
        });
    }

    private static byte[] readAll(InputStream stream) throws Exception {
        try (InputStream in = stream) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buffer = new byte[16384];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
                if (out.size() > MAX_RESPONSE) {
                    throw new Exception("Response is too large");
                }
            }
            return out.toByteArray();
        }
    }
}
