package net.nitromc.api.gui;

import java.awt.image.BufferedImage;
import net.nitromc.api.model.SkinModel;

/**
 * Setzt aus einer Minecraft-Skin-Datei die Vorderansicht des Spielers zusammen (16×32 Pixel):
 * Kopf, Körper, Arme und Beine inklusive der zweiten Ebene (Hut, Jacke, Ärmel, Hose).
 *
 * <p>Unterstützt das aktuelle 64×64-Format und das alte 64×32-Format, bei dem der linke Arm und das linke Bein
 * gespiegelte Kopien der rechten Seite sind.
 */
final class SkinFrontView {

    static final int WIDTH = 16;
    static final int HEIGHT = 32;
    static final int TRANSPARENT = 0;

    private static final int LIMB_HEIGHT = 12;

    private SkinFrontView() {
    }

    /**
     * @return Pixel als {@code [zeile][spalte]} im ARGB-Format; {@link #TRANSPARENT} für leere Stellen
     */
    static int[][] render(BufferedImage skin, SkinModel model) {
        boolean legacy = skin.getHeight() == 32;
        int armWidth = model == SkinModel.SLIM ? 3 : 4;
        int[][] pixels = new int[HEIGHT][WIDTH];
        Canvas canvas = new Canvas(skin, pixels);

        // Kopf und Hut
        canvas.base(8, 8, 8, 8, 4, 0, false);
        if (!legacy || hasTransparentHatArea(skin)) {
            canvas.overlay(40, 8, 8, 8, 4, 0);
        }

        // Körper und Jacke
        canvas.base(20, 20, 8, LIMB_HEIGHT, 4, 8, false);
        // Rechter Arm (aus Sicht des Betrachters links) und rechtes Bein
        canvas.base(44, 20, armWidth, LIMB_HEIGHT, 4 - armWidth, 8, false);
        canvas.base(4, 20, 4, LIMB_HEIGHT, 4, 20, false);

        if (legacy) {
            canvas.base(44, 20, armWidth, LIMB_HEIGHT, 12, 8, true);
            canvas.base(4, 20, 4, LIMB_HEIGHT, 8, 20, true);
        } else {
            canvas.base(36, 52, armWidth, LIMB_HEIGHT, 12, 8, false);
            canvas.base(20, 52, 4, LIMB_HEIGHT, 8, 20, false);

            canvas.overlay(20, 36, 8, LIMB_HEIGHT, 4, 8);
            canvas.overlay(44, 36, armWidth, LIMB_HEIGHT, 4 - armWidth, 8);
            canvas.overlay(52, 52, armWidth, LIMB_HEIGHT, 12, 8);
            canvas.overlay(4, 36, 4, LIMB_HEIGHT, 4, 20);
            canvas.overlay(4, 52, 4, LIMB_HEIGHT, 8, 20);
        }
        return pixels;
    }

    /**
     * Alte 64×32-Skins haben die Hut-Ebene oft komplett deckend gefüllt. Minecraft blendet sie in diesem Fall
     * aus und zeigt sie nur, wenn der Bereich auch durchsichtige Pixel enthält.
     */
    private static boolean hasTransparentHatArea(BufferedImage skin) {
        for (int y = 0; y < 16; y++) {
            for (int x = 32; x < 64; x++) {
                if (skin.getRGB(x, y) >>> 24 < 128) {
                    return true;
                }
            }
        }
        return false;
    }

    private record Canvas(BufferedImage skin, int[][] pixels) {

        /** Grundebene: wird wie im Spiel immer deckend gezeichnet. */
        void base(int srcX, int srcY, int width, int height, int destX, int destY, boolean mirrored) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int sourceX = srcX + (mirrored ? width - 1 - x : x);
                    pixels[destY + y][destX + x] = 0xFF000000 | (skin.getRGB(sourceX, srcY + y) & 0xFFFFFF);
                }
            }
        }

        /** Zweite Ebene: transparente Pixel werden übersprungen, halbtransparente überblendet. */
        void overlay(int srcX, int srcY, int width, int height, int destX, int destY) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int color = skin.getRGB(srcX + x, srcY + y);
                    int alpha = color >>> 24;
                    if (alpha > 0) {
                        int[] row = pixels[destY + y];
                        row[destX + x] = blend(row[destX + x], color, alpha);
                    }
                }
            }
        }

        private static int blend(int below, int above, int alpha) {
            int red = mix(below >> 16, above >> 16, alpha);
            int green = mix(below >> 8, above >> 8, alpha);
            int blue = mix(below, above, alpha);
            return 0xFF000000 | red << 16 | green << 8 | blue;
        }

        private static int mix(int below, int above, int alpha) {
            return ((above & 0xFF) * alpha + (below & 0xFF) * (255 - alpha)) / 255;
        }
    }
}
