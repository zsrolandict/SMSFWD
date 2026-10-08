package hu.smsfwd.app;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.Message;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
final class MailTransport {
    static void send(EmailConfig config,String target,String subject,String body) throws Exception {
        send(config,target,subject,body,Session.getInstance(config.properties()),config.port);
    }
    static void send(EmailConfig config,String target,String subject,String body,Session session,int port) throws Exception {
        MimeMessage message=new MimeMessage(session);
        message.setFrom(new InternetAddress(config.address,true));
        message.setRecipient(Message.RecipientType.TO,new InternetAddress(target,true));
        message.setSubject(subject.replaceAll("[\\r\\n]"," "),"UTF-8");message.setText(body,"UTF-8");
        message.setSentDate(new java.util.Date());message.saveChanges();
        try(Transport transport=session.getTransport("smtp")) {
            transport.connect(config.host,port,config.address,config.password);
            transport.sendMessage(message,message.getAllRecipients());
        }
    }
}
