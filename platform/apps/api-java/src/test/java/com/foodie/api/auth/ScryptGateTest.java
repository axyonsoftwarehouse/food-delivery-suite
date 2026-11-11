package com.foodie.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foodie.api.ApiException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Teste de carga de 11/11/2026: cada SCrypt usa 64 MiB e ~15 logins simultâneos esgotavam o heap,
 * derrubando a API. O portão limita quantos hashes rodam ao mesmo tempo.
 */
class ScryptGateTest {

    @Test
    void nuncaRodaMaisHashesAoMesmoTempoQueOLimite() throws Exception {
        ScryptGate gate = new ScryptGate(2, Duration.ofSeconds(5));
        AtomicInteger running = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                results.add(pool.submit(() -> gate.run(() -> {
                    peak.accumulateAndGet(running.incrementAndGet(), Math::max);
                    sleep(30);
                    running.decrementAndGet();
                    return 1;
                })));
            }
            int done = 0;
            for (Future<Integer> result : results) done += result.get(10, TimeUnit.SECONDS);
            assertThat(done).isEqualTo(8);
            assertThat(peak.get()).isEqualTo(2);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void quemEsperaDemaisRecebe503EmVezDeEsgotarAMemoria() throws Exception {
        ScryptGate gate = new ScryptGate(1, Duration.ofMillis(100));
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> first = pool.submit(() -> gate.run(() -> {
                holding.countDown();
                await(release);
                return 1;
            }));
            assertThat(holding.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> gate.run(() -> 2))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).status()).isEqualTo(503));

            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(gate.run(() -> 3)).isEqualTo(3);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void liberaAVagaMesmoQuandoOHashFalha() {
        ScryptGate gate = new ScryptGate(1, Duration.ofMillis(100));
        assertThatThrownBy(() -> gate.run(() -> { throw new IllegalStateException("falhou"); }))
            .isInstanceOf(IllegalStateException.class);
        assertThat(gate.run(() -> 4)).isEqualTo(4);
    }

    @Test
    void oPasswordVerifierPassaPeloPortao() {
        PasswordVerifier closed = new PasswordVerifier(new ScryptGate(0, Duration.ofMillis(50)));
        assertThatThrownBy(() -> closed.hash("uma-senha-qualquer"))
            .isInstanceOf(ApiException.class);

        PasswordVerifier open = new PasswordVerifier();
        String stored = open.hash("uma-senha-qualquer");
        PasswordVerifier closedForMatch = new PasswordVerifier(new ScryptGate(0, Duration.ofMillis(50)));
        assertThatThrownBy(() -> closedForMatch.matches("uma-senha-qualquer", stored))
            .isInstanceOf(ApiException.class);
        assertThat(open.matches("uma-senha-qualquer", stored)).isTrue();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
