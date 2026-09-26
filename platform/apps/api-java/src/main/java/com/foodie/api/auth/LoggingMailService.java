package com.foodie.api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Provedor padrão de desenvolvimento: registra a mensagem no log, sem enviar email real. */
@Service
public class LoggingMailService implements MailProvider {
    private static final Logger log = LoggerFactory.getLogger(LoggingMailService.class);

    @Override
    public String provider() {
        return "log";
    }

    @Override
    public void send(String to, String subject, String body) {
        log.info("E-mail simulado | para={} | assunto={} | corpo={}", to, subject, body);
    }
}
