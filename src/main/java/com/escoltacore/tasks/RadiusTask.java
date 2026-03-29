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
 * ══════════════════════════════════════════════════════════════════════
 *  RadiusTask — Square border with rising particles
 * ══════════════════════════════════════════════════════════════════════
 *
 *  Border shape: SQUARE (4 sides, axis-aligned to world X/Z)
 *  Side length: radius * 2 (e.g. radius=15 → 30x30 square)
 *
 *  Particle motion: columns rise from Y=0 to Y=WALL_HEIGHT
 *    Each tick, a cursor advances along the perimeter.
 *    At each perimeter position, a particle is spawned at a Y that
 *    cycles upward — creating a "wall rising" visual effect.
 *
 *  Performance: POINTS_PER_TICK particles per tick regardless of radius.
 *  The perimeter is divided into PERIMETER_STEPS virtual positions.
 *  Every tick advances by POINTS_PER_TICK steps, wrapping around.
 *
 *  Warning zone: WARN_SHRINK blocks inside the border triggers ActionBar.
 * ══════════════════════════════════════════════════════════════════════
 */
public class RadiusTask extends BukkitRunnable {

    // ── Visual constants ──────────────────────────────────────────────────────
    private static final int    POINTS_PER_TICK  = 6;       // particles per tick
    private static final int    PERIMETER_STEPS  = 120;     // virtual points on the square edge
    private static final double WALL_HEIGHT      = 4.0;     // how tall the wall appears (blocks)
    private static final double Y_STEP           = 0.4;     // vertical increment per tick cycle
    private static final double WARN_SHRINK      = 3.0;     // blocks inside border for warning
    private static final float  DUST_SIZE        = 1.6f;

    // ── Fields ────────────────────────────────────────────────────────────────
    private final GameArena arena;
    private final double    half;        // radius (half-side of the square)
    private final double    halfSq;      // half² for distance check (circular check still used)
    private final double    warnHalf;    // warning zone half-width
    private final Particle  particle;

    private int    perimeterCursor = 0;  // advances each tick
    private double yPhase          = 0;  // cycles 0 → WALL_HEIGHT, then resets

    public RadiusTask(EscoltaCorePlugin plugin, GameArena arena, double radius, Particle particle) {
        this.arena    = arena;
        this.half     = radius;
        // We keep the enforcement circular (distanceSquared) — radius = half-diagonal of square
        // so the square border is inscribed in the circular kill zone. Feels fair.
        this.halfSq   = radius * radius;
        double warnR  = Math.max(1.0, radius - WARN_SHRINK);
        this.warnHalf = warnR;
        this.particle = particle;
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

        // ── Advance Y phase ─────────────────────────────────────────────────
        yPhase += Y_STEP;
        if (yPhase > WALL_HEIGHT) yPhase = 0;

        // ── Draw square border ──────────────────────────────────────────────
        for (int i = 0; i < POINTS_PER_TICK; i++) {
            perimeterCursor = (perimeterCursor + 1) % PERIMETER_STEPS;
            Location loc = perimeterToLocation(center, perimeterCursor);
            if (loc != null) spawnParticle(world, loc);
        }

        // ── Check defenders ─────────────────────────────────────────────────
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!arena.isPlayerInGame(p)) continue;
            if (p.getUniqueId().equals(arena.getEscortId())) continue;
            if (p.isDead()) continue;

            boolean diffWorld = !p.getWorld().equals(world);
            double  distSq    = diffWorld ? Double.MAX_VALUE
                                          : p.getLocation().distanceSquared(center);

            if (diffWorld || distSq > halfSq) {
                p.setHealth(0);
                arena.endGame(false,
                        MessageUtils.get("defeat-out-of-bounds").replace("%player%", p.getName()));
                cancel();
                return;
            }
            if (distSq > warnHalf * warnHalf) {
                p.sendActionBar(MessageUtils.component(MessageUtils.get("radius-warning")));
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 2f);
                danger = true;
            }
        }

        if (danger) escort.addPotionEffect(
                new PotionEffect(PotionEffectType.GLOWING, 10, 0, false, false));
    }

    // ── Square perimeter mapping ───────────────────────────────────────────────

    /**
     * Maps a step index (0..PERIMETER_STEPS-1) to a world Location on the square edge.
     *
     * Square has 4 sides, each assigned PERIMETER_STEPS/4 virtual points.
     * The Y coordinate rises with yPhase — particles start at ground and climb upward,
     * creating a continuous "wall rising" animation.
     *
     * Side assignment:
     *   0..29   → North side  (Z = center.z - half, X varies)
     *   30..59  → East side   (X = center.x + half, Z varies)
     *   60..89  → South side  (Z = center.z + half, X varies reversed)
     *   90..119 → West side   (X = center.x - half, Z varies reversed)
     */
    private Location perimeterToLocation(Location center, int step) {
        int stepsPerSide = PERIMETER_STEPS / 4;
        int side         = step / stepsPerSide;
        int indexOnSide  = step % stepsPerSide;

        // t goes 0.0 → 1.0 along the side
        double t    = (double) indexOnSide / stepsPerSide;
        double span = half * 2.0;

        double x, z;
        switch (side) {
            case 0 -> { // North: X from -half to +half, Z = -half
                x = center.getX() - half + t * span;
                z = center.getZ() - half;
            }
            case 1 -> { // East: X = +half, Z from -half to +half
                x = center.getX() + half;
                z = center.getZ() - half + t * span;
            }
            case 2 -> { // South: X from +half to -half, Z = +half
                x = center.getX() + half - t * span;
                z = center.getZ() + half;
            }
            case 3 -> { // West: X = -half, Z from +half to -half
                x = center.getX() - half;
                z = center.getZ() + half - t * span;
            }
            default -> { return null; }
        }

        // Y rises from ground level upward, cycling continuously
        double y = center.getY() + yPhase;

        return new Location(center.getWorld(), x, y, z);
    }

    // ── Particle spawning ─────────────────────────────────────────────────────

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
