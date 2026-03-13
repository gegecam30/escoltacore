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
                Material mat = Material.valueOf(key.toUpperCase());
                blacklist.add(mat);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().log(Level.WARNING, "Material inválido en blacklist: " + key);
            }
        }
    }

    /**
     * Busca un material aleatorio que sea bloque, ítem y sólido,
     * y que no esté en la lista negra.
     */
    public Material getRandomTarget() {
        Material[] materials = Material.values();
        Material selected = null;
        
        // Intentamos hasta 100 veces encontrar uno válido para evitar bucles infinitos
        // si la blacklist fuera enorme.
        for (int i = 0; i < 100; i++) {
            Material candidate = materials[ThreadLocalRandom.current().nextInt(materials.length)];
            
            if (isValid(candidate)) {
                selected = candidate;
                break;
            }
        }
        
        // Fallback por si acaso (Piedra)
        return selected != null ? selected : Material.STONE;
    }

    private boolean isValid(Material m) {
        // Filtrar aire, legacy y cosas que no son bloques físicos o items obtenibles
        if (!m.isItem() || !m.isSolid() || m.isAir()) return false;
        
        // Filtrar blacklist
        return !blacklist.contains(m);
    }
}