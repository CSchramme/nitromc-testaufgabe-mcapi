package net.nitromc.api.gui;

import com.destroystokyo.paper.profile.ProfileProperty;
import java.awt.image.BufferedImage;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.nitromc.api.model.NameHistoryEntry;
import net.nitromc.api.model.PlayerProfile;
import net.nitromc.api.model.SkinModel;
import net.nitromc.api.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Menü, das ein Spielerprofil grafisch darstellt: oben der Spielerkopf mit echtem Skin, darunter Name, UUID,
 * Namenshistorie und Abrufzeitpunkt, unten die Navigation. Während des Ladens bzw. bei Fehlern zeigt es einen
 * entsprechenden Zustand.
 *
 * <pre>
 * [ ][ ][ ][ ][☻][ ][ ][ ][ ]
 * [ ][N][ ][U][ ][H][ ][A][ ]
 * [ ][ ][ ][⚒][ ][✕][ ][ ][ ]
 * </pre>
 *
 * <p>Das Menü ist sein eigener {@link InventoryHolder}, damit {@link ProfileGui} es eindeutig erkennt.
 */
public final class ProfileMenu implements InventoryHolder {

    private static final int SIZE = 3 * 9;
    private static final int MAX_HISTORY_LINES = 10;

    private static final int HEAD_SLOT = 4;
    private static final int NAME_SLOT = 10;
    private static final int UUID_SLOT = 12;
    private static final int HISTORY_SLOT = 14;
    private static final int FETCHED_AT_SLOT = 16;
    private static final int NEW_SEARCH_SLOT = 21;
    private static final int CLOSE_SLOT = 23;

    private final String requestedName;
    private final DateTimeFormatter dateFormatter;
    private final Consumer<Player> newSearchAction;
    private final Map<Integer, Consumer<Player>> clickActions = new HashMap<>();
    private final Inventory inventory;
    private PlayerProfile shownProfile;

    ProfileMenu(String requestedName, DateTimeFormatter dateFormatter, Consumer<Player> newSearchAction) {
        this.requestedName = requestedName;
        this.dateFormatter = dateFormatter;
        this.newSearchAction = newSearchAction;
        this.inventory = Bukkit.createInventory(this, SIZE, MenuItems.title("PROFIL", requestedName));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void open(Player player) {
        player.openInventory(inventory);
    }

    Optional<Consumer<Player>> actionAt(int slot) {
        return Optional.ofNullable(clickActions.get(slot));
    }

    void showLoading() {
        resetLayout();
        inventory.setItem(HEAD_SLOT, MenuItems.item(Material.SPYGLASS, "Suche läuft",
                "Profil von &f" + requestedName, "wird von mc-api.io geladen …"));
    }

    void showError(String message) {
        resetLayout();
        inventory.setItem(HEAD_SLOT, MenuItems.item(Material.SKELETON_SKULL, "Nicht geladen",
                message, "", "> Unten einen anderen Namen suchen"));
    }

    void showProfile(PlayerProfile profile) {
        resetLayout();
        shownProfile = profile;
        inventory.setItem(HEAD_SLOT, createHead(profile, List.of(),
                profile.skinUrlIfPresent().isPresent() ? "&8Skin-Vorschau wird geladen …" : "Standard-Skin"));
        inventory.setItem(NAME_SLOT, MenuItems.item(Material.NAME_TAG, "Name", "&f" + profile.name()));
        String uuid = profile.uuid().toString();
        setAction(UUID_SLOT, MenuItems.item(Material.PAPER, "UUID",
                "&f" + uuid.substring(0, 18), "&f" + uuid.substring(18), "", "> Klicken zum Kopieren"),
                player -> sendAndClose(player, "&7UUID von &e" + profile.name() + "&7: &f" + uuid,
                        "&eKlicken zum Kopieren", ClickEvent.copyToClipboard(uuid)));
        profile.skinUrlIfPresent().ifPresent(url -> clickActions.put(HEAD_SLOT, player -> sendAndClose(player,
                "&7Skin von &e" + profile.name() + "&7: &f" + url, "&eKlicken zum Öffnen", ClickEvent.openUrl(url))));
        inventory.setItem(HISTORY_SLOT, createHistoryItem(profile.nameHistory()));
        inventory.setItem(FETCHED_AT_SLOT, MenuItems.item(Material.CLOCK, "Abgerufen",
                "&f" + dateFormatter.format(profile.fetchedAt()), "Quelle: &fmc-api.io"));
    }

    /** Ergänzt den Kopf um die Vorderansicht des Skins, sobald die Skin-Datei geladen ist. */
    void showSkin(PlayerProfile profile, BufferedImage skin) {
        if (profile != shownProfile) {
            return;
        }
        List<Component> preview = MenuItems.pixelArt(SkinFrontView.render(skin, profile.skinModel()));
        String model = profile.skinModel() == SkinModel.SLIM ? "Schmale Arme (Alex)" : "Normale Arme (Steve)";
        inventory.setItem(HEAD_SLOT, createHead(profile, preview, model));
    }

    /** Wird aufgerufen, wenn die Skin-Datei nicht geladen werden konnte. */
    void showSkinUnavailable(PlayerProfile profile) {
        if (profile == shownProfile) {
            inventory.setItem(HEAD_SLOT, createHead(profile, List.of(), "&8Keine Skin-Vorschau verfügbar"));
        }
    }

    /** Navigation, die in jedem Zustand gleich ist. Alle übrigen Slots bleiben bewusst leer. */
    private void resetLayout() {
        inventory.clear();
        clickActions.clear();
        shownProfile = null;
        setAction(NEW_SEARCH_SLOT, MenuItems.item(Material.ANVIL, "Neue Suche",
                "Anderen Spieler nachschlagen", "", "> Klicken zum Suchen"), newSearchAction);
        setAction(CLOSE_SLOT, MenuItems.item(Material.BARRIER, "Schließen",
                "", "> Klicken zum Schließen"), Player::closeInventory);
    }

    private ItemStack createHead(PlayerProfile profile, List<Component> preview, String skinInfo) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, meta -> {
            com.destroystokyo.paper.profile.PlayerProfile skullProfile = Bukkit.createProfile(profile.uuid(), profile.name());
            profile.skinTextureIfPresent().ifPresent(texture ->
                    skullProfile.setProperty(new ProfileProperty("textures", texture.value(), texture.signature())));
            meta.setPlayerProfile(skullProfile);
            List<String> lines = profile.skinUrlIfPresent().isPresent()
                    ? List.of("Java Edition", skinInfo, "", "> Klicken für den Skin-Link")
                    : List.of("Java Edition", skinInfo);
            MenuItems.applyText(meta, profile.name(), preview, lines);
        });
        return head;
    }

    private ItemStack createHistoryItem(List<NameHistoryEntry> history) {
        List<String> lines = new ArrayList<>();
        int firstShown = Math.max(0, history.size() - MAX_HISTORY_LINES);
        for (int index = firstShown; index < history.size(); index++) {
            NameHistoryEntry entry = history.get(index);
            String date = entry.changedAtIfKnown().map(changedAt -> " &8(" + dateFormatter.format(changedAt) + ")").orElse("");
            lines.add((index + 1) + ". &f" + entry.name() + date);
        }
        if (history.size() == 1) {
            lines.add("");
            lines.add("&8Keine früheren Namen bekannt.");
        }
        return MenuItems.item(Material.WRITABLE_BOOK, "Namenshistorie", lines.toArray(String[]::new));
    }

    /** Schließt das Menü (im Inventar ist der Chat nicht sichtbar) und sendet eine anklickbare Nachricht. */
    private static void sendAndClose(Player player, String message, String hoverText, ClickEvent clickEvent) {
        player.closeInventory();
        MessageUtil.sendClickable(player, message, hoverText, clickEvent);
    }

    private void setAction(int slot, ItemStack item, Consumer<Player> action) {
        inventory.setItem(slot, item);
        clickActions.put(slot, action);
    }
}
