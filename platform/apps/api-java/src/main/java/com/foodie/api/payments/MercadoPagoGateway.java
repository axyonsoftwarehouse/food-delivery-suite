package com.foodie.api.payments;

import com.foodie.api.ApiException;
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

    public MercadoPagoGateway(@Value("${app.mercadopago.access-token:}") String accessToken,
                              @Value("${app.mercadopago.base-url:https://api.mercadopago.com}") String baseUrl,
                              @Value("${app.mercadopago.notification-url:}") String notificationUrl) {
        this.accessToken = accessToken;
        this.notificationUrl = notificationUrl;
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
            throw new ApiException(502, "Mercado Pago recusou a cobranÃ§a (" + error.getStatusCode().value() + ")");
        }
    }

    @Override
    public Charge fetch(String externalId) {
        requireConfigured();
        try {
            return toCharge(get("/v1/payments/" + externalId));
        } catch (RestClientResponseException error) {
            throw new ApiException(502, "Mercado Pago recusou a consulta (" + error.getStatusCode().value() + ")");
        }
    }

    public boolean configured() {
        return accessToken != null && !accessToken.isBlank();
    }

    private void requireConfigured() {
        if (!configured()) throw new ApiException(503, "Pagamento online nÃ£o configurado: defina MERCADOPAGO_ACCESS_TOKEN");
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

        Map<String, Object> preference = post("/checkout/preferences", body, request.idempotencyKey());
        return new Charge(str(preference.get("id")), String.valueOf(request.orderId()), request.amountCents(), "pending", "preference_created",
            null, null, str(preference.get("init_point")), null);
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
