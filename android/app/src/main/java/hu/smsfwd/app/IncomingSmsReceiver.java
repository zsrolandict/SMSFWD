package hu.smsfwd.app;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.telephony.SubscriptionManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.UUID;

public class IncomingSmsReceiver extends BroadcastReceiver {
    static String normalize(String value) { return RuleMatcher.normalize(value); }
    @Override public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        PendingResult pending = goAsync();
        new Thread(() -> { try { process(context.getApplicationContext(), intent); } finally { pending.finish(); } }, "smsfwd-receive").start();
    }
    private void process(Context context, Intent intent) {
        synchronized (SmsStore.class) {
            SharedPreferences prefs = SmsStore.preferences(context);
            if (!prefs.getBoolean("active", false)) return;
            try {
                SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
                if (messages == null || messages.length == 0) return;
                String sender = messages[0].getDisplayOriginatingAddress();
                if (sender == null) sender = "Ismeretlen";
                StringBuilder combined = new StringBuilder();
                for (SmsMessage m : messages) if (m.getMessageBody() != null) combined.append(m.getMessageBody());
                String body = combined.toString();
                if (body.startsWith("SMSFWD | ")) return;
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                digest.update((sender + ":" + messages[0].getTimestampMillis() + ":" + intent.getIntExtra("subscription", -1)).getBytes(StandardCharsets.UTF_8));
                Object[] pdus = (Object[]) intent.getExtras().get("pdus");
                if (pdus != null) for (Object pdu : pdus) if (pdu instanceof byte[]) digest.update((byte[]) pdu);
                String fingerprint = android.util.Base64.encodeToString(digest.digest(), android.util.Base64.NO_WRAP);
                JSONArray seen = new JSONArray(prefs.getString("seen", "[]"));
                for (int i = 0; i < seen.length(); i++) if (fingerprint.equals(seen.optString(i))) return;
                JSONArray rules = new JSONArray(prefs.getString("rules", "[]"));
                HashSet<String> destinations = new HashSet<>();
                for (int i = 0; i < rules.length(); i++) {
                    JSONObject rule = rules.optJSONObject(i);
                    if (rule == null || !rule.optBoolean("enabled") || rule.optBoolean("sample")) continue;
                    String keyword = rule.optString("keyword");
                    if (!RuleMatcher.matches(rule.optString("senderType"), rule.optString("sender"), keyword, sender, body)) continue;
                    String channel = rule.optString("channel"), target = rule.optString("target").trim();
                    if (channel.equals("sms")) target = normalize(target);
                    if (!destinations.add(channel + ":" + target)) continue;
                    JSONObject record = new JSONObject().put("id", UUID.randomUUID().toString()).put("sender", sender).put("body", body).put("target", target).put("channel", channel).put("ruleName", rule.optString("name")).put("at", Instant.now().toString()).put("simulated", false).put("started", System.currentTimeMillis());
                    if (channel.equals("email")) {
                        boolean configured=EmailCredentials.metadata(context).optBoolean("configured");
                        record.put("status",configured ? "pending" : "blocked").put("error",configured ? "" : "Állítsd be a küldő postafiókot a Beállításokban.");
                        if(SmsStore.add(context,record) && configured)EmailWorker.enqueue(context,record.getString("id"));
                        continue;
                    }
                    if (!target.matches("\\+[1-9]\\d{7,14}") || target.equals(normalize(sender))) {
                        record.put("status", "blocked").put("error", "Érvénytelen cél vagy visszaküldés a feladónak."); SmsStore.add(context, record); continue;
                    }
                    if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                        record.put("status", "blocked").put("error", "Hiányzik az SMS-küldési engedély."); SmsStore.add(context, record); continue;
                    }
                    int subId = SubscriptionManager.getDefaultSmsSubscriptionId();
                    if (!SubscriptionManager.isValidSubscriptionId(subId)) {
                        record.put("status", "blocked").put("error", "Állíts be alapértelmezett SMS-SIM-et a telefonon."); SmsStore.add(context, record); continue;
                    }
                    SmsManager manager = SmsManager.getSmsManagerForSubscriptionId(subId);
                    ArrayList<String> parts = manager.divideMessage("SMSFWD | " + sender + "\n" + body);
                    String day = LocalDate.now().toString();
                    int used = day.equals(prefs.getString("budgetDay", "")) ? prefs.getInt("budgetUsed", 0) : 0;
                    int limit = prefs.getInt("limit", 20);
                    if (used + parts.size() > limit) {
                        record.put("status", "blocked").put("error", "A napi SMS-szegmenslimit nem engedi a küldést."); SmsStore.add(context, record); continue;
                    }
                    record.put("status", "in_flight").put("parts", parts.size());
                    if (!SmsStore.add(context, record)) continue;
                    if (!prefs.edit().putString("budgetDay", day).putInt("budgetUsed", used + parts.size()).commit()) {
                        SmsStore.setState(context, record.getString("id"), "blocked", "Nem sikerült a költségkeretet tárolni."); continue;
                    }
                    ArrayList<PendingIntent> callbacks = new ArrayList<>();
                    for (int part = 0; part < parts.size(); part++) {
                        Intent callback = new Intent(context, SmsSentReceiver.class).setData(Uri.parse("smsfwd://sent/" + record.getString("id") + "/" + part)).putExtra("deliveryId", record.getString("id")).putExtra("part", part);
                        callbacks.add(PendingIntent.getBroadcast(context, 0, callback, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE));
                    }
                    try { manager.sendMultipartTextMessage(target, null, parts, callbacks, null); }
                    catch (Exception ex) { SmsStore.setState(context, record.getString("id"), "unknown", "Nem igazolható a küldés: " + ex.getClass().getSimpleName() + ". Nem ismételjük automatikusan."); }
                }
                JSONArray nextSeen = new JSONArray(); nextSeen.put(fingerprint);
                for (int i = 0; i < Math.min(199, seen.length()); i++) nextSeen.put(seen.get(i));
                prefs.edit().putString("seen", nextSeen.toString()).commit();
            } catch (Exception ex) {
                try { SmsStore.add(context, new JSONObject().put("id", UUID.randomUUID().toString()).put("sender", "Android").put("target", "").put("channel", "sms").put("body", "SMS-feldolgozási hiba").put("at", Instant.now().toString()).put("status", "failed").put("error", ex.getClass().getSimpleName()).put("simulated", false)); } catch (Exception ignored) {}
            }
        }
    }
}
