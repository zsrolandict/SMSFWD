package hu.smsfwd.app;
import java.util.Properties;
final class EmailConfig {
    final String host, address, password;
    final int port;
    EmailConfig(String host, int port, String address, String password) {
        if (!host.matches("[A-Za-z0-9][A-Za-z0-9.-]*") || (!host.contains("."))) throw new IllegalArgumentException("Érvényes SMTP-kiszolgáló szükséges.");
        if (port != 465 && port != 587) throw new IllegalArgumentException("A port 465 (TLS) vagy 587 (STARTTLS) legyen.");
        if (!address.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new IllegalArgumentException("Érvényes küldő e-mail cím szükséges.");
        if (password.isEmpty()) throw new IllegalArgumentException("A küldő fiók alkalmazásjelszava szükséges.");
        this.host=host;this.port=port;this.address=address;this.password=password;
    }
    Properties properties() {
        Properties p=new Properties();
        p.setProperty("mail.smtp.host",host);p.setProperty("mail.smtp.port",String.valueOf(port));
        p.setProperty("mail.smtp.auth","true");p.setProperty("mail.smtp.ssl.checkserveridentity","true");
        p.setProperty("mail.smtp.connectiontimeout","15000");p.setProperty("mail.smtp.timeout","20000");p.setProperty("mail.smtp.writetimeout","20000");
        p.setProperty("mail.smtp.ssl.enable",String.valueOf(port==465));
        p.setProperty("mail.smtp.starttls.enable",String.valueOf(port==587));
        p.setProperty("mail.smtp.starttls.required",String.valueOf(port==587));
        return p;
    }
}
