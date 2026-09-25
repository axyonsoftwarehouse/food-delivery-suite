package com.foodie.api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LoggingMailService implements MailService {
    private static final Logger log = LoggerFactory.getLogger(LoggingMailService.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("E-mail simulado | para={} | assunto={} | corpo={}", to, subject, body);
    }
}
