package com.escoltacore.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public abstract class EscoltaMenu implements InventoryHolder {

    protected final Inventory inventory;
    protected final Player viewer;

    public EscoltaMenu(Player viewer, int rows, String title) {
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, rows * 9, MessageUtils.component(title));
    }

    public abstract void setMenuItems();
    public abstract void handleMenu(InventoryClickEvent e);

    public void open() {
        setMenuItems();
        viewer.openInventory(inventory);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
