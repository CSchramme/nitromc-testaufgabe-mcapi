package net.nitromc.api.service;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * Einstellungen für {@link McApiService}. Bewusst ohne Bukkit-Abhängigkeit, damit der Service
 * auch außerhalb des Servers (z. B. in Unit-Tests) verwendet werden kann.
 */
public record ApiSettings(
        URI baseUri,
        Duration connectTimeout,
        Duration requestTimeout,
        int maxRetries,
        Duration retryBaseDelay,
        Duration cacheTtl,
        int cacheMaxEntries
) {

    public static final String DEFAULT_BASE_URL = "https://mc-api.io";

    public ApiSettings {
        Objects.requireNonNull(baseUri, "baseUri");
        requirePositive(connectTimeout, "connectTimeout");
        requirePositive(requestTimeout, "requestTimeout");
        Objects.requireNonNull(retryBaseDelay, "retryBaseDelay");
        Objects.requireNonNull(cacheTtl, "cacheTtl");
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must not be negative");
        }
        if (cacheMaxEntries < 1) {
            throw new IllegalArgumentException("cacheMaxEntries must be at least 1");
        }
        baseUri = stripTrailingSlash(baseUri);
    }

    public static ApiSettings defaults() {
        return new ApiSettings(
                URI.create(DEFAULT_BASE_URL),
                Duration.ofSeconds(5),
                Duration.ofSeconds(10),
                2,
                Duration.ofMillis(500),
                Duration.ofMinutes(5),
                500
        );
    }

    public boolean cacheEnabled() {
        return !cacheTtl.isZero() && !cacheTtl.isNegative();
    }

    private static void requirePositive(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static URI stripTrailingSlash(URI uri) {
        String value = uri.toString();
        return value.endsWith("/") ? URI.create(value.substring(0, value.length() - 1)) : uri;
    }
}
