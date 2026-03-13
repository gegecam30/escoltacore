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

import java.util.Arrays;
import java.util.List;

/**
 * GUI de configuración de arena.
 *
 * Partículas actualizadas para Paper 1.21.1:
 *   REDSTONE       → DUST
 *   VILLAGER_HAPPY → HAPPY_VILLAGER
 *   SPELL_WITCH    → WITCH
 */
public class ArenaConfigMenu extends EscoltaMenu {

    private final GameArena arena;

    /**
     * Lista de partículas disponibles para rotar.
     * ⚠️ Nombres correctos en 1.21.1
     */
    private static final List<Particle> PARTICLES = Arrays.asList(
            Particle.DUST,            // antes REDSTONE  — partícula con color personalizado (cyan)
            Particle.FLAME,           // llama naranja
            Particle.HAPPY_VILLAGER,  // antes VILLAGER_HAPPY — brillo verde
            Particle.HEART,           // corazón rosa
            Particle.NOTE,            // nota musical
            Particle.WITCH            // antes SPELL_WITCH — partícula morada mágica
    );

    /** Nombres amigables para mostrar en el lore del GUI */
    private static final List<String> PARTICLE_DISPLAY_NAMES = Arrays.asList(
            "&b✦ Cian (Dust)",
            "&6✦ Llama",
            "&a✦ Aldeano Feliz",
            "&d✦ Corazón",
            "&e✦ Nota Musical",
            "&5✦ Bruja"
    );

    public ArenaConfigMenu(Player viewer, GameArena arena) {
        super(viewer, 3,
                MessageUtils.get("gui.config-title").replace("%arena%", arena.getName()));
        this.arena = arena;
    }

    @Override
    public void setMenuItems() {
        // ── Slot 11: Radio ────────────────────────────────────────────────────
        inventory.setItem(11, new ItemBuilder(Material.BEACON)
                .name("&bRadio de Protección")
                .lore(
                        "&7Actual: &e" + (int) arena.getRadius() + " bloques",
                        "",
                        "&aClick Izq&7: +1 bloque",
                        "&cClick Der&7: -1 bloque",
                        "&8Mín: 5  |  Máx: 50"
                ).build());

        // ── Slot 13: Partícula ────────────────────────────────────────────────
        Particle currentParticle = arena.getParticle();
        int currentIndex = PARTICLES.indexOf(currentParticle);
        if (currentIndex < 0) currentIndex = 0;

        String particleDisplay = PARTICLE_DISPLAY_NAMES.get(currentIndex);
        String nextDisplay = PARTICLE_DISPLAY_NAMES.get((currentIndex + 1) % PARTICLES.size());

        inventory.setItem(13, new ItemBuilder(Material.BLAZE_POWDER)
                .name("&6Partícula del Borde")
                .lore(
                        "&7Actual: " + particleDisplay,
                        "&7Siguiente: " + nextDisplay,
                        "",
                        "&eClick&7: Cambiar estilo"
                ).build());

        // ── Slot 15: Iniciar ──────────────────────────────────────────────────
        inventory.setItem(15, new ItemBuilder(Material.EMERALD_BLOCK)
                .name("&a&lINICIAR PARTIDA")
                .lore(
                        "&7Click para comenzar el juego.",
                        "&8Requiere mín. " + "&e2 &8jugadores."
                ).build());

        // ── Decoración: relleno gris ──────────────────────────────────────────
        fillBorder();
    }

    @Override
    public void handleMenu(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();

        switch (e.getSlot()) {

            // RADIO
            case 11 -> {
                double current = arena.getRadius();
                if (e.isLeftClick() && current < 50) {
                    arena.setRadius(current + 1);
                } else if (e.isRightClick() && current > 5) {
                    arena.setRadius(current - 1);
                }
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                setMenuItems(); // Refrescar GUI
            }

            // PARTÍCULAS
            case 13 -> {
                Particle currentP = arena.getParticle();
                int index = PARTICLES.indexOf(currentP);
                if (index < 0) index = 0;
                int nextIndex = (index + 1) % PARTICLES.size();
                arena.setParticle(PARTICLES.get(nextIndex));

                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                setMenuItems();
            }

            // INICIAR
            case 15 -> {
                p.closeInventory();
                arena.start(p);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Rellena el borde del inventario con cristal gris como decoración. */
    private void fillBorder() {
        var filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .name("&8")
                .build();

        // Fila superior e inferior (primera y tercera fila en inventario de 3 filas)
        for (int i = 0; i < 9; i++) {
            if (inventory.getItem(i) == null)      inventory.setItem(i, filler);
            if (inventory.getItem(18 + i) == null) inventory.setItem(18 + i, filler);
        }
        // Columnas laterales
        for (int i = 9; i < 18; i++) {
            if (i == 9 || i == 17) inventory.setItem(i, filler);
        }
    }
}
