package com.foodie.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class ShortCacheTest {
    private final AtomicLong now = new AtomicLong();
    private final AtomicInteger loads = new AtomicInteger();
    private final ShortCache<String, String> cache = new ShortCache<>(Duration.ofSeconds(10), 3, now::get);

    @AfterEach
    void clearTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }

    private String load(String key) {
        loads.incrementAndGet();
        return key + "-v" + loads.get();
    }

    @Test
    void servesFromMemoryWithinTheTtlAndReloadsAfter() {
        assertThat(cache.get("a", this::load)).isEqualTo("a-v1");
        now.addAndGet(Duration.ofSeconds(9).toNanos());
        assertThat(cache.get("a", this::load)).isEqualTo("a-v1");
        now.addAndGet(Duration.ofSeconds(1).toNanos());
        assertThat(cache.get("a", this::load)).isEqualTo("a-v2");
        assertThat(loads).hasValue(2);
    }

    @Test
    void doesNotStoreMissingValues() {
        assertThat(cache.get("x", key -> { loads.incrementAndGet(); return null; })).isNull();
        assertThat(cache.get("x", key -> { loads.incrementAndGet(); return null; })).isNull();
        assertThat(loads).hasValue(2);
    }

    @Test
    void invalidationDropsTheEntryRightAway() {
        cache.get("a", this::load);
        cache.get("b", this::load);
        cache.invalidate("a");
        assertThat(cache.get("a", this::load)).isEqualTo("a-v3");
        assertThat(cache.get("b", this::load)).isEqualTo("b-v2");

        cache.invalidateIf(value -> value.startsWith("b"));
        assertThat(cache.get("b", this::load)).isEqualTo("b-v4");

        cache.invalidateAll();
        assertThat(cache.get("a", this::load)).isEqualTo("a-v5");
    }

    @Test
    void aLoadThatRacesAnInvalidationIsNotStored() {
        // O carregamento leu o dado antes de o logout apagar a sessão: não pode guardar o usuário velho.
        String value = cache.get("a", key -> {
            cache.invalidate("a");
            return "velho";
        });
        assertThat(value).isEqualTo("velho");
        assertThat(cache.get("a", this::load)).isEqualTo("a-v1");
    }

    @Test
    void invalidatesAgainWhenTheTransactionEnds() {
        TransactionSynchronizationManager.initSynchronization();
        cache.invalidate("a");
        // Leitura entre a invalidação e o commit ainda vê o dado antigo e o guarda.
        cache.get("a", key -> "antes-do-commit");
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        }
        assertThat(cache.get("a", this::load)).isEqualTo("a-v1");
    }

    @Test
    void keepsTheSizeBounded() {
        cache.get("a", this::load);
        cache.get("b", this::load);
        cache.get("c", this::load);
        cache.get("d", this::load);
        assertThat(cache.size()).isLessThanOrEqualTo(3);
    }

    @Test
    void zeroTtlAlwaysLoads() {
        ShortCache<String, String> disabled = ShortCache.disabled();
        disabled.get("a", this::load);
        disabled.get("a", this::load);
        assertThat(loads).hasValue(2);
    }
}
