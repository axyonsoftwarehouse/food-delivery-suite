package com.foodie.api.payments.accounts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodie.api.ApiException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Vinculação de aplicações do Mercado Pago (OAuth authorization_code com PKCE). Só HTTP: quem guarda
 * e decide é o {@link PaymentAccountService}. O redirect_uri é fixo e tem de ser igual ao cadastrado
 * na aplicação — o Mercado Pago recusa qualquer diferença.
 */
@Component
public class MercadoPagoOAuthClient {
    public record OAuthTokens(String accessToken, String refreshToken, String publicKey, String userId, long expiresInSeconds) {}

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String authBaseUrl;
    private final RestClient client;

    public MercadoPagoOAuthClient(@Value("${app.mercadopago.client-id:}") String clientId,
                                  @Value("${app.mercadopago.client-secret:}") String clientSecret,
                                  @Value("${app.mercadopago.oauth-redirect-uri:}") String redirectUri,
                                  @Value("${app.mercadopago.base-url:https://api.mercadopago.com}") String apiBaseUrl,
                                  @Value("${app.mercadopago.auth-base-url:https://auth.mercadopago.com.br}") String authBaseUrl) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.authBaseUrl = authBaseUrl;
        // Timeouts: uma chamada pendurada ao Mercado Pago seguraria a thread da requisição e o lock de
        // linha do estado OAuth no banco.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.client = RestClient.builder().baseUrl(apiBaseUrl).requestFactory(factory).build();
    }

    public boolean configured() {
        return notBlank(clientId) && notBlank(clientSecret) && notBlank(redirectUri);
    }

    public String authorizationUrl(String state, String codeChallenge) {
        return authBaseUrl + "/authorization?client_id=" + enc(clientId) + "&response_type=code&platform_id=mp"
            + "&state=" + enc(state) + "&redirect_uri=" + enc(redirectUri)
            + "&code_challenge=" + enc(codeChallenge) + "&code_challenge_method=S256";
    }

    public OAuthTokens exchangeCode(String code, String codeVerifier) {
        try {
            return tokens(token(Map.of("grant_type", "authorization_code", "code", code, "redirect_uri", redirectUri, "code_verifier", codeVerifier)));
        } catch (RestClientResponseException error) {
            throw new ApiException(502, "Mercado Pago recusou a vinculação: " + message(error));
        } catch (ResourceAccessException error) {
            throw new ApiException(502, "Mercado Pago não respondeu: " + error.getMessage());
        }
    }

    /** Vazio quando o Mercado Pago recusa (autorização revogada, refresh vencido): a loja precisa reconectar. */
    public Optional<OAuthTokens> refresh(String refreshToken) {
        try {
            return Optional.of(tokens(token(Map.of("grant_type", "refresh_token", "refresh_token", refreshToken))));
        } catch (RestClientResponseException error) {
            if (error.getStatusCode().is4xxClientError()) return Optional.empty();
            throw new ApiException(502, "Mercado Pago recusou a renovação: " + message(error));
        } catch (ResourceAccessException error) {
            throw new ApiException(502, "Mercado Pago não respondeu: " + error.getMessage());
        }
    }

    public String nickname(String accessToken) {
        try {
            Map<String, Object> me = client.get().uri("/users/me").header("Authorization", "Bearer " + accessToken)
                .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (me == null) return null;
            Object nickname = me.get("nickname");
            if (nickname != null && !String.valueOf(nickname).isBlank()) return String.valueOf(nickname);
            Object email = me.get("email");
            return email == null ? null : String.valueOf(email);
        } catch (RuntimeException error) {
            return null;
        }
    }

    private Map<String, Object> token(Map<String, String> grant) {
        Map<String, String> form = new java.util.LinkedHashMap<>();
        form.put("client_id", clientId);
        form.put("client_secret", clientSecret);
        form.putAll(grant);
        String body = form.entrySet().stream().map(e -> enc(e.getKey()) + "=" + enc(e.getValue())).collect(Collectors.joining("&"));
        return client.post().uri("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED).body(body)
            .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    private static OAuthTokens tokens(Map<String, Object> body) {
        if (body == null || body.get("access_token") == null) throw new ApiException(502, "Mercado Pago não devolveu o token da loja");
        Object expires = body.get("expires_in");
        return new OAuthTokens(str(body.get("access_token")), str(body.get("refresh_token")), str(body.get("public_key")),
            str(body.get("user_id")), expires instanceof Number n ? n.longValue() : 0L);
    }

    private static String message(RestClientResponseException error) {
        String status = "HTTP " + error.getStatusCode().value();
        try {
            Map<?, ?> body = new ObjectMapper().readValue(error.getResponseBodyAsString(), Map.class);
            Object text = body.get("message");
            return text == null ? status : status + " - " + text;
        } catch (Exception ignored) {
            return status;
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
