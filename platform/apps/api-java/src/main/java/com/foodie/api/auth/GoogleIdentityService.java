package com.foodie.api.auth;

import com.foodie.api.ApiException;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Verifica o ID token do Google (Google Identity Services) consultando o endpoint
 * {@code tokeninfo}. Fica desativado enquanto {@code app.auth.google.client-id} não estiver definido.
 */
@Service
public class GoogleIdentityService {
    private final RestClient client;
    private final String clientId;

    public GoogleIdentityService(@Value("${app.auth.google.client-id:}") String clientId,
                                 @Value("${app.auth.google.base-url:https://oauth2.googleapis.com}") String baseUrl) {
        this.clientId = clientId;
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    public boolean configured() {
        return clientId != null && !clientId.isBlank();
    }

    public GoogleIdentity verify(String idToken) {
        if (!configured()) throw new ApiException(503, "Login com Google não configurado: defina GOOGLE_CLIENT_ID");
        if (idToken == null || idToken.isBlank()) throw new ApiException(400, "Token do Google obrigatório");
        Map<String, Object> info;
        try {
            info = client.get()
                .uri(builder -> builder.path("/tokeninfo").queryParam("id_token", idToken).build())
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (RestClientResponseException error) {
            throw new ApiException(401, "Token do Google inválido");
        }
        if (info == null) throw new ApiException(401, "Token do Google inválido");
        if (!clientId.equals(String.valueOf(info.get("aud")))) {
            throw new ApiException(401, "Token do Google não corresponde a este aplicativo");
        }
        String email = info.get("email") == null ? null : String.valueOf(info.get("email"));
        boolean verified = Boolean.TRUE.equals(info.get("email_verified")) || "true".equals(String.valueOf(info.get("email_verified")));
        if (email == null || !verified) throw new ApiException(401, "Email do Google não verificado");
        String name = info.get("name") == null ? email : String.valueOf(info.get("name"));
        return new GoogleIdentity(email.toLowerCase(Locale.ROOT), name, true);
    }

    public record GoogleIdentity(String email, String name, boolean emailVerified) {}
}
