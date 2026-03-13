package com.escoltacore.utils;

import com.escoltacore.EscoltaCorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

public final class MessageUtils {

    private static FileConfiguration cfg;

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder()
                    .character('§')
                    .hexColors()
                    .useUnusualXRepeatedCharacterHexFormat()
                    .build();

    private MessageUtils() {}

    public static void init(EscoltaCorePlugin plugin) {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) plugin.saveResource("messages.yml", false);
        cfg = YamlConfiguration.loadConfiguration(file);
    }

    // ── Raw string (translated) ────────────────────────────────────────────────

    /** Gets a translated string from messages.yml. */
    public static String get(String path) {
        if (cfg == null) return path;
        String msg = cfg.getString(path);
        return msg != null ? ColorUtils.translate(msg) : "§cMissing: " + path;
    }

    /** Gets a translated string list from messages.yml. */
    public static List<String> getList(String path) {
        if (cfg == null) return List.of();
        return cfg.getStringList(path).stream()
                .map(ColorUtils::translate)
                .collect(Collectors.toList());
    }

    // ── Component ─────────────────────────────────────────────────────────────

    /** Converts any raw string (& + hex) to an Adventure Component. */
    public static Component component(String text) {
        return LEGACY.deserialize(ColorUtils.translate(text));
    }

    /** Gets a key from messages.yml and returns it as a Component. */
    public static Component componentFromKey(String path) {
        return component(get(path));
    }

    // ── Send helpers ──────────────────────────────────────────────────────────

    /** Sends a prefixed message from messages.yml. */
    public static void send(CommandSender sender, String path) {
        String prefix = cfg != null ? ColorUtils.translate(cfg.getString("prefix", "")) : "";
        sender.sendMessage(component(prefix + get(path)));
    }

    /** Sends a prefixed message with one placeholder replaced. */
    public static void send(CommandSender sender, String path, String ph, String val) {
        String prefix = cfg != null ? ColorUtils.translate(cfg.getString("prefix", "")) : "";
        sender.sendMessage(component(prefix + get(path).replace(ph, val)));
    }

    /** Sends a prefixed message with two placeholders replaced. */
    public static void send(CommandSender sender, String path, String ph1, String v1,
                            String ph2, String v2) {
        String prefix = cfg != null ? ColorUtils.translate(cfg.getString("prefix", "")) : "";
        sender.sendMessage(component(prefix + get(path).replace(ph1, v1).replace(ph2, v2)));
    }

    /** Sends a raw text string (& codes translated). */
    public static void sendRaw(CommandSender sender, String text) {
        sender.sendMessage(component(text));
    }
}
