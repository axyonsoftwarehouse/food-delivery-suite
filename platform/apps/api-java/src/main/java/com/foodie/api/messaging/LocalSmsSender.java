package com.foodie.api.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Provedor padrão de desenvolvimento: registra a mensagem no log, sem enviar SMS real.
 * Suficiente para homologação e testes quando nenhum provedor externo está configurado.
 */
@Service
public class LocalSmsSender implements SmsSender {
    private static final Logger log = LoggerFactory.getLogger(LocalSmsSender.class);

    @Override
    public String provider() {
        return "local";
    }

    @Override
    public void send(String phone, String message) {
        log.info("[SMS local] para {}: {}", phone, message);
    }
}
