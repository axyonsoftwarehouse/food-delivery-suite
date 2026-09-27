package com.foodie.api.messaging;

import com.foodie.api.ApiException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Provedor de SMS genérico via webhook HTTP (E46). Envia {@code {to, message}} para a URL
 * configurada em {@code SMS_GENERIC_URL}, com token opcional em {@code SMS_GENERIC_TOKEN}.
 */
@Service
public class WebhookSmsSender implements SmsSender {
    private final String url;
    private final String token;
    private final RestClient client = RestClient.create();

    public WebhookSmsSender(@Value("${app.sms.generic.url:}") String url, @Value("${app.sms.generic.token:}") String token) {
        this.url = url;
        this.token = token;
    }

    @Override
    public String provider() {
        return "generic";
    }

    @Override
    public void send(String phone, String message) {
        if (url == null || url.isBlank()) throw new ApiException(503, "SMS genérico não configurado: defina SMS_GENERIC_URL");
        try {
            client.post().uri(url)
                .headers(headers -> { if (token != null && !token.isBlank()) headers.setBearerAuth(token); })
                .body(Map.of("to", phone, "message", message))
                .retrieve().toBodilessEntity();
        } catch (RestClientResponseException error) {
            throw new ApiException(502, "Falha ao enviar SMS pelo provedor genérico");
        }
    }
}
