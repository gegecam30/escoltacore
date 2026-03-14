package com.escoltacore.commands;

import com.escoltacore.EscoltaCorePlugin;
import com.escoltacore.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/**
 * Handles /escolta admin <subcommand>
 * Accepts CommandSender so console can run these commands too.
 * Requires: escoltacore.admin
 */
public class AdminCommand {

    private final EscoltaCorePlugin plugin;

    public AdminCommand(EscoltaCorePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (!sender.hasPermission("escoltacore.admin")) {
            MessageUtils.send(sender, "no-permission");
            return true;
        }

        if (args.length < 2) { sendHelp(sender); return true; }

        switch (args[1].toLowerCase()) {

            // /escolta admin create <name>
            case "create" -> {
                if (args.length < 3) {
                    MessageUtils.sendRaw(sender, "&cUsage: /escolta admin create <name>");
                    return true;
                }
                String name = args[2];
                if (plugin.getArenaManager().createPublicArena(name)) {
                    MessageUtils.send(sender, "arena-created", "%name%", name);
                } else {
                    MessageUtils.send(sender, "arena-exists", "%name%", name);
                }
            }

            // /escolta admin delete <name>
            case "delete" -> {
                if (args.length < 3) {
                    MessageUtils.sendRaw(sender, "&cUsage: /escolta admin delete <name>");
                    return true;
                }
                String name = args[2];
                if (plugin.getArenaManager().deletePublicArena(name)) {
                    MessageUtils.send(sender, "arena-deleted", "%name%", name);
                } else {
                    MessageUtils.send(sender, "arena-not-found", "%name%", name);
                }
            }

            // /escolta admin setspawn <name>  — requires a player (needs location)
            case "setspawn" -> {
                if (!(sender instanceof Player p)) {
                    MessageUtils.sendRaw(sender, "&cYou must be in-game to set a spawn point.");
                    return true;
                }
                if (args.length < 3) {
                    MessageUtils.sendRaw(sender, "&cUsage: /escolta admin setspawn <name>");
                    return true;
                }
                String name = args[2];
                if (plugin.getArenaManager().setSpawn(name, p.getLocation())) {
                    MessageUtils.send(sender, "arena-spawn-set", "%name%", name);
                } else {
                    MessageUtils.send(sender, "arena-not-found", "%name%", name);
                }
            }

            // /escolta admin join <arena> <player>
            case "join" -> {
                if (args.length < 4) {
                    MessageUtils.sendRaw(sender, "&cUsage: /escolta admin join <arena> <player>");
                    return true;
                }
                String arenaName = args[2];
                Player target = Bukkit.getPlayerExact(args[3]);
                if (target == null) {
                    MessageUtils.send(sender, "player-not-found", "%player%", args[3]);
                    return true;
                }
                // Check arena exists before trying
                if (plugin.getArenaManager().getArenaByName(arenaName) == null) {
                    MessageUtils.send(sender, "arena-not-found", "%name%", arenaName);
                    return true;
                }
                // Check player is not already in a game
                if (plugin.getArenaManager().getArena(target) != null) {
                    MessageUtils.sendRaw(sender, "&c" + target.getName() + " is already in a game.");
                    return true;
                }
                if (plugin.getArenaManager().forceJoin(target, arenaName)) {
                    String msg = MessageUtils.get("arena-force-joined")
                            .replace("%player%", target.getName())
                            .replace("%name%", arenaName);
                    MessageUtils.sendRaw(sender, msg);
                } else {
                    MessageUtils.sendRaw(sender, "&cCould not add " + target.getName()
                            + " to '" + arenaName + "' (arena may be full or in progress).");
                }
            }

            // /escolta admin reload
            case "reload" -> {
                plugin.reloadConfig();
                MessageUtils.init(plugin);
                // Push new min/max values from config into live arenas
                plugin.getArenaManager().reloadConfig();
                MessageUtils.sendRaw(sender, "&aConfiguration and messages reloaded.");
            }

            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        MessageUtils.sendRaw(sender, "&6&l--- EscoltaCore Admin Help ---");
        MessageUtils.sendRaw(sender, "&e/escolta admin create <name>            &7- Create public arena");
        MessageUtils.sendRaw(sender, "&e/escolta admin delete <name>            &7- Delete public arena");
        MessageUtils.sendRaw(sender, "&e/escolta admin setspawn <name>          &7- Set spawn (stand on it)");
        MessageUtils.sendRaw(sender, "&e/escolta admin join <arena> <player>    &7- Force player into arena");
        MessageUtils.sendRaw(sender, "&e/escolta admin reload                   &7- Reload config & messages");
    }

    public List<String> tabComplete(String[] args) {
        if (args.length == 2) {
            return List.of("create", "delete", "setspawn", "join", "reload");
        }
        if (args.length == 3 && List.of("delete","setspawn","join")
                .contains(args[1].toLowerCase())) {
            return List.copyOf(plugin.getArenaManager().getPublicArenaNames());
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("join")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return Collections.emptyList();
    }
}
