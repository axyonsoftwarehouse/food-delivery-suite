package com.foodie.api.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Provedor padrão de desenvolvimento: registra a mensagem no log, sem enviar SMS real.
 * Suficiente para homologação e testes quando nenhum provedor externo está configurado.
 */
@Service
public class LocalSmsSender implements SmsSender {
    private static final Logger log = LoggerFactory.getLogger(LocalSmsSender.class);
    private final boolean logBody;

    public LocalSmsSender(@Value("${app.sms.log-body:false}") boolean logBody) {
        this.logBody = logBody;
    }

    @Override
    public String provider() {
        return "local";
    }

    @Override
    public void send(String phone, String message) {
        // A mensagem carrega o código OTP. Não vai para o log por padrão.
        if (logBody) log.info("[SMS local] para {}: {}", phone, message);
        else log.info("[SMS local] para {}: mensagem omitida (SMS_LOG_BODY=true liga)", phone);
    }
}
