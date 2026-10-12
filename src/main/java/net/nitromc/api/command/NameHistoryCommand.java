package net.nitromc.api.command;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.nitromc.api.model.NameHistoryEntry;
import net.nitromc.api.service.McApiService;
import net.nitromc.api.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

/**
 * {@code /namehistory <spielername>} – zeigt die bekannten Namen eines Spielers.
 */
public final class NameHistoryCommand extends PlayerLookupCommand<List<NameHistoryEntry>> {

    private final DateTimeFormatter dateFormatter;

    public NameHistoryCommand(Plugin plugin, McApiService apiService, DateTimeFormatter dateFormatter) {
        super(plugin, apiService);
        this.dateFormatter = dateFormatter;
    }

    @Override
    protected CompletableFuture<List<NameHistoryEntry>> load(String playerName) {
        return apiService.fetchNameHistory(playerName);
    }

    @Override
    protected void display(CommandSender sender, String requestedName, List<NameHistoryEntry> history) {
        MessageUtil.send(sender, "&7Namenshistorie von &e" + requestedName + "&7:");

        for (int index = 0; index < history.size(); index++) {
            NameHistoryEntry entry = history.get(index);
            String date = entry.changedAtIfKnown()
                    .map(changedAt -> " &8(" + dateFormatter.format(changedAt) + ")")
                    .orElse("");
            MessageUtil.send(sender, "&7" + (index + 1) + ". &f" + entry.name() + date);
        }

        if (history.size() == 1) {
            MessageUtil.send(sender, "&8Keine früheren Namen bekannt. Mojang stellt die Namenshistorie seit 2022 "
                    + "nicht mehr bereit, daher liefert die API nur die ihr bekannten Namen.");
        }
    }
}
