package net.nitromc.api.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import net.nitromc.api.model.NameHistoryEntry;
import net.nitromc.api.model.PlayerProfile;

/**
 * Kapselt die komplette Kommunikation mit <a href="https://mc-api.io/docs">mc-api.io</a>.
 *
 * <p>Alle Requests laufen über {@link HttpClient#sendAsync} und blockieren nie den aufrufenden Thread.
 * Ergebnisse werden als {@link CompletableFuture} geliefert, Fehler als {@link ApiException}.
 * Temporäre Fehler (Netzwerk, Timeout, 429, 5xx) werden mit exponentiellem Backoff wiederholt,
 * erfolgreiche Profile für die konfigurierte Dauer zwischengespeichert.
 */
public final class McApiService {

    private static final Pattern JAVA_PLAYER_NAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
    private static final String JAVA_EDITION = "JAVA";
    private static final String USER_AGENT = "NitroApiPlugin/1.0 (+https://github.com/NitroMC-Minecraft/Test-Aufgabe)";
    /** Längere Wartezeiten vom Rate-Limit werden nicht abgewartet, sondern direkt als Fehler gemeldet. */
    private static final Duration MAX_RATE_LIMIT_WAIT = Duration.ofSeconds(10);
    /** Skin-Dateien werden nur vom offiziellen Mojang-Texturserver geladen. */
    private static final String TEXTURE_HOST = "textures.minecraft.net";
    private static final int SKIN_WIDTH = 64;

    private final ApiSettings settings;
    private final Logger logger;
    private final ExecutorService executor;
    private final HttpClient httpClient;
    private final McApiResponseParser parser = new McApiResponseParser();
    private final AsyncCache<String, PlayerProfile> profileCache;
    private final AsyncCache<URI, BufferedImage> skinCache;
    private final Clock clock;

    public McApiService(ApiSettings settings, Logger logger) {
        this(settings, logger, Clock.systemDefaultZone());
    }

    McApiService(ApiSettings settings, Logger logger, Clock clock) {
        this.settings = settings;
        this.logger = logger;
        this.clock = clock;
        this.executor = Executors.newCachedThreadPool(daemonThreadFactory());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(settings.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .executor(executor)
                .build();
        this.profileCache = new AsyncCache<>(settings.cacheTtl(), settings.cacheMaxEntries(), clock);
        this.skinCache = new AsyncCache<>(settings.cacheTtl(), settings.cacheMaxEntries(), clock);
    }

    /**
     * Prüft, ob der Name ein gültiger Java-Spielername ist (1–16 Zeichen, Buchstaben, Ziffern, Unterstrich).
     */
    public static boolean isValidPlayerName(String name) {
        return name != null && JAVA_PLAYER_NAME.matcher(name).matches();
    }

    /**
     * Lädt das Profil (UUID, Name, Skin, bekannte Namen) eines Java-Spielers.
     */
    public CompletableFuture<PlayerProfile> fetchProfile(String playerName) {
        if (!isValidPlayerName(playerName)) {
            return CompletableFuture.failedFuture(
                    new ApiException(ApiException.Reason.INVALID_NAME, "Invalid player name: " + playerName));
        }
        if (!settings.cacheEnabled()) {
            return requestProfile(playerName);
        }
        return profileCache.getOrLoad(playerName.toLowerCase(Locale.ROOT), () -> requestProfile(playerName));
    }

    /**
     * Lädt die bekannten Namen eines Spielers in chronologischer Reihenfolge.
     *
     * <p>mc-api.io liefert die Namenshistorie als Teil des Profils ({@code names}), daher genügt ein Request.
     */
    public CompletableFuture<List<NameHistoryEntry>> fetchNameHistory(String playerName) {
        return fetchProfile(playerName).thenApply(PlayerProfile::nameHistory);
    }

    /**
     * Lädt die Skin-Datei (64×64 bzw. 64×32 Pixel) eines Profils von {@code textures.minecraft.net}.
     *
     * @throws ApiException mit {@link ApiException.Reason#INVALID_RESPONSE}, wenn das Profil keinen Skin hat
     *                      oder die Datei kein gültiges Skin-Bild ist (als fehlgeschlagenes Future)
     */
    public CompletableFuture<BufferedImage> fetchSkinImage(PlayerProfile profile) {
        Optional<URI> skinUri = profile.skinUrlIfPresent().flatMap(McApiService::toTextureUri);
        if (skinUri.isEmpty()) {
            return CompletableFuture.failedFuture(new ApiException(ApiException.Reason.INVALID_RESPONSE,
                    "No Mojang skin texture for " + profile.name()));
        }
        if (!settings.cacheEnabled()) {
            return requestSkinImage(skinUri.get());
        }
        return skinCache.getOrLoad(skinUri.get(), () -> requestSkinImage(skinUri.get()));
    }

    public void shutdown() {
        profileCache.clear();
        skinCache.clear();
        httpClient.shutdownNow();
        executor.shutdownNow();
    }

    private CompletableFuture<PlayerProfile> requestProfile(String playerName) {
        HttpRequest request = newRequest("/profile/" + encode(playerName) + "/" + JAVA_EDITION);
        return sendWithRetry(request, HttpResponse.BodyHandlers.ofString(), 0)
                .thenApply(response -> parser.parseProfile(response.body(), clock.instant()));
    }

    private CompletableFuture<BufferedImage> requestSkinImage(URI skinUri) {
        HttpRequest request = HttpRequest.newBuilder(skinUri)
                .timeout(settings.requestTimeout())
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        return sendWithRetry(request, HttpResponse.BodyHandlers.ofByteArray(), 0)
                .thenApply(response -> readSkinImage(response.body()));
    }

    static BufferedImage readSkinImage(byte[] data) {
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(data));
        } catch (IOException exception) {
            throw new ApiException(ApiException.Reason.INVALID_RESPONSE, "Skin is not a readable image", exception);
        }
        boolean validSize = image != null && image.getWidth() == SKIN_WIDTH
                && (image.getHeight() == SKIN_WIDTH || image.getHeight() == SKIN_WIDTH / 2);
        if (!validSize) {
            throw new ApiException(ApiException.Reason.INVALID_RESPONSE, "Skin image has an unsupported format");
        }
        return image;
    }

    private static Optional<URI> toTextureUri(String url) {
        try {
            URI uri = URI.create(url);
            return "https".equals(uri.getScheme()) && TEXTURE_HOST.equals(uri.getHost()) ? Optional.of(uri) : Optional.empty();
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private HttpRequest newRequest(String path) {
        return HttpRequest.newBuilder(URI.create(settings.baseUri() + path))
                .timeout(settings.requestTimeout())
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
    }

    private <T> CompletableFuture<HttpResponse<T>> sendWithRetry(
            HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler, int attempt) {
        return httpClient.sendAsync(request, bodyHandler)
                .handle((response, error) -> {
                    ApiException failure = error != null ? fromTransportError(error) : fromResponse(response);
                    if (failure == null) {
                        return CompletableFuture.completedFuture(response);
                    }

                    Optional<Duration> retryDelay = retryDelay(failure, response, attempt);
                    if (retryDelay.isEmpty()) {
                        logFailure(request, failure);
                        return CompletableFuture.<HttpResponse<T>>failedFuture(failure);
                    }

                    logger.fine(() -> "Retrying %s in %d ms (attempt %d/%d): %s".formatted(
                            request.uri(), retryDelay.get().toMillis(), attempt + 1, settings.maxRetries(),
                            failure.getMessage()));
                    return CompletableFuture
                            .runAsync(() -> { }, CompletableFuture.delayedExecutor(
                                    retryDelay.get().toMillis(), TimeUnit.MILLISECONDS, executor))
                            .thenCompose(ignored -> sendWithRetry(request, bodyHandler, attempt + 1));
                })
                .thenCompose(future -> future);
    }

    /** Liefert {@code null}, wenn die Antwort erfolgreich war. */
    private ApiException fromResponse(HttpResponse<?> response) {
        int status = response.statusCode();
        if (status == 200) {
            return null;
        }

        String body = response.body() instanceof String text ? text : null;
        String detail = parser.parseErrorMessage(body).orElse("HTTP " + status);
        ApiException.Reason reason = switch (status) {
            case 400 -> ApiException.Reason.INVALID_NAME;
            case 404 -> ApiException.Reason.PLAYER_NOT_FOUND;
            case 429 -> ApiException.Reason.RATE_LIMITED;
            case 500, 502, 503, 504 -> ApiException.Reason.SERVICE_UNAVAILABLE;
            default -> ApiException.Reason.UNEXPECTED_STATUS;
        };
        return new ApiException(reason, detail, status);
    }

    private static ApiException fromTransportError(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        if (cause instanceof ApiException apiException) {
            return apiException;
        }
        if (cause instanceof HttpTimeoutException) {
            return new ApiException(ApiException.Reason.TIMEOUT, "Request timed out", cause);
        }
        if (cause instanceof IOException) {
            return new ApiException(ApiException.Reason.NETWORK_ERROR, "Connection failed: " + cause, cause);
        }
        return new ApiException(ApiException.Reason.NETWORK_ERROR, "Unexpected transport error: " + cause, cause);
    }

    private Optional<Duration> retryDelay(ApiException failure, HttpResponse<?> response, int attempt) {
        if (!failure.reason().isTemporary() || attempt >= settings.maxRetries()) {
            return Optional.empty();
        }

        Duration backoff = settings.retryBaseDelay().multipliedBy(1L << attempt);
        if (failure.reason() != ApiException.Reason.RATE_LIMITED || response == null) {
            return Optional.of(backoff);
        }

        OptionalLong resetSeconds = rateLimitResetSeconds(response);
        if (resetSeconds.isEmpty()) {
            return Optional.of(backoff);
        }
        Duration wait = Duration.ofSeconds(resetSeconds.getAsLong());
        return wait.compareTo(MAX_RATE_LIMIT_WAIT) > 0 ? Optional.empty() : Optional.of(max(wait, backoff));
    }

    /** mc-api.io sendet {@code X-RateLimit-Reset} (Sekunden), Standard-Clients teils {@code Retry-After}. */
    private static OptionalLong rateLimitResetSeconds(HttpResponse<?> response) {
        for (String header : List.of("Retry-After", "X-RateLimit-Reset")) {
            Optional<String> value = response.headers().firstValue(header);
            if (value.isPresent()) {
                try {
                    return OptionalLong.of(Math.max(0, Long.parseLong(value.get().trim())));
                } catch (NumberFormatException ignored) {
                    // z. B. ein HTTP-Datum statt Sekunden – dann normalen Backoff verwenden
                }
            }
        }
        return OptionalLong.empty();
    }

    private void logFailure(HttpRequest request, ApiException failure) {
        if (failure.reason().isTemporary() || failure.reason() == ApiException.Reason.UNEXPECTED_STATUS) {
            logger.log(Level.WARNING, "mc-api.io request %s failed (%s): %s".formatted(
                    request.uri(), failure.reason(), failure.getMessage()));
        } else {
            logger.fine(() -> "mc-api.io request %s failed (%s): %s".formatted(
                    request.uri(), failure.reason(), failure.getMessage()));
        }
    }

    private static Duration max(Duration first, Duration second) {
        return first.compareTo(second) >= 0 ? first : second;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static ThreadFactory daemonThreadFactory() {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "NitroApiPlugin-HTTP-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
