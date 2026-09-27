package com.foodie.api.auth;

import com.foodie.api.ApiException;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Verifica o access token do Facebook consultando o Graph API (E47). */
@Service
public class FacebookIdentityService {
    private final RestClient client;
    private final String appId;

    public FacebookIdentityService(@Value("${app.auth.facebook.app-id:}") String appId,
                                   @Value("${app.auth.facebook.base-url:https://graph.facebook.com}") String baseUrl) {
        this.appId = appId;
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    public boolean configured() {
        return appId != null && !appId.isBlank();
    }

    public Identity verify(String accessToken) {
        if (!configured()) throw new ApiException(503, "Login com Facebook não configurado: defina FACEBOOK_APP_ID");
        if (accessToken == null || accessToken.isBlank()) throw new ApiException(400, "Token do Facebook obrigatório");
        Map<String, Object> info;
        try {
            info = client.get()
                .uri(builder -> builder.path("/me").queryParam("fields", "id,name,email").queryParam("access_token", accessToken).build())
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (RestClientResponseException error) {
            throw new ApiException(401, "Token do Facebook inválido");
        }
        if (info == null || info.get("email") == null) throw new ApiException(401, "Email do Facebook indisponível");
        String email = String.valueOf(info.get("email")).toLowerCase(Locale.ROOT);
        String name = info.get("name") == null ? email : String.valueOf(info.get("name"));
        return new Identity(email, name);
    }

    public record Identity(String email, String name) {}
}
