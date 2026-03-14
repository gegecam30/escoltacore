package com.escoltacore.managers;

import com.escoltacore.EscoltaCorePlugin;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class ItemManager {

    private final EscoltaCorePlugin plugin;
    private final List<Material> blacklist  = new ArrayList<>();
    private final List<Material> whitelist  = new ArrayList<>(); // sprite-supported pool

    public ItemManager(EscoltaCorePlugin plugin) {
        this.plugin = plugin;
        loadBlacklist();
        loadWhitelist();
    }

    private void loadBlacklist() {
        for (String key : plugin.getConfig().getStringList("random-objective.blacklist")) {
            try {
                blacklist.add(Material.valueOf(key.toUpperCase()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().log(Level.WARNING, "Invalid material in blacklist: " + key);
            }
        }
    }

    private void loadWhitelist() {
        List<String> keys = plugin.getConfig().getStringList("random-objective.sprite-pool");
        if (keys.isEmpty()) return; // whitelist disabled — use full random pool

        for (String key : keys) {
            try {
                Material m = Material.valueOf(key.toUpperCase());
                if (m.isItem()) {
                    whitelist.add(m);
                } else {
                    plugin.getLogger().log(Level.WARNING,
                            "Material in sprite-pool is not an item: " + key);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().log(Level.WARNING,
                        "Invalid material in sprite-pool: " + key);
            }
        }

        if (!whitelist.isEmpty()) {
            plugin.getLogger().info("sprite-pool active with " + whitelist.size() + " materials.");
        }
    }

    /**
     * Returns a random target material.
     *
     * If sprite-pool is populated in config, picks exclusively from that list
     * (ignoring the blacklist, since the pool is manually curated).
     *
     * If sprite-pool is empty, falls back to the full random mode filtered by
     * the blacklist.
     */
    public Material getRandomTarget() {
        if (!whitelist.isEmpty()) {
            // Pick randomly from the curated sprite pool
            return whitelist.get(ThreadLocalRandom.current().nextInt(whitelist.size()));
        }

        // Full random mode — up to 300 attempts to find a valid material
        Material[] all = Material.values();
        for (int i = 0; i < 300; i++) {
            Material m = all[ThreadLocalRandom.current().nextInt(all.length)];
            if (isValid(m)) return m;
        }
        return Material.STONE;
    }

    /** Used only in full-random mode. */
    private boolean isValid(Material m) {
        return m.isItem() && m.isSolid() && !m.isAir() && !blacklist.contains(m);
    }

    public List<Material> getWhitelist() {
        return Collections.unmodifiableList(whitelist);
    }

    public boolean isWhitelistActive() {
        return !whitelist.isEmpty();
    }
}
