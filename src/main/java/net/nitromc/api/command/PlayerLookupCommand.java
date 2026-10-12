package net.nitromc.api.command;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.nitromc.api.service.McApiService;
import net.nitromc.api.util.ApiErrorMessages;
import net.nitromc.api.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Gemeinsamer Ablauf für Befehle der Form {@code /<befehl> <spielername>}:
 * Eingabe prüfen, Daten asynchron laden und das Ergebnis auf dem Main-Thread ausgeben.
 *
 * @param <T> Typ des geladenen Ergebnisses
 */
abstract class PlayerLookupCommand<T> implements TabExecutor {

    private final Plugin plugin;
    protected final McApiService apiService;

    protected PlayerLookupCommand(Plugin plugin, McApiService apiService) {
        this.plugin = plugin;
        this.apiService = apiService;
    }

    /** Startet den asynchronen Abruf. Wird auf dem Main-Thread aufgerufen und darf nicht blockieren. */
    protected abstract CompletableFuture<T> load(String playerName);

    /** Gibt das Ergebnis aus. Wird auf dem Main-Thread aufgerufen. */
    protected abstract void display(CommandSender sender, String requestedName, T result);

    /**
     * Wird aufgerufen, wenn ein Spieler den Befehl ohne Argument ausführt.
     *
     * @return {@code true}, wenn der Aufruf behandelt wurde; sonst wird die Verwendung angezeigt
     */
    protected boolean handleWithoutArguments(Player player) {
        return false;
    }

    @Override
    public final boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 && sender instanceof Player player && handleWithoutArguments(player)) {
            return true;
        }
        if (args.length != 1) {
            MessageUtil.send(sender, "&cVerwendung: &7/" + label + " <spielername>");
            return true;
        }

        String playerName = args[0];
        if (!McApiService.isValidPlayerName(playerName)) {
            MessageUtil.send(sender, "&cUngültiger Spielername. Erlaubt sind 1–16 Zeichen (a–z, 0–9, _).");
            return true;
        }

        MessageUtil.send(sender, "&7Lade Daten für &e" + playerName + "&7...");
        load(playerName).whenComplete((result, error) -> runOnMainThread(() -> {
            if (!canReceiveMessages(sender)) {
                return;
            }
            if (error != null) {
                sendError(sender, playerName, error);
            } else {
                display(sender, playerName, result);
            }
        }));
        return true;
    }

    @Override
    public final List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .filter(player -> !(sender instanceof Player self) || self.canSee(player))
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private void sendError(CommandSender sender, String playerName, Throwable error) {
        MessageUtil.send(sender, ApiErrorMessages.describe(error, playerName, plugin.getLogger()));
    }

    private void runOnMainThread(Runnable task) {
        // Nach dem Deaktivieren des Plugins dürfen keine Tasks mehr registriert werden.
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private static boolean canReceiveMessages(CommandSender sender) {
        return !(sender instanceof Player player) || player.isOnline();
    }
}
