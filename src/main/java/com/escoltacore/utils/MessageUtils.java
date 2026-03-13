package com.escoltacore.utils;

import com.escoltacore.EscoltaCorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class MessageUtils {

    private static FileConfiguration messagesConfig;

    public static void init(EscoltaCorePlugin plugin) {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(file);
    }

    // --- NUEVO MÉTODO: Obtener String crudo para GUIs e Items ---
    public static String get(String path) {
        if (messagesConfig == null) return path;
        String msg = messagesConfig.getString(path);
        if (msg == null) return "Missing: " + path;
        return ColorUtils.translate(msg);
    }

    public static void send(CommandSender sender, String path) {
        if (messagesConfig == null) return;
        String msg = messagesConfig.getString(path);
        String prefix = messagesConfig.getString("prefix", "&8[&6Escolta&8] &r");

        if (msg != null) {
            sender.sendMessage(ColorUtils.translate(prefix + msg));
        } else {
            sender.sendMessage(ColorUtils.translate("&cMissing translation: " + path));
        }
    }
    
    // Método para enviar mensajes con replacements (ej: %name%)
    public static void send(CommandSender sender, String path, String placeholder, String value) {
        if (messagesConfig == null) return;
        String msg = messagesConfig.getString(path);
        String prefix = messagesConfig.getString("prefix", "");
        if (msg != null) {
            msg = msg.replace(placeholder, value);
            sender.sendMessage(ColorUtils.translate(prefix + msg));
        }
    }

    public static void sendRaw(CommandSender sender, String text) {
        sender.sendMessage(ColorUtils.translate(text));
    }

    public static Component component(String text) {
        String translated = ColorUtils.translate(text);
        return LegacyComponentSerializer.legacySection().deserialize(translated);
    }
}