package net.nitromc.api.gui;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.nitromc.api.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Gestaltung der Menüs: Titel im NitroMC-Farbverlauf und einheitlich aufgebaute Tooltips.
 * Texte nutzen dieselben Farbcodes ({@code &a}, {@code #RRGGBB}) wie Chat-Nachrichten und werden über
 * {@link MessageUtil#colorize(String)} aufgelöst.
 */
final class MenuItems {

    /** Farbverlauf des NitroMC-Prefix aus {@link MessageUtil}. */
    private static final List<String> BRAND_GRADIENT =
            List.of("#9500DD", "#7F1DE1", "#693BE5", "#5358E9", "#3C75EC", "#2693F0", "#10B0F4");
    private static final String ACCENT = "#693BE5";
    private static final String SEPARATOR = "&8&m                      ";
    private static final String PIXEL = "█";
    /** Farbe des Tooltip-Hintergrunds, damit transparente Skin-Pixel unsichtbar bleiben. */
    private static final TextColor TOOLTIP_BACKGROUND = TextColor.color(0x100010);

    /** MessageUtil erzeugt Hex-Farben im Format §x§R§R§G§G§B§B. */
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.SECTION_CHAR)
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private MenuItems() {
    }

    /** Menütitel, z. B. „PROFIL • Notch“ mit Farbverlauf im ersten Teil. */
    static Component title(String headline, String detail) {
        return text(gradient(headline) + " &8• &7" + detail);
    }

    /** Kurzer Menütitel nur mit Farbverlauf, z. B. für den Amboss mit seiner schmalen Titelzeile. */
    static Component title(String headline) {
        return text(gradient(headline));
    }

    /**
     * Item im Menü-Stil: Überschrift in Akzentfarbe, darunter Trennlinie und Zeilen.
     * Leere Zeilen erzeugen Abstand, Zeilen mit {@code >} am Anfang werden als Hinweis (gelb) dargestellt.
     */
    static ItemStack item(Material material, String name, String... lines) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> applyText(meta, name, List.of(lines)));
        return item;
    }

    static void applyText(ItemMeta meta, String name, List<String> lines) {
        applyText(meta, name, List.of(), lines);
    }

    /** Wie {@link #applyText(ItemMeta, String, List)}, mit zusätzlichen Zeilen (z. B. einem Bild) über dem Text. */
    static void applyText(ItemMeta meta, String name, List<Component> header, List<String> lines) {
        meta.displayName(text(ACCENT + "&l" + name));

        List<Component> lore = new ArrayList<>();
        lore.add(text(SEPARATOR));
        lore.addAll(header);
        for (String line : lines) {
            lore.add(text(formatLine(line)));
        }
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
    }

    /**
     * Wandelt ein Pixelbild ({@code [zeile][spalte]}, ARGB) in Tooltip-Zeilen aus farbigen Blockzeichen um.
     * Gleichfarbige Nachbarn werden zusammengefasst, damit die Item-Daten klein bleiben.
     */
    static List<Component> pixelArt(int[][] pixels) {
        List<Component> rows = new ArrayList<>(pixels.length);
        for (int[] row : pixels) {
            TextComponent.Builder line = Component.text();
            int x = 0;
            while (x < row.length) {
                int start = x;
                while (x < row.length && row[x] == row[start]) {
                    x++;
                }
                TextColor color = row[start] >>> 24 == 0 ? TOOLTIP_BACKGROUND : TextColor.color(row[start] & 0xFFFFFF);
                line.append(Component.text(PIXEL.repeat(x - start), color));
            }
            rows.add(line.build().decoration(TextDecoration.ITALIC, false));
        }
        return rows;
    }

    static Component text(String legacyText) {
        // Item-Texte sind in Minecraft standardmäßig kursiv – das wird hier abgeschaltet.
        return LEGACY.deserialize(MessageUtil.colorize(legacyText)).decoration(TextDecoration.ITALIC, false);
    }

    private static String formatLine(String line) {
        if (line.isEmpty()) {
            return "";
        }
        if (line.startsWith(">")) {
            return "&e» " + line.substring(1).trim();
        }
        return "&8▪ &7" + line;
    }

    private static String gradient(String word) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < word.length(); index++) {
            int colorIndex = word.length() == 1 ? 0 : index * (BRAND_GRADIENT.size() - 1) / (word.length() - 1);
            builder.append(BRAND_GRADIENT.get(colorIndex)).append("&l").append(word.charAt(index));
        }
        return builder.append("&r").toString();
    }
}
