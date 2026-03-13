package com.escoltacore.hooks;

import com.escoltacore.EscoltaCorePlugin;
import com.escoltacore.arena.GameArena;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * PlaceholderAPI expansion.
 *
 * Per-player:
 *   %escoltacore_status%       → game state for the player's current arena
 *   %escoltacore_role%         → Escort / Defender / None
 *   %escoltacore_radius%       → current radius
 *   %escoltacore_target_item%  → item name or "Waiting..."
 *
 * Per-arena (for NPC signs/scoreboards):
 *   %escoltacore_arena_<name>_status%   → WAITING / STARTING / RUNNING / ENDING
 *   %escoltacore_arena_<name>_count%    → current player count
 *   %escoltacore_arena_<name>_max%      → max players
 */
public class EscoltaExpansion extends PlaceholderExpansion {

    private final EscoltaCorePlugin plugin;

    public EscoltaExpansion(EscoltaCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getIdentifier() { return "escoltacore"; }
    @Override public @NotNull String getAuthor()     { return "_gengis"; }
    @Override public @NotNull String getVersion()    { return plugin.getDescription().getVersion(); }
    @Override public boolean persist()               { return true; }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        // ── Per-arena placeholders (no player context needed) ─────────────────
        // Format: arena_<name>_<field>
        if (params.startsWith("arena_")) {
            String rest = params.substring(6); // strip "arena_"
            int lastUnderscore = rest.lastIndexOf('_');
            if (lastUnderscore < 1) return "";

            String arenaName = rest.substring(0, lastUnderscore);
            String field     = rest.substring(lastUnderscore + 1);
            GameArena arena  = plugin.getArenaManager().getArenaByName(arenaName);

            if (arena == null) return "N/A";
            return switch (field.toLowerCase()) {
                case "status" -> arena.getState().name();
                case "count"  -> String.valueOf(arena.getPlayerCount());
                case "max"    -> String.valueOf(arena.getMaxPlayers());
                default       -> "";
            };
        }

        // ── Per-player placeholders ────────────────────────────────────────────
        if (player == null) return "";
        GameArena arena = plugin.getArenaManager().getArena(player);

        if (arena == null) {
            return switch (params.toLowerCase()) {
                case "status"      -> "Lobby";
                case "role"        -> "None";
                case "radius"      -> "0";
                case "target_item" -> "None";
                default            -> "";
            };
        }

        return switch (params.toLowerCase()) {
            case "status"      -> arena.getState().name();
            case "radius"      -> String.valueOf((int) arena.getRadius());
            case "target_item" -> arena.getTargetItem() != null
                    ? arena.getTargetItem().name() : "Waiting...";
            case "role"        -> player.getUniqueId().equals(arena.getEscortId())
                    ? "Escort" : "Defender";
            default            -> null;
        };
    }
}
