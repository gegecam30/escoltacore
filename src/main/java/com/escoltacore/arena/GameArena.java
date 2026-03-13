package com.escoltacore.arena;

import com.escoltacore.EscoltaCorePlugin;
import com.escoltacore.managers.ItemManager;
import com.escoltacore.tasks.RadiusTask;
import com.escoltacore.utils.ItemBuilder;
import com.escoltacore.utils.MessageUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.*;

/**
 * Representa una sala de juego (arena) de EscoltaCore.
 *
 * Cambios para 1.21.1:
 *  - Particle por defecto: DUST (antes REDSTONE)
 *  - getMessageList() ahora usa MessageUtils.getList() correctamente
 *  - sendTitle() usa Title.Times con Adventure Duration
 */
public class GameArena {

    private final EscoltaCorePlugin plugin;
    private final String name;
    private final UUID ownerId;
    private final ItemManager itemManager;

    // ── Estado ────────────────────────────────────────────────────────────────
    private GameState state;
    private UUID escoltadoId;
    private Material targetItem;
    private final Set<UUID> players = new HashSet<>();
    private RadiusTask radiusTask;

    // ── Configuración ─────────────────────────────────────────────────────────
    private double radius = 15.0;
    private Particle particle = Particle.DUST; // ← era REDSTONE en 1.20, DUST en 1.21

    // ── Constructor ───────────────────────────────────────────────────────────

    public GameArena(EscoltaCorePlugin plugin, String name, UUID ownerId) {
        this.plugin = plugin;
        this.name = name;
        this.ownerId = ownerId;
        this.state = GameState.WAITING;
        this.itemManager = new ItemManager(plugin);
    }

    // ── Gestión de jugadores ──────────────────────────────────────────────────

    public boolean addPlayer(Player p) {
        if (state != GameState.WAITING) {
            MessageUtils.send(p, "arena-already-in");
            return false;
        }
        players.add(p.getUniqueId());

        String msg = MessageUtils.get("arena-join")
                .replace("%player%", p.getName())
                .replace("%count%", String.valueOf(players.size()));
        broadcastRaw(msg);

        giveLobbyItems(p);
        return true;
    }

    public void removePlayer(Player p) {
        players.remove(p.getUniqueId());
        p.getInventory().clear();

        String msg = MessageUtils.get("arena-leave").replace("%player%", p.getName());
        broadcastRaw(msg);

        if (state == GameState.RUNNING && players.size() < 2) {
            forceStop();
        }
    }

    // ── Ítems del lobby ───────────────────────────────────────────────────────

    public void giveLobbyItems(Player p) {
        p.getInventory().clear();

        // Selector de clases (para todos)
        ItemStack classSelector = new ItemBuilder(Material.NETHER_STAR)
                .name(MessageUtils.get("items.class-selector.name"))
                .lore(MessageUtils.getList("items.class-selector.lore").toArray(new String[0]))
                .build();
        p.getInventory().setItem(4, classSelector);

        // Herramienta de configuración (solo el dueño)
        if (p.getUniqueId().equals(ownerId)) {
            ItemStack configTool = new ItemBuilder(Material.COMPARATOR)
                    .name(MessageUtils.get("items.config-tool.name"))
                    .lore(MessageUtils.getList("items.config-tool.lore").toArray(new String[0]))
                    .build();
            p.getInventory().setItem(0, configTool);
        }
    }

    // ── Lógica de juego ───────────────────────────────────────────────────────

    public void start(Player initiator) {
        if (!initiator.getUniqueId().equals(ownerId)
                && !initiator.hasPermission("escoltacore.admin")) {
            MessageUtils.send(initiator, "no-permission");
            return;
        }

        int minPlayers = plugin.getConfig().getInt("game-loop.min-players", 2);
        if (players.size() < minPlayers) {
            MessageUtils.send(initiator, "arena-min-players",
                    "%min%", String.valueOf(minPlayers));
            return;
        }

        this.state = GameState.STARTING;

        // Elegir escoltado al azar
        List<UUID> playerList = new ArrayList<>(players);
        Collections.shuffle(playerList);
        this.escoltadoId = playerList.get(0);
        this.targetItem = itemManager.getRandomTarget();

        Player escoltadoPlayer = Bukkit.getPlayer(escoltadoId);
        if (escoltadoPlayer == null) { forceStop(); return; }

        Location startLoc = escoltadoPlayer.getLocation();

        // Preparar a cada jugador
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;

            p.teleport(startLoc);
            p.getInventory().clear();
            p.setHealth(20);
            p.setFoodLevel(20);
            p.setGameMode(GameMode.SURVIVAL);

            if (p.getUniqueId().equals(escoltadoId)) {
                sendTitle(p, "game-intro-title", "game-intro-subtitle");
            } else {
                sendTitle(p, "defender-intro-title", "defender-intro-subtitle");
            }
        }

        this.state = GameState.RUNNING;

        String itemName = targetItem.name().replace("_", " ");
        broadcastRaw("&7=================================");
        broadcastRaw(MessageUtils.get("objective-broadcast").replace("%item%", itemName));
        broadcastRaw(MessageUtils.get("escort-broadcast").replace("%player%", escoltadoPlayer.getName()));
        broadcastRaw("&7=================================");

        this.radiusTask = new RadiusTask(plugin, this, radius, particle);
        this.radiusTask.runTaskTimer(plugin, 0L, 1L);
    }

    public void endGame(boolean victory, String triggerName) {
        if (state != GameState.RUNNING) return;
        if (radiusTask != null) radiusTask.cancel();
        this.state = GameState.ENDING;

        if (victory) {
            String title    = MessageUtils.get("victory-title");
            String subtitle = MessageUtils.get("victory-subtitle").replace("%player%", triggerName);
            broadcastTitle(title, subtitle);
            playSoundGlobal(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        } else {
            String title    = MessageUtils.get("defeat-title");
            String subtitle = MessageUtils.get("defeat-subtitle");
            broadcastTitle(title, subtitle);
            playSoundGlobal(Sound.ENTITY_WITHER_DEATH, 1f, 0.5f);
        }

        Bukkit.getScheduler().runTaskLater(plugin, this::softReset, 100L);
    }

    private void softReset() {
        if (radiusTask != null) { radiusTask.cancel(); radiusTask = null; }
        this.state = GameState.WAITING;
        this.escoltadoId = null;
        this.targetItem = null;

        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.setHealth(20);
                p.setFoodLevel(20);
                p.getInventory().clear();
                giveLobbyItems(p);
            }
        }
        broadcastRaw(MessageUtils.get("arena-wait-reset"));
    }

    public void forceStop() {
        if (radiusTask != null) { radiusTask.cancel(); radiusTask = null; }
        this.state = GameState.WAITING;

        for (UUID uuid : new HashSet<>(players)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.getInventory().clear();
                p.setHealth(20);
                p.setFoodLevel(20);
            }
        }
        players.clear();
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    private void broadcastRaw(String msg) {
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) MessageUtils.sendRaw(p, msg);
        }
    }

    private void broadcastTitle(String titleStr, String subtitleStr) {
        Component titleComp    = MessageUtils.component(titleStr);
        Component subtitleComp = MessageUtils.component(subtitleStr);

        Title.Times times = Title.Times.times(
                Duration.ofMillis(500),   // fade in
                Duration.ofSeconds(3),    // stay
                Duration.ofMillis(500)    // fade out
        );
        Title title = Title.title(titleComp, subtitleComp, times);

        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.showTitle(title);
        }
    }

    private void sendTitle(Player p, String titleKey, String subKey) {
        Title.Times times = Title.Times.times(
                Duration.ofMillis(300),
                Duration.ofSeconds(2),
                Duration.ofMillis(500)
        );
        p.showTitle(Title.title(
                MessageUtils.componentFromKey(titleKey),
                MessageUtils.componentFromKey(subKey),
                times
        ));
    }

    private void playSoundGlobal(Sound s, float volume, float pitch) {
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.playSound(p.getLocation(), s, volume, pitch);
        }
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public String getName()                    { return name; }
    public UUID getOwnerId()                   { return ownerId; }
    public GameState getState()                { return state; }
    public UUID getEscoltadoId()               { return escoltadoId; }
    public Material getTargetItem()            { return targetItem; }
    public double getRadius()                  { return radius; }
    public void setRadius(double r)            { this.radius = r; }
    public Particle getParticle()              { return particle; }
    public void setParticle(Particle p)        { this.particle = p; }
    public boolean isPlayerInGame(Player p)    { return players.contains(p.getUniqueId()); }
    public int getPlayerCount()                { return players.size(); }
}
