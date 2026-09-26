package com.foodie.api.notifications;

import java.util.Map;

public interface FcmSender {
    boolean configured();

    /**
     * Envia uma notificação a um token de dispositivo.
     *
     * @throws InvalidFcmTokenException quando o token está inválido/desregistrado e deve ser descartado
     */
    void send(String deviceToken, String title, String body, Map<String, Object> data);

    class InvalidFcmTokenException extends RuntimeException {
        public InvalidFcmTokenException(String message) {
            super(message);
        }
    }
}
