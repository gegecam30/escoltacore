package com.escoltacore.listeners;

import com.escoltacore.utils.EscoltaMenu;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

public class GuiListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        InventoryHolder holder = e.getInventory().getHolder();
        if (!(holder instanceof EscoltaMenu menu)) return;

        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;
        menu.handleMenu(e);
    }
}
