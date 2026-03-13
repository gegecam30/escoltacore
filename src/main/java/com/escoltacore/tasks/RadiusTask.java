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
 * Draws the border ring and enforces radius limits.
 * Particle names are correct for Paper 1.21.1:
 *   DUST (was REDSTONE), HAPPY_VILLAGER (was VILLAGER_HAPPY), WITCH (was SPELL_WITCH)
 */
public class RadiusTask extends BukkitRunnable {

    private final GameArena arena;
    private final double radiusSq;
    private final double warningSq;
    private final double radius;
    private final Particle particle;
    private double angle = 0;

    public RadiusTask(EscoltaCorePlugin plugin, GameArena arena, double radius, Particle particle) {
        this.arena = arena;
        this.radius = radius;
        this.radiusSq = radius * radius;
        this.particle = particle;
        double warnRadius = Math.max(1.0, radius - 3.0);
        this.warningSq = warnRadius * warnRadius;
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
        boolean danger = false;

        // Draw ring
        for (int i = 0; i < 20; i++) {
            angle += Math.PI / 40;
            Location loc = center.clone().add(
                    radius * Math.cos(angle), 0.5, radius * Math.sin(angle));
            spawnParticle(escort.getWorld(), loc);
        }

        // Check defenders
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!arena.isPlayerInGame(p)) continue;
            if (p.getUniqueId().equals(arena.getEscortId())) continue;
            if (p.isDead()) continue;

            boolean diff = !p.getWorld().equals(escort.getWorld());
            double dist = diff ? Double.MAX_VALUE : p.getLocation().distanceSquared(center);

            if (diff || dist > radiusSq) {
                p.setHealth(0);
                arena.endGame(false,
                        MessageUtils.get("defeat-out-of-bounds").replace("%player%", p.getName()));
                cancel();
                return;
            }
            if (dist > warningSq) {
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
            case DUST -> world.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(0, 255, 255), 1.5f));
            case FLAME, HAPPY_VILLAGER, HEART, NOTE, WITCH ->
                    world.spawnParticle(particle, loc, 1, 0, 0, 0, 0);
            default -> {
                try { world.spawnParticle(particle, loc, 1, 0, 0, 0, 0); }
                catch (Exception ignored) {
                    world.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.WHITE, 1.0f));
                }
            }
        }
    }
}
