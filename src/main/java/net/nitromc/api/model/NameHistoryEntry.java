package net.nitromc.api.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Ein Name, den ein Spieler verwendet hat.
 *
 * @param name      der Spielername
 * @param changedAt Zeitpunkt, zu dem der Name laut API verwendet wurde, oder {@code null}, falls unbekannt
 */
public record NameHistoryEntry(String name, Instant changedAt) {

    public NameHistoryEntry {
        Objects.requireNonNull(name, "name");
    }

    public static NameHistoryEntry withoutDate(String name) {
        return new NameHistoryEntry(name, null);
    }

    public Optional<Instant> changedAtIfKnown() {
        return Optional.ofNullable(changedAt);
    }
}
