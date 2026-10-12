package net.nitromc.api.service;

/**
 * Fehler bei der Kommunikation mit mc-api.io. Der {@link Reason} beschreibt die Ursache,
 * damit aufrufende Schichten passende Meldungen ausgeben können.
 */
public final class ApiException extends RuntimeException {

    public enum Reason {
        /** Der übergebene Name kann kein gültiger Java-Spielername sein. */
        INVALID_NAME(false),
        /** Zu diesem Namen existiert kein Spieler. */
        PLAYER_NOT_FOUND(false),
        /** Das Rate-Limit der API wurde überschritten. */
        RATE_LIMITED(true),
        /** Die API oder ein Upstream-Dienst (z. B. Mojang) ist nicht verfügbar. */
        SERVICE_UNAVAILABLE(true),
        /** Die API hat nicht innerhalb des Timeouts geantwortet. */
        TIMEOUT(true),
        /** Die Verbindung zur API ist fehlgeschlagen. */
        NETWORK_ERROR(true),
        /** Die Antwort der API konnte nicht verarbeitet werden. */
        INVALID_RESPONSE(false),
        /** Die API hat mit einem nicht erwarteten Statuscode geantwortet. */
        UNEXPECTED_STATUS(false);

        private final boolean temporary;

        Reason(boolean temporary) {
            this.temporary = temporary;
        }

        /** Ob ein erneuter Versuch sinnvoll sein kann. */
        public boolean isTemporary() {
            return temporary;
        }
    }

    private final Reason reason;
    private final int statusCode;

    public ApiException(Reason reason, String message) {
        this(reason, message, -1, null);
    }

    public ApiException(Reason reason, String message, int statusCode) {
        this(reason, message, statusCode, null);
    }

    public ApiException(Reason reason, String message, Throwable cause) {
        this(reason, message, -1, cause);
    }

    private ApiException(Reason reason, String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.reason = reason;
        this.statusCode = statusCode;
    }

    public Reason reason() {
        return reason;
    }

    /** HTTP-Statuscode der Antwort oder {@code -1}, falls keine Antwort vorlag. */
    public int statusCode() {
        return statusCode;
    }
}
