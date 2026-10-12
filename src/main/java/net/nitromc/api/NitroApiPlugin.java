package net.nitromc.api;

import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import net.nitromc.api.command.McInfoCommand;
import net.nitromc.api.command.NameHistoryCommand;
import net.nitromc.api.gui.ProfileGui;
import net.nitromc.api.service.ApiSettings;
import net.nitromc.api.service.McApiService;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class NitroApiPlugin extends JavaPlugin {

    private static final String DEFAULT_DATE_FORMAT = "dd.MM.yyyy HH:mm";

    private McApiService apiService;
    private ProfileGui profileGui;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        apiService = new McApiService(loadApiSettings(getConfig()), getLogger());
        DateTimeFormatter dateFormatter = loadDateFormatter(getConfig());

        profileGui = new ProfileGui(this, apiService, dateFormatter);
        getServer().getPluginManager().registerEvents(profileGui, this);

        registerCommand("mcinfo", new McInfoCommand(this, apiService, dateFormatter, profileGui));
        registerCommand("namehistory", new NameHistoryCommand(this, apiService, dateFormatter));
    }

    @Override
    public void onDisable() {
        if (profileGui != null) {
            profileGui.closeAll();
            profileGui = null;
        }
        if (apiService != null) {
            apiService.shutdown();
            apiService = null;
        }
    }

    private void registerCommand(String name, TabExecutor executor) {
        PluginCommand command = Objects.requireNonNull(getCommand(name), () -> "Command missing in plugin.yml: " + name);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private ApiSettings loadApiSettings(FileConfiguration config) {
        ApiSettings defaults = ApiSettings.defaults();
        return new ApiSettings(
                URI.create(config.getString("api.base-url", ApiSettings.DEFAULT_BASE_URL)),
                positiveMillis(config.getLong("api.connect-timeout-ms"), defaults.connectTimeout()),
                positiveMillis(config.getLong("api.request-timeout-ms"), defaults.requestTimeout()),
                Math.max(0, config.getInt("api.max-retries", defaults.maxRetries())),
                Duration.ofMillis(Math.max(0, config.getLong("api.retry-base-delay-ms",
                        defaults.retryBaseDelay().toMillis()))),
                Duration.ofSeconds(Math.max(0, config.getLong("cache.ttl-seconds", defaults.cacheTtl().toSeconds()))),
                Math.max(1, config.getInt("cache.max-entries", defaults.cacheMaxEntries()))
        );
    }

    private DateTimeFormatter loadDateFormatter(FileConfiguration config) {
        String pattern = config.getString("date-format", DEFAULT_DATE_FORMAT);
        try {
            return DateTimeFormatter.ofPattern(pattern).withZone(ZoneId.systemDefault());
        } catch (IllegalArgumentException exception) {
            getLogger().warning("Invalid date-format '" + pattern + "', falling back to " + DEFAULT_DATE_FORMAT);
            return DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT).withZone(ZoneId.systemDefault());
        }
    }

    private static Duration positiveMillis(long millis, Duration fallback) {
        return millis > 0 ? Duration.ofMillis(millis) : fallback;
    }
}
