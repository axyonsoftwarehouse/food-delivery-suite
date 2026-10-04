package com.foodie.api.payments;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void acceptsTheOrderIdSignedInItsOriginalCase() {
        // Medido no staging em 04/10/2026: a notificação de order (ORDTST01M4250…) chegou assinada sobre o
        // id na caixa ORIGINAL — com o segredo certo, só o manifesto em maiúsculas reproduziu o v1 recebido.
        // Os vetores foram calculados fora do Java, para não validar a conta com ela mesma.
        String secret = "segredo-de-teste";
        String dataId = "ORD01JQ4S4KY8HWQ6NA5PXB65B3D3";
        String requestId = "2066ca19-c6f1-498a-be75-1923005edd06";
        String ts = "1742505638683";
        String v1 = "4f3098d033ca6f5107d146201969b7d2e55a60777de3a4f1d9467be4526068b9";

        assertTrue(WebhookVerifier.verify(secret, dataId, requestId, ts, v1));
        assertEquals("id:ORD01JQ4S4KY8HWQ6NA5PXB65B3D3;request-id:2066ca19-c6f1-498a-be75-1923005edd06;ts:1742505638683;",
            WebhookVerifier.manifest(dataId, requestId, ts));
    }

    @Test
    void stillAcceptsTheOrderIdSignedInLowerCase() {
        // A documentação do provedor manda converter o id para minúsculas. Não foi o que o provedor fez
        // nesta aplicação, mas a forma documentada continua aceita — as duas são HMAC com o mesmo segredo.
        String secret = "segredo-de-teste";
        String dataId = "ORD01JQ4S4KY8HWQ6NA5PXB65B3D3";
        String requestId = "2066ca19-c6f1-498a-be75-1923005edd06";
        String ts = "1742505638683";
        String v1 = "1cb5a89224b9ea9a0239402a8805de2c943322fac8eb49dea63133c0942bd229";

        assertTrue(WebhookVerifier.verify(secret, dataId, requestId, ts, v1));
        assertFalse(WebhookVerifier.verify("outro-segredo", dataId, requestId, ts, v1));
    }

    @Test
    void withoutSecretSkipsVerification() {
        assertTrue(WebhookVerifier.verify("", "1", "req", "ts", "qualquer"));
        assertTrue(WebhookVerifier.verify(null, "1", "req", null, null));
    }
}
