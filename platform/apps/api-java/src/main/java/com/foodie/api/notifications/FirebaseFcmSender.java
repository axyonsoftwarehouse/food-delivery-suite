package com.foodie.api.notifications;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodie.api.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Envia notificações pelo Firebase Cloud Messaging HTTP v1 usando uma conta de serviço
 * (JWT RS256 assinado localmente e trocado por um access token OAuth2 em cache).
 * Sem {@code FCM_SERVICE_ACCOUNT_JSON} o envio fica desativado e {@link #configured()} é falso.
 */
@Service
public class FirebaseFcmSender implements FcmSender {
    private static final Logger log = LoggerFactory.getLogger(FirebaseFcmSender.class);
    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    private static final String DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String SEND_ENDPOINT = "https://fcm.googleapis.com/v1/projects/%s/messages:send";
    private static final long TOKEN_SKEW_SECONDS = 60;

    private final ObjectMapper json;
    private final String serviceAccount;
    private final String configuredProjectId;
    private final RestClient client;

    private volatile Credentials credentials;
    private volatile boolean parsed;
    private volatile String accessToken;
    private volatile Instant accessTokenExpiry = Instant.EPOCH;

    public FirebaseFcmSender(ObjectMapper json,
                             @Value("${app.fcm.service-account-json:}") String serviceAccount,
                             @Value("${app.fcm.project-id:}") String configuredProjectId) {
        this.json = json;
        this.serviceAccount = serviceAccount;
        this.configuredProjectId = configuredProjectId;
        this.client = RestClient.create();
    }

    @Override
    public boolean configured() {
        return credentials() != null;
    }

    @Override
    public void send(String deviceToken, String title, String body, Map<String, Object> data) {
        Credentials creds = credentials();
        if (creds == null) throw new ApiException(503, "FCM não configurado: defina FCM_SERVICE_ACCOUNT_JSON");
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("token", deviceToken);
        message.put("notification", Map.of("title", title, "body", body));
        if (data != null && !data.isEmpty()) message.put("data", data);
        try {
            client.post().uri(String.format(SEND_ENDPOINT, creds.projectId()))
                .header("Authorization", "Bearer " + accessToken(creds))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("message", message))
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientResponseException error) {
            int status = error.getStatusCode().value();
            String response = error.getResponseBodyAsString();
            if (status == 404 || response.contains("UNREGISTERED") || response.contains("INVALID_ARGUMENT")) {
                throw new InvalidFcmTokenException("Token FCM inválido: " + truncate(response));
            }
            throw new ApiException(502, "FCM recusou o envio (" + status + ")");
        }
    }

    private String accessToken(Credentials creds) {
        Instant now = Instant.now();
        if (accessToken != null && now.isBefore(accessTokenExpiry)) return accessToken;
        synchronized (this) {
            if (accessToken != null && now.isBefore(accessTokenExpiry)) return accessToken;
            try {
                MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
                form.add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
                form.add("assertion", assertion(creds, now));
                JsonNode response = client.post().uri(creds.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
                if (response == null || response.get("access_token") == null) {
                    throw new ApiException(502, "FCM não devolveu access token");
                }
                accessToken = response.get("access_token").asText();
                long expiresIn = response.hasNonNull("expires_in") ? response.get("expires_in").asLong() : 3600;
                accessTokenExpiry = now.plusSeconds(Math.max(60, expiresIn - TOKEN_SKEW_SECONDS));
                return accessToken;
            } catch (RestClientResponseException error) {
                throw new ApiException(502, "Falha ao autenticar no FCM (" + error.getStatusCode().value() + ")");
            }
        }
    }

    private String assertion(Credentials creds, Instant now) {
        try {
            String header = base64Url(json.writeValueAsBytes(Map.of("alg", "RS256", "typ", "JWT")));
            String claims = base64Url(json.writeValueAsBytes(Map.of(
                "iss", creds.clientEmail(),
                "scope", SCOPE,
                "aud", creds.tokenUri(),
                "iat", now.getEpochSecond(),
                "exp", now.getEpochSecond() + 3600)));
            String signingInput = header + "." + claims;
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(creds.privateKey());
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            return signingInput + "." + base64Url(signature.sign());
        } catch (Exception error) {
            throw new ApiException(500, "Falha ao assinar credencial do FCM: " + error.getMessage());
        }
    }

    private Credentials credentials() {
        if (parsed) return credentials;
        synchronized (this) {
            if (parsed) return credentials;
            parsed = true;
            if (serviceAccount == null || serviceAccount.isBlank()) return null;
            try {
                JsonNode node = json.readTree(serviceAccount);
                String email = text(node, "client_email");
                String key = text(node, "private_key");
                String projectId = configuredProjectId != null && !configuredProjectId.isBlank()
                    ? configuredProjectId : text(node, "project_id");
                String tokenUri = text(node, "token_uri");
                if (email == null || key == null || projectId == null) {
                    log.warn("FCM_SERVICE_ACCOUNT_JSON sem client_email, private_key ou project_id; envio desativado");
                    return null;
                }
                credentials = new Credentials(email, parseKey(key), projectId,
                    tokenUri == null || tokenUri.isBlank() ? DEFAULT_TOKEN_URI : tokenUri);
                return credentials;
            } catch (Exception error) {
                log.warn("FCM_SERVICE_ACCOUNT_JSON inválido ({}); envio desativado", error.getMessage());
                return null;
            }
        }
    }

    private static PrivateKey parseKey(String pem) throws Exception {
        String normalized = pem.replace("\\n", "\n")
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(normalized);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String truncate(String value) {
        if (value == null) return null;
        return value.length() > 480 ? value.substring(0, 480) : value;
    }

    private record Credentials(String clientEmail, PrivateKey privateKey, String projectId, String tokenUri) {}
}
