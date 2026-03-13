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

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

public class CoreCommand implements CommandExecutor, TabCompleter {

    private final EscoltaCorePlugin plugin;

    public CoreCommand(EscoltaCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            MessageUtils.sendRaw(sender, "&cOnly players.");
            return true;
        }
        Player p = (Player) sender;

        // --- CORRECCIÓN CLAVE ---
        // Si no hay argumentos, enviamos la ayuda custom y retornamos TRUE
        // para evitar que salga el mensaje feo del plugin.yml
        if (args.length == 0) {
            sendHelp(p);
            return true; 
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "create":
                if (args.length < 2) {
                    MessageUtils.sendRaw(p, "&cUsage: /escolta create <name>");
                    return true;
                }
                plugin.getArenaManager().createArena(p, args[1]);
                break;

            case "join":
                if (args.length < 2) {
                    MessageUtils.sendRaw(p, "&cUsage: /escolta join <name>");
                    return true;
                }
                plugin.getArenaManager().joinArena(p, args[1]);
                break;

            case "leave":
                plugin.getArenaManager().leaveArena(p);
                break;
                
            case "config": 
                GameArena myArena = plugin.getArenaManager().getArena(p);
                if (myArena != null && myArena.getOwnerId().equals(p.getUniqueId())) {
                    new ArenaConfigMenu(p, myArena).open();
                } else {
                     MessageUtils.sendRaw(p, "&cNo arena owner.");
                }
                break;

            case "reload": 
                plugin.reloadConfig();
                MessageUtils.init(plugin); 
                MessageUtils.sendRaw(p, "&aConfiguration reloaded.");
                break;
                
            case "start":
                GameArena arenaToStart = plugin.getArenaManager().getArena(p);
                if (arenaToStart == null) {
                    MessageUtils.send(p, "arena-not-in");
                    return true;
                }
                arenaToStart.start(p);
                break;

            default:
                // Si escriben un comando que no existe (/escolta saltar), mostramos ayuda
                sendHelp(p);
                break;
        }
        return true;
    }

    private void sendHelp(Player p) {
        // Obtenemos la lista bonita desde messages.yml
        List<String> helpList = plugin.getConfig().getStringList("gui.help"); 
        
        // Si por error la lista está vacía en config, mostramos un fallback
        if (helpList == null || helpList.isEmpty()) {
             MessageUtils.sendRaw(p, "&6--- EscoltaCore Default Help ---");
             MessageUtils.sendRaw(p, "&e/escolta create <name>");
             MessageUtils.sendRaw(p, "&e/escolta join <name>");
             MessageUtils.sendRaw(p, "&e/escolta leave");
        } else {
            for (String line : helpList) {
                MessageUtils.sendRaw(p, line);
            }
        }
    }
    
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            list.add("create"); list.add("join"); list.add("leave"); list.add("reload"); list.add("config"); list.add("start");
            return list;
        }
        return Collections.emptyList();
    }
}