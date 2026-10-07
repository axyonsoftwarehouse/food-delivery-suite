package com.foodie.api.notifications;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * Endpoints de Web Push aceitos: só os serviços de push dos navegadores, por HTTPS. O servidor faz POST
 * no endpoint gravado; aceitar qualquer URL deixava um usuário apontar esse POST para endereços internos
 * da rede do servidor (SSRF).
 */
public final class PushEndpoints {
    /** Hosts exatos ou sufixos (começando com ".") dos serviços de push dos navegadores. */
    private static final List<String> ALLOWED = List.of(
        "fcm.googleapis.com",                // Chrome, Edge (Chromium), Opera, Brave, Samsung Internet
        "updates.push.services.mozilla.com", // Firefox
        "web.push.apple.com",                // Safari
        ".notify.windows.com"                // Edge legado (WNS)
    );

    private PushEndpoints() {}

    public static boolean allowed(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) return false;
        URI uri;
        try {
            uri = URI.create(endpoint.strip());
        } catch (IllegalArgumentException invalid) {
            return false;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null) return false;
        if (uri.getPort() != -1 && uri.getPort() != 443) return false;
        String host = uri.getHost();
        if (host == null) return false;
        String normalized = host.toLowerCase(Locale.ROOT);
        for (String allowed : ALLOWED) {
            if (allowed.startsWith(".") ? normalized.endsWith(allowed) : normalized.equals(allowed)) return true;
        }
        return false;
    }
}
