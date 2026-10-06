package com.foodie.api.auth;

import com.foodie.api.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Verifica o access token do Facebook consultando o Graph API (E47).
 *
 * <p>Um access token do Facebook não diz por si só para qual aplicativo foi emitido: qualquer app em
 * que a pessoa fez login gera um token que responde ao {@code /me}. Por isso o token passa primeiro
 * pelo {@code /debug_token}, autenticado com o token do NOSSO app, e só é aceito se for válido e do
 * nosso {@code app_id}. Sem isso, um token obtido por outro app entrava na conta com o mesmo email.
 * O {@code /me} ainda vai com {@code appsecret_proof}, que o Graph só aceita para tokens do nosso app.
 */
@Service
public class FacebookIdentityService {
    private final RestClient client;
    private final String appId;
    private final String appSecret;

    public FacebookIdentityService(@Value("${app.auth.facebook.app-id:}") String appId,
                                   @Value("${app.auth.facebook.app-secret:}") String appSecret,
                                   @Value("${app.auth.facebook.base-url:https://graph.facebook.com}") String baseUrl) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    public boolean configured() {
        return appId != null && !appId.isBlank() && appSecret != null && !appSecret.isBlank();
    }

    public Identity verify(String accessToken) {
        if (!configured()) throw new ApiException(503, "Login com Facebook não configurado: defina FACEBOOK_APP_ID e FACEBOOK_APP_SECRET");
        if (accessToken == null || accessToken.isBlank()) throw new ApiException(400, "Token do Facebook obrigatório");
        requireIssuedForThisApp(accessToken);
        Map<String, Object> info;
        try {
            info = client.get()
                .uri(builder -> builder.path("/me")
                    .queryParam("fields", "id,name,email")
                    .queryParam("access_token", accessToken)
                    .queryParam("appsecret_proof", appSecretProof(accessToken))
                    .build())
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

    @SuppressWarnings("unchecked")
    private void requireIssuedForThisApp(String accessToken) {
        Map<String, Object> debug;
        try {
            debug = client.get()
                .uri(builder -> builder.path("/debug_token")
                    .queryParam("input_token", accessToken)
                    .queryParam("access_token", appId + "|" + appSecret)
                    .build())
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (RestClientResponseException error) {
            throw new ApiException(401, "Token do Facebook inválido");
        }
        Object data = debug == null ? null : debug.get("data");
        if (!(data instanceof Map<?, ?> raw)) throw new ApiException(401, "Token do Facebook inválido");
        Map<String, Object> token = (Map<String, Object>) raw;
        boolean valid = Boolean.TRUE.equals(token.get("is_valid")) || "true".equals(String.valueOf(token.get("is_valid")));
        if (!valid) throw new ApiException(401, "Token do Facebook inválido");
        if (!appId.equals(String.valueOf(token.get("app_id")))) {
            throw new ApiException(401, "Token do Facebook não corresponde a este aplicativo");
        }
    }

    String appSecretProof(String accessToken) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(accessToken.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public record Identity(String email, String name) {}
}
