package com.escoltacore;

import com.escoltacore.commands.CoreCommand;
import com.escoltacore.database.DatabaseProvider;
import com.escoltacore.database.MySQLProvider;
import com.escoltacore.database.SQLiteProvider;
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

    private ArenaManager    arenaManager;
    private DatabaseProvider database;

    @Override
    public void onEnable() {
        instance = this;

        // 1. Config + messages
        saveDefaultConfig();
        MessageUtils.init(this);

        // 2. Database
        String storageType = getConfig().getString("storage", "sqlite");
        if ("mysql".equalsIgnoreCase(storageType)) {
            database = new MySQLProvider(this);
        } else {
            database = new SQLiteProvider(this);
        }
        database.init();

        // 3. Managers (loads arenas.yml)
        arenaManager = new ArenaManager(this);

        // 4. Commands
        var cmd = getCommand("escolta");
        if (cmd != null) {
            CoreCommand core = new CoreCommand(this);
            cmd.setExecutor(core);
            cmd.setTabCompleter(core);
        }

        // 5. Listeners
        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(), this);

        // 6. PlaceholderAPI
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new EscoltaExpansion(this).register();
            getLogger().info("PlaceholderAPI hook registered.");
        }

        printBanner();
    }

    @Override
    public void onDisable() {
        if (arenaManager != null) arenaManager.shutdown();
        if (database != null)     database.close();
        Bukkit.getConsoleSender().sendMessage(
                ColorUtils.translate("&6[EscoltaCore] &cDisabled."));
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public static EscoltaCorePlugin getInstance() { return instance; }
    public ArenaManager     getArenaManager()     { return arenaManager; }
    public DatabaseProvider getDatabase()         { return database; }

    // ── Banner ────────────────────────────────────────────────────────────────

    private void printBanner() {
        String v = getDescription().getVersion();
        String[] lines = {
            "&6+----------------------------------------------------------+",
            "&6|                                                          |",
            "&6|   &e&lEscoltaCore &7v" + String.format("%-43s", v)    + "&6|",
            "&6|   &7Arcade escort minigame for Paper 1.21.1             &6|",
            "&6|                                                          |",
            "&6|   &7Dev: &f_gengis  &7|  &7Original concept: &f&lElRichMC   &6|",
            "&6|                                                          |",
            "&6+----------------------------------------------------------+"
        };
        for (String line : lines) {
            Bukkit.getConsoleSender().sendMessage(ColorUtils.translate(line));
        }
    }
}
