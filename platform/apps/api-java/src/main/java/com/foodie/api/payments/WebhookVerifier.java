package com.foodie.api.payments;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class WebhookVerifier {
    private WebhookVerifier() {}

    /** O manifesto que o provedor assina. Público (ids e horário) — o segredo não entra aqui. */
    public static String manifest(String dataId, String requestId, String ts) {
        return "id:" + (dataId == null ? "" : dataId)
            + ";request-id:" + (requestId == null ? "" : requestId)
            + ";ts:" + (ts == null ? "" : ts) + ";";
    }

    public static boolean verify(String secret, String dataId, String requestId, String ts, String v1) {
        if (secret == null || secret.isBlank()) return true;
        if (ts == null || v1 == null || v1.isBlank()) return false;
        String manifest = manifest(dataId, requestId, ts);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8))).getBytes(StandardCharsets.UTF_8);
            byte[] provided = v1.trim().toLowerCase().getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(expected, provided);
        } catch (Exception error) {
            return false;
        }
    }
}
