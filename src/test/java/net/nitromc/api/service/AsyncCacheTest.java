package net.nitromc.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AsyncCacheTest {

    private final MutableClock clock = new MutableClock();
    private final AsyncCache<String, String> cache = new AsyncCache<>(Duration.ofMinutes(5), 2, clock);
    private final AtomicInteger loads = new AtomicInteger();

    @Test
    void returnsCachedValueUntilExpired() {
        assertEquals("value-1", cache.getOrLoad("notch", this::load).join());
        assertEquals("value-1", cache.getOrLoad("notch", this::load).join());
        assertEquals(1, loads.get());

        clock.advance(Duration.ofMinutes(5));
        assertEquals("value-2", cache.getOrLoad("notch", this::load).join());
        assertEquals(2, loads.get());
    }

    @Test
    void sharesPendingRequest() {
        CompletableFuture<String> pending = new CompletableFuture<>();
        CompletableFuture<String> first = cache.getOrLoad("notch", () -> pending);
        CompletableFuture<String> second = cache.getOrLoad("notch", this::load);

        pending.complete("shared");
        assertEquals("shared", first.join());
        assertEquals("shared", second.join());
        assertEquals(0, loads.get());
    }

    @Test
    void doesNotCacheFailures() {
        CompletableFuture<String> failed = cache.getOrLoad("notch",
                () -> CompletableFuture.failedFuture(new IllegalStateException("boom")));
        assertTrue(failed.isCompletedExceptionally());

        assertEquals("value-1", cache.getOrLoad("notch", this::load).join());
    }

    @Test
    void evictsOldestEntryWhenFull() {
        cache.getOrLoad("a", this::load).join();
        clock.advance(Duration.ofSeconds(1));
        cache.getOrLoad("b", this::load).join();
        clock.advance(Duration.ofSeconds(1));
        cache.getOrLoad("c", this::load).join();

        assertEquals(2, cache.size());
        cache.getOrLoad("c", this::load).join();
        assertEquals(3, loads.get());
    }

    private CompletableFuture<String> load() {
        return CompletableFuture.completedFuture("value-" + loads.incrementAndGet());
    }

    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-05-16T16:30:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
