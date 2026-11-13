package com.foodie.api;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Cache em memória de vida curta para leituras repetidas em toda chamada (sessão, papel do funcionário).
 * Teste de carga de 12/11: cada chamada pedia ~4 conexões ao banco, metade delas só para descobrir quem
 * é o usuário. Com poucos segundos de validade, a tela que consulta a cada 8 s passa a ir ao banco uma
 * vez por janela em vez de uma vez por chamada.
 *
 * Invalidação: imediata, e de novo depois do commit quando há transação aberta (senão uma leitura feita
 * entre a invalidação e o commit guardaria o dado velho). Uma leitura que começou antes de uma
 * invalidação não grava o resultado (contador de épocas). Com várias instâncias da API, a invalidação só
 * vale na instância que fez a mudança; nas outras, o dado fica velho no máximo pela validade.
 */
public final class ShortCache<K, V> {
    private record Entry<V>(V value, long expiresAt) {}

    private final Map<K, Entry<V>> entries = new ConcurrentHashMap<>();
    private final AtomicLong epoch = new AtomicLong();
    private final long ttlNanos;
    private final int maxEntries;
    private final LongSupplier clock;

    public ShortCache(Duration ttl, int maxEntries) {
        this(ttl, maxEntries, System::nanoTime);
    }

    ShortCache(Duration ttl, int maxEntries, LongSupplier clock) {
        this.ttlNanos = Math.max(0, ttl.toNanos());
        this.maxEntries = Math.max(1, maxEntries);
        this.clock = clock;
    }

    public static <K, V> ShortCache<K, V> disabled() {
        return new ShortCache<>(Duration.ZERO, 1);
    }

    /** Devolve o valor guardado ou carrega. Resultado {@code null} não é guardado. */
    public V get(K key, Function<K, V> loader) {
        if (ttlNanos == 0) return loader.apply(key);
        long now = clock.getAsLong();
        Entry<V> entry = entries.get(key);
        if (entry != null && now - entry.expiresAt() < 0) return entry.value();
        long seen = epoch.get();
        V value = loader.apply(key);
        if (value == null || epoch.get() != seen) return value;
        if (entries.size() >= maxEntries) prune(now);
        entries.put(key, new Entry<>(value, now + ttlNanos));
        // Uma invalidação entre a conferência acima e o put não pode deixar o valor velho guardado.
        if (epoch.get() != seen) entries.remove(key);
        return value;
    }

    public void invalidate(K key) {
        run(() -> entries.remove(key));
    }

    public void invalidateIf(Predicate<V> stale) {
        run(() -> entries.values().removeIf(entry -> stale.test(entry.value())));
    }

    public void invalidateAll() {
        run(entries::clear);
    }

    int size() {
        return entries.size();
    }

    private void run(Runnable removal) {
        epoch.incrementAndGet();
        removal.run();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    epoch.incrementAndGet();
                    removal.run();
                }
            });
        }
    }

    private void prune(long now) {
        entries.values().removeIf(entry -> now - entry.expiresAt() >= 0);
        if (entries.size() >= maxEntries) entries.clear();
    }
}
