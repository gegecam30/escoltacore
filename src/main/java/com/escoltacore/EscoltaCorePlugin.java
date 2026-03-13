package com.escoltacore;

import com.escoltacore.commands.CoreCommand;
import com.escoltacore.hooks.EscoltaExpansion;
import com.escoltacore.listeners.GameListener;
import com.escoltacore.listeners.GuiListener;
import com.escoltacore.managers.ArenaManager;
import com.escoltacore.utils.ColorUtils;
import com.escoltacore.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class EscoltaCorePlugin extends JavaPlugin {

    private static EscoltaCorePlugin instance;
    private ArenaManager arenaManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        MessageUtils.init(this);

        this.arenaManager = new ArenaManager(this);

        if (getCommand("escolta") != null) {
            CoreCommand cmd = new CoreCommand(this);
            getCommand("escolta").setExecutor(cmd);
            getCommand("escolta").setTabCompleter(cmd);
        }

        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(), this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new EscoltaExpansion(this).register();
            getLogger().info("PlaceholderAPI hook registrado.");
        }

        printBanner();
    }

    @Override
    public void onDisable() {
        if (arenaManager != null) arenaManager.shutdown();
        Bukkit.getConsoleSender().sendMessage(
                ColorUtils.translate("&6[EscoltaCore] &cPlugin desactivado."));
    }

    private void printBanner() {
        String v = getDescription().getVersion();
        String[] banner = {
            "&6+-------------------------------------------------------+",
            "&6|                                                       |",
            "&6|   &e&lEscoltaCore &7- &fArcade Minigame                   &6|",
            "&6|                                                       |",
            "&6|   &7Version: &f" + String.format("%-33s", v)        + "&6|",
            "&6|   &7Dev:     &f" + String.format("%-33s", "_gengis") + "&6|",
            "&6|   &7MC:      &f" + String.format("%-33s", "1.21.1") + "&6|",
            "&6|                                                       |",
            "&6|   &eOriginal Concept: &f&lElRichMC                    &6|",
            "&6|                                                       |",
            "&6+-------------------------------------------------------+"
        };
        for (String line : banner) {
            Bukkit.getConsoleSender().sendMessage(ColorUtils.translate(line));
        }
    }

    public static EscoltaCorePlugin getInstance() { return instance; }
    public ArenaManager getArenaManager()         { return arenaManager; }
}
