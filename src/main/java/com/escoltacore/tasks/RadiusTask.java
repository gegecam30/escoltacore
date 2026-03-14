package com.escoltacore.tasks;

import com.escoltacore.EscoltaCorePlugin;
import com.escoltacore.arena.GameArena;
import com.escoltacore.utils.MessageUtils;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Draws the protection border and enforces the radius.
 *
 * Visual strategy — OPTIMIZED SPIRAL:
 *   Instead of spawning all N points of a ring each tick (expensive for large radii),
 *   we advance a single "cursor" angle by a fixed arc each tick and spawn a small
 *   cluster of points at multiple heights at that arc position.
 *
 *   Result per tick: POINTS_PER_TICK × LAYERS particles (default 4 × 3 = 12/tick).
 *   The spiral completes one full revolution every ~2 seconds, giving the illusion
 *   of a solid cylinder without the TPS cost of rendering the whole ring at once.
 *
 *   At 20 TPS, 12 particles/tick per arena is negligible even with 8 simultaneous games.
 */
public class RadiusTask extends BukkitRunnable {

    // ─── Visual tuning ────────────────────────────────────────────────────────────
    private static final int    POINTS_PER_TICK   = 4;
    private static final int    FULL_CIRCLE_STEPS = 40;  // 20 TPS × 2s
    private static final int    LAYERS            = 3;
    private static final double LAYER_HEIGHT      = 1.0;
    private static final double Y_OFFSET_BASE     = 0.5;
    private static final float  DUST_SIZE         = 1.8f;
    private static final double WARNING_SHRINK    = 3.0;

    // ─── Fields ───────────────────────────────────────────────────────────────────
    private final GameArena arena;
    private final double    radius;
    private final double    radiusSq;
    private final double    warningSq;
    private final Particle  particle;
    private double angle       = 0;
    private int    layerCursor = 0;

    public RadiusTask(EscoltaCorePlugin plugin, GameArena arena, double radius, Particle particle) {
        this.arena     = arena;
        this.radius    = radius;
        this.radiusSq  = radius * radius;
        this.particle  = particle;
        double warnR   = Math.max(1.0, radius - WARNING_SHRINK);
        this.warningSq = warnR * warnR;
    }

    @Override
    public void run() {
        if (arena.getEscortId() == null) { cancel(); return; }

        Player escort = Bukkit.getPlayer(arena.getEscortId());
        if (escort == null || !escort.isOnline()) {
            arena.endGame(false, "Target disconnected");
            cancel();
            return;
        }

        Location center = escort.getLocation();
        World    world  = escort.getWorld();
        boolean  danger = false;

        // ── Draw spiral ─────────────────────────────────────────────────────────
        double arcStep = (2 * Math.PI) / FULL_CIRCLE_STEPS;
        for (int i = 0; i < POINTS_PER_TICK; i++) {
            angle += arcStep;
            double x   = radius * Math.cos(angle);
            double z   = radius * Math.sin(angle);
            double yOff = Y_OFFSET_BASE + (layerCursor % LAYERS) * LAYER_HEIGHT;
            layerCursor++;
            spawnParticle(world, center.clone().add(x, yOff, z));
        }

        // ── Check defenders ─────────────────────────────────────────────────────
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!arena.isPlayerInGame(p)) continue;
            if (p.getUniqueId().equals(arena.getEscortId())) continue;
            if (p.isDead()) continue;

            boolean diffWorld = !p.getWorld().equals(world);
            double  distSq    = diffWorld ? Double.MAX_VALUE
                                          : p.getLocation().distanceSquared(center);

            if (diffWorld || distSq > radiusSq) {
                p.setHealth(0);
                arena.endGame(false,
                        MessageUtils.get("defeat-out-of-bounds").replace("%player%", p.getName()));
                cancel();
                return;
            }
            if (distSq > warningSq) {
                p.sendActionBar(MessageUtils.component(MessageUtils.get("radius-warning")));
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 2f);
                danger = true;
            }
        }

        if (danger) escort.addPotionEffect(
                new PotionEffect(PotionEffectType.GLOWING, 10, 0, false, false));
    }

    private void spawnParticle(World world, Location loc) {
        switch (particle) {
            case DUST -> world.spawnParticle(
                    Particle.DUST, loc, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(0, 220, 255), DUST_SIZE));

            case SOUL_FIRE_FLAME -> world.spawnParticle(
                    Particle.DUST, loc, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(30, 200, 200), DUST_SIZE));

            case DRAGON_BREATH -> world.spawnParticle(
                    Particle.DUST, loc, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(150, 0, 220), DUST_SIZE));

            case CRIMSON_SPORE -> world.spawnParticle(
                    Particle.DUST, loc, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(200, 20, 20), DUST_SIZE));

            case FLAME, HAPPY_VILLAGER, HEART, NOTE, WITCH,
                 ENCHANT, ANGRY_VILLAGER, SNOWFLAKE, END_ROD,
                 CHERRY_LEAVES, GLOW ->
                    world.spawnParticle(particle, loc, 1, 0, 0, 0, 0);

            default -> {
                try {
                    world.spawnParticle(particle, loc, 1, 0, 0, 0, 0);
                } catch (Exception ignored) {
                    world.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.WHITE, 1.2f));
                }
            }
        }
    }
}
