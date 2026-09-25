package com.foodie.api.payments;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class WebhookVerifierTest {
    @Test
    void acceptsValidSignatureAndRejectsTamperedOnes() throws Exception {
        String secret = "segredo-de-teste";
        String dataId = "123456789";
        String requestId = "req-1";
        String ts = "1700000000";
        String manifest = "id:" + dataId + ";request-id:" + requestId + ";ts:" + ts + ";";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String v1 = HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));

        assertTrue(WebhookVerifier.verify(secret, dataId, requestId, ts, v1));
        assertFalse(WebhookVerifier.verify(secret, dataId, requestId, ts, "deadbeef"));
        assertFalse(WebhookVerifier.verify(secret, dataId, requestId, null, v1));
        assertFalse(WebhookVerifier.verify("outro-segredo", dataId, requestId, ts, v1));
    }

    @Test
    void withoutSecretSkipsVerification() {
        assertTrue(WebhookVerifier.verify("", "1", "req", "ts", "qualquer"));
        assertTrue(WebhookVerifier.verify(null, "1", "req", null, null));
    }
}
