package com.escoltacore.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Color utility for Paper 1.21.1 — pure Adventure API, zero BungeeCord dependency.
 * Supports: legacy & codes and custom hex {#rrggbb}.
 */
public final class ColorUtils {

    private static final Pattern HEX_PATTERN = Pattern.compile("\\{#([A-Fa-f0-9]{6})\\}");

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder()
                    .character('§')
                    .hexColors()
                    .useUnusualXRepeatedCharacterHexFormat()
                    .build();

    private ColorUtils() {}

    /**
     * Translates & codes and {#hex} to § string — use for legacy APIs or console.
     */
    public static String translate(String message) {
        if (message == null || message.isEmpty()) return "";

        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuilder buffer = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(buffer,
                    Matcher.quoteReplacement(buildHexCode(matcher.group(1))));
        }
        matcher.appendTail(buffer);

        return buffer.toString().replace('&', '§');
    }

    /**
     * Converts a raw string (& codes + {#hex}) directly to an Adventure Component.
     */
    public static Component toComponent(String message) {
        return LEGACY.deserialize(translate(message));
    }

    private static String buildHexCode(String hex) {
        StringBuilder sb = new StringBuilder("§x");
        for (char c : hex.toCharArray()) sb.append('§').append(c);
        return sb.toString();
    }
}
