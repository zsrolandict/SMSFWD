package hu.smsfwd.app;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;
import java.util.concurrent.*;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import javax.net.ssl.*;

public class GmailTransportTest {
    private SSLContext tls() throws Exception {
        java.security.cert.Certificate cert = CertificateFactory.getInstance("X.509")
            .generateCertificate(getClass().getResourceAsStream("/smtp-test-cert.pem"));
        String pem = new String(getClass().getResourceAsStream("/smtp-test-key.pem").readAllBytes(), StandardCharsets.US_ASCII)
            .replaceAll("-----[^-]+-----", "").replaceAll("\\s", "");
        PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
        KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
        store.load(null, null);
        store.setKeyEntry("server", key, "test-only".toCharArray(), new java.security.cert.Certificate[]{cert});
        KeyManagerFactory km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        km.init(store, "test-only".toCharArray());
        KeyStore trust = KeyStore.getInstance(KeyStore.getDefaultType());
        trust.load(null, null);
        trust.setCertificateEntry("test-ca", cert);
        TrustManagerFactory tm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tm.init(trust);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(km.getKeyManagers(), tm.getTrustManagers(), null);
        return context;
    }

    private static String line(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int next;
        while ((next = input.read()) != -1 && next != '\n') if (next != '\r') bytes.write(next);
        return bytes.toString(StandardCharsets.US_ASCII);
    }

    private static final class Request {
        final String firstLine, body;
        final Map<String, String> headers;
        Request(String firstLine, Map<String, String> headers, String body) {
            this.firstLine = firstLine; this.headers = headers; this.body = body;
        }
    }

    private final class Fixture implements AutoCloseable {
        final SSLContext tls;
        final SSLServerSocket server;
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final Future<Request> received;
        Fixture(int status) throws Exception {
            tls = tls();
            server = (SSLServerSocket) tls.getServerSocketFactory().createServerSocket(0);
            server.setSoTimeout(10000);
            received = executor.submit(() -> {
                try (SSLSocket socket = (SSLSocket) server.accept()) {
                    socket.setSoTimeout(10000);
                    InputStream input = new BufferedInputStream(socket.getInputStream());
                    String firstLine = line(input), header;
                    Map<String, String> headers = new HashMap<>();
                    while (!(header = line(input)).isEmpty()) {
                        int colon = header.indexOf(':');
                        headers.put(header.substring(0, colon).toLowerCase(Locale.ROOT), header.substring(colon + 1).trim());
                    }
                    int length = Integer.parseInt(headers.get("content-length"));
                    String body = new String(input.readNBytes(length), StandardCharsets.UTF_8);
                    if (status != 0) {
                        String location = status == 302 ? "Location: https://example.invalid/collect\r\n" : "";
                        String response = "HTTP/1.1 " + status + " Fixture\r\n" + location
                            + "Content-Type: application/json\r\nContent-Length: 2\r\nConnection: close\r\n\r\n{}";
                        socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
                        socket.getOutputStream().flush();
                    }
                    return new Request(firstLine, headers, body);
                }
            });
        }
        HttpsURLConnection connection() throws Exception {
            HttpsURLConnection connection = (HttpsURLConnection) new URL("https://127.0.0.1:" + server.getLocalPort()
                + "/gmail/v1/users/me/messages/send").openConnection(Proxy.NO_PROXY);
            // Trust only the local test certificate. Default hostname verification is retained.
            connection.setSSLSocketFactory(tls.getSocketFactory());
            return connection;
        }
        @Override public void close() throws Exception { server.close(); executor.shutdownNow(); }
    }

    @Test public void verifiedHttpsPostsUtf8MimeAndBearerOnlyToSendEndpoint() throws Exception {
        try (Fixture fixture = new Fixture(200)) {
            GmailTransport.send("test-only-token", "sender@example.com", "recipient@example.com", "SMS érkezett", "Árvíztűrő tükörfúrógép", fixture.connection());
            Request request = fixture.received.get(10, TimeUnit.SECONDS);
            assertEquals("POST /gmail/v1/users/me/messages/send HTTP/1.1", request.firstLine);
            assertEquals("Bearer test-only-token", request.headers.get("authorization"));
            assertEquals("application/json; charset=UTF-8", request.headers.get("content-type"));
            assertTrue(request.body.startsWith("{\"raw\":\""));
            String raw = request.body.substring(8, request.body.length() - 2);
            assertFalse(raw.contains("="));
            MimeMessage message = new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(Base64.getUrlDecoder().decode(raw)));
            assertEquals("sender@example.com", message.getFrom()[0].toString());
            assertEquals("recipient@example.com", message.getAllRecipients()[0].toString());
            assertEquals("SMS érkezett", message.getSubject());
            assertEquals("Árvíztűrő tükörfúrógép", message.getContent().toString().trim());
            assertFalse(request.body.contains("test-only-token"));
        }
    }

    @Test public void accountAndRecipientCannotInjectHeaders() throws Exception {
        for (String target : new String[]{"recipient@example.com\r\nBcc: victim@example.com", "missing-at", "a@example.com,b@example.com"}) {
            assertThrows(Exception.class, () -> GmailTransport.rawMessage("sender@example.com", target, "SMS", "Body"));
        }
        String raw = GmailTransport.rawMessage("sender@example.com", "recipient@example.com", "SMS\r\nBcc: victim@example.com", "Body");
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(Base64.getUrlDecoder().decode(raw)));
        assertNull(message.getHeader("Bcc"));
        assertEquals("SMS  Bcc: victim@example.com", message.getSubject());
    }

    @Test public void definitiveAuthorizationRejectionsRemainBlockedAndNeverRetry() throws Exception {
        for (int status : new int[]{401, 403, 429}) {
            try (Fixture fixture = new Fixture(status)) {
                GmailTransport.RejectedException error = assertThrows(GmailTransport.RejectedException.class,
                    () -> GmailTransport.send("test-only-token", "sender@example.com", "recipient@example.com", "SMS", "Body", fixture.connection()));
                assertEquals(status != 429, error.reconnectRequired);
                assertFalse(error.getMessage().contains("test-only-token"));
                assertNotNull(fixture.received.get(10, TimeUnit.SECONDS));
            }
        }
    }

    @Test public void redirectsServerFailuresAndLostAcknowledgementsStayUncertain() throws Exception {
        for (int status : new int[]{302, 503, 0}) {
            try (Fixture fixture = new Fixture(status)) {
                HttpsURLConnection connection = fixture.connection();
                Exception error = assertThrows(IOException.class,
                    () -> GmailTransport.send("test-only-token", "sender@example.com", "recipient@example.com", "SMS", "Body", connection));
                assertFalse(error instanceof GmailTransport.RejectedException);
                assertFalse(connection.getInstanceFollowRedirects());
                assertNotNull(fixture.received.get(10, TimeUnit.SECONDS));
            }
        }
    }
}
