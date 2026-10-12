package net.nitromc.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import net.nitromc.api.model.NameHistoryEntry;
import net.nitromc.api.model.PlayerProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class McApiServiceTest {

    private static final String NOTCH_JSON = """
            { "name": "Notch", "uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5",
              "decodedTexture": { "textures": { "SKIN": { "url": "http://textures.minecraft.net/texture/abc" } } } }
            """;

    private final Queue<StubResponse> responses = new ConcurrentLinkedQueue<>();
    private final Queue<String> requestedPaths = new ConcurrentLinkedQueue<>();
    private final AtomicInteger requestCount = new AtomicInteger();

    private HttpServer server;
    private McApiService service;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    @AfterEach
    void stop() {
        if (service != null) {
            service.shutdown();
        }
        server.stop(0);
    }

    @Test
    void fetchesAndParsesProfile() {
        service = newService(Duration.ofMinutes(5), 0);
        responses.add(StubResponse.json(200, NOTCH_JSON));

        PlayerProfile profile = service.fetchProfile("Notch").join();

        assertEquals("Notch", profile.name());
        assertEquals("https://textures.minecraft.net/texture/abc", profile.skinUrl());
        assertEquals(List.of("/profile/Notch/JAVA"), List.copyOf(requestedPaths));
    }

    @Test
    void cachesProfilesCaseInsensitively() {
        service = newService(Duration.ofMinutes(5), 0);
        responses.add(StubResponse.json(200, NOTCH_JSON));

        service.fetchProfile("Notch").join();
        List<NameHistoryEntry> history = service.fetchNameHistory("notch").join();

        assertEquals(List.of(NameHistoryEntry.withoutDate("Notch")), history);
        assertEquals(1, requestCount.get());
    }

    @Test
    void rejectsInvalidNamesWithoutRequest() {
        service = newService(Duration.ZERO, 0);

        assertFailure(ApiException.Reason.INVALID_NAME, service.fetchProfile("not a name!"));
        assertFailure(ApiException.Reason.INVALID_NAME, service.fetchProfile("ThisNameIsWayTooLong"));
        assertEquals(0, requestCount.get());
    }

    @Test
    void mapsNotFoundWithoutRetry() {
        service = newService(Duration.ZERO, 3);
        responses.add(StubResponse.json(404, """
                { "error": true, "message": "Could not find uuid from name 'Nobody'.", "cause": "ProfileNotFoundException" }
                """));

        ApiException exception = assertFailure(ApiException.Reason.PLAYER_NOT_FOUND, service.fetchProfile("Nobody"));

        assertEquals(404, exception.statusCode());
        assertEquals("Could not find uuid from name 'Nobody'.", exception.getMessage());
        assertEquals(1, requestCount.get());
    }

    @Test
    void retriesTemporaryErrors() {
        service = newService(Duration.ZERO, 2);
        responses.add(StubResponse.json(503, "{ \"error\": true, \"message\": \"Mojang unavailable\" }"));
        responses.add(new StubResponse(429, "{}", "X-RateLimit-Reset", "0"));
        responses.add(StubResponse.json(200, NOTCH_JSON));

        assertEquals("Notch", service.fetchProfile("Notch").join().name());
        assertEquals(3, requestCount.get());
    }

    @Test
    void givesUpAfterMaxRetries() {
        service = newService(Duration.ZERO, 1);
        responses.add(StubResponse.json(502, "{}"));
        responses.add(StubResponse.json(502, "{}"));
        responses.add(StubResponse.json(200, NOTCH_JSON));

        assertFailure(ApiException.Reason.SERVICE_UNAVAILABLE, service.fetchProfile("Notch"));
        assertEquals(2, requestCount.get());
    }

    @Test
    void doesNotWaitForLongRateLimits() {
        service = newService(Duration.ZERO, 3);
        responses.add(new StubResponse(429, "{}", "X-RateLimit-Reset", "60"));

        assertFailure(ApiException.Reason.RATE_LIMITED, service.fetchProfile("Notch"));
        assertEquals(1, requestCount.get());
    }

    @Test
    void reportsTimeouts() {
        service = new McApiService(new ApiSettings(baseUri(), Duration.ofSeconds(2), Duration.ofMillis(200),
                0, Duration.ZERO, Duration.ZERO, 10), Logger.getAnonymousLogger());
        responses.add(StubResponse.delayed(Duration.ofSeconds(2)));

        assertFailure(ApiException.Reason.TIMEOUT, service.fetchProfile("Notch"));
    }

    @Test
    void reportsUnparsableResponses() {
        service = newService(Duration.ZERO, 0);
        responses.add(StubResponse.json(200, "{ \"unexpected\": true }"));

        assertFailure(ApiException.Reason.INVALID_RESPONSE, service.fetchProfile("Notch"));
    }

    @Test
    void validatesPlayerNames() {
        assertTrue(McApiService.isValidPlayerName("Notch"));
        assertTrue(McApiService.isValidPlayerName("a"));
        assertTrue(McApiService.isValidPlayerName("Lukas_Dev_123456"));
        assertFalse(McApiService.isValidPlayerName(""));
        assertFalse(McApiService.isValidPlayerName(null));
        assertFalse(McApiService.isValidPlayerName("../uuid"));
        assertFalse(McApiService.isValidPlayerName("Lukas_Dev_1234567"));
    }

    private McApiService newService(Duration cacheTtl, int maxRetries) {
        ApiSettings settings = new ApiSettings(baseUri(), Duration.ofSeconds(2), Duration.ofSeconds(2),
                maxRetries, Duration.ofMillis(10), cacheTtl, 10);
        return new McApiService(settings, Logger.getAnonymousLogger());
    }

    private URI baseUri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    private static ApiException assertFailure(ApiException.Reason expected, CompletableFuture<?> future) {
        CompletionException exception = assertThrows(CompletionException.class, future::join);
        ApiException apiException = assertInstanceOf(ApiException.class, exception.getCause());
        assertEquals(expected, apiException.reason());
        return apiException;
    }

    private void handle(HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        requestedPaths.add(exchange.getRequestURI().getPath());
        StubResponse response = responses.poll();
        if (response == null) {
            response = StubResponse.json(500, "{ \"error\": true, \"message\": \"no stub left\" }");
        }

        if (response.delay() != null) {
            try {
                Thread.sleep(response.delay().toMillis());
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        if (response.headerName() != null) {
            exchange.getResponseHeaders().add(response.headerName(), response.headerValue());
        }
        exchange.sendResponseHeaders(response.status(), body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private record StubResponse(int status, String body, String headerName, String headerValue, Duration delay) {

        StubResponse(int status, String body, String headerName, String headerValue) {
            this(status, body, headerName, headerValue, null);
        }

        static StubResponse json(int status, String body) {
            return new StubResponse(status, body, null, null, null);
        }

        static StubResponse delayed(Duration delay) {
            return new StubResponse(200, NOTCH_JSON, null, null, delay);
        }
    }
}
