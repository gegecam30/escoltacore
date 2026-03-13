package com.escoltacore.tasks;

import com.escoltacore.EscoltaCorePlugin;
import com.escoltacore.arena.GameArena;
import com.escoltacore.utils.MessageUtils;
import org.bukkit.scheduler.BukkitRunnable;

public class CountdownTask extends BukkitRunnable {

    private final EscoltaCorePlugin plugin;
    private final GameArena arena;
    private int secondsLeft;

    public CountdownTask(EscoltaCorePlugin plugin, GameArena arena, int seconds) {
        this.plugin = plugin;
        this.arena = arena;
        this.secondsLeft = seconds;
    }

    @Override
    public void run() {
        if (secondsLeft <= 0) {
            this.cancel();
            arena.start();
            return;
        }

        // Announce at 10, 5, 4, 3, 2, 1
        if (secondsLeft <= 5 || secondsLeft == 10) {
            String msg = MessageUtils.get("game-starting-countdown")
                    .replace("%seconds%", String.valueOf(secondsLeft));
            for (var uuid : arena.getPlayers()) {
                var p = org.bukkit.Bukkit.getPlayer(uuid);
                if (p != null) MessageUtils.sendRaw(p, msg);
            }
        }
        secondsLeft--;
    }
}
