package com.escoltacore.tasks;

import com.escoltacore.EscoltaCorePlugin;
import com.escoltacore.arena.GameArena;
import com.escoltacore.utils.MessageUtils;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class RadiusTask extends BukkitRunnable {

    private final GameArena arena;
    private final double radiusSquared;
    private final double warningSquared; // NUEVO: Zona de aviso
    private final double radius;
    private final Particle particle;
    
    private double angle = 0;

    public RadiusTask(EscoltaCorePlugin plugin, GameArena arena, double radius, Particle particle) {
        this.arena = arena;
        this.radius = radius;
        this.radiusSquared = radius * radius;
        this.particle = particle;
        
        // La advertencia empieza 3 bloques antes del borde final
        double warningRadius = Math.max(1.0, radius - 3.0);
        this.warningSquared = warningRadius * warningRadius;
    }

    @Override
    public void run() {
        if (arena.getEscoltadoId() == null) { this.cancel(); return; }
        Player escoltado = Bukkit.getPlayer(arena.getEscoltadoId());
        
        if (escoltado == null || !escoltado.isOnline()) {
            arena.endGame(false, "Target disconnected");
            return;
        }

        Location center = escoltado.getLocation();
        boolean dangerMode = false; // Para activar el glowing si ALGUIEN está en peligro

        // 1. DIBUJAR PARTÍCULAS
        for (int i = 0; i < 20; i++) {
            angle += Math.PI / 40;
            double x = radius * Math.cos(angle);
            double z = radius * Math.sin(angle);
            Location loc = center.clone().add(x, 0.5, z);
            
            if (particle == Particle.REDSTONE) {
                Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(0, 255, 255), 1.5f);
                escoltado.getWorld().spawnParticle(Particle.REDSTONE, loc, 1, dust);
            } else {
                escoltado.getWorld().spawnParticle(particle, loc, 1, 0, 0, 0, 0);
            }
        }

        // 2. VERIFICAR JUGADORES
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!arena.isPlayerInGame(p)) continue;
            if (p.getUniqueId().equals(arena.getEscoltadoId())) continue;
            if (p.isDead()) continue; 

            double distSq = p.getLocation().distanceSquared(center);
            boolean differentWorld = !p.getWorld().equals(escoltado.getWorld());

            // CASO 1: FUERA DEL LÍMITE (MUERTE)
            if (differentWorld || distSq > radiusSquared) {
                p.setHealth(0); 
                arena.endGame(false, MessageUtils.get("defeat-out-of-bounds").replace("%player%", p.getName()));
                this.cancel(); 
                return; 
            }
            
            // CASO 2: ZONA DE ADVERTENCIA (CERCA DEL BORDE)
            if (distSq > warningSquared) {
                // Enviar Alerta
                p.sendActionBar(MessageUtils.component(MessageUtils.get("radius-warning")));
                // Sonido de advertencia (tick agudo)
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 2f);
                dangerMode = true; // Activar glowing para el escoltado
            }
        }
        
        // SI ALGUIEN ESTÁ EN PELIGRO -> EL ESCOLTADO BRILLA
        if (dangerMode) {
            // Le damos Glowing por 1 segundo (se renueva cada tick si sigue el peligro)
            escoltado.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 10, 0, false, false));
        }
    }
}