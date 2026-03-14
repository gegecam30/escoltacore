package com.escoltacore.map;

import com.escoltacore.EscoltaCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Manages loading sprites from disk and creating FILLED_MAP ItemStacks.
 *
 * ── How sprites are resolved (in order) ──────────────────────────────────────
 *  1. plugins/EscoltaCore/sprites/<MATERIAL_NAME>.png  (manual drop-in)
 *  2. Auto-download from Faithful-Pack GitHub repo     (requires internet)
 *  3. Procedural fallback: average color swatch        (always works offline)
 *
 * ── File naming rules ────────────────────────────────────────────────────────
 *  File name must be the Bukkit Material name in UPPERCASE, e.g. DIAMOND.png
 *  Any resolution PNG is accepted — will be scaled to 128×128.
 *
 * ── Map cleanup ──────────────────────────────────────────────────────────────
 *  MapViews are tracked per-arena and freed in forceStop()/softReset().
 */
public class SpriteMapManager {

    private static final int MAP_SIZE = 128;

    // Faithful 32x repo — higher res than vanilla 16x, cleaner upscale to 128
    private static final String BASE_BLOCK = "https://raw.githubusercontent.com/"
            + "Faithful-Pack/Default-Java/1.21.5/assets/minecraft/textures/block/%s.png";
    private static final String BASE_ITEM  = "https://raw.githubusercontent.com/"
            + "Faithful-Pack/Default-Java/1.21.5/assets/minecraft/textures/item/%s.png";

    private final EscoltaCorePlugin plugin;
    private final File spritesFolder;

    /** Material name (upper) → scaled 128×128 BufferedImage, loaded at startup */
    private final Map<String, BufferedImage> cache = new HashMap<>();

    /** arenaName → list of MapView IDs created for that arena */
    private final Map<String, List<Integer>> arenaMapIds = new HashMap<>();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .build();

    public SpriteMapManager(EscoltaCorePlugin plugin) {
        this.plugin = plugin;
        this.spritesFolder = new File(plugin.getDataFolder(), "sprites");
        if (!spritesFolder.exists()) spritesFolder.mkdirs();

        writePlaceholderReadme();
        preloadAll();
    }

    // ── Startup loading ───────────────────────────────────────────────────────

    private void preloadAll() {
        List<String> pool = plugin.getConfig().getStringList("random-objective.sprite-pool");
        if (pool.isEmpty()) {
            plugin.getLogger().info("[Sprites] sprite-pool is empty — map display disabled.");
            return;
        }
        int loaded = 0, downloaded = 0, generated = 0;
        for (String entry : pool) {
            String matName = entry.trim().toUpperCase();
            if (cache.containsKey(matName)) continue;

            // 1 — Manual file on disk
            File f = new File(spritesFolder, matName + ".png");
            if (f.exists()) {
                BufferedImage img = readAndScale(f);
                if (img != null) { cache.put(matName, img); loaded++; continue; }
            }

            // 2 — Auto-download (async so server doesn't stall)
            final String mn = matName;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                BufferedImage img = tryDownload(mn);
                if (img != null) {
                    cache.put(mn, img);
                    plugin.getLogger().info("[Sprites] Downloaded: " + mn);
                    // Save to disk for next restart
                    saveToFile(img, new File(spritesFolder, mn + ".png"));
                } else {
                    // 3 — Procedural fallback
                    cache.put(mn, generateFallback(mn));
                    plugin.getLogger().info("[Sprites] Generated fallback swatch: " + mn);
                }
            });
            // Count as "in progress" — will be in cache by game start
            generated++;
        }
        plugin.getLogger().info(String.format(
                "[Sprites] Pre-loaded %d from disk. %d downloading/generating async.",
                loaded, generated));
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public boolean hasSprite(Material material) {
        return cache.containsKey(material.name());
    }

    /**
     * Creates a FILLED_MAP showing the sprite, tagged with OBJECTIVE_KEY.
     * Returns null if no sprite is available (caller should fall back to KNOWLEDGE_BOOK).
     */
    public ItemStack createMapItem(Material material, String arenaName,
                                   String displayName, String[] lore) {
        BufferedImage sprite = cache.get(material.name());
        if (sprite == null) return null;

        World world = Bukkit.getWorlds().get(0);
        MapView mapView = Bukkit.createMap(world);
        mapView.setScale(MapView.Scale.CLOSEST);
        mapView.setTrackingPosition(false);
        mapView.setUnlimitedTracking(false);

        for (MapRenderer r : mapView.getRenderers()) mapView.removeRenderer(r);
        mapView.addRenderer(new SpriteRenderer(sprite));

        arenaMapIds.computeIfAbsent(arenaName, k -> new ArrayList<>())
                   .add(mapView.getId());

        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) mapItem.getItemMeta();
        meta.setMapView(mapView);

        var leg = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand();
        meta.displayName(leg.deserialize(displayName));
        if (lore.length > 0) {
            List<net.kyori.adventure.text.Component> loreList = new ArrayList<>();
            for (String line : lore) loreList.add(leg.deserialize(line));
            meta.lore(loreList);
        }
        meta.getPersistentDataContainer().set(
                com.escoltacore.arena.GameArena.OBJECTIVE_KEY,
                org.bukkit.persistence.PersistentDataType.BOOLEAN, true);

        mapItem.setItemMeta(meta);
        return mapItem;
    }

    /** Frees all MapViews created for an arena. Call on softReset and forceStop. */
    public void freeArenaMaps(String arenaName) {
        List<Integer> ids = arenaMapIds.remove(arenaName);
        if (ids == null) return;
        for (int id : ids) {
            MapView view = Bukkit.getMap(id);
            if (view != null)
                for (MapRenderer r : view.getRenderers()) view.removeRenderer(r);
        }
    }

    // ── Download ──────────────────────────────────────────────────────────────

    /**
     * Tries block/ texture first, then item/ texture.
     * Returns null if both fail (no internet, 404, etc.).
     */
    private BufferedImage tryDownload(String matName) {
        String fileName = matName.toLowerCase();

        // Some materials have a different file name in the repo
        String remapped = TEXTURE_REMAP.getOrDefault(matName, fileName);

        // Try block first, then item
        for (String url : new String[]{
                BASE_BLOCK.formatted(remapped),
                BASE_ITEM.formatted(remapped)}) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(8))
                        .GET().build();
                HttpResponse<InputStream> resp =
                        http.send(req, HttpResponse.BodyHandlers.ofInputStream());
                if (resp.statusCode() == 200) {
                    BufferedImage raw = ImageIO.read(resp.body());
                    if (raw != null) return scale(raw, MAP_SIZE, MAP_SIZE);
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    // ── Procedural fallback ───────────────────────────────────────────────────

    /**
     * Creates a 128×128 colored swatch using a deterministic color derived from
     * the material name. Better than nothing, and clearly distinguishable per material.
     */
    private BufferedImage generateFallback(String matName) {
        // Hash the name to a hue, keep saturation/brightness high so it's visible on map
        int hash = matName.hashCode();
        float hue = Math.abs(hash % 360) / 360f;
        Color base  = Color.getHSBColor(hue, 0.8f, 0.85f);
        Color dark  = Color.getHSBColor(hue, 0.9f, 0.55f);
        Color light = Color.getHSBColor(hue, 0.5f, 0.98f);

        BufferedImage img = new BufferedImage(MAP_SIZE, MAP_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        // Background
        g.setColor(base);
        g.fillRect(0, 0, MAP_SIZE, MAP_SIZE);

        // Checkerboard pixel-art pattern (2px cells) to hint at "item texture"
        for (int x = 0; x < MAP_SIZE; x += 4) {
            for (int y = 0; y < MAP_SIZE; y += 4) {
                g.setColor(((x + y) / 4 % 2 == 0) ? light : dark);
                g.fillRect(x, y, 2, 2);
            }
        }

        // Border
        g.setColor(dark);
        g.drawRect(2, 2, MAP_SIZE - 5, MAP_SIZE - 5);

        // Abbreviation text (first 3 chars) in the center
        String abbr = matName.length() > 3 ? matName.substring(0, 3) : matName;
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 28));
        g.setColor(Color.WHITE);
        FontMetrics fm = g.getFontMetrics();
        int tx = (MAP_SIZE - fm.stringWidth(abbr)) / 2;
        int ty = (MAP_SIZE - fm.getHeight()) / 2 + fm.getAscent();
        // Shadow
        g.setColor(dark);
        g.drawString(abbr, tx + 2, ty + 2);
        g.setColor(Color.WHITE);
        g.drawString(abbr, tx, ty);

        g.dispose();
        return img;
    }

    // ── Image helpers ─────────────────────────────────────────────────────────

    private BufferedImage readAndScale(File f) {
        try {
            BufferedImage raw = ImageIO.read(f);
            if (raw == null) throw new IOException("null from ImageIO");
            return scale(raw, MAP_SIZE, MAP_SIZE);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[Sprites] Failed to read: " + f.getName(), e);
            return null;
        }
    }

    private BufferedImage scale(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        // Nearest-neighbor keeps Minecraft pixel-art crisp on a 128px map
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private void saveToFile(BufferedImage img, File dest) {
        try { ImageIO.write(img, "PNG", dest); }
        catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[Sprites] Could not save: " + dest.getName(), e);
        }
    }

    // ── README placeholder ────────────────────────────────────────────────────

    private void writePlaceholderReadme() {
        File readme = new File(spritesFolder, "README.txt");
        if (readme.exists()) return;
        try (var w = new java.io.FileWriter(readme)) {
            w.write(
"""
EscoltaCore — sprites/
======================
Place 16×16 (or higher) PNG files here to override auto-downloaded textures.

File naming: <MATERIAL_NAME>.png  (Bukkit Material name, uppercase)
  Examples:  DIAMOND.png   GOLD_INGOT.png   OAK_LOG.png

On first startup the plugin will:
  1. Check this folder for each material in sprite-pool (config.yml)
  2. If missing, attempt to download from Faithful-Pack GitHub
  3. If download fails (offline server), generate a colored fallback swatch

Downloaded files are saved here automatically for future restarts.

Source repo:
  https://github.com/Faithful-Pack/Default-Java/tree/1.21.5/assets/minecraft/textures/
""");
        } catch (IOException ignored) {}
    }

    // ── Texture name remapping ────────────────────────────────────────────────
    // Bukkit Material name → actual filename in the Faithful repo (without extension).
    // Only entries that differ need to be listed here.

    private static final Map<String, String> TEXTURE_REMAP = Map.ofEntries(
            Map.entry("OAK_LOG",         "oak_log"),
            Map.entry("GOLD_INGOT",      "gold_ingot"),
            Map.entry("IRON_INGOT",      "iron_ingot"),
            Map.entry("OAK_PLANKS",      "oak_planks"),
            Map.entry("STONE_BRICKS",    "stone_bricks"),
            Map.entry("MOSSY_COBBLESTONE","mossy_cobblestone"),
            Map.entry("NETHER_BRICK",    "nether_bricks"),
            Map.entry("RED_MUSHROOM",    "red_mushroom_block"),
            Map.entry("BROWN_MUSHROOM",  "brown_mushroom_block")
            // Add more entries here if a material isn't found automatically
    );
}
