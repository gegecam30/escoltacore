package com.escoltacore.listeners;

import com.escoltacore.EscoltaCorePlugin;
import com.escoltacore.arena.GameArena;
import com.escoltacore.arena.GameState;
import com.escoltacore.gui.ArenaConfigMenu;
import com.escoltacore.utils.MessageUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent; // IMPORTANTE
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class GameListener implements Listener {

    private final EscoltaCorePlugin plugin;

    public GameListener(EscoltaCorePlugin plugin) {
        this.plugin = plugin;
    }

    // --- NUEVO: EVITAR TIRAR ÍTEMS EN EL LOBBY ---
    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        GameArena arena = plugin.getArenaManager().getArena(p);
        
        // Si está en el lobby (WAITING), prohibido tirar nada (items de config/clases)
        if (arena != null && arena.getState() == GameState.WAITING) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        if (item == null) return;

        GameArena arena = plugin.getArenaManager().getArena(p);
        if (arena == null || arena.getState() != GameState.WAITING) return;

        if (item.getType() == Material.COMPARATOR) {
            if (arena.getOwnerId().equals(p.getUniqueId())) {
                new ArenaConfigMenu(p, arena).open();
                e.setCancelled(true);
            }
        }
        
        if (item.getType() == Material.NETHER_STAR) {
            MessageUtils.sendRaw(p, "&7[Classes] &eComing soon...");
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        GameArena arena = plugin.getArenaManager().getArena(victim);
        
        // Si está en una arena (Jugando o Esperando), borrar SIEMPRE los drops
        // Esto evita dupes de items de lobby o items de partida
        if (arena != null) {
            e.getDrops().clear(); 
            e.setDroppedExp(0);
        }

        if (arena != null && arena.getState() == GameState.RUNNING) {
            if (arena.isPlayerInGame(victim)) {
                arena.endGame(false, victim.getName());
                e.setDeathMessage(null); 
            }
        }
    }
    
    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        GameArena arena = plugin.getArenaManager().getArena(p);
        
        if (arena == null || arena.getState() != GameState.RUNNING) return;
        if (!arena.isPlayerInGame(p)) return;

        Material pickedType = e.getItem().getItemStack().getType();
        
        if (pickedType == arena.getTargetItem()) {
            arena.endGame(true, p.getName());
            e.setCancelled(true); 
            e.getItem().remove(); 
        }
    }
}