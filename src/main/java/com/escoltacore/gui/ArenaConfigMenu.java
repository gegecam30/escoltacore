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

/**
 * Config GUI for private lobby owners.
 * Particle slot now opens ParticleSelectMenu instead of cycling inline.
 */
public class ArenaConfigMenu extends EscoltaMenu {

    private final GameArena arena;

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

        // Slot 13 — Particle (opens submenu)
        String particleLabel = "&e" + arena.getParticle().name()
                .replace("_", " ").toLowerCase();
        // Capitalise first letter
        if (!particleLabel.isEmpty())
            particleLabel = particleLabel.substring(0, 3)
                    + Character.toUpperCase(particleLabel.charAt(3))
                    + particleLabel.substring(4);

        inventory.setItem(13, new ItemBuilder(Material.BLAZE_POWDER)
                .name("&6Border Particle")
                .lore(
                        "&7Current: " + particleLabel,
                        "",
                        "&eClick&7: Open particle selector",
                        "&815 styles available"
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
                p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1f);
                new ParticleSelectMenu(p, arena).open();
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
