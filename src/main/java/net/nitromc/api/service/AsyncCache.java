package net.nitromc.api.service;

import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Thread-sicherer Cache mit Ablaufzeit für asynchron geladene Werte.
 *
 * <p>Es werden die {@link CompletableFuture}s selbst gespeichert. Dadurch teilen sich gleichzeitige Anfragen
 * für denselben Schlüssel einen laufenden Request, statt die API mehrfach anzufragen. Fehlgeschlagene
 * Ladevorgänge werden sofort wieder entfernt, damit der nächste Aufruf es erneut versucht.
 */
final class AsyncCache<K, V> {

    private final Map<K, Entry<V>> entries = new ConcurrentHashMap<>();
    private final long ttlMillis;
    private final int maxEntries;
    private final Clock clock;

    AsyncCache(Duration ttl, int maxEntries, Clock clock) {
        this.ttlMillis = ttl.toMillis();
        this.maxEntries = maxEntries;
        this.clock = clock;
    }

    CompletableFuture<V> getOrLoad(K key, Supplier<CompletableFuture<V>> loader) {
        long now = clock.millis();
        CompletableFuture<V> placeholder = new CompletableFuture<>();
        Entry<V> entry = entries.compute(key, (ignored, current) ->
                current != null && !current.isExpired(now) ? current : new Entry<>(placeholder, now + ttlMillis));

        if (entry.future() != placeholder) {
            return entry.future().copy();
        }

        evictIfFull(now);
        startLoading(key, entry, loader);
        return placeholder.copy();
    }

    int size() {
        return entries.size();
    }

    void clear() {
        entries.clear();
    }

    private void startLoading(K key, Entry<V> entry, Supplier<CompletableFuture<V>> loader) {
        CompletableFuture<V> loading;
        try {
            loading = loader.get();
        } catch (RuntimeException exception) {
            loading = CompletableFuture.failedFuture(exception);
        }

        loading.whenComplete((value, error) -> {
            if (error != null) {
                entries.remove(key, entry);
                entry.future().completeExceptionally(error);
            } else {
                entry.future().complete(value);
            }
        });
    }

    private void evictIfFull(long now) {
        if (entries.size() <= maxEntries) {
            return;
        }
        entries.entrySet().removeIf(mapEntry -> mapEntry.getValue().isExpired(now));

        // Danach die ältesten abgeschlossenen Einträge verdrängen. Laufende Requests bleiben erhalten.
        while (entries.size() > maxEntries) {
            Optional<Map.Entry<K, Entry<V>>> oldest = entries.entrySet().stream()
                    .filter(mapEntry -> mapEntry.getValue().future().isDone())
                    .min(Comparator.comparingLong(mapEntry -> mapEntry.getValue().expiresAt()));
            if (oldest.isEmpty()) {
                return;
            }
            entries.remove(oldest.get().getKey(), oldest.get().getValue());
        }
    }

    private record Entry<V>(CompletableFuture<V> future, long expiresAt) {

        /** Laufende Requests laufen nie ab, abgeschlossene erst nach Ablauf der TTL. */
        boolean isExpired(long now) {
            return future.isDone() && now >= expiresAt;
        }
    }
}
