package com.foodie.api.payments;

import com.foodie.api.ApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class MercadoPagoGateway implements PaymentGateway {
    private final RestClient client;
    private final String accessToken;
    private final String notificationUrl;
    private final String webhookSecret;
    private final String paymentReturnUrl;
    private final boolean sandbox;

    public MercadoPagoGateway(@Value("${app.mercadopago.access-token:}") String accessToken,
                              @Value("${app.mercadopago.base-url:https://api.mercadopago.com}") String baseUrl,
                              @Value("${app.mercadopago.notification-url:}") String notificationUrl,
                              @Value("${app.mercadopago.webhook-secret:}") String webhookSecret,
                              @Value("${app.mobile.payment-return-url:}") String paymentReturnUrl,
                              @Value("${app.mercadopago.sandbox:false}") boolean sandbox) {
        this.accessToken = accessToken;
        this.notificationUrl = notificationUrl;
        this.webhookSecret = webhookSecret;
        this.paymentReturnUrl = paymentReturnUrl;
        this.sandbox = sandbox;
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public String provider() {
        return "mercadopago";
    }

    @Override
    public Charge create(ChargeRequest request) {
        requireConfigured();
        try {
            return "pix".equals(request.method()) ? createPix(request) : createPreference(request);
        } catch (RestClientResponseException error) {
            throw new ApiException(502, "Mercado Pago recusou a cobrança: " + providerMessage(error));
        }
    }

    @Override
    public Charge fetch(String externalId) {
        requireConfigured();
        try {
            return toCharge(get("/v1/payments/" + externalId));
        } catch (RestClientResponseException error) {
            throw new ApiException(502, "Mercado Pago recusou a consulta: " + providerMessage(error));
        }
    }

    /**
     * Primeira mensagem útil que o provedor devolveu. Sem isto o atendimento (e quem está na tela) recebe
     * só "recusou a cobrança (400)" e não descobre que o motivo era, por exemplo, o email do pagador.
     */
    static String providerMessage(RestClientResponseException error) {
        String situacao = "HTTP " + error.getStatusCode().value();
        String corpo = error.getResponseBodyAsString();
        if (corpo == null || corpo.isBlank()) return situacao;
        try {
            Map<String, Object> resposta = new ObjectMapper().readValue(corpo, new TypeReference<Map<String, Object>>() {});
            Object mensagem = resposta.get("message");
            if (mensagem != null && !String.valueOf(mensagem).isBlank()) return situacao + " - " + mensagem;
            if (resposta.get("cause") instanceof List<?> causas && !causas.isEmpty() && causas.getFirst() instanceof Map<?, ?> primeira) {
                Object descricao = primeira.get("description");
                if (descricao != null && !String.valueOf(descricao).isBlank()) return situacao + " - " + descricao;
            }
            return situacao;
        } catch (Exception ignorado) {
            return situacao;
        }
    }

    public boolean configured() {
        return accessToken != null && !accessToken.isBlank();
    }

    @Override
    public java.util.Optional<String> webhookChargeId(WebhookRequest request) {
        Map<String, Object> body = request.body();
        String type = firstString(body, "type", "topic");
        if (type != null && !"payment".equals(type)) return java.util.Optional.empty();
        Object data = body.get("data");
        if (data instanceof Map<?, ?> map && map.get("id") != null) return java.util.Optional.of(String.valueOf(map.get("id")));
        return java.util.Optional.empty();
    }

    @Override
    public boolean verifyWebhook(WebhookRequest request) {
        String dataId = webhookChargeId(request).orElse(null);
        String requestId = header(request.headers(), "x-request-id");
        String ts = null;
        String v1 = null;
        String signature = header(request.headers(), "x-signature");
        if (signature != null) {
            for (String part : signature.split(",")) {
                String[] pair = part.split("=", 2);
                if (pair.length != 2) continue;
                if ("ts".equals(pair[0].trim())) ts = pair[1].trim();
                else if ("v1".equals(pair[0].trim())) v1 = pair[1].trim();
            }
        }
        return WebhookVerifier.verify(webhookSecret, dataId, requestId, ts, v1);
    }

    private static String firstString(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            Object value = body.get(key);
            if (value != null) return String.valueOf(value);
        }
        return null;
    }

    private static String header(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) return entry.getValue();
        }
        return null;
    }

    private void requireConfigured() {
        if (!configured()) throw new ApiException(503, "Pagamento online não configurado: defina MERCADOPAGO_ACCESS_TOKEN");
    }

    private Charge createPix(ChargeRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("transaction_amount", request.amountCents() / 100.0);
        body.put("description", request.description());
        body.put("payment_method_id", "pix");
        body.put("external_reference", String.valueOf(request.orderId()));
        body.put("payer", Map.of("email", request.payerEmail()));
        if (!notificationUrl.isBlank()) body.put("notification_url", notificationUrl);

        Map<String, Object> payment = post("/v1/payments", body, request.idempotencyKey());
        Charge charge = toCharge(payment);
        Map<String, Object> data = nested(payment, "point_of_interaction", "transaction_data");
        if (data == null) return charge;
        return new Charge(charge.externalId(), charge.externalReference(), charge.amountCents(), charge.status(), charge.rawStatus(),
            str(data.get("qr_code")), str(data.get("qr_code_base64")), str(data.get("ticket_url")), charge.expiresAt());
    }

    private Charge createPreference(ChargeRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(Map.of(
            "title", request.description(),
            "quantity", 1,
            "unit_price", request.amountCents() / 100.0,
            "currency_id", "BRL"
        )));
        body.put("external_reference", String.valueOf(request.orderId()));
        body.put("payer", Map.of("email", request.payerEmail()));
        if (!notificationUrl.isBlank()) body.put("notification_url", notificationUrl);
        if (!paymentReturnUrl.isBlank()) {
            body.put("back_urls", Map.of(
                "success", paymentReturnUrl,
                "pending", paymentReturnUrl,
                "failure", paymentReturnUrl));
            if (paymentReturnUrl.startsWith("http")) body.put("auto_return", "approved");
        }

        Map<String, Object> preference = post("/checkout/preferences", body, request.idempotencyKey());
        return new Charge(str(preference.get("id")), String.valueOf(request.orderId()), request.amountCents(), "pending", "preference_created",
            null, null, initPoint(preference), null);
    }

    /**
     * Escolhe o ponto de entrada da preferência. Em ambiente de teste vale o {@code sandbox_init_point}:
     * o ponto de entrada de produção recusa os cartões de teste. A marcação vem da configuração
     * ({@code app.mercadopago.sandbox}), decidida na conferência do token — o prefixo do token não
     * separa conta real de usuário de teste, porque o access token de usuário de teste também começa
     * com {@code APP_USR-}.
     */
    static String chooseInitPoint(Map<String, Object> preference, boolean sandbox) {
        String sandboxPoint = str(preference.get("sandbox_init_point"));
        if (sandbox && sandboxPoint != null && !sandboxPoint.isBlank()) return sandboxPoint;
        return str(preference.get("init_point"));
    }

    private String initPoint(Map<String, Object> preference) {
        return chooseInitPoint(preference, sandbox || isTestTokenPrefix());
    }

    private boolean isTestTokenPrefix() {
        return accessToken != null && accessToken.startsWith("TEST-");
    }

    private Charge toCharge(Map<String, Object> payment) {
        String raw = str(payment.get("status"));
        return new Charge(str(payment.get("id")), str(payment.get("external_reference")), amountCents(payment.get("transaction_amount")),
            MercadoPagoStatus.normalize(raw), raw, null, null, null, instant(payment.get("date_of_expiration")));
    }

    private Map<String, Object> get(String path) {
        return client.get().uri(path).header("Authorization", "Bearer " + accessToken)
            .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    private Map<String, Object> post(String path, Map<String, Object> body, String idempotencyKey) {
        return client.post().uri(path)
            .header("Authorization", "Bearer " + accessToken)
            .header("X-Idempotency-Key", idempotencyKey == null || idempotencyKey.isBlank() ? UUID.randomUUID().toString() : idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nested(Map<String, Object> source, String... path) {
        Object current = source;
        for (String key : path) {
            if (!(current instanceof Map<?, ?> map)) return null;
            current = map.get(key);
        }
        return current instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static long amountCents(Object value) {
        if (value instanceof Number number) return Math.round(number.doubleValue() * 100);
        if (value instanceof String text) {
            try { return Math.round(Double.parseDouble(text) * 100); } catch (NumberFormatException ignored) { return 0; }
        }
        return 0;
    }

    private static Instant instant(Object value) {
        if (value == null) return null;
        try { return OffsetDateTime.parse(String.valueOf(value)).toInstant(); } catch (RuntimeException error) { return null; }
    }
}
