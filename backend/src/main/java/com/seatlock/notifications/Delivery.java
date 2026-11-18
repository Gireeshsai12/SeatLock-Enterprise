package com.seatlock.notifications;

import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Delivery {
    public record Message(UUID id, String recipient, String subject, String body) {}
    private final JdbcTemplate jdbc;
    private final JavaMailSender smtp;
    private final String sender;

    public Delivery(JdbcTemplate jdbc, ObjectProvider<JavaMailSender> smtp, @Value("${seatlock.smtp-from}") String sender) {
        this.jdbc = jdbc;
        this.smtp = sender.isBlank() ? null : smtp.getObject();
        this.sender = sender;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void deliver(Message message) {
        int claimed = jdbc.update("""
            insert into notification_deliveries(id,recipient,subject,body,delivery_mode)
            values (?,?,?,?,?) on conflict do nothing
            """,message.id(),message.recipient(),message.subject(),message.body(),smtp == null ? "SIMULATED" : "SMTP");
        if (claimed == 1 && smtp != null) {
            var mail = new SimpleMailMessage();
            mail.setFrom(sender);
            mail.setTo(message.recipient());
            mail.setSubject(message.subject());
            mail.setText(message.body());
            smtp.send(mail);
        }
    }
}
