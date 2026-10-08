package hu.smsfwd.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class EmailConfigTest {
    @Test public void startTlsCannotDowngrade() {
        java.util.Properties p=new EmailConfig("smtp.gmail.com",587,"sender@example.com","test-only").properties();
        assertEquals("true",p.getProperty("mail.smtp.starttls.required"));assertEquals("true",p.getProperty("mail.smtp.ssl.checkserveridentity"));assertNull(p.getProperty("mail.smtp.ssl.trust"));
    }
    @Test public void implicitTlsIsEnabled() {assertEquals("true",new EmailConfig("smtp.gmail.com",465,"sender@example.com","test-only").properties().getProperty("mail.smtp.ssl.enable"));}
    @Test(expected=IllegalArgumentException.class) public void plaintextPortRejected() {new EmailConfig("smtp.gmail.com",25,"sender@example.com","test-only");}
    @Test(expected=IllegalArgumentException.class) public void missingPasswordRejected() {new EmailConfig("smtp.gmail.com",587,"sender@example.com","");}
    @Test(expected=IllegalArgumentException.class) public void headerInjectionRejected() {new EmailConfig("smtp.gmail.com",587,"sender@example.com\r\nBcc: victim@example.com","test-only");}
}
