/*
 * Copyright (c) 2026 Lukas Pellny.
 * This code is proprietary and was developed for NitroMC.
 * All rights reserved.
 */

package net.nitromc.api.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.CommandSender;

public final class MessageUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("(#[a-fA-F0-9]{6})");
    private static final String PREFIX = "#9500DD&lN#7F1DE1&lI#693BE5&lT#5358E9&lR#3C75EC&lO#2693F0&lM#10B0F4&lC &8» &7"; //main prefix

    private MessageUtil() {
    }

    public static void send(CommandSender sender, String message) {
        sender.sendMessage(colorize(PREFIX + message));
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
