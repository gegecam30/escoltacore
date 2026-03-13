package com.escoltacore.managers;

import com.escoltacore.EscoltaCorePlugin;
import org.bukkit.Material;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class ItemManager {

    private final EscoltaCorePlugin plugin;
    private final List<Material> blacklist = new ArrayList<>();

    public ItemManager(EscoltaCorePlugin plugin) {
        this.plugin = plugin;
        loadBlacklist();
    }

    private void loadBlacklist() {
        List<String> configList = plugin.getConfig().getStringList("random-objective.blacklist");
        for (String key : configList) {
            try {
                blacklist.add(Material.valueOf(key.toUpperCase()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().log(Level.WARNING, "Material inválido en blacklist: " + key);
            }
        }
    }

    public Material getRandomTarget() {
        Material[] materials = Material.values();
        for (int i = 0; i < 200; i++) {
            Material candidate = materials[ThreadLocalRandom.current().nextInt(materials.length)];
            if (isValid(candidate)) return candidate;
        }
        return Material.STONE; // fallback
    }

    private boolean isValid(Material m) {
        if (!m.isItem() || !m.isSolid() || m.isAir()) return false;
        return !blacklist.contains(m);
    }
}
