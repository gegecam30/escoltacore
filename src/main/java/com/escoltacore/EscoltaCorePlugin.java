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

        // 1. Config y Utils
        saveDefaultConfig();
        MessageUtils.init(this);

        // 2. Managers
        this.arenaManager = new ArenaManager(this);

        // 3. Comandos
        if (getCommand("escolta") != null) {
            CoreCommand cmd = new CoreCommand(this);
            getCommand("escolta").setExecutor(cmd);
            getCommand("escolta").setTabCompleter(cmd);
        }

        // 4. Listeners
        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(), this);

        // 5. Hooks
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new EscoltaExpansion(this).register();
        }

        // 6. BANNER DE INICIO (El cuadrado Chévere)
        printBanner();
    }

    @Override
    public void onDisable() {
        if (arenaManager != null) {
            arenaManager.shutdown();
        }
        Bukkit.getConsoleSender().sendMessage(ColorUtils.translate("&6[EscoltaCore] &cPlugin disabled."));
    }

    private void printBanner() {
        String version = getDescription().getVersion();
        
        // Usamos Bukkit.getConsoleSender() para que los colores salgan nítidos sin el prefijo [INFO] feo
        String[] banner = {
            "&6+-------------------------------------------------------+",
            "&6|                                                       |",
            "&6|   &e&lEscoltaCore &7- &fArcade Minigame                   &6|",
            "&6|                                                       |",
            "&6|   &7Version: &f" + String.format("%-33s", version) + "&6|",
            "&6|   &7Dev:     &f" + String.format("%-33s", "_gengis") + "&6|", // Pon tu nombre aquí
            "&6|                                                       |",
            "&6|   &eOriginal Concept & Idea: &f&lElRichMC               &6|",
            "&6|                                                       |",
            "&6+-------------------------------------------------------+"
        };

        for (String line : banner) {
            Bukkit.getConsoleSender().sendMessage(ColorUtils.translate(line));
        }
    }

    public static EscoltaCorePlugin getInstance() {
        return instance;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }
}