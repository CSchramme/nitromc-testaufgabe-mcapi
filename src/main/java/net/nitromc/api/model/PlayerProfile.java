package net.nitromc.api.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Profil eines Java-Spielers, wie es von mc-api.io geliefert wird.
 *
 * @param uuid        eindeutige ID des Spielers
 * @param name        aktueller Spielername
 * @param skinUrl     URL der Skin-Textur oder {@code null}, wenn der Spieler den Standard-Skin nutzt
 * @param skinModel   Armbreite des Skins
 * @param skinTexture signierte Textur-Daten oder {@code null}, falls die API keine liefert
 * @param nameHistory bekannte Namen in chronologischer Reihenfolge (enthält immer den aktuellen Namen)
 * @param fetchedAt   Zeitpunkt, zu dem das Profil von der API abgerufen wurde
 */
public record PlayerProfile(
        UUID uuid,
        String name,
        String skinUrl,
        SkinModel skinModel,
        SkinTexture skinTexture,
        List<NameHistoryEntry> nameHistory,
        Instant fetchedAt
) {

    private static final String PROFILE_URL_TEMPLATE = "https://namemc.com/profile/%s";

    public PlayerProfile {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(skinModel, "skinModel");
        Objects.requireNonNull(fetchedAt, "fetchedAt");
        nameHistory = List.copyOf(nameHistory);
    }

    public Optional<String> skinUrlIfPresent() {
        return Optional.ofNullable(skinUrl);
    }

    public Optional<SkinTexture> skinTextureIfPresent() {
        return Optional.ofNullable(skinTexture);
    }

    public String profileUrl() {
        return PROFILE_URL_TEMPLATE.formatted(uuid);
    }
}
