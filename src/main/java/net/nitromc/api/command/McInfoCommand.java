package net.nitromc.api.command;

import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.event.ClickEvent;
import net.nitromc.api.gui.ProfileGui;
import net.nitromc.api.model.PlayerProfile;
import net.nitromc.api.service.McApiService;
import net.nitromc.api.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * {@code /mcinfo <spielername>} – zeigt UUID, Name, Skin- und Profil-URL eines Spielers.
 * Führt ein Spieler den Befehl ohne Namen aus, öffnet sich stattdessen die Profil-GUI mit Namenseingabe.
 */
public final class McInfoCommand extends PlayerLookupCommand<PlayerProfile> {

    private final DateTimeFormatter dateFormatter;
    private final ProfileGui profileGui;

    public McInfoCommand(Plugin plugin, McApiService apiService, DateTimeFormatter dateFormatter, ProfileGui profileGui) {
        super(plugin, apiService);
        this.dateFormatter = dateFormatter;
        this.profileGui = profileGui;
    }

    @Override
    protected boolean handleWithoutArguments(Player player) {
        profileGui.openSearch(player);
        return true;
    }

    @Override
    protected CompletableFuture<PlayerProfile> load(String playerName) {
        return apiService.fetchProfile(playerName);
    }

    @Override
    protected void display(CommandSender sender, String requestedName, PlayerProfile profile) {
        String uuid = profile.uuid().toString();

        MessageUtil.sendClickable(sender, "&7UUID: &f" + uuid, "&eKlicken zum Kopieren", ClickEvent.copyToClipboard(uuid));
        MessageUtil.send(sender, "&7Name: &f" + profile.name());
        profile.skinUrlIfPresent().ifPresentOrElse(
                url -> MessageUtil.sendClickable(sender, "&7Skin: &f" + url, "&eKlicken zum Öffnen", ClickEvent.openUrl(url)),
                () -> MessageUtil.send(sender, "&7Skin: &8Standard-Skin"));
        MessageUtil.sendClickable(sender, "&7Profil: &f" + profile.profileUrl(), "&eKlicken zum Öffnen",
                ClickEvent.openUrl(profile.profileUrl()));
        MessageUtil.send(sender, "&7Abgerufen: &f" + dateFormatter.format(profile.fetchedAt()));
    }
}
