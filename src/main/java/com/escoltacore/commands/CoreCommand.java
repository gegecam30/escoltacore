package com.escoltacore.commands;

import com.escoltacore.EscoltaCorePlugin;
import com.escoltacore.arena.GameArena;
import com.escoltacore.gui.ArenaConfigMenu;
import com.escoltacore.utils.MessageUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CoreCommand implements CommandExecutor, TabCompleter {

    private final EscoltaCorePlugin plugin;

    public CoreCommand(EscoltaCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            MessageUtils.sendRaw(sender, "&cOnly players.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(p);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> {
                if (args.length < 2) { MessageUtils.sendRaw(p, "&cUso: /escolta create <nombre>"); return true; }
                plugin.getArenaManager().createArena(p, args[1]);
            }
            case "join" -> {
                if (args.length < 2) { MessageUtils.sendRaw(p, "&cUso: /escolta join <nombre>"); return true; }
                plugin.getArenaManager().joinArena(p, args[1]);
            }
            case "leave"  -> plugin.getArenaManager().leaveArena(p);
            case "start"  -> {
                GameArena arena = plugin.getArenaManager().getArena(p);
                if (arena == null) { MessageUtils.send(p, "arena-not-in"); return true; }
                arena.start(p);
            }
            case "config" -> {
                GameArena arena = plugin.getArenaManager().getArena(p);
                if (arena != null && arena.getOwnerId().equals(p.getUniqueId())) {
                    new ArenaConfigMenu(p, arena).open();
                } else {
                    MessageUtils.sendRaw(p, "&cNo eres el dueño de ninguna arena.");
                }
            }
            case "reload" -> {
                plugin.reloadConfig();
                MessageUtils.init(plugin);
                MessageUtils.sendRaw(p, "&aConfiguración recargada.");
            }
            default -> sendHelp(p);
        }
        return true;
    }

    private void sendHelp(Player p) {
        List<String> helpList = plugin.getConfig().getStringList("gui.help");
        if (helpList.isEmpty()) {
            MessageUtils.sendRaw(p, "&6--- EscoltaCore Help ---");
            MessageUtils.sendRaw(p, "&e/escolta create <nombre>");
            MessageUtils.sendRaw(p, "&e/escolta join <nombre>");
            MessageUtils.sendRaw(p, "&e/escolta leave");
            MessageUtils.sendRaw(p, "&e/escolta start");
        } else {
            helpList.forEach(line -> MessageUtils.sendRaw(p, line));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("create", "join", "leave", "start", "config", "reload");
        }
        return Collections.emptyList();
    }
}
