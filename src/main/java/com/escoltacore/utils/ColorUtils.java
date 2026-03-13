package com.escoltacore.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilidad de colores compatible con Paper 1.21.1.
 * NO usa net.md_5.bungee (deprecated). Usa Adventure API nativa.
 *
 * Soporta:
 *  - Códigos legacy:  &a, &l, &r, etc.
 *  - HEX custom:      {#rrggbb}
 */
public class ColorUtils {

    // Patrón para capturar {#ffffff}
    private static final Pattern HEX_PATTERN = Pattern.compile("\\{#([A-Fa-f0-9]{6})\\}");

    // Serializador legacy de Adventure (§ como código de color)
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder()
                    .character('§')
                    .hexColors()
                    .useUnusualXRepeatedCharacterHexFormat()
                    .build();

    /**
     * Traduce códigos legacy (&a, {#rrggbb}) a un String con §-codes.
     * Útil para APIs que aún esperan Strings coloreados (sendMessage legacy, etc.)
     *
     * @param message Mensaje con & y/o {#hex}
     * @return String con § codes procesados
     */
    public static String translate(String message) {
        if (message == null || message.isEmpty()) return "";

        // 1. Convertir {#rrggbb} → §x§r§r§g§g§b§b  (formato hex de Adventure)
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuilder buffer = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            String adventureHex = buildHexCode(hex);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(adventureHex));
        }
        matcher.appendTail(buffer);

        // 2. Convertir & → §
        String withSections = buffer.toString().replace('&', '§');

        return withSections;
    }

    /**
     * Convierte un String (puede tener & y {#hex}) directamente a un Adventure Component.
     * Es el método preferido para Paper 1.21.1.
     *
     * @param message Mensaje raw
     * @return Component de Adventure listo para usar
     */
    public static Component toComponent(String message) {
        String translated = translate(message);
        return LEGACY.deserialize(translated);
    }

    // ── Internos ──────────────────────────────────────────────────────────────

    /**
     * Construye el formato §x§r§r§g§g§b§b que Adventure / Bukkit entienden para hex.
     */
    private static String buildHexCode(String hex) {
        // §x seguido de §<cada char del hex>
        StringBuilder sb = new StringBuilder("§x");
        for (char c : hex.toCharArray()) {
            sb.append('§').append(c);
        }
        return sb.toString();
    }
}
