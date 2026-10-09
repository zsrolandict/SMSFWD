package hu.smsfwd.app;

import android.content.Context;
import com.google.android.gms.auth.api.identity.ClearTokenRequest;
import com.google.android.gms.auth.api.identity.Identity;
import org.json.JSONObject;

/** Provider routing preserves existing encrypted SMTP credentials during Gmail connection changes. */
final class MailAccounts {
    static final class BlockedException extends Exception {
        BlockedException(String message) { super(message); }
    }

    interface PreparedSender { void send(String target, String subject, String body) throws Exception; }

    static String provider(Context context) {
        String selected = SmsStore.preferences(context).getString("emailProvider", "");
        if (selected.equals("gmail") || selected.equals("smtp") || selected.equals("none")) return selected;
        return EmailCredentials.metadata(context).optBoolean("configured") ? "smtp" : "none";
    }

    static JSONObject metadata(Context context) {
        JSONObject smtp = EmailCredentials.metadata(context);
        String provider = provider(context), gmailAddress = GmailCredentials.address(context);
        boolean smtpConfigured = smtp.optBoolean("configured"), gmailConnected = !gmailAddress.isEmpty();
        boolean reconnect = GmailCredentials.reconnectRequired(context);
        try {
            return new JSONObject().put("provider", provider)
                .put("configured", provider.equals("gmail") ? gmailConnected && !reconnect : provider.equals("smtp") && smtpConfigured)
                .put("address", provider.equals("gmail") ? gmailAddress : smtp.optString("address"))
                .put("host", smtp.optString("host")).put("port", smtp.optInt("port", 587))
                .put("smtpConfigured", smtpConfigured).put("smtpAddress", smtp.optString("address"))
                .put("gmailConnected", gmailConnected).put("gmailAddress", gmailAddress).put("reconnectRequired", reconnect);
        } catch (Exception ignored) { return new JSONObject(); }
    }

    static boolean isConfigured(Context context) { return metadata(context).optBoolean("configured"); }

    static void selectSmtp(Context context) throws Exception {
        if (!SmsStore.preferences(context).edit().putString("emailProvider", "smtp").commit()) {
            throw new IllegalStateException("Nem sikerült kiválasztani a küldő postafiókot.");
        }
    }

    /** Authorize before the history record becomes in_flight: a pre-send failure is safe to retry manually. */
    static PreparedSender prepare(Context context) throws BlockedException {
        if (provider(context).equals("gmail")) {
            String address = GmailCredentials.address(context);
            String token = GmailCredentials.freshToken(context);
            return (target, subject, body) -> {
                try { GmailTransport.send(token, address, target, subject, body); }
                catch (GmailTransport.RejectedException e) {
                    if (e.reconnectRequired) {
                        GmailCredentials.markReconnect(context);
                        // Expire the rejected cached token. Do not retry this send automatically.
                        try { Identity.getAuthorizationClient(context).clearToken(ClearTokenRequest.builder().setToken(token).build()); }
                        catch (Exception ignored) { /* The HTTP rejection remains definitive even if cache cleanup fails. */ }
                    }
                    throw e;
                }
            };
        }
        try {
            EmailConfig config = provider(context).equals("smtp") ? EmailCredentials.load(context) : null;
            if (config == null) throw new BlockedException("Állítsd be a küldő postafiókot a Beállításokban.");
            return (target, subject, body) -> MailTransport.send(config, target, subject, body);
        } catch (BlockedException e) { throw e; }
        catch (Exception e) { throw new BlockedException("A mentett postafiók nem olvasható. Állítsd be újra."); }
    }

    static void send(Context context, String target, String subject, String body) throws Exception {
        prepare(context).send(target, subject, body);
    }
}
