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
 * Paged particle selection submenu.
 * 15 curated particles — 7 shown per page (slots 11-17), arrows on slots 18 and 26.
 *
 * Layout (3 rows = 27 slots):
 *   Row 0  [0..8]  : decorative header filler
 *   Row 1  [9..17] : particles for this page (slots 10-16), back arrow slot 9, next arrow slot 17
 *   Row 2  [18..26]: selected indicator (slot 22), back-to-config button (slot 18)
 */
public class ParticleSelectMenu extends EscoltaMenu {

    // ─── Curated list ───────────────────────────────────────────────────────────
    // (name, particle, representative item, color tag)
    private record ParticleEntry(String displayName, Particle particle, Material icon, String colorTag) {}

    private static final List<ParticleEntry> ENTRIES = List.of(
        new ParticleEntry("Cyan Dust",       Particle.DUST,            Material.CYAN_DYE,            "&b"),
        new ParticleEntry("Flame",           Particle.FLAME,           Material.TORCH,               "&6"),
        new ParticleEntry("Soul Fire",       Particle.SOUL_FIRE_FLAME, Material.SOUL_TORCH,          "&3"),
        new ParticleEntry("Heart",           Particle.HEART,           Material.PINK_DYE,            "&d"),
        new ParticleEntry("Note",            Particle.NOTE,            Material.NOTE_BLOCK,          "&e"),
        new ParticleEntry("Witch Magic",     Particle.WITCH,           Material.BREWING_STAND,       "&5"),
        new ParticleEntry("Enchantment",     Particle.ENCHANT,         Material.ENCHANTING_TABLE,    "&9"),
        new ParticleEntry("Happy Villager",  Particle.HAPPY_VILLAGER,  Material.EMERALD,             "&a"),
        new ParticleEntry("Angry Villager",  Particle.ANGRY_VILLAGER,  Material.IRON_INGOT,          "&c"),
        new ParticleEntry("Snowflake",       Particle.SNOWFLAKE,       Material.SNOWBALL,            "&f"),
        new ParticleEntry("End Rod",         Particle.END_ROD,         Material.END_ROD,             "&e"),
        new ParticleEntry("Dragon Breath",   Particle.DRAGON_BREATH,   Material.DRAGON_EGG,          "&5"),
        new ParticleEntry("Cherry Blossom",  Particle.CHERRY_LEAVES,   Material.CHERRY_SAPLING,      "&d"),
        new ParticleEntry("Glow",            Particle.GLOW,            Material.GLOWSTONE_DUST,      "&6"),
        new ParticleEntry("Crimson Spore",   Particle.CRIMSON_SPORE,   Material.CRIMSON_FUNGUS,      "&4")
    );

    private static final int PER_PAGE = 7;

    // Slots in row 1 where particles are displayed (indices within the 27-slot inventory)
    private static final int[] PARTICLE_SLOTS = {10, 11, 12, 13, 14, 15, 16};

    // Fixed slots
    private static final int SLOT_PREV      = 9;   // left arrow
    private static final int SLOT_NEXT      = 17;  // right arrow
    private static final int SLOT_BACK      = 18;  // back to ArenaConfigMenu
    private static final int SLOT_CURRENT   = 22;  // shows currently selected particle

    // ─── State ──────────────────────────────────────────────────────────────────
    private final GameArena arena;
    private int page = 0; // 0-indexed

    public ParticleSelectMenu(Player viewer, GameArena arena) {
        super(viewer, 3, "&8Select Border Particle");
        this.arena = arena;

        // Start on the page that contains the currently selected particle
        int currentIdx = indexOfCurrent();
        if (currentIdx >= 0) page = currentIdx / PER_PAGE;
    }

    // ─── Build UI ────────────────────────────────────────────────────────────────
    @Override
    public void setMenuItems() {
        // Clear all first so stale items don't linger when re-rendering
        inventory.clear();

        int totalPages = totalPages();
        int startIdx   = page * PER_PAGE;

        // ── Row 0: header filler ──
        var filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name("&8").build();
        for (int i = 0; i < 9; i++) inventory.setItem(i, filler);

        // ── Row 1: particles ──
        for (int slotPos = 0; slotPos < PARTICLE_SLOTS.length; slotPos++) {
            int entryIdx = startIdx + slotPos;
            if (entryIdx >= ENTRIES.size()) break;

            ParticleEntry e  = ENTRIES.get(entryIdx);
            boolean selected = e.particle() == arena.getParticle();

            String name  = e.colorTag() + "&l" + e.displayName();
            String[] lore;
            if (selected) {
                lore = new String[]{"&a✔ Currently active", "", "&7Click to reselect"};
            } else {
                lore = new String[]{"&7Click to select"};
            }

            // Highlight selected item with a glow-effect border (enchanted book trick)
            ItemBuilder ib = new ItemBuilder(e.icon()).name(name).lore(lore);
            if (selected) ib.glow();

            inventory.setItem(PARTICLE_SLOTS[slotPos], ib.build());
        }

        // ── Row 1: navigation arrows ──
        if (page > 0) {
            inventory.setItem(SLOT_PREV, new ItemBuilder(Material.ARROW)
                    .name("&e« Previous")
                    .lore("&7Page " + page + "/" + totalPages).build());
        } else {
            inventory.setItem(SLOT_PREV, filler);
        }
        if (page < totalPages - 1) {
            inventory.setItem(SLOT_NEXT, new ItemBuilder(Material.ARROW)
                    .name("&eNext »")
                    .lore("&7Page " + (page + 2) + "/" + totalPages).build());
        } else {
            inventory.setItem(SLOT_NEXT, filler);
        }

        // ── Row 2: back button ──
        inventory.setItem(SLOT_BACK, new ItemBuilder(Material.OAK_DOOR)
                .name("&c« Back to Config")
                .lore("&7Return to the arena settings.").build());

        // ── Row 2: currently selected display ──
        int curIdx = indexOfCurrent();
        if (curIdx >= 0) {
            ParticleEntry cur = ENTRIES.get(curIdx);
            inventory.setItem(SLOT_CURRENT, new ItemBuilder(cur.icon())
                    .name(cur.colorTag() + "&lCurrent: &f" + cur.displayName())
                    .lore("&7This is your active border particle.").build());
        }

        // ── Row 2: filler for empty row 2 slots ──
        for (int i = 18; i < 27; i++) {
            if (inventory.getItem(i) == null) inventory.setItem(i, filler);
        }
    }

    // ─── Click handler ────────────────────────────────────────────────────────────
    @Override
    public void handleMenu(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        int slot = e.getSlot();

        // ── Previous page ──
        if (slot == SLOT_PREV && page > 0) {
            page--;
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.8f);
            setMenuItems();
            return;
        }

        // ── Next page ──
        if (slot == SLOT_NEXT && page < totalPages() - 1) {
            page++;
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.8f);
            setMenuItems();
            return;
        }

        // ── Back to config ──
        if (slot == SLOT_BACK) {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            new ArenaConfigMenu(p, arena).open();
            return;
        }

        // ── Particle selection ──
        for (int slotPos = 0; slotPos < PARTICLE_SLOTS.length; slotPos++) {
            if (slot == PARTICLE_SLOTS[slotPos]) {
                int entryIdx = page * PER_PAGE + slotPos;
                if (entryIdx >= ENTRIES.size()) return;

                arena.setParticle(ENTRIES.get(entryIdx).particle());
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
                p.sendActionBar(MessageUtils.component(
                        "&aParticle set: " + ENTRIES.get(entryIdx).colorTag() + ENTRIES.get(entryIdx).displayName()));
                setMenuItems(); // refresh to update checkmark
                return;
            }
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────
    private int totalPages() {
        return (int) Math.ceil((double) ENTRIES.size() / PER_PAGE);
    }

    private int indexOfCurrent() {
        Particle cur = arena.getParticle();
        for (int i = 0; i < ENTRIES.size(); i++) {
            if (ENTRIES.get(i).particle() == cur) return i;
        }
        return -1;
    }
}
