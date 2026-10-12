package net.nitromc.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.nitromc.api.model.NameHistoryEntry;
import net.nitromc.api.model.PlayerProfile;
import net.nitromc.api.model.SkinModel;
import net.nitromc.api.model.SkinTexture;
import org.junit.jupiter.api.Test;

class McApiResponseParserTest {

    private static final Instant NOW = Instant.parse("2026-05-16T16:30:00Z");

    private final McApiResponseParser parser = new McApiResponseParser();

    @Test
    void parsesProfileWithSkin() {
        String json = """
                {
                  "cachedAt": 1791730208415,
                  "decodedTexture": {
                    "profileId": "069a79f444e94726a5befca90e38aaf5",
                    "profileName": "Notch",
                    "textures": { "SKIN": { "url": "http://textures.minecraft.net/texture/2920" } }
                  },
                  "name": "Notch",
                  "textures": { "value": "abc", "signature": "def" },
                  "uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5"
                }
                """;

        PlayerProfile profile = parser.parseProfile(json, NOW);

        assertEquals(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"), profile.uuid());
        assertEquals("Notch", profile.name());
        assertEquals("https://textures.minecraft.net/texture/2920", profile.skinUrl());
        assertEquals("https://namemc.com/profile/069a79f4-44e9-4726-a5be-fca90e38aaf5", profile.profileUrl());
        assertEquals(NOW, profile.fetchedAt());
        assertEquals(List.of(NameHistoryEntry.withoutDate("Notch")), profile.nameHistory());
        assertEquals(new SkinTexture("abc", "def"), profile.skinTexture());
        assertEquals(SkinModel.CLASSIC, profile.skinModel());
    }

    @Test
    void detectsSlimSkinModel() {
        PlayerProfile profile = parser.parseProfile("""
                { "name": "Alex", "uuid": "bef1d0a8-e281-4f69-9200-64e07c235c96",
                  "decodedTexture": { "textures": { "SKIN": {
                    "url": "http://textures.minecraft.net/texture/46ac", "metadata": { "model": "slim" } } } } }
                """, NOW);

        assertEquals(SkinModel.SLIM, profile.skinModel());
    }

    @Test
    void acceptsUuidWithoutDashesAndMissingSkin() {
        PlayerProfile profile = parser.parseProfile("""
                { "name": "Steve", "uuid": "8667ba71b85a4004af54457a9734eed7" }
                """, NOW);

        assertEquals(UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7"), profile.uuid());
        assertNull(profile.skinUrl());
        assertTrue(profile.skinUrlIfPresent().isEmpty());
        assertTrue(profile.skinTextureIfPresent().isEmpty());
    }

    @Test
    void sortsNameHistoryChronologically() {
        PlayerProfile profile = parser.parseProfile("""
                {
                  "name": "LukasDev_",
                  "uuid": "bef1d0a8-e281-4f69-9200-64e07c235c96",
                  "names": { "LukasDev_": 1704063600000, "LukasDev": 0, "TheLukasDev": 1600000000000 }
                }
                """, NOW);

        assertEquals(List.of(
                NameHistoryEntry.withoutDate("LukasDev"),
                new NameHistoryEntry("TheLukasDev", Instant.ofEpochMilli(1600000000000L)),
                new NameHistoryEntry("LukasDev_", Instant.ofEpochMilli(1704063600000L))
        ), profile.nameHistory());
    }

    @Test
    void addsCurrentNameWhenMissingFromHistory() {
        PlayerProfile profile = parser.parseProfile("""
                { "name": "NewName", "uuid": "bef1d0a8-e281-4f69-9200-64e07c235c96", "names": { "OldName": 1600000000000 } }
                """, NOW);

        assertEquals(List.of("OldName", "NewName"),
                profile.nameHistory().stream().map(NameHistoryEntry::name).toList());
    }

    @Test
    void rejectsIncompleteOrMalformedProfiles() {
        assertInvalidResponse("{ \"name\": \"Notch\" }");
        assertInvalidResponse("{ \"name\": \"Notch\", \"uuid\": \"not-a-uuid\" }");
        assertInvalidResponse("<html>Bad Gateway</html>");
        assertInvalidResponse("");
    }

    @Test
    void extractsErrorMessage() {
        assertEquals(Optional.of("Could not find uuid from name 'Notchh'. Wrong edition?"), parser.parseErrorMessage("""
                { "error": true, "message": "Could not find uuid from name 'Notchh'. Wrong edition?", "cause": "ProfileNotFoundException" }
                """));
        assertEquals(Optional.empty(), parser.parseErrorMessage("not json"));
        assertEquals(Optional.empty(), parser.parseErrorMessage(""));
    }

    private void assertInvalidResponse(String json) {
        ApiException exception = assertThrows(ApiException.class, () -> parser.parseProfile(json, NOW));
        assertEquals(ApiException.Reason.INVALID_RESPONSE, exception.reason());
    }
}
