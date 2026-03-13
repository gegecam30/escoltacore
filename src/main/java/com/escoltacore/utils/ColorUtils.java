package com.escoltacore.utils;

import net.md_5.bungee.api.ChatColor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtils {

    // Patrón para capturar {#ffffff}
    private static final Pattern HEX_PATTERN = Pattern.compile("\\{#([A-Fa-f0-9]{6})\\}");

    /**
     * Traduce códigos de color HEX ({#ffffff}) y Legacy (&a).
     * @param message El mensaje crudo.
     * @return Mensaje coloreado compatible con Spigot/Paper.
     */
    public static String translate(String message) {
        if (message == null || message.isEmpty()) return "";

        // 1. Procesar HEX
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuilder buffer = new StringBuilder();

        while (matcher.find()) {
            String hexCode = matcher.group(1);
            matcher.appendReplacement(buffer, ChatColor.of("#" + hexCode).toString());
        }
        matcher.appendTail(buffer);

        // 2. Procesar Legacy (&a, &l, etc.)
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }
}