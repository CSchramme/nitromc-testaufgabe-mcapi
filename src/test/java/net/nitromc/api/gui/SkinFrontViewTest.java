package net.nitromc.api.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.image.BufferedImage;
import net.nitromc.api.model.SkinModel;
import org.junit.jupiter.api.Test;

class SkinFrontViewTest {

    private static final int HEAD = 0xFF110000;
    private static final int HAT = 0xFF220000;
    private static final int BODY = 0xFF003300;
    private static final int RIGHT_ARM = 0xFF000044;
    private static final int LEFT_ARM = 0xFF000055;
    private static final int RIGHT_LEG = 0xFF660000;
    private static final int LEFT_LEG = 0xFF007700;

    @Test
    void placesAllBodyPartsOfModernSkin() {
        BufferedImage skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        fill(skin, 8, 8, 8, 8, HEAD);
        fill(skin, 20, 20, 8, 12, BODY);
        fill(skin, 44, 20, 4, 12, RIGHT_ARM);
        fill(skin, 36, 52, 4, 12, LEFT_ARM);
        fill(skin, 4, 20, 4, 12, RIGHT_LEG);
        fill(skin, 20, 52, 4, 12, LEFT_LEG);

        int[][] view = SkinFrontView.render(skin, SkinModel.CLASSIC);

        assertEquals(HEAD, view[0][4]);
        assertEquals(HEAD, view[7][11]);
        assertEquals(SkinFrontView.TRANSPARENT, view[0][0]);
        assertEquals(BODY, view[8][4]);
        assertEquals(RIGHT_ARM, view[8][0]);
        assertEquals(LEFT_ARM, view[19][15]);
        assertEquals(RIGHT_LEG, view[20][4]);
        assertEquals(LEFT_LEG, view[31][11]);
        assertEquals(SkinFrontView.TRANSPARENT, view[20][0]);
    }

    @Test
    void drawsOpaqueOverlayAndSkipsTransparentOverlay() {
        BufferedImage skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        fill(skin, 8, 8, 8, 8, HEAD);
        fill(skin, 40, 8, 4, 8, HAT);

        int[][] view = SkinFrontView.render(skin, SkinModel.CLASSIC);

        assertEquals(HAT, view[0][4]);
        assertEquals(HEAD, view[0][8]);
    }

    @Test
    void ignoresFullyOpaqueHatAreaOfLegacySkins() {
        BufferedImage skin = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        fill(skin, 8, 8, 8, 8, HEAD);
        fill(skin, 32, 0, 32, 16, HAT);

        assertEquals(HEAD, SkinFrontView.render(skin, SkinModel.CLASSIC)[0][4]);

        skin.setRGB(63, 0, 0);
        assertEquals(HAT, SkinFrontView.render(skin, SkinModel.CLASSIC)[0][4]);
    }

    @Test
    void slimArmsAreThreePixelsWide() {
        BufferedImage skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        fill(skin, 44, 20, 3, 12, RIGHT_ARM);
        fill(skin, 36, 52, 3, 12, LEFT_ARM);

        int[][] view = SkinFrontView.render(skin, SkinModel.SLIM);

        assertEquals(SkinFrontView.TRANSPARENT, view[8][0]);
        assertEquals(RIGHT_ARM, view[8][1]);
        assertEquals(LEFT_ARM, view[8][14]);
        assertEquals(SkinFrontView.TRANSPARENT, view[8][15]);
    }

    @Test
    void mirrorsRightLimbsForLegacySkins() {
        BufferedImage skin = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        fill(skin, 44, 20, 1, 12, RIGHT_ARM);
        fill(skin, 45, 20, 3, 12, BODY);

        int[][] view = SkinFrontView.render(skin, SkinModel.CLASSIC);

        assertEquals(RIGHT_ARM, view[8][0]);
        assertEquals(RIGHT_ARM, view[8][15]);
        assertEquals(BODY, view[8][12]);
    }

    private static void fill(BufferedImage image, int x, int y, int width, int height, int argb) {
        for (int dy = 0; dy < height; dy++) {
            for (int dx = 0; dx < width; dx++) {
                image.setRGB(x + dx, y + dy, argb);
            }
        }
    }
}
