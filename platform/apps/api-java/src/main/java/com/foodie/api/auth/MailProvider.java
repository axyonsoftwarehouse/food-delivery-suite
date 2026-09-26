package com.foodie.api.auth;

/** Provedor de entrega de email. O ativo é escolhido por {@code app.mail.provider}. */
public interface MailProvider {
    String provider();

    void send(String to, String subject, String body);
}
