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
 * Tarea periódica que:
 *  1. Dibuja el anillo de partículas alrededor del Escoltado.
 *  2. Verifica si algún Defensor sale del radio.
 *  3. Aplica advertencia visual/sonora en la zona de peligro.
 *
 * Compatible con Paper 1.21.1:
 *  - Particle.DUST        (antes REDSTONE)
 *  - Particle.HAPPY_VILLAGER (antes VILLAGER_HAPPY)
 *  - Particle.WITCH       (antes SPELL_WITCH)
 */
public class RadiusTask extends BukkitRunnable {

    private final GameArena arena;
    private final double radiusSquared;
    private final double warningSquared;
    private final double radius;
    private final Particle particle;

    private double angle = 0;

    public RadiusTask(EscoltaCorePlugin plugin, GameArena arena, double radius, Particle particle) {
        this.arena = arena;
        this.radius = radius;
        this.radiusSquared = radius * radius;
        this.particle = particle;

        // Zona de advertencia: 3 bloques antes del límite
        double warningRadius = Math.max(1.0, radius - 3.0);
        this.warningSquared = warningRadius * warningRadius;
    }

    @Override
    public void run() {
        if (arena.getEscoltadoId() == null) {
            this.cancel();
            return;
        }

        Player escoltado = Bukkit.getPlayer(arena.getEscoltadoId());
        if (escoltado == null || !escoltado.isOnline()) {
            arena.endGame(false, "Target disconnected");
            this.cancel();
            return;
        }

        Location center = escoltado.getLocation();
        boolean dangerMode = false;

        // ── 1. DIBUJAR PARTÍCULAS ─────────────────────────────────────────────
        for (int i = 0; i < 20; i++) {
            angle += Math.PI / 40;
            double x = radius * Math.cos(angle);
            double z = radius * Math.sin(angle);
            Location loc = center.clone().add(x, 0.5, z);

            spawnParticle(escoltado, loc);
        }

        // ── 2. VERIFICAR JUGADORES ────────────────────────────────────────────
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!arena.isPlayerInGame(p)) continue;
            if (p.getUniqueId().equals(arena.getEscoltadoId())) continue;
            if (p.isDead()) continue;

            boolean differentWorld = !p.getWorld().equals(escoltado.getWorld());
            double distSq = differentWorld ? Double.MAX_VALUE
                    : p.getLocation().distanceSquared(center);

            // CASO 1: Fuera del límite → muerte inmediata
            if (differentWorld || distSq > radiusSquared) {
                p.setHealth(0);
                arena.endGame(false,
                        MessageUtils.get("defeat-out-of-bounds").replace("%player%", p.getName()));
                this.cancel();
                return;
            }

            // CASO 2: Zona de advertencia (cerca del borde)
            if (distSq > warningSquared) {
                p.sendActionBar(MessageUtils.component(MessageUtils.get("radius-warning")));
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 2f);
                dangerMode = true;
            }
        }

        // ── 3. GLOWING AL ESCOLTADO SI HAY PELIGRO ───────────────────────────
        if (dangerMode) {
            escoltado.addPotionEffect(
                    new PotionEffect(PotionEffectType.GLOWING, 10, 0, false, false));
        }
    }

    /**
     * Maneja el spawn de partículas con compatibilidad 1.21.1.
     *
     * Cambios de nombres en 1.21:
     *   REDSTONE        → DUST
     *   VILLAGER_HAPPY  → HAPPY_VILLAGER
     *   SPELL_WITCH     → WITCH
     */
    private void spawnParticle(Player escoltado, Location loc) {
        World world = escoltado.getWorld();

        switch (particle) {
            // DUST es la partícula con color configurable (ex-REDSTONE)
            case DUST -> {
                // DustOptions: color + tamaño
                Particle.DustOptions dust =
                        new Particle.DustOptions(Color.fromRGB(0, 255, 255), 1.5f);
                world.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, dust);
            }

            // Partículas sin datos extra — spawn directo
            case FLAME, HAPPY_VILLAGER, HEART, NOTE, WITCH -> {
                world.spawnParticle(particle, loc, 1, 0, 0, 0, 0);
            }

            // Fallback genérico por si se configura una partícula sin datos
            default -> {
                try {
                    world.spawnParticle(particle, loc, 1, 0, 0, 0, 0);
                } catch (Exception ignored) {
                    // Si la partícula requiere datos que no tenemos, usamos DUST como fallback
                    world.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.WHITE, 1.0f));
                }
            }
        }
    }
}
