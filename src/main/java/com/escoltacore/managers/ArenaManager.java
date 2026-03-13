package com.escoltacore.managers;

import com.escoltacore.EscoltaCorePlugin;
import com.escoltacore.arena.GameArena;
import com.escoltacore.utils.MessageUtils;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class ArenaManager {

    private final EscoltaCorePlugin plugin;
    private final Map<String, GameArena> arenas = new HashMap<>();
    private final Map<String, String> playerArenaMap = new HashMap<>();

    public ArenaManager(EscoltaCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void createArena(Player creator, String name) {
        if (arenas.containsKey(name)) {
            MessageUtils.send(creator, "arena-exists", "%name%", name);
            return;
        }
        
        GameArena newArena = new GameArena(plugin, name, creator.getUniqueId());
        arenas.put(name, newArena);
        
        // Unir al creador automáticamente
        joinArena(creator, name); 
        
        // Mensaje de éxito (Ahora en inglés desde config)
        MessageUtils.send(creator, "arena-created", "%name%", name);
    }

    public void joinArena(Player p, String name) {
        if (playerArenaMap.containsKey(p.getUniqueId().toString())) {
            MessageUtils.send(p, "arena-already-in");
            return;
        }
        
        GameArena arena = arenas.get(name);
        if (arena == null) {
            MessageUtils.send(p, "arena-not-found", "%name%", name);
            return;
        }

        if (arena.addPlayer(p)) {
            playerArenaMap.put(p.getUniqueId().toString(), name);
        }
    }

    public void leaveArena(Player p) {
        String arenaName = playerArenaMap.remove(p.getUniqueId().toString());
        if (arenaName != null) {
            GameArena arena = arenas.get(arenaName);
            if (arena != null) {
                // Verificar si es el DUEÑO quien se va
                boolean isOwner = p.getUniqueId().equals(arena.getOwnerId());
                
                arena.removePlayer(p);
                
                if (isOwner) {
                    // El dueño se fue -> Cerrar la sala para todos
                    arena.forceStop(); // Echa a los que queden
                    arenas.remove(arenaName);
                    // Avisar que se cerró
                    MessageUtils.sendRaw(p, MessageUtils.get("arena-owner-left").replace("%name%", arenaName));
                } 
                // Si la sala se queda vacía (aunque no sea el dueño), borrarla
                else if (arena.getPlayerCount() == 0) {
                    arenas.remove(arenaName);
                }
            }
        } else {
            MessageUtils.send(p, "arena-not-in");
        }
    }

    public GameArena getArena(Player p) {
        String name = playerArenaMap.get(p.getUniqueId().toString());
        if (name == null) return null;
        return arenas.get(name);
    }
    
    public void shutdown() {
        for (GameArena arena : arenas.values()) {
            arena.forceStop();
        }
        arenas.clear();
        playerArenaMap.clear();
    }
}