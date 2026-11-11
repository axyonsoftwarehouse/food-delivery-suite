package com.foodie.api.catalog;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Limite por IP das consultas de CEP que saem para o serviço externo (o cache não conta). Em
 * memória: a API roda numa instância só, e o objetivo é não virar um proxy aberto do ViaCEP.
 */
@Component
public class PostalLookupLimiter {
    static final int LIMIT = 20;
    static final long WINDOW_MILLIS = 10 * 60 * 1000L;
    private static final int MAX_TRACKED = 10_000;

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final Clock clock;

    public PostalLookupLimiter() {
        this(Clock.systemUTC());
    }

    PostalLookupLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Conta a consulta e diz se ela pode sair. Sem IP conhecido, não limita. */
    public boolean allow(String ip) {
        if (ip == null || ip.isBlank()) return true;
        long now = clock.millis();
        if (hits.size() > MAX_TRACKED) hits.entrySet().removeIf(entry -> expired(entry.getValue(), now));
        Deque<Long> times = hits.computeIfAbsent(ip, ignored -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() >= WINDOW_MILLIS) times.pollFirst();
            if (times.size() >= LIMIT) return false;
            times.addLast(now);
            return true;
        }
    }

    private static boolean expired(Deque<Long> times, long now) {
        synchronized (times) {
            return times.isEmpty() || now - times.peekLast() >= WINDOW_MILLIS;
        }
    }
}
