package net.nitromc.api.gui;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.nitromc.api.model.PlayerProfile;
import net.nitromc.api.service.McApiService;
import net.nitromc.api.util.ApiErrorMessages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

/**
 * Steuert die Profil-GUI: Namenseingabe im Amboss ({@link PlayerSearchMenu}), asynchrones Laden über den
 * {@link McApiService} und Anzeige im {@link ProfileMenu}. Verhindert außerdem, dass Items aus den Menüs
 * genommen werden können.
 */
public final class ProfileGui implements Listener {

    private final Plugin plugin;
    private final McApiService apiService;
    private final DateTimeFormatter dateFormatter;
    private final Map<UUID, PlayerSearchMenu> openSearches = new HashMap<>();

    public ProfileGui(Plugin plugin, McApiService apiService, DateTimeFormatter dateFormatter) {
        this.plugin = plugin;
        this.apiService = apiService;
        this.dateFormatter = dateFormatter;
    }

    /** Öffnet die Namenseingabe. */
    public void openSearch(Player player) {
        PlayerSearchMenu search = new PlayerSearchMenu(player);
        PlayerSearchMenu previous = openSearches.put(player.getUniqueId(), search);
        if (previous != null) {
            previous.clear();
        }
        search.open(player);
    }

    /** Schließt alle offenen Menüs, z. B. beim Deaktivieren des Plugins. */
    public void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            InventoryView view = player.getOpenInventory();
            if (view.getTopInventory().getHolder(false) instanceof ProfileMenu || isOwnSearch(player, view)) {
                player.closeInventory();
            }
        }
        openSearches.values().forEach(PlayerSearchMenu::clear);
        openSearches.clear();
    }

    private void search(Player player, String playerName) {
        ProfileMenu menu = newProfileMenu(playerName);
        menu.showLoading();
        menu.open(player);

        apiService.fetchProfile(playerName).whenComplete((profile, error) -> runOnMainThread(() -> {
            if (error != null) {
                menu.showError(ApiErrorMessages.describe(error, playerName, plugin.getLogger()));
            } else {
                menu.showProfile(profile);
                loadSkinPreview(menu, profile);
            }
        }));
    }

    /** Lädt die Skin-Datei nach, ohne die Anzeige des restlichen Profils zu verzögern. */
    private void loadSkinPreview(ProfileMenu menu, PlayerProfile profile) {
        if (profile.skinUrlIfPresent().isEmpty()) {
            return;
        }
        apiService.fetchSkinImage(profile).whenComplete((skin, error) -> runOnMainThread(() -> {
            if (error != null) {
                plugin.getLogger().fine(() -> "Skin preview unavailable for " + profile.name() + ": " + error);
                menu.showSkinUnavailable(profile);
            } else {
                menu.showSkin(profile, skin);
            }
        }));
    }

    private ProfileMenu newProfileMenu(String playerName) {
        return new ProfileMenu(playerName, dateFormatter, this::openSearch);
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player) || !isOwnSearch(player, event.getView())) {
            return;
        }
        event.setResult(openSearches.get(player.getUniqueId()).resultItem());
        event.getView().setRepairCost(0);
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        boolean clickedTop = event.getRawSlot() >= 0 && event.getRawSlot() < event.getView().getTopInventory().getSize();

        if (event.getInventory().getHolder(false) instanceof ProfileMenu menu) {
            // Auch Klicks im eigenen Inventar abbrechen, sonst ließen sich Items per Shift-Klick hineinlegen.
            event.setCancelled(true);
            if (clickedTop) {
                // Inventare dürfen während des Click-Events nicht gewechselt werden, daher einen Tick später.
                menu.actionAt(event.getRawSlot()).ifPresent(action -> runOnMainThread(() -> action.accept(player)));
            }
        } else if (isOwnSearch(player, event.getView())) {
            event.setCancelled(true);
            if (clickedTop && event.getRawSlot() == PlayerSearchMenu.RESULT_SLOT) {
                openSearches.get(player.getUniqueId()).validInput()
                        .ifPresent(name -> runOnMainThread(() -> search(player, name)));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        boolean ownSearch = event.getWhoClicked() instanceof Player player && isOwnSearch(player, event.getView());
        if (ownSearch || event.getInventory().getHolder(false) instanceof ProfileMenu) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player && isOwnSearch(player, event.getView())) {
            openSearches.remove(player.getUniqueId()).clear();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PlayerSearchMenu search = openSearches.remove(event.getPlayer().getUniqueId());
        if (search != null) {
            search.clear();
        }
    }

    private boolean isOwnSearch(Player player, InventoryView view) {
        PlayerSearchMenu search = openSearches.get(player.getUniqueId());
        return search != null && search.view() == view;
    }

    private void runOnMainThread(Runnable task) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
}
