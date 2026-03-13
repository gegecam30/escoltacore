package com.escoltacore.hooks;

import com.escoltacore.EscoltaCorePlugin;
import com.escoltacore.arena.GameArena;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

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
        if (player == null) return "";

        GameArena arena = plugin.getArenaManager().getArena(player);

        if (arena == null) {
            return switch (params.toLowerCase()) {
                case "status"      -> "Lobby";
                case "role"        -> "Sin Rol";
                case "radius"      -> "0";
                case "target_item" -> "Ninguno";
                default            -> "";
            };
        }

        return switch (params.toLowerCase()) {
            case "status"      -> arena.getState().name();
            case "radius"      -> String.valueOf((int) arena.getRadius());
            case "target_item" -> arena.getTargetItem() != null
                    ? arena.getTargetItem().name() : "Esperando...";
            case "role"        -> player.getUniqueId().equals(arena.getEscoltadoId())
                    ? "Escoltado" : "Defensor";
            default            -> null;
        };
    }
}
