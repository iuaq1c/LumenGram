package org.telegram.lumen;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

// Free speech to text for voice and round video messages, done on the phone with a Vosk model
// downloaded on first use. Telegram Premium users keep the transcription of Telegram itself.
public class LumenTranscribe {

    public static final String ENABLED = "localTranscribe";

    private static final String MODEL_RU = "vosk-model-small-ru-0.22";
    private static final String MODEL_EN = "vosk-model-small-en-us-0.15";
    private static final String MODEL_BASE_URL = "https://alphacephei.com/vosk/models/";
    private static final long FILE_WAIT_MS = 60_000;
    private static final long FILE_POLL_MS = 300;
    private static final long CODEC_TIMEOUT_US = 10_000;

    private static final Object modelLock = new Object();
    private static Model model;
    private static String modelName;

    public static boolean enabled() {
        return LumenConfig.prefs().getBoolean(ENABLED, true);
    }

    // Ogg Opus decoding through MediaExtractor needs Android 10
    public static boolean isLocal(MessageObject messageObject) {
        return messageObject != null && enabled() && Build.VERSION.SDK_INT >= 29
            && !UserConfig.getInstance(messageObject.currentAccount).isPremium();
    }

    // Calls done on the UI thread with the text, an empty one when nothing was recognized
    public static void transcribe(MessageObject messageObject, Utilities.Callback<String> done) {
        final int account = messageObject.currentAccount;
        final File file = FileLoader.getInstance(account).getPathToMessage(messageObject.messageOwner);
        if (file == null || !file.exists()) {
            FileLoader.getInstance(account).loadFile(messageObject.getDocument(), messageObject, FileLoader.PRIORITY_HIGH, 0);
        }
        Utilities.globalQueue.postRunnable(() -> {
            String text = "";
            try {
                final File audio = waitForFile(file);
                final Model loaded = loadModel();
                if (audio != null && loaded != null) {
                    text = recognize(loaded, audio);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
            final String result = text;
            AndroidUtilities.runOnUIThread(() -> done.run(result));
        });
    }

    private static File waitForFile(File file) throws InterruptedException {
        if (file == null) {
            return null;
        }
        final long until = System.currentTimeMillis() + FILE_WAIT_MS;
        while (!file.exists() && System.currentTimeMillis() < until) {
            Thread.sleep(FILE_POLL_MS);
        }
        return file.exists() ? file : null;
    }

    private static Model loadModel() throws Exception {
        final String name = "ru".equals(LocaleController.getInstance().getCurrentLocale().getLanguage()) ? MODEL_RU : MODEL_EN;
        synchronized (modelLock) {
            if (model != null && name.equals(modelName)) {
                return model;
            }
            final File root = new File(ApplicationLoader.applicationContext.getFilesDir(), "lumen-speech");
            final File dir = new File(root, name);
            if (!new File(dir, "am").exists()) {
                AndroidUtilities.runOnUIThread(() -> android.widget.Toast.makeText(ApplicationLoader.applicationContext, LumenLang.get("TranscribeDownloading"), android.widget.Toast.LENGTH_LONG).show());
                download(MODEL_BASE_URL + name + ".zip", root);
            }
            if (model != null) {
                model.close();
            }
            model = new Model(dir.getAbsolutePath());
            modelName = name;
            return model;
        }
    }

    private static void download(String url, File root) throws Exception {
        final HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        try (InputStream input = new BufferedInputStream(connection.getInputStream());
             ZipInputStream zip = new ZipInputStream(input)) {
            final String rootPath = root.getCanonicalPath() + File.separator;
            final byte[] buffer = new byte[64 * 1024];
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                final File out = new File(root, entry.getName());
                if (!out.getCanonicalPath().startsWith(rootPath)) {
                    continue;
                }
                if (entry.isDirectory()) {
                    out.mkdirs();
                    continue;
                }
                out.getParentFile().mkdirs();
                try (OutputStream output = new FileOutputStream(out)) {
                    int read;
                    while ((read = zip.read(buffer)) > 0) {
                        output.write(buffer, 0, read);
                    }
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    // Decodes the audio track to 16-bit mono PCM and feeds it to the recognizer
    private static String recognize(Model model, File file) throws Exception {
        final MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        Recognizer recognizer = null;
        try {
            extractor.setDataSource(file.getAbsolutePath());
            MediaFormat format = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                final MediaFormat track = extractor.getTrackFormat(i);
                final String mime = track.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    extractor.selectTrack(i);
                    format = track;
                    break;
                }
            }
            if (format == null) {
                return "";
            }
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            codec.configure(format, null, null, 0);
            codec.start();
            int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            while (true) {
                if (!inputDone) {
                    final int inIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US);
                    if (inIndex >= 0) {
                        final ByteBuffer in = codec.getInputBuffer(inIndex);
                        final int size = extractor.readSampleData(in, 0);
                        if (size < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }
                final int outIndex = codec.dequeueOutputBuffer(info, CODEC_TIMEOUT_US);
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    final MediaFormat outFormat = codec.getOutputFormat();
                    channels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    sampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                } else if (outIndex >= 0) {
                    if (recognizer == null) {
                        recognizer = new Recognizer(model, sampleRate);
                    }
                    final ByteBuffer out = codec.getOutputBuffer(outIndex);
                    out.position(info.offset);
                    out.limit(info.offset + info.size);
                    final short[] mono = toMono(out.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer(), channels);
                    if (mono.length > 0) {
                        recognizer.acceptWaveForm(mono, mono.length);
                    }
                    codec.releaseOutputBuffer(outIndex, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break;
                    }
                }
            }
            if (recognizer == null) {
                return "";
            }
            final String text = new JSONObject(recognizer.getFinalResult()).optString("text").trim();
            return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
        } finally {
            if (recognizer != null) {
                recognizer.close();
            }
            if (codec != null) {
                codec.release();
            }
            extractor.release();
        }
    }

    private static short[] toMono(ShortBuffer samples, int channels) {
        final int count = Math.max(1, channels);
        final int frames = samples.remaining() / count;
        final short[] mono = new short[frames];
        for (int i = 0; i < frames; i++) {
            int sum = 0;
            for (int c = 0; c < count; c++) {
                sum += samples.get();
            }
            mono[i] = (short) (sum / count);
        }
        return mono;
    }
}
