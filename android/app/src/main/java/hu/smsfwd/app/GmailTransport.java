package hu.smsfwd.app;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.Properties;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.net.ssl.HttpsURLConnection;

/** Sends directly through Gmail API; never launches a composer or retries an uncertain send. */
final class GmailTransport {
    private static final String SEND_URL = "https://gmail.googleapis.com/gmail/v1/users/me/messages/send";

    static final class RejectedException extends Exception {
        final boolean reconnectRequired;
        RejectedException(int status) {
            super(rejectionMessage(status));
            reconnectRequired = status == 401 || status == 403;
        }
    }

    static String rawMessage(String address, String target, String subject, String body) throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setFrom(strictAddress(address));
        message.setRecipient(Message.RecipientType.TO, strictAddress(target));
        message.setSubject(subject.replaceAll("[\\r\\n]", " "), "UTF-8");
        message.setText(body, "UTF-8");
        message.setSentDate(new Date());
        message.saveChanges();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        message.writeTo(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
    }

    private static InternetAddress strictAddress(String value) throws Exception {
        if (value == null || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Érvénytelen e-mail cím.");
        }
        InternetAddress address = new InternetAddress(value, true);
        address.validate();
        if (address.isGroup() || address.getPersonal() != null) {
            throw new IllegalArgumentException("Egyetlen e-mail cím szükséges.");
        }
        return address;
    }

    static void send(String accessToken, String address, String target, String subject, String body) throws Exception {
        send(accessToken, address, target, subject, body, (HttpsURLConnection) new URL(SEND_URL).openConnection());
    }

    // Injectable verified HTTPS connection is used only by the local transport test.
    static void send(String accessToken, String address, String target, String subject, String body,
                     HttpsURLConnection connection) throws Exception {
        if (accessToken == null || accessToken.isEmpty() || accessToken.indexOf('\r') >= 0 || accessToken.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Hiányzik a Google küldési engedélye.");
        }
        byte[] payload = ("{\"raw\":\"" + rawMessage(address, target, subject, body) + "\"}").getBytes(StandardCharsets.UTF_8);
        try {
            // Keep default TLS trust/hostname checks and never forward a bearer token to a redirect.
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(payload.length);
            try (OutputStream output = connection.getOutputStream()) { output.write(payload); }
            int status = connection.getResponseCode();
            if (status == 200) return; // Gmail's send endpoint has acknowledged acceptance.
            if (status >= 400 && status < 500) throw new RejectedException(status);
            // Disconnects, 5xx, and unexpected redirects never trigger automatic resend.
            throw new java.io.IOException("Gmail API: HTTP " + status);
        } finally {
            connection.disconnect();
        }
    }

    private static String rejectionMessage(int status) {
        if (status == 401) return "A Gmail elutasította a Google-engedélyt. Kapcsold össze újra a Google-fiókot; az e-mail nem lett elküldve.";
        if (status == 403) return "A Gmail nem engedélyezte a küldést. Ellenőrizd a Gmail API és az OAuth-beállításokat, majd kapcsold össze újra a fiókot; az e-mail nem lett elküldve.";
        if (status == 429) return "A Gmail küldési korlátot jelzett. Várj, majd próbáld újra kézzel az Előzményekben; az e-mail nem lett elküldve.";
        return "A Gmail elutasította a küldést (HTTP " + status + "). Ellenőrizd a címzettet és a fiókot; az e-mail nem lett elküldve.";
    }
}
