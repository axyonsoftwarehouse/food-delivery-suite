package com.foodie.api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Provedor padrão de desenvolvimento: registra a mensagem no log, sem enviar email real. */
@Service
public class LoggingMailService implements MailProvider {
    private static final Logger log = LoggerFactory.getLogger(LoggingMailService.class);
    private final boolean logBody;

    public LoggingMailService(@Value("${app.mail.log-body:false}") boolean logBody) {
        this.logBody = logBody;
    }

    @Override
    public String provider() {
        return "log";
    }

    @Override
    public void send(String to, String subject, String body) {
        // O corpo carrega links de verificação/reset. Não vai para o log por padrão.
        if (logBody) log.info("E-mail simulado | para={} | assunto={} | corpo={}", to, subject, body);
        else log.info("E-mail simulado | para={} | assunto={} | corpo omitido (MAIL_LOG_BODY=true liga)", to, subject);
    }
}
