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
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class GameListener implements Listener {

    private final EscoltaCorePlugin plugin;

    public GameListener(EscoltaCorePlugin plugin) {
        this.plugin = plugin;
    }

    /** Prohibir tirar ítems en el lobby */
    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        GameArena arena = plugin.getArenaManager().getArena(e.getPlayer());
        if (arena != null && arena.getState() == GameState.WAITING) {
            e.setCancelled(true);
        }
    }

    /** Click con ítem de configuración o selector de clases */
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR
                && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        if (item == null) return;

        GameArena arena = plugin.getArenaManager().getArena(p);
        if (arena == null || arena.getState() != GameState.WAITING) return;

        if (item.getType() == Material.COMPARATOR
                && arena.getOwnerId().equals(p.getUniqueId())) {
            new ArenaConfigMenu(p, arena).open();
            e.setCancelled(true);
        }

        if (item.getType() == Material.NETHER_STAR) {
            MessageUtils.sendRaw(p, "&7[Clases] &ePróximamente...");
            e.setCancelled(true);
        }
    }

    /** Muerte de un jugador en la partida */
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        GameArena arena = plugin.getArenaManager().getArena(victim);

        if (arena != null) {
            e.getDrops().clear();
            e.setDroppedExp(0);
        }

        if (arena != null && arena.getState() == GameState.RUNNING
                && arena.isPlayerInGame(victim)) {
            arena.endGame(false, victim.getName());
            e.setDeathMessage(null);
        }
    }

    /** Recogida del ítem objetivo → victoria */
    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        GameArena arena = plugin.getArenaManager().getArena(p);

        if (arena == null || arena.getState() != GameState.RUNNING) return;
        if (!arena.isPlayerInGame(p)) return;

        if (e.getItem().getItemStack().getType() == arena.getTargetItem()) {
            e.setCancelled(true);
            e.getItem().remove();
            arena.endGame(true, p.getName());
        }
    }

    /** Salida del servidor: limpiar la arena */
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        GameArena arena = plugin.getArenaManager().getArena(e.getPlayer());
        if (arena != null) {
            plugin.getArenaManager().leaveArena(e.getPlayer());
        }
    }
}
