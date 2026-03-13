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

public class MessageUtils {

    private static FileConfiguration messagesConfig;
    private static final LegacyComponentSerializer LEGACY_SERIALIZER =
            LegacyComponentSerializer.builder()
                    .character('§')
                    .hexColors()
                    .useUnusualXRepeatedCharacterHexFormat()
                    .build();

    public static void init(EscoltaCorePlugin plugin) {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(file);
    }

    /**
     * Obtiene un String coloreado desde messages.yml.
     * Útil para construir ítems, broadcasts, etc.
     */
    public static String get(String path) {
        if (messagesConfig == null) return path;
        String msg = messagesConfig.getString(path);
        if (msg == null) return "§cMissing: " + path;
        return ColorUtils.translate(msg);
    }

    /**
     * Obtiene una lista de Strings coloreados desde messages.yml.
     */
    public static List<String> getList(String path) {
        if (messagesConfig == null) return List.of();
        List<String> list = messagesConfig.getStringList(path);
        return list.stream().map(ColorUtils::translate).collect(Collectors.toList());
    }

    /**
     * Envía un mensaje con prefijo, buscando la key en messages.yml.
     */
    public static void send(CommandSender sender, String path) {
        if (messagesConfig == null) return;
        String msg = messagesConfig.getString(path);
        String prefix = messagesConfig.getString("prefix", "");

        if (msg != null) {
            sender.sendMessage(component(ColorUtils.translate(prefix) + ColorUtils.translate(msg)));
        } else {
            sender.sendMessage(component("§cMissing translation: " + path));
        }
    }

    /**
     * Envía un mensaje con prefijo + un placeholder reemplazado.
     */
    public static void send(CommandSender sender, String path, String placeholder, String value) {
        if (messagesConfig == null) return;
        String msg = messagesConfig.getString(path);
        String prefix = messagesConfig.getString("prefix", "");
        if (msg != null) {
            msg = msg.replace(placeholder, value);
            sender.sendMessage(component(ColorUtils.translate(prefix) + ColorUtils.translate(msg)));
        }
    }

    /**
     * Envía un texto raw (con & codes) directamente al sender.
     */
    public static void sendRaw(CommandSender sender, String text) {
        sender.sendMessage(component(ColorUtils.translate(text)));
    }

    /**
     * Convierte un String con § codes a un Adventure Component.
     * Es el método central para Paper 1.21.1 Adventure API.
     */
    public static Component component(String text) {
        // Si ya pasó por ColorUtils.translate, tiene §. Si no, traducimos por si acaso.
        String translated = ColorUtils.translate(text);
        return LEGACY_SERIALIZER.deserialize(translated);
    }

    /**
     * Convierte una key de messages.yml directamente a Component.
     */
    public static Component componentFromKey(String path) {
        return component(get(path));
    }
}
