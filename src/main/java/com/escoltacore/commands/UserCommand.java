package com.escoltacore.commands;

import com.escoltacore.EscoltaCorePlugin;
import com.escoltacore.arena.GameArena;
import com.escoltacore.arena.GameState;
import com.escoltacore.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.Collections;
import java.util.List;

public class UserCommand {

    private final EscoltaCorePlugin plugin;
    private static final DecimalFormat DF = new DecimalFormat("0.0");

    public UserCommand(EscoltaCorePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean handle(Player p, String[] args) {
        if (!p.hasPermission("escoltacore.play")) {
            MessageUtils.send(p, "no-permission");
            return true;
        }

        if (args.length < 2) { sendHelp(p); return true; }

        switch (args[1].toLowerCase()) {

            case "create" -> {
                if (args.length < 3) { MessageUtils.sendRaw(p, "&cUsage: /escolta user create <n>"); return true; }
                plugin.getArenaManager().createPrivateLobby(p, args[2]);
            }

            case "join" -> {
                if (args.length < 3) { MessageUtils.sendRaw(p, "&cUsage: /escolta user join <n>"); return true; }
                plugin.getArenaManager().joinArena(p, args[2]);
            }

            case "leave" -> plugin.getArenaManager().leaveArena(p);

            case "invite" -> {
                if (args.length < 3) { MessageUtils.sendRaw(p, "&cUsage: /escolta user invite <player>"); return true; }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) { MessageUtils.send(p, "player-not-found", "%player%", args[2]); return true; }
                plugin.getArenaManager().sendInvite(p, target);
            }

            case "accept"  -> plugin.getArenaManager().acceptInvite(p);
            case "decline" -> plugin.getArenaManager().declineInvite(p);

            case "kick" -> {
                if (args.length < 3) { MessageUtils.sendRaw(p, "&cUsage: /escolta user kick <player>"); return true; }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) { MessageUtils.send(p, "player-not-found", "%player%", args[2]); return true; }
                plugin.getArenaManager().kickPlayer(p, target);
            }

            case "lobbies", "lobbys" -> plugin.getArenaManager().sendLobbiesList(p);

            case "stats" -> {
                plugin.getDatabase().getStats(p.getUniqueId()).thenAccept(stats -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        MessageUtils.sendRaw(p, MessageUtils.get("stats-header"));
                        MessageUtils.sendRaw(p, MessageUtils.get("stats-played")
                                .replace("%played%", String.valueOf(stats.played())));
                        MessageUtils.sendRaw(p, MessageUtils.get("stats-won")
                                .replace("%won%", String.valueOf(stats.won())));
                        MessageUtils.sendRaw(p, MessageUtils.get("stats-lost")
                                .replace("%lost%", String.valueOf(stats.lost())));
                        MessageUtils.sendRaw(p, MessageUtils.get("stats-ratio")
                                .replace("%ratio%", DF.format(stats.winRate())));
                        MessageUtils.sendRaw(p, MessageUtils.get("stats-footer"));
                    });
                });
            }

            // Internal commands triggered by clickable chat buttons — not shown in help
            case "voteplay" -> {
                if (args.length < 3) return true;
                GameArena arena = plugin.getArenaManager().getArena(p);
                if (arena == null || arena.getState() != GameState.ENDING) {
                    MessageUtils.sendRaw(p, "&cNo active vote right now.");
                    return true;
                }
                arena.castVote(p, true);
            }

            case "voteleave" -> {
                if (args.length < 3) return true;
                GameArena arena = plugin.getArenaManager().getArena(p);
                if (arena == null || arena.getState() != GameState.ENDING) {
                    MessageUtils.sendRaw(p, "&cNo active vote right now.");
                    return true;
                }
                arena.castVote(p, false);
            }

            default -> sendHelp(p);
        }
        return true;
    }

    private void sendHelp(Player p) {
        MessageUtils.sendRaw(p, "&6&l--- EscoltaCore Help ---");
        MessageUtils.sendRaw(p, "&e/escolta user create <n>   &7- Create a private lobby");
        MessageUtils.sendRaw(p, "&e/escolta user join <n>     &7- Join a public arena");
        MessageUtils.sendRaw(p, "&e/escolta user leave        &7- Leave current game");
        MessageUtils.sendRaw(p, "&e/escolta user invite <p>   &7- Invite a player to your lobby");
        MessageUtils.sendRaw(p, "&e/escolta user kick <p>     &7- Kick from your lobby");
        MessageUtils.sendRaw(p, "&e/escolta user accept       &7- Accept a pending invite");
        MessageUtils.sendRaw(p, "&e/escolta user decline      &7- Decline a pending invite");
        MessageUtils.sendRaw(p, "&e/escolta user lobbies      &7- View public arenas");
        MessageUtils.sendRaw(p, "&e/escolta user stats        &7- View your statistics");
    }

    public List<String> tabComplete(String[] args) {
        if (args.length == 2) {
            return List.of("create", "join", "leave", "invite", "kick",
                    "accept", "decline", "lobbies", "stats");
            // voteplay/voteleave intentionally excluded — internal use only
        }
        if (args.length == 3) {
            return switch (args[1].toLowerCase()) {
                case "join" -> List.copyOf(plugin.getArenaManager().getPublicArenaNames());
                case "invite", "kick" -> Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName).toList();
                default -> Collections.emptyList();
            };
        }
        return Collections.emptyList();
    }
}
