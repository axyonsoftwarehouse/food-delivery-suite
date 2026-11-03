package com.foodie.api.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Defesa de CSRF para escritas autenticadas por cookie. SameSite=Lax já bloqueia o cookie em
 * POST cross-site; esta checagem de Origin/Referer cobre o caso de o cookie chegar e o domínio
 * não ser um dos papéis do Foodie. Mobile/Bearer, curl, smokes e webhooks não enviam o cookie e
 * seguem sem a checagem.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class OriginCheckFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(OriginCheckFilter.class);
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final String SESSION_COOKIE = "foodie_session";

    private final Set<String> allowedOrigins;

    public OriginCheckFilter(@Value("${app.public-base-url:}") String publicBaseUrl,
                             @Value("${app.allowed-origins:}") String allowedOrigins) {
        LinkedHashSet<String> origins = new LinkedHashSet<>();
        add(origins, publicBaseUrl);
        if (allowedOrigins != null) {
            for (String item : allowedOrigins.split(",")) add(origins, item);
        }
        this.allowedOrigins = Set.copyOf(origins);
        if (origins.isEmpty()) {
            log.warn("Nenhuma origem de navegador configurada; escritas com cookie serão recusadas quando houver Origin/Referer");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SAFE_METHODS.contains(request.getMethod()) || isWebhook(request) || !needsCheck(request)) {
            chain.doFilter(request, response);
            return;
        }
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank()) origin = originFrom(request.getHeader("Referer"));
        if (origin == null || origin.isBlank()) {
            // Cliente móvel/API com Bearer, curl ou chamada servidor-a-servidor: não há CSRF de navegador.
            chain.doFilter(request, response);
            return;
        }
        String normalized = normalize(origin);
        if (normalized == null || !allowedOrigins.contains(normalized)) {
            log.warn("Origem recusada em {} {}: {}", request.getMethod(), request.getRequestURI(), origin);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"status\":403,\"error\":\"Forbidden\",\"message\":\"Origem não permitida\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean needsCheck(HttpServletRequest request) {
        return hasSessionCookie(request) || request.getRequestURI().startsWith("/auth/");
    }

    private static boolean hasSessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return false;
        for (Cookie cookie : cookies) {
            if (SESSION_COOKIE.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) return true;
        }
        return false;
    }

    private static boolean isWebhook(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/webhooks/");
    }

    /** Extrai a origem do Referer quando o navegador não manda Origin. */
    private static String originFrom(String referer) {
        if (referer == null || referer.isBlank()) return null;
        try {
            URI uri = URI.create(referer.strip());
            if (uri.getScheme() == null || uri.getHost() == null) return null;
            return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static void add(Set<String> origins, String raw) {
        String normalized = normalize(raw);
        if (normalized != null) origins.add(normalized);
    }

    private static String normalize(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            URI uri = URI.create(raw.strip());
            if (uri.getScheme() == null || uri.getHost() == null) return null;
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            boolean defaultPort = ("https".equals(scheme) && (port == -1 || port == 443))
                || ("http".equals(scheme) && (port == -1 || port == 80));
            return scheme + "://" + host + (defaultPort ? "" : ":" + port);
        } catch (RuntimeException error) {
            return null;
        }
    }
}
