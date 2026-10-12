package net.nitromc.api.gui;

import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.nitromc.api.service.McApiService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.view.AnvilView;

/**
 * Amboss-Fenster, in dessen Textfeld der Spieler einen Namen eingibt. Ein Klick auf das Ergebnis startet die Suche.
 */
final class PlayerSearchMenu {

    static final int RESULT_SLOT = 2;
    private static final int INPUT_SLOT = 0;

    private final AnvilView view;

    PlayerSearchMenu(Player player) {
        this.view = MenuType.ANVIL.builder()
                .title(MenuItems.title("SPIELERSUCHE"))
                .checkReachable(false)
                .build(player);
    }

    void open(Player player) {
        ItemStack input = new ItemStack(Material.PAPER);
        // Leerer Name, damit das Textfeld ohne Vorgabe startet.
        input.editMeta(meta -> {
            meta.displayName(Component.empty());
            meta.lore(List.of(MenuItems.text("&8▪ &7Tippe oben den Spielernamen ein"),
                    MenuItems.text("&8▪ &7und klicke rechts auf das Ergebnis.")));
        });
        view.getTopInventory().setItem(INPUT_SLOT, input);
        player.openInventory(view);
    }

    AnvilView view() {
        return view;
    }

    /** Aktueller, gültiger Name aus dem Textfeld. */
    Optional<String> validInput() {
        String text = view.getRenameText();
        return text != null && McApiService.isValidPlayerName(text.trim()) ? Optional.of(text.trim()) : Optional.empty();
    }

    /** Erzeugt das Ergebnis-Item passend zur aktuellen Eingabe. */
    ItemStack resultItem() {
        String text = view.getRenameText();
        if (text == null || text.isBlank()) {
            return null;
        }
        return validInput()
                .map(name -> MenuItems.item(Material.NAME_TAG, name, "Spieler nachschlagen", "", "> Klicken zum Suchen"))
                .orElseGet(() -> MenuItems.item(Material.BARRIER, "Ungültiger Name",
                        "&c1–16 Zeichen erlaubt:", "&cBuchstaben, Ziffern und _"));
    }

    /** Leert das Fenster, damit Vanilla beim Schließen keine Items an den Spieler zurückgibt. */
    void clear() {
        view.getTopInventory().clear();
    }
}
