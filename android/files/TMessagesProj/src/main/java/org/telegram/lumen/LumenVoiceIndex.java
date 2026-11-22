/*
 * This file is part of LumenGram, a modification of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */
package org.telegram.lumen;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.Utilities;

import java.util.ArrayList;
import java.util.Locale;

// Text of every transcribed voice and round video message, kept on the phone so it can be searched.
public class LumenVoiceIndex {

    private static final int MAX_RESULTS = 200;

    public static class Entry {
        public long dialogId;
        public int messageId;
        public int date;
        public String text;
    }

    private static class Helper extends SQLiteOpenHelper {
        Helper() {
            super(ApplicationLoader.applicationContext, "lumen_voice.db", null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE voices (account INTEGER, dialog_id INTEGER, msg_id INTEGER, date INTEGER, "
                + "text TEXT, text_lower TEXT, PRIMARY KEY (account, dialog_id, msg_id))");
            db.execSQL("CREATE INDEX voices_date ON voices (account, date)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        }
    }

    private static Helper helper;

    private static synchronized SQLiteDatabase db() {
        if (helper == null) {
            helper = new Helper();
        }
        return helper.getWritableDatabase();
    }

    public static void save(MessageObject message, String text) {
        if (message == null || message.messageOwner == null || TextUtils.isEmpty(text)) {
            return;
        }
        final int account = message.currentAccount;
        final long dialogId = message.getDialogId();
        final int messageId = message.getId();
        final int date = message.messageOwner.date;
        Utilities.globalQueue.postRunnable(() -> {
            try {
                final ContentValues values = new ContentValues();
                values.put("account", account);
                values.put("dialog_id", dialogId);
                values.put("msg_id", messageId);
                values.put("date", date);
                values.put("text", text);
                values.put("text_lower", text.toLowerCase(Locale.ROOT));
                db().insertWithOnConflict("voices", null, values, SQLiteDatabase.CONFLICT_REPLACE);
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    // Newest first; an empty query lists everything. Runs on the caller's thread.
    public static ArrayList<Entry> search(int account, String query) {
        final ArrayList<Entry> result = new ArrayList<>();
        final String needle = "%" + query.trim().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "") + "%";
        try (Cursor cursor = db().rawQuery(
            "SELECT dialog_id, msg_id, date, text FROM voices WHERE account = ? AND text_lower LIKE ? ORDER BY date DESC LIMIT " + MAX_RESULTS,
            new String[]{String.valueOf(account), needle})) {
            while (cursor.moveToNext()) {
                final Entry entry = new Entry();
                entry.dialogId = cursor.getLong(0);
                entry.messageId = cursor.getInt(1);
                entry.date = cursor.getInt(2);
                entry.text = cursor.getString(3);
                result.add(entry);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return result;
    }
}
