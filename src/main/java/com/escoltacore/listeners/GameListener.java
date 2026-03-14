package com.escoltacore.listeners;

import com.escoltacore.EscoltaCorePlugin;
import com.escoltacore.arena.GameArena;
import com.escoltacore.arena.GameState;
import com.escoltacore.gui.ArenaConfigMenu;
import com.escoltacore.utils.MessageUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class GameListener implements Listener {

    private final EscoltaCorePlugin plugin;

    public GameListener(EscoltaCorePlugin plugin) {
        this.plugin = plugin;
    }

    // ── Drop protection ───────────────────────────────────────────────────────

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        GameArena arena = plugin.getArenaManager().getArena(e.getPlayer());
        if (arena == null) return;

        if (arena.getState() == GameState.WAITING) {
            // Block dropping any lobby item
            e.setCancelled(true);
            return;
        }
        if (arena.getState() == GameState.RUNNING) {
            // Only block the objective display item
            if (isTagged(e.getItemDrop().getItemStack(), GameArena.OBJECTIVE_KEY)) {
                e.setCancelled(true);
            }
        }
    }

    // ── Slot protection ───────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        GameArena arena = plugin.getArenaManager().getArena(p);
        if (arena == null) return;

        if (arena.getState() == GameState.WAITING) {
            // Block slot 8 (leave item) from being moved
            if (e.getSlot() == 8) { e.setCancelled(true); return; }
            // Block moving any leave-tagged item
            ItemStack cur = e.getCurrentItem();
            if (cur != null && isTagged(cur, GameArena.LEAVE_KEY)) e.setCancelled(true);
            return;
        }

        if (arena.getState() == GameState.RUNNING) {
            // Block slot 8 (objective display item) from being moved
            if (e.getSlot() == 8) { e.setCancelled(true); return; }
            // Block shift-clicking the objective item from any slot
            ItemStack cur = e.getCurrentItem();
            if (cur != null && isTagged(cur, GameArena.OBJECTIVE_KEY)) e.setCancelled(true);
        }
    }

    // ── Interact (lobby tools + leave item) ───────────────────────────────────

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR
                && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        if (item == null) return;

        GameArena arena = plugin.getArenaManager().getArena(p);
        if (arena == null) return;

        // Leave item works in any non-running state
        if (isTagged(item, GameArena.LEAVE_KEY)) {
            e.setCancelled(true);
            plugin.getArenaManager().leaveArena(p);
            return;
        }

        if (arena.getState() != GameState.WAITING) return;

        // Config tool — private lobby owner only
        if (item.getType() == Material.COMPARATOR
                && !arena.isPublic()
                && arena.getOwnerId().equals(p.getUniqueId())) {
            new ArenaConfigMenu(p, arena).open();
            e.setCancelled(true);
            return;
        }

        // Class selector
        if (item.getType() == Material.NETHER_STAR) {
            MessageUtils.sendRaw(p, "&7[Classes] &eComing soon...");
            e.setCancelled(true);
        }
    }

    // ── Death ─────────────────────────────────────────────────────────────────

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        GameArena arena = plugin.getArenaManager().getArena(victim);
        if (arena == null) return;

        e.getDrops().clear();
        e.setDroppedExp(0);

        if (arena.getState() == GameState.RUNNING && arena.isPlayerInGame(victim)) {
            arena.endGame(false, victim.getName());
            e.setDeathMessage(null);
        }
    }

    // ── Pickup (victory detection) ────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        GameArena arena = plugin.getArenaManager().getArena(p);
        if (arena == null || arena.getState() != GameState.RUNNING) return;
        if (!arena.isPlayerInGame(p)) return;

        ItemStack picked = e.getItem().getItemStack();

        // PDC-tagged display items must never be picked up
        if (isTagged(picked, GameArena.OBJECTIVE_KEY)
                || isTagged(picked, GameArena.LEAVE_KEY)) {
            e.setCancelled(true);
            return;
        }

        // Target material → victory
        if (picked.getType() == arena.getTargetItem()) {
            e.setCancelled(true);
            e.getItem().remove();
            arena.endGame(true, p.getName());
        }
    }

    // ── Disconnect cleanup ────────────────────────────────────────────────────

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (plugin.getArenaManager().getArena(e.getPlayer()) != null) {
            plugin.getArenaManager().leaveArena(e.getPlayer());
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private boolean isTagged(ItemStack item, org.bukkit.NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(key, PersistentDataType.BOOLEAN);
    }
}
