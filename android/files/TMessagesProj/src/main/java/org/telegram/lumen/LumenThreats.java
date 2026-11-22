package org.telegram.lumen;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Pattern;

public final class LumenThreats {
    public static final String ENABLED = "isThreatProtectionEnabled";
    public static final String PHRASES = "threatPhrases";
    public static final String EXCEPTIONS = "threatExceptions";
    public static final String TRUST_CONTACTS = "shouldTrustContacts";
    public static final String MODE = "threatMode";
    public static final String MATCH = "threatMatch";
    private static final int LOG_LIMIT = 200;
    public static final int UPDATE_MASK = 1 << 27;

    private LumenThreats() { }

    private static SharedPreferences prefs(int account) {
        return ApplicationLoader.applicationContext.getSharedPreferences("lumen_threats_"
            + UserConfig.getInstance(account).getClientUserId(), Context.MODE_PRIVATE);
    }

    public static synchronized JSONArray log(int account) {
        try { return new JSONArray(prefs(account).getString("log", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    public static synchronized void clear(int account) {
        prefs(account).edit().remove("log").apply();
        refresh();
    }

    public static synchronized void process(int account, ArrayList<MessageObject> messages) {
        if (!LumenConfig.getBool(ENABLED) || messages == null) return;
        final int now = ConnectionsManager.getInstance(account).getCurrentTime();
        for (MessageObject message : messages) {
            if (message == null || message.messageOwner == null || message.isOutOwner()
                || message.messageOwner.action != null && !(message.messageOwner.action instanceof TLRPC.TL_messageActionEmpty)
                || now - message.messageOwner.date > 120) continue;
            final long peer = message.getDialogId();
            if (!DialogObject.isUserDialog(peer) || peer == UserConfig.getInstance(account).getClientUserId()
                || UserObject.isService(peer)) continue;
            final TLRPC.User user = MessagesController.getInstance(account).getUser(peer);
            if (user == null || user.bot || user.deleted || user.self
                || user.contact && LumenConfig.prefs().getBoolean(TRUST_CONTACTS, true)) continue;
            boolean excluded = false;
            for (String id : LumenConfig.getString(EXCEPTIONS, "").split("[\\s,;]+")) {
                if (id.equals(String.valueOf(peer))) { excluded = true; break; }
            }
            if (excluded) continue;
            final String text = normalize(message.messageOwner.message);
            String matched = null;
            final boolean substring = "substring".equals(LumenConfig.getString(MATCH, "word"));
            final String[] phrases = LumenConfig.getString(PHRASES, "").split("\n");
            for (int i = 0; i < Math.min(LOG_LIMIT, phrases.length); i++) {
                String phrase = normalize(phrases[i].trim());
                if (phrase.isEmpty() || phrase.length() > 200) continue;
                if (substring ? text.contains(phrase) : Pattern.compile("(^|[^\\p{L}\\p{N}_])"
                    + Pattern.quote(phrase) + "($|[^\\p{L}\\p{N}_])").matcher(text).find()) {
                    matched = phrases[i].trim(); break;
                }
            }
            if (matched == null || find(account, peer, message.getId()) != null) continue;
            final JSONObject event = new JSONObject();
            try {
                event.put("peer", peer).put("mid", message.getId()).put("phrase", matched)
                    .put("at", System.currentTimeMillis()).put("status", "hidden").put("hidden", true);
            } catch (Exception ignored) { continue; }
            save(account, event);
            if ("block".equals(LumenConfig.getString(MODE, "hide"))) changeBlock(account, event, true, null);
        }
    }

    public static boolean isHidden(int account, long peer, int mid) {
        if (!LumenConfig.getBool(ENABLED)) return false;
        final JSONObject event = find(account, peer, mid);
        return event != null && event.optBoolean("hidden", true);
    }

    public static synchronized JSONObject find(int account, long peer, int mid) {
        final JSONArray entries = log(account);
        for (int i = 0; i < entries.length(); i++) {
            final JSONObject entry = entries.optJSONObject(i);
            if (entry != null && entry.optLong("peer") == peer && entry.optInt("mid") == mid) return entry;
        }
        return null;
    }

    public static void reveal(int account, JSONObject event) {
        try { event.put("hidden", false); } catch (Exception ignored) { }
        save(account, event);
        refresh();
    }

    public static void refresh() {
        AndroidUtilities.runOnUIThread(() -> {
            for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
                if (!UserConfig.getInstance(account).isClientActivated()) continue;
                NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK);
                NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload);
            }
        });
    }

    public static void changeBlock(int account, JSONObject event, boolean block, Runnable onDone) {
        final MessagesController controller = MessagesController.getInstance(account);
        final long peer = event.optLong("peer");
        final TLRPC.InputPeer input = controller.getInputPeer(peer);
        final long self = UserConfig.getInstance(account).getClientUserId();
        if (input == null) return;
        final org.telegram.tgnet.TLObject request;
        if (block) {
            final TLRPC.TL_contacts_block req = new TLRPC.TL_contacts_block(); req.id = input; request = req;
        } else {
            final TLRPC.TL_contacts_unblock req = new TLRPC.TL_contacts_unblock(); req.id = input; request = req;
        }
        ConnectionsManager.getInstance(account).sendRequest(request, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (UserConfig.getInstance(account).getClientUserId() != self) return;
            final boolean ok = error == null && response instanceof TLRPC.TL_boolTrue;
            try { event.put("status", ok ? block ? "blocked" : "unblocked" : "failed"); }
            catch (Exception ignored) { }
            save(account, event);
            if (ok) {
                final boolean wasBlocked = controller.blockePeers.indexOfKey(peer) >= 0;
                if (block) controller.blockePeers.put(peer, 1); else controller.blockePeers.delete(peer);
                if (controller.totalBlockedCount >= 0 && wasBlocked != block) controller.totalBlockedCount += block ? 1 : -1;
                NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.blockedUsersDidLoad);
            }
            if (onDone != null) onDone.run();
        }));
    }

    public static MessageObject display(MessageObject original) {
        if (original == null || !isHidden(original.currentAccount, original.getDialogId(), original.getId())) return original;
        TLRPC.Message copy;
        try {
            final SerializedData output = new SerializedData(original.messageOwner.getObjectSize());
            original.messageOwner.serializeToStream(output);
            final SerializedData input = new SerializedData(output.toByteArray());
            copy = TLRPC.Message.TLdeserialize(input, input.readInt32(true), true);
            output.cleanup(); input.cleanup();
        } catch (Exception e) {
            copy = new TLRPC.TL_message();
            copy.id = original.getId();
            copy.date = original.messageOwner.date;
            copy.peer_id = original.messageOwner.peer_id;
            copy.from_id = original.messageOwner.from_id;
            copy.dialog_id = original.getDialogId();
        }
        copy.message = LumenLang.get("ThreatHidden") + " · " + LumenLang.get("ThreatLog");
        copy.media = new TLRPC.TL_messageMediaEmpty();
        copy.entities.clear();
        copy.reply_markup = null;
        copy.grouped_id = 0;
        return new MessageObject(original.currentAccount, copy, true, false);
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    private static synchronized void save(int account, JSONObject event) {
        final JSONArray result = new JSONArray(); result.put(event);
        final JSONArray old = log(account);
        for (int i = 0; i < old.length() && result.length() < LOG_LIMIT; i++) {
            final JSONObject entry = old.optJSONObject(i);
            if (entry != null && (entry.optLong("peer") != event.optLong("peer")
                || entry.optInt("mid") != event.optInt("mid"))) result.put(entry);
        }
        prefs(account).edit().putString("log", result.toString()).apply();
    }
}
