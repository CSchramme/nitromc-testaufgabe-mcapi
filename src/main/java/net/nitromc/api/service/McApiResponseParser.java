package net.nitromc.api.service;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import net.nitromc.api.model.NameHistoryEntry;
import net.nitromc.api.model.PlayerProfile;
import net.nitromc.api.model.SkinModel;
import net.nitromc.api.model.SkinTexture;

/**
 * Wandelt JSON-Antworten von mc-api.io in Model-Objekte um.
 */
public final class McApiResponseParser {

    private static final Pattern UNDASHED_UUID = Pattern.compile("^[0-9a-fA-F]{32}$");
    private static final String SKIN_TEXTURE_KEY = "SKIN";
    private static final String SLIM_MODEL = "slim";

    private final Gson gson = new Gson();

    /**
     * Liest ein {@code ProfileDocument} von {@code GET /profile/{name}/JAVA}.
     *
     * @throws ApiException mit {@link ApiException.Reason#INVALID_RESPONSE}, wenn die Antwort unbrauchbar ist
     */
    public PlayerProfile parseProfile(String json, Instant fetchedAt) {
        ProfileResponse response = fromJson(json, ProfileResponse.class);
        if (response == null || isBlank(response.name()) || isBlank(response.uuid())) {
            throw invalidResponse("Profile response is missing name or uuid", null);
        }

        return new PlayerProfile(
                parseUuid(response.uuid()),
                response.name(),
                extractSkinUrl(response.decodedTexture()),
                extractSkinModel(response.decodedTexture()),
                extractSkinTexture(response.textures()),
                buildNameHistory(response.name(), response.names()),
                fetchedAt
        );
    }

    /**
     * Liest die Fehlermeldung aus einer Fehlerantwort ({@code {"error": true, "message": "..."}}).
     */
    public Optional<String> parseErrorMessage(String json) {
        if (isBlank(json)) {
            return Optional.empty();
        }
        try {
            ErrorResponse error = gson.fromJson(json, ErrorResponse.class);
            return Optional.ofNullable(error).map(ErrorResponse::message).filter(message -> !message.isBlank());
        } catch (JsonParseException exception) {
            return Optional.empty();
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return gson.fromJson(json, type);
        } catch (JsonParseException exception) {
            throw invalidResponse("Response is not valid JSON", exception);
        }
    }

    private static UUID parseUuid(String raw) {
        String value = UNDASHED_UUID.matcher(raw).matches()
                ? raw.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})", "$1-$2-$3-$4-$5")
                : raw;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw invalidResponse("Invalid uuid in response: " + raw, exception);
        }
    }

    private static String extractSkinUrl(DecodedTexture decodedTexture) {
        TextureUrl skin = skinTexture(decodedTexture);
        if (skin == null || isBlank(skin.url())) {
            return null;
        }
        // Mojang liefert die Textur-URLs per http aus, textures.minecraft.net unterstützt aber auch https.
        return skin.url().replaceFirst("^http://", "https://");
    }

    /** Mojang kennzeichnet schmale Arme mit {@code "metadata": {"model": "slim"}}, sonst fehlt das Feld. */
    private static SkinModel extractSkinModel(DecodedTexture decodedTexture) {
        TextureUrl skin = skinTexture(decodedTexture);
        boolean slim = skin != null && skin.metadata() != null && SLIM_MODEL.equalsIgnoreCase(skin.metadata().model());
        return slim ? SkinModel.SLIM : SkinModel.CLASSIC;
    }

    private static TextureUrl skinTexture(DecodedTexture decodedTexture) {
        if (decodedTexture == null || decodedTexture.textures() == null) {
            return null;
        }
        return decodedTexture.textures().get(SKIN_TEXTURE_KEY);
    }

    private static SkinTexture extractSkinTexture(Textures textures) {
        if (textures == null || isBlank(textures.value())) {
            return null;
        }
        return new SkinTexture(textures.value(), isBlank(textures.signature()) ? null : textures.signature());
    }

    /**
     * Baut die Namenshistorie aus der {@code names}-Map (Name → Zeitstempel in ms). Mojang hat die öffentliche
     * Name-History-API 2022 abgeschaltet, daher ist die Map häufig leer. Der aktuelle Name ist immer enthalten.
     */
    private static List<NameHistoryEntry> buildNameHistory(String currentName, Map<String, Long> names) {
        List<NameHistoryEntry> history = new ArrayList<>();
        if (names != null) {
            names.forEach((name, timestamp) -> {
                if (!isBlank(name)) {
                    history.add(new NameHistoryEntry(name, toInstant(timestamp)));
                }
            });
        }
        history.sort(Comparator.comparing(NameHistoryEntry::changedAt, Comparator.nullsFirst(Comparator.naturalOrder())));

        boolean containsCurrent = history.stream().anyMatch(entry -> entry.name().equalsIgnoreCase(currentName));
        if (!containsCurrent) {
            history.add(NameHistoryEntry.withoutDate(currentName));
        }
        return history;
    }

    private static Instant toInstant(Long epochMillis) {
        return epochMillis == null || epochMillis <= 0 ? null : Instant.ofEpochMilli(epochMillis);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static ApiException invalidResponse(String message, Throwable cause) {
        return new ApiException(ApiException.Reason.INVALID_RESPONSE, message, cause);
    }

    // DTOs, die 1:1 der JSON-Struktur von mc-api.io entsprechen. Nicht benötigte Felder werden ignoriert.

    private record ProfileResponse(
            String name, String uuid, Textures textures, DecodedTexture decodedTexture, Map<String, Long> names) {
    }

    private record Textures(String value, String signature) {
    }

    private record DecodedTexture(Map<String, TextureUrl> textures) {
    }

    private record TextureUrl(String url, TextureMetadata metadata) {
    }

    private record TextureMetadata(String model) {
    }

    private record ErrorResponse(String message, String cause) {
    }
}
