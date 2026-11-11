package com.foodie.api.auth;

import com.foodie.api.ApiException;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Limita quantos hashes SCrypt rodam ao mesmo tempo. Cada um usa 64 MiB de memória: no teste de
 * carga de 11/11/2026, ~15 logins simultâneos esgotaram o heap (956 MB) e derrubaram a API. Com o
 * limite, o pico fica em {@code vagas × 64 MiB}; quem não consegue vaga a tempo recebe 503 e tenta
 * de novo, em vez de levar a API inteira junto.
 */
final class ScryptGate {
    /** 4 × 64 MiB = 256 MiB no pior caso, folga para o resto da API num heap de ~1 GB. */
    static final ScryptGate DEFAULT = new ScryptGate(4, Duration.ofSeconds(10));

    private final Semaphore slots;
    private final Duration maxWait;

    ScryptGate(int slots, Duration maxWait) {
        this.slots = new Semaphore(slots, true);
        this.maxWait = maxWait;
    }

    <T> T run(Supplier<T> hash) {
        boolean acquired;
        try {
            acquired = slots.tryAcquire(maxWait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw busy();
        }
        if (!acquired) throw busy();
        try {
            return hash.get();
        } finally {
            slots.release();
        }
    }

    private static ApiException busy() {
        return new ApiException(503, "Muita gente entrando ao mesmo tempo. Tente de novo em alguns segundos.");
    }
}
