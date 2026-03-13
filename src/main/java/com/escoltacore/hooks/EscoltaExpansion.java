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

    @Override
    public @NotNull String getIdentifier() {
        return "escoltacore";
    }

    @Override
    public @NotNull String getAuthor() {
        return "TuUsuario";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        // CORRECCIÓN: Obtenemos la arena específica del jugador
        GameArena arena = plugin.getArenaManager().getArena(player);

        // Si el jugador NO está en una arena, devolvemos valores por defecto
        if (arena == null) {
            if (params.equalsIgnoreCase("status")) return "Lobby";
            if (params.equalsIgnoreCase("role")) return "Sin Rol";
            if (params.equalsIgnoreCase("radius")) return "0";
            if (params.equalsIgnoreCase("target_item")) return "Ninguno";
            return "";
        }

        // Si SÍ está en arena, devolvemos los datos de SU partida
        if (params.equalsIgnoreCase("status")) {
            return arena.getState().name();
        }

        if (params.equalsIgnoreCase("radius")) {
            return String.valueOf(arena.getRadius());
        }

        if (params.equalsIgnoreCase("target_item")) {
            if (arena.getTargetItem() != null) {
                return arena.getTargetItem().name();
            }
            return "Esperando...";
        }

        if (params.equalsIgnoreCase("role")) {
            if (player.getUniqueId().equals(arena.getEscoltadoId())) {
                return "Escoltado";
            }
            return "Defensor";
        }

        return null; 
    }
}