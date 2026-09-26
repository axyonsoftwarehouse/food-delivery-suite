package com.foodie.api.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ponte entre o contrato de sessão por cookie (web) e o cabeçalho {@code Authorization: Bearer}
 * usado pelos aplicativos móveis.
 *
 * <p>A sessão continua sendo a mesma tabela {@code sessions} e o mesmo token. Quando a requisição
 * traz um Bearer e ainda não possui o cookie {@code foodie_session}, o token é exposto ao
 * controlador como se tivesse vindo do cookie. Assim os 44 usos de {@code @CookieValue} existentes
 * passam a aceitar navegador e app sem qualquer alteração, e o token nunca aparece na URL.
 */
@Component
public class MobileSessionFilter extends OncePerRequestFilter {
    static final String SESSION_COOKIE = "foodie_session";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = bearerToken(request);
        if (token == null || hasSessionCookie(request)) {
            chain.doFilter(request, response);
            return;
        }
        chain.doFilter(new BearerRequest(request, new Cookie(SESSION_COOKIE, token)), response);
    }

    private static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) return null;
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private static boolean hasSessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return false;
        for (Cookie cookie : cookies) {
            if (SESSION_COOKIE.equals(cookie.getName())) return true;
        }
        return false;
    }

    private static final class BearerRequest extends HttpServletRequestWrapper {
        private final Cookie cookie;

        BearerRequest(HttpServletRequest request, Cookie cookie) {
            super(request);
            this.cookie = cookie;
        }

        @Override
        public Cookie[] getCookies() {
            Cookie[] original = super.getCookies();
            if (original == null) return new Cookie[] { cookie };
            List<Cookie> merged = new ArrayList<>(original.length + 1);
            Collections.addAll(merged, original);
            merged.add(cookie);
            return merged.toArray(new Cookie[0]);
        }
    }
}
