package com.foodie.api.auth;

import com.foodie.api.ApiException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** Provedor SMTP (estrutura pronta; requer SPRING_MAIL_HOST e MAIL_FROM). */
@Service
public class SmtpMailService implements MailProvider {
    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;

    public SmtpMailService(ObjectProvider<JavaMailSender> mailSender, @Value("${app.mail.from:}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public String provider() {
        return "smtp";
    }

    public boolean configured() {
        return mailSender.getIfAvailable() != null && from != null && !from.isBlank();
    }

    @Override
    public void send(String to, String subject, String body) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) throw new ApiException(503, "SMTP não configurado: defina SPRING_MAIL_HOST");
        if (from == null || from.isBlank()) throw new ApiException(503, "SMTP sem remetente: defina MAIL_FROM");
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        sender.send(message);
    }
}
