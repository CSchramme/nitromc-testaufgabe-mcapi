/*
 * Copyright (c) 2026 Lukas Pellny.
 * This code is proprietary and was developed for NitroMC.
 * All rights reserved.
 */

package net.nitromc.api.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.CommandSender;

public final class MessageUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("(#[a-fA-F0-9]{6})");
    private static final String PREFIX = "#9500DD&lN#7F1DE1&lI#693BE5&lT#5358E9&lR#3C75EC&lO#2693F0&lM#10B0F4&lC &8» &7"; //main prefix
    // Erweiterung: liest die von colorize() erzeugten §-Codes (inkl. Hex im Format §x§R§R§G§G§B§B) als Component
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.SECTION_CHAR)
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private MessageUtil() {
    }

    public static void send(CommandSender sender, String message) {
        sender.sendMessage(colorize(PREFIX + message));
    }

    /**
     * Erweiterung: Sendet eine Nachricht mit Prefix, die beim Anklicken eine Aktion auslöst,
     * z. B. {@link ClickEvent#openUrl(String)} oder {@link ClickEvent#copyToClipboard(String)}.
     */
    public static void sendClickable(CommandSender sender, String message, String hoverText, ClickEvent clickEvent) {
        sender.sendMessage(LEGACY.deserialize(prefixed(message))
                .hoverEvent(HoverEvent.showText(LEGACY.deserialize(colorize(hoverText))))
                .clickEvent(clickEvent));
    }

    public static String prefixed(String message) {
        return colorize(PREFIX + message);
    }

    public static String colorize(String input) {
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuilder builder = new StringBuilder();

        while (matcher.find()) {
            matcher.appendReplacement(builder, ChatColor.of(matcher.group(1)).toString());
        }

        matcher.appendTail(builder);
        return ChatColor.translateAlternateColorCodes('&', builder.toString());
    }
}
