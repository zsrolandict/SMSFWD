package hu.smsfwd.app;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.concurrent.*;
import javax.net.ssl.*;
import javax.mail.Session;

public class MailTransportTest {
    private SSLContext tls() throws Exception {
        java.security.cert.Certificate cert=CertificateFactory.getInstance("X.509").generateCertificate(getClass().getResourceAsStream("/smtp-test-cert.pem"));
        String pem=new String(getClass().getResourceAsStream("/smtp-test-key.pem").readAllBytes(),StandardCharsets.US_ASCII).replaceAll("-----[^-]+-----","").replaceAll("\\s","");
        PrivateKey key=KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(java.util.Base64.getDecoder().decode(pem)));
        KeyStore store=KeyStore.getInstance(KeyStore.getDefaultType());store.load(null,null);store.setKeyEntry("server",key,"test-only".toCharArray(),new java.security.cert.Certificate[]{cert});
        KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(store,"test-only".toCharArray());
        KeyStore trust=KeyStore.getInstance(KeyStore.getDefaultType());trust.load(null,null);trust.setCertificateEntry("test-ca",cert);
        TrustManagerFactory tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tm.init(trust);
        SSLContext context=SSLContext.getInstance("TLS");context.init(km.getKeyManagers(),tm.getTrustManagers(),null);return context;
    }
    @Test public void smtpOverVerifiedTlsSendsRealMimeMessage() throws Exception {
        SSLContext tls=tls();
        try(SSLServerSocket server=(SSLServerSocket)tls.getServerSocketFactory().createServerSocket(0)) {
            server.setSoTimeout(15000);
            ExecutorService executor=Executors.newSingleThreadExecutor();
            Future<String> message=executor.submit(()-> {
                try(SSLSocket socket=(SSLSocket)server.accept()) {
                    socket.setSoTimeout(15000);BufferedReader input=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));PrintWriter out=new PrintWriter(socket.getOutputStream(),true,StandardCharsets.UTF_8);
                    out.println("220 test SMTP");StringBuilder received=new StringBuilder();String line;int login=0;boolean data=false;
                    while((line=input.readLine())!=null) {
                        if(data){if(line.equals(".")){data=false;out.println("250 accepted");}else received.append(line).append('\n');continue;}
                        if(login==1){login=2;out.println("334 UGFzc3dvcmQ6");continue;}
                        if(login==2){login=0;out.println("235 authenticated");continue;}
                        if(line.startsWith("EHLO")){out.println("250-test");out.println("250 AUTH LOGIN PLAIN");}
                        else if(line.startsWith("AUTH LOGIN")){login=1;out.println("334 VXNlcm5hbWU6");}
                        else if(line.startsWith("AUTH PLAIN")){out.println("235 authenticated");}
                        else if(line.startsWith("MAIL FROM") || line.startsWith("RCPT TO")){received.append(line).append('\n');out.println("250 OK");}
                        else if(line.equals("DATA")){data=true;out.println("354 continue");}
                        else if(line.equals("QUIT")){out.println("221 bye");break;}
                        else out.println("250 OK");
                    }
                    return received.toString();
                }
            });
            try {
                EmailConfig config=new EmailConfig("127.0.0.1",465,"sender@example.com","test-only");java.util.Properties p=config.properties();
                p.put("mail.smtp.ssl.socketFactory",tls.getSocketFactory());p.setProperty("mail.smtp.ssl.socketFactory.fallback","false");
                MailTransport.send(config,"recipient@example.com","SMS teszt","Eredeti SMS tartalom",Session.getInstance(p),server.getLocalPort());
                String received=message.get(15,TimeUnit.SECONDS);
                assertTrue(received.contains("RCPT TO:<recipient@example.com>"));assertTrue(received.contains("Eredeti SMS tartalom"));assertTrue(received.contains("From: sender@example.com"));
            } finally {executor.shutdownNow();}
        }
    }
}
