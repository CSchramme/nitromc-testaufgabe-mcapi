package net.nitromc.api.model;

import java.util.Objects;

/**
 * Von Mojang signierte Textur-Daten eines Spielers (Base64-kodiertes {@code textures}-Property).
 * Damit lässt sich z. B. ein Spielerkopf mit dem echten Skin darstellen.
 *
 * @param value     Base64-kodierter Textur-Wert
 * @param signature Mojang-Signatur des Werts oder {@code null}, falls nicht vorhanden
 */
public record SkinTexture(String value, String signature) {

    public SkinTexture {
        Objects.requireNonNull(value, "value");
    }
}
