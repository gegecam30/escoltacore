package com.escoltacore.listeners;

import com.escoltacore.utils.EscoltaMenu;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

public class GuiListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        // ¿El inventario tiene un dueño (Holder)?
        InventoryHolder holder = e.getInventory().getHolder();

        // ¿El dueño es uno de NUESTROS menús?
        if (holder instanceof EscoltaMenu) {
            e.setCancelled(true); // Bloquear que muevan ítems por defecto
            
            if (e.getCurrentItem() == null) return;

            // Delegar la lógica al propio menú
            EscoltaMenu menu = (EscoltaMenu) holder;
            menu.handleMenu(e);
        }
    }
}