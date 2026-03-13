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

public class ArenaConfigMenu extends EscoltaMenu {

    private final GameArena arena;
    
    // Lista de partículas disponibles para rotar
    private final List<Particle> particles = Arrays.asList(
            Particle.REDSTONE, 
            Particle.FLAME, 
            Particle.VILLAGER_HAPPY, 
            Particle.HEART, 
            Particle.NOTE,
            Particle.SPELL_WITCH
    );

    public ArenaConfigMenu(Player viewer, GameArena arena) {
        // Título limpio desde config
        super(viewer, 3, MessageUtils.get("gui.config-title").replace("%arena%", arena.getName()));
        this.arena = arena;
    }

    @Override
    public void setMenuItems() {
        // Slot 11: Radio
        inventory.setItem(11, new ItemBuilder(Material.BEACON)
                .name("&bProtection Radius")
                .lore(
                    "&7Current: &e" + arena.getRadius(), 
                    "", 
                    "&aL-Click: +1", 
                    "&cR-Click: -1"
                ).build());

        // Slot 13: Partícula (Muestra la actual)
        // Obtenemos el nombre "bonito" de la partícula actual
        String particleName = arena.getParticle().name();
        
        inventory.setItem(13, new ItemBuilder(Material.BLAZE_POWDER)
                .name("&6Border Particle")
                .lore(
                    "&7Current: &e" + particleName,
                    "",
                    "&eClick to switch style"
                ).build());

        // Slot 15: Iniciar
        inventory.setItem(15, new ItemBuilder(Material.EMERALD_BLOCK)
                .name("&a&lSTART GAME")
                .lore("&7Click to start").build());
    }

    @Override
    public void handleMenu(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();

        // RADIO
        if (e.getSlot() == 11) {
            double current = arena.getRadius();
            if (e.isLeftClick()) arena.setRadius(current + 1);
            else if (e.isRightClick() && current > 5) arena.setRadius(current - 1);
            
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            setMenuItems(); // Actualizar visualmente
        }

        // PARTÍCULAS (Lógica de rotación)
        else if (e.getSlot() == 13) {
            // Buscamos el índice actual
            Particle currentP = arena.getParticle();
            int index = particles.indexOf(currentP);
            
            // Calculamos el siguiente índice (rotativo)
            int nextIndex = (index + 1) % particles.size();
            Particle nextP = particles.get(nextIndex);
            
            // Guardamos
            arena.setParticle(nextP);
            
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
            setMenuItems(); // Actualizar visualmente
        }

        // INICIAR
        else if (e.getSlot() == 15) {
            p.closeInventory();
            arena.start(p);
        }
    }
}