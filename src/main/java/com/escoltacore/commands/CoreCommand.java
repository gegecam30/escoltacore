package com.escoltacore.commands;

import com.escoltacore.EscoltaCorePlugin;
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

    private final AdminCommand adminCmd;
    private final UserCommand  userCmd;

    public CoreCommand(EscoltaCorePlugin plugin) {
        this.adminCmd = new AdminCommand(plugin);
        this.userCmd  = new UserCommand(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player p) sendRootHelp(p);
            else MessageUtils.sendRaw(sender, "&eUsage: /escolta <admin|user> <subcommand>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "admin" -> {
                return adminCmd.handle(sender, args);
            }
            case "user" -> {
                if (!(sender instanceof Player p)) {
                    MessageUtils.sendRaw(sender, "&cOnly players can use /escolta user.");
                    return true;
                }
                return userCmd.handle(p, args);
            }
            default -> {
                if (sender instanceof Player p) sendRootHelp(p);
                else MessageUtils.sendRaw(sender, "&eUsage: /escolta <admin|user> <subcommand>");
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            // "user" is always visible to players; "admin" only if they have the permission
            if (sender instanceof Player) options.add("user");
            if (sender.hasPermission("escoltacore.admin")) options.add("admin");
            return options;
        }
        return switch (args[0].toLowerCase()) {
            case "admin" -> sender.hasPermission("escoltacore.admin")
                    ? adminCmd.tabComplete(args) : Collections.emptyList();
            case "user"  -> userCmd.tabComplete(args);
            default      -> Collections.emptyList();
        };
    }

    private void sendRootHelp(Player p) {
        MessageUtils.sendRaw(p, "&6&lEscoltaCore");
        MessageUtils.sendRaw(p, "&e/escolta user  &7- Player commands");
        if (p.hasPermission("escoltacore.admin"))
            MessageUtils.sendRaw(p, "&e/escolta admin &7- Admin commands");
    }
}