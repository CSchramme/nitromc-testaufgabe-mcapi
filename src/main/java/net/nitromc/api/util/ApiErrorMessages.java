package net.nitromc.api.util;

import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.nitromc.api.service.ApiException;

/**
 * Übersetzt Fehler aus dem {@link net.nitromc.api.service.McApiService} in Texte für Spieler.
 * Wird von Befehlen (Chat) und der GUI gemeinsam genutzt.
 */
public final class ApiErrorMessages {

    private ApiErrorMessages() {
    }

    /**
     * Liefert eine Meldung mit Farbcodes. Unerwartete Fehler werden zusätzlich geloggt.
     */
    public static String describe(Throwable error, String playerName, Logger logger) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        if (!(cause instanceof ApiException apiException)) {
            logger.log(Level.SEVERE, "Unexpected error while looking up " + playerName, cause);
            return "&cBeim Abrufen der Daten ist ein unerwarteter Fehler aufgetreten.";
        }

        if (apiException.reason() == ApiException.Reason.INVALID_RESPONSE) {
            logger.log(Level.WARNING, "Invalid response from mc-api.io for " + playerName, apiException);
        }
        return switch (apiException.reason()) {
            case INVALID_NAME -> "&e" + playerName + " &cist kein gültiger Java-Spielername.";
            case PLAYER_NOT_FOUND -> "&cEs wurde kein Spieler mit dem Namen &e" + playerName + " &cgefunden.";
            case RATE_LIMITED -> "&cZu viele Anfragen an die API. Bitte versuche es gleich erneut.";
            case SERVICE_UNAVAILABLE -> "&cDie API ist momentan nicht verfügbar. Bitte versuche es später erneut.";
            case TIMEOUT -> "&cDie API hat nicht rechtzeitig geantwortet. Bitte versuche es später erneut.";
            case NETWORK_ERROR -> "&cDie Verbindung zur API ist fehlgeschlagen.";
            case INVALID_RESPONSE, UNEXPECTED_STATUS -> "&cDie API hat eine unerwartete Antwort geliefert.";
        };
    }
}
