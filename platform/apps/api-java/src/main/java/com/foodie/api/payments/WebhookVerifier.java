package com.foodie.api.payments;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class WebhookVerifier {
    private WebhookVerifier() {}

    /**
     * O manifesto que o provedor assina. Público (ids e horário) — o segredo não entra aqui.
     *
     * O {@code data.id} entra <b>como chegou</b>. A documentação manda converter ids alfanuméricos para
     * minúsculas, mas no staging (04/10/2026) a notificação de order chegou assinada sobre o id na caixa
     * original ({@code ORDTST01M4250…}): com o segredo certo, só esse manifesto reproduziu o {@code v1}.
     * A forma em minúsculas continua aceita em {@link #verify}.
     */
    public static String manifest(String dataId, String requestId, String ts) {
        return "id:" + (dataId == null ? "" : dataId)
            + ";request-id:" + (requestId == null ? "" : requestId)
            + ";ts:" + (ts == null ? "" : ts) + ";";
    }

    public static boolean verify(String secret, String dataId, String requestId, String ts, String v1) {
        // Sem segredo não há como conferir a assinatura: recusar é a única resposta segura. Antes disto a
        // verificação era pulada quando o segredo estava vazio, então um ambiente sem a chave aceitava
        // notificação de qualquer origem. O staging passou a ter o segredo em 04/10/2026.
        if (secret == null || secret.isBlank()) return false;
        if (ts == null || v1 == null || v1.isBlank()) return false;
        String lower = dataId == null ? null : dataId.toLowerCase(java.util.Locale.ROOT);
        return matches(secret, manifest(dataId, requestId, ts), v1)
            || matches(secret, manifest(lower, requestId, ts), v1);
    }

    private static boolean matches(String secret, String manifest, String v1) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8))).getBytes(StandardCharsets.UTF_8);
            byte[] provided = v1.trim().toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(expected, provided);
        } catch (Exception error) {
            return false;
        }
    }
}
