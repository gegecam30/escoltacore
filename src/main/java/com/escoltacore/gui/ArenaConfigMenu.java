package com.escoltacore.gui;

import com.escoltacore.arena.GameArena;
import com.escoltacore.utils.EscoltaMenu;
import com.escoltacore.utils.ItemBuilder;
import com.escoltacore.utils.MessageUtils;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;

/**
 * Config GUI for private lobby owners.
 * Particle names are correct for Paper 1.21.1.
 */
public class ArenaConfigMenu extends EscoltaMenu {

    private final GameArena arena;

    private static final List<Particle> PARTICLES = List.of(
            Particle.DUST,           // cyan glow (was REDSTONE)
            Particle.FLAME,          // orange flame
            Particle.HAPPY_VILLAGER, // green sparkle (was VILLAGER_HAPPY)
            Particle.HEART,          // pink heart
            Particle.NOTE,           // music note
            Particle.WITCH           // purple magic (was SPELL_WITCH)
    );

    private static final List<String> PARTICLE_NAMES = List.of(
            "&b✦ Cyan Dust",
            "&6✦ Flame",
            "&a✦ Happy Villager",
            "&d✦ Heart",
            "&e✦ Note",
            "&5✦ Witch"
    );

    public ArenaConfigMenu(Player viewer, GameArena arena) {
        super(viewer, 3,
                MessageUtils.get("gui.config-title").replace("%arena%", arena.getName()));
        this.arena = arena;
    }

    @Override
    public void setMenuItems() {
        fillBorder();

        // Slot 11 — Radius
        inventory.setItem(11, new ItemBuilder(Material.BEACON)
                .name("&bProtection Radius")
                .lore(
                        "&7Current: &e" + (int) arena.getRadius() + " blocks",
                        "",
                        "&aLeft-Click&7: +1",
                        "&cRight-Click&7: -1",
                        "&8Min: 5  |  Max: 50"
                ).build());

        // Slot 13 — Particle
        int idx  = PARTICLES.indexOf(arena.getParticle());
        if (idx < 0) idx = 0;
        int next = (idx + 1) % PARTICLES.size();

        inventory.setItem(13, new ItemBuilder(Material.BLAZE_POWDER)
                .name("&6Border Particle")
                .lore(
                        "&7Current: " + PARTICLE_NAMES.get(idx),
                        "&7Next:    " + PARTICLE_NAMES.get(next),
                        "",
                        "&eClick&7: Cycle style"
                ).build());

        // Slot 15 — Start
        inventory.setItem(15, new ItemBuilder(Material.EMERALD_BLOCK)
                .name("&a&lSTART GAME")
                .lore("&7Click to start the game.").build());
    }

    @Override
    public void handleMenu(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();

        switch (e.getSlot()) {
            case 11 -> {
                double r = arena.getRadius();
                if (e.isLeftClick()  && r < 50) arena.setRadius(r + 1);
                if (e.isRightClick() && r > 5)  arena.setRadius(r - 1);
                p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1f);
                setMenuItems();
            }
            case 13 -> {
                int idx  = PARTICLES.indexOf(arena.getParticle());
                if (idx < 0) idx = 0;
                arena.setParticle(PARTICLES.get((idx + 1) % PARTICLES.size()));
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                setMenuItems();
            }
            case 15 -> {
                p.closeInventory();
                arena.start(p);
            }
        }
    }

    private void fillBorder() {
        var filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name("&8").build();
        for (int i = 0; i < 9; i++) {
            if (inventory.getItem(i) == null)      inventory.setItem(i, filler);
            if (inventory.getItem(18 + i) == null) inventory.setItem(18 + i, filler);
        }
        inventory.setItem(9,  filler);
        inventory.setItem(17, filler);
    }
}
