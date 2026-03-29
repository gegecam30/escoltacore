package com.escoltacore.tasks;

import com.escoltacore.EscoltaCorePlugin;
import com.escoltacore.arena.GameArena;
import com.escoltacore.utils.MessageUtils;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * ══════════════════════════════════════════════════════════════════════
 *  RouletteTask — Casino-style objective roulette for EscoltaCore
 * ══════════════════════════════════════════════════════════════════════
 *
 *  Visual layout (9-slot inventory):
 *    [0][1][2][3][★4★][5][6][7][8]
 *                  ↑ Winner lands here
 *
 *  Timing:
 *    - Frame = one "shift" of the belt (all items move left by 1)
 *    - Each frame waits N ticks before firing the next one
 *    - delay[] array controls acceleration → deceleration curve
 *    - Total duration: ~9 seconds
 *
 *  Sound phases:
 *    - FAST  (frames 0-14)  : UI_BUTTON_CLICK tick, pitch 1.0
 *    - MID   (frames 15-19) : BLOCK_NOTE_BLOCK_HAT, pitch rising
 *    - SLOW  (frames 20-24) : BLOCK_NOTE_BLOCK_HARP pitch 0.8→1.8 (dramatic)
 *    - FINAL (frame 25)     : UI_TOAST_CHALLENGE_COMPLETE + title reveal
 *
 *  Winner positioning:
 *    The winner is decided BEFORE the roulette starts.
 *    The belt array is constructed so that after exactly TOTAL_FRAMES shifts,
 *    the winner sits in CENTER_SLOT (slot 4).
 * ══════════════════════════════════════════════════════════════════════
 */
public class RouletteTask extends BukkitRunnable {

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final int   INVENTORY_SIZE  = 9;
    private static final int   CENTER_SLOT     = 4;
    private static final String INV_TITLE      = "&8&l⚡ &6&lFINDING OBJECTIVE&8&l ⚡";

    /**
     * Delay table (ticks between frames).
     * Index = frame number. Total frames = array length.
     *
     * Curve: instant fast → smooth deceleration → dramatic crawl at end.
     * Total ticks ≈ sum of array = ~160 ticks = ~8 seconds at 20 TPS.
     */
    private static final int[] DELAYS = {
        // Fast burst (2 ticks = 10 fps feel)
        2, 2, 2, 2, 2, 2, 2, 2, 2, 2,   // frames 0-9
        // Slowing down
        3, 3, 3, 3, 3,                   // frames 10-14
        // Mid phase
        4, 4, 5, 5, 6,                   // frames 15-19
        // Dramatic crawl — each one feels heavy
        8, 10, 13, 17, 22,               // frames 20-24
        // FINAL: the winner click (frame 25, handled specially)
        0
    };

    private static final int TOTAL_FRAMES    = DELAYS.length - 1; // 25 shifts + final
    private static final int FAST_END        = 14;
    private static final int MID_END         = 19;
    // slow phase = frames 20-24, final = 25

    // Pitch sequence for the last 5 dramatic frames (frames 20-24)
    private static final float[] SLOW_PITCHES = { 0.8f, 1.0f, 1.2f, 1.5f, 1.8f };

    // ── Fields ────────────────────────────────────────────────────────────────
    private final EscoltaCorePlugin plugin;
    private final GameArena         arena;
    private final Inventory         inventory;
    private final List<Material>    belt;      // circular buffer of materials on the belt
    private final Material          winner;
    private final Runnable          onFinished;

    private int  frameIndex  = 0;   // which frame we're about to execute
    private int  tickCounter = 0;   // ticks elapsed since last frame
    private boolean done     = false;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * @param plugin      Plugin instance
     * @param arena       The arena this roulette belongs to
     * @param pool        Full material pool (will be shuffled internally)
     * @param winner      The material that must end up in CENTER_SLOT
     * @param onFinished  Callback fired after the winner reveal (on main thread)
     */
    public RouletteTask(EscoltaCorePlugin plugin,
                        GameArena arena,
                        List<Material> pool,
                        Material winner,
                        Runnable onFinished) {
        this.plugin     = plugin;
        this.arena      = arena;
        this.winner     = winner;
        this.onFinished = onFinished;

        // Build inventory
        this.inventory = Bukkit.createInventory(null, INVENTORY_SIZE,
                MessageUtils.component(INV_TITLE));

        // Build belt — winner must land on CENTER_SLOT after TOTAL_FRAMES shifts
        this.belt = buildBelt(pool, winner);

        // Fill inventory with first 9 items of the belt
        refreshInventory();
    }

    // ── Runnable ──────────────────────────────────────────────────────────────

    @Override
    public void run() {
        if (done) { cancel(); return; }

        tickCounter++;

        int targetDelay = DELAYS[Math.min(frameIndex, TOTAL_FRAMES)];

        // Not time for next frame yet
        if (tickCounter < targetDelay) return;
        tickCounter = 0;

        // ── Execute frame ──
        if (frameIndex < TOTAL_FRAMES) {
            shiftBelt();
            refreshInventory();
            playTickSound(frameIndex);
            frameIndex++;
        } else if (frameIndex == TOTAL_FRAMES) {
            // ── FINAL FRAME — winner is in CENTER_SLOT ──
            done = true;
            revealWinner();
        }
    }

    // ── Belt logic ────────────────────────────────────────────────────────────

    /**
     * Constructs the belt List so that after exactly TOTAL_FRAMES left-shifts,
     * the winner ends up at index CENTER_SLOT (position 4 in the visible window).
     *
     * Belt size = INVENTORY_SIZE + TOTAL_FRAMES (we need that many materials to
     * fill the right side as items disappear on the left).
     *
     * The visible window at frame F is belt[F .. F+8].
     * We want belt[TOTAL_FRAMES + CENTER_SLOT] == winner.
     * So we place the winner at position (TOTAL_FRAMES + CENTER_SLOT) = 29.
     */
    private List<Material> buildBelt(List<Material> pool, Material winner) {
        int beltSize    = INVENTORY_SIZE + TOTAL_FRAMES; // 9 + 25 = 34
        int winnerIndex = TOTAL_FRAMES + CENTER_SLOT;    // 25 + 4  = 29

        // Create a shuffled filler pool (excludes winner — we place it manually)
        List<Material> filler = new ArrayList<>(pool);
        filler.remove(winner);
        // If pool is tiny, repeat it to fill the belt
        while (filler.size() < beltSize - 1) filler.addAll(filler);
        Collections.shuffle(filler);

        List<Material> result = new ArrayList<>(beltSize);
        int fillerIdx = 0;
        for (int i = 0; i < beltSize; i++) {
            if (i == winnerIndex) {
                result.add(winner);
            } else {
                result.add(filler.get(fillerIdx++ % filler.size()));
            }
        }
        return result;
    }

    /** Shifts the visible window left by 1 (frameIndex acts as the start pointer). */
    private void shiftBelt() {
        // frameIndex is already incremented after this call, so window is belt[frameIndex..frameIndex+8]
        // We don't mutate the list — the window pointer IS frameIndex
    }

    /**
     * Renders the current visible window (belt[frameIndex .. frameIndex+8]) into the inventory.
     * CENTER_SLOT (4) gets gold highlight + glow.
     * Neighbors (3, 5) get silver highlight.
     * All others are dim.
     */
    private void refreshInventory() {
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            int beltPos   = frameIndex + slot;
            Material mat  = beltPos < belt.size() ? belt.get(beltPos) : Material.GRAY_STAINED_GLASS_PANE;
            inventory.setItem(slot, buildSlotItem(mat, slot));
        }
        // Push update to all viewers
        for (UUID uuid : arena.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.getOpenInventory().getTopInventory().equals(inventory)) {
                p.updateInventory();
            }
        }
    }

    /**
     * Builds the ItemStack for a given slot with appropriate styling.
     *
     *  CENTER  (4): gold name + glow enchant
     *  NEAR    (3,5): yellow name
     *  SIDE    (2,6): gray name
     *  EDGE    (0,1,7,8): dark gray name
     */
    private ItemStack buildSlotItem(Material mat, int slot) {
        String rawName = GameArena.formatItemName(mat);

        String styledName = switch (slot) {
            case CENTER_SLOT      -> "&6&l✦ " + rawName + " &6&l✦";
            case 3, 5             -> "&e"  + rawName;
            case 2, 6             -> "&7"  + rawName;
            default               -> "&8"  + rawName; // 0,1,7,8
        };

        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(MessageUtils.component(styledName));
        meta.lore(List.of()); // No lore to keep it clean

        if (slot == CENTER_SLOT) {
            // Glow effect — enchantment hidden
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        }

        item.setItemMeta(meta);
        return item;
    }

    // ── Sound logic ───────────────────────────────────────────────────────────

    private void playTickSound(int frame) {
        if (frame <= FAST_END) {
            // Fast phase: rapid mechanical tick
            playSoundToAll(Sound.UI_BUTTON_CLICK, 0.4f, 1.0f);

        } else if (frame <= MID_END) {
            // Mid phase: slightly louder, pitched up
            int idx   = frame - FAST_END - 1;          // 0..4
            float pit = 1.0f + (idx * 0.05f);           // 1.00→1.20
            playSoundToAll(Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, pit);

        } else {
            // Slow dramatic phase: harp, ascending pitch
            int idx   = frame - MID_END - 1;            // 0..4
            float pit = SLOW_PITCHES[Math.min(idx, SLOW_PITCHES.length - 1)];
            playSoundToAll(Sound.BLOCK_NOTE_BLOCK_HARP, 1.0f, pit);

            // Extra suspense: send a subtle action bar countdown
            int remaining = TOTAL_FRAMES - frame;       // 4,3,2,1,0
            if (remaining > 0) {
                String dots = "●".repeat(remaining) + "○".repeat(4 - remaining + 1);
                broadcastActionBar("&7" + dots);
            }
        }
    }

    private void playSoundToAll(Sound sound, float volume, float pitch) {
        for (UUID uuid : arena.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.playSound(p.getLocation(), sound, volume, pitch);
        }
    }

    private void broadcastActionBar(String msg) {
        for (UUID uuid : arena.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendActionBar(MessageUtils.component(msg));
        }
    }

    // ── Final reveal ──────────────────────────────────────────────────────────

    private void revealWinner() {
        // 1. Freeze the display — winner is already in center, refresh once more to be sure
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            int beltPos  = TOTAL_FRAMES + slot;
            Material mat = beltPos < belt.size() ? belt.get(beltPos) : Material.GRAY_STAINED_GLASS_PANE;
            // Dim everything except center
            if (slot == CENTER_SLOT) {
                inventory.setItem(slot, buildSlotItem(mat, CENTER_SLOT));
            } else {
                // Dim to glass pane with no name
                ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                ItemMeta fm = filler.getItemMeta();
                if (fm != null) { fm.displayName(MessageUtils.component("&8")); filler.setItemMeta(fm); }
                inventory.setItem(slot, filler);
            }
        }

        // 2. Big reveal sound
        playSoundToAll(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        // Layered: add a second sound slightly delayed for impact
        Bukkit.getScheduler().runTaskLater(plugin, () ->
                playSoundToAll(Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f), 5L);

        // 3. Show winner title to all players
        String itemName = GameArena.formatItemName(winner);
        for (UUID uuid : arena.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            p.showTitle(net.kyori.adventure.title.Title.title(
                    MessageUtils.component("&6&l★ OBJECTIVE ★"),
                    MessageUtils.component("&fFind: &b&l" + itemName),
                    net.kyori.adventure.title.Title.Times.times(
                            java.time.Duration.ofMillis(200),
                            java.time.Duration.ofSeconds(3),
                            java.time.Duration.ofMillis(800))
            ));
        }

        // 4. Clear action bar
        broadcastActionBar("");

        // 5. After 2.5s: close inventories and fire onFinished callback
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (UUID uuid : arena.getPlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.getOpenInventory().getTopInventory().equals(inventory)) {
                    p.closeInventory();
                }
            }
            // Small additional delay after close before game starts
            Bukkit.getScheduler().runTaskLater(plugin, onFinished, 10L);
        }, 50L); // 2.5 seconds

        cancel();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Opens the roulette inventory for all players currently in the arena,
     * then starts the task running every tick.
     */
    public void openAndStart() {
        for (UUID uuid : arena.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.openInventory(inventory);
        }
        runTaskTimer(plugin, 0L, 1L);
    }

    /** Opens the roulette for a specific player (e.g. late joiner). */
    public void openFor(Player p) {
        p.openInventory(inventory);
    }
}
