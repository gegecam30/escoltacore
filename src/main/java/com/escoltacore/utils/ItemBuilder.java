package com.escoltacore.utils;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    public ItemBuilder name(String name) {
        meta.displayName(MessageUtils.component(name));
        return this;
    }

    public ItemBuilder lore(String... lore) {
        List<Component> components = Arrays.stream(lore)
                .map(MessageUtils::component)
                .collect(Collectors.toList());
        meta.lore(components);
        return this;
    }

    public ItemBuilder lore(List<String> lore) {
        meta.lore(lore.stream().map(MessageUtils::component).collect(Collectors.toList()));
        return this;
    }

    /** Adds a glowing enchantment effect without showing enchantment text. */
    public ItemBuilder glow() {
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        return this;
    }

    /** Tags the item with a PDC key (boolean true). */
    public ItemBuilder tag(NamespacedKey key) {
        meta.getPersistentDataContainer().set(key, PersistentDataType.BOOLEAN, true);
        return this;
    }

    public ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }
}
