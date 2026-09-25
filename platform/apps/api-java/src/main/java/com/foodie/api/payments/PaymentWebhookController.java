package com.foodie.api.payments;

import com.foodie.api.ApiException;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentWebhookController {
    private final OnlinePaymentService online;

    public PaymentWebhookController(OnlinePaymentService online) {
        this.online = online;
    }

    @PostMapping("/webhooks/mercadopago")
    public Map<String, Object> mercadoPago(@RequestHeader Map<String, String> headers,
                                           @RequestBody(required = false) Map<String, Object> body) {
        String type = firstString(body, "type", "topic");
        if (type != null && !"payment".equals(type)) return Map.of("ok", true, "ignored", true);
        String dataId = dataId(body);
        if (dataId == null) return Map.of("ok", true, "ignored", true);

        String requestId = header(headers, "x-request-id");
        String signature = header(headers, "x-signature");
        String ts = null;
        String v1 = null;
        if (signature != null) {
            for (String part : signature.split(",")) {
                String[] pair = part.split("=", 2);
                if (pair.length != 2) continue;
                if ("ts".equals(pair[0].trim())) ts = pair[1].trim();
                else if ("v1".equals(pair[0].trim())) v1 = pair[1].trim();
            }
        }
        if (!online.verifySignature(dataId, requestId, ts, v1)) throw new ApiException(401, "Assinatura do webhook invÃ¡lida");
        return online.handleWebhook(dataId);
    }

    private static String dataId(Map<String, Object> body) {
        if (body == null) return null;
        Object data = body.get("data");
        if (data instanceof Map<?, ?> map && map.get("id") != null) return String.valueOf(map.get("id"));
        return null;
    }

    private static String firstString(Map<String, Object> body, String... keys) {
        if (body == null) return null;
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
}
