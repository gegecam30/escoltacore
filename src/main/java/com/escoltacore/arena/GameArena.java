package com.escoltacore.arena;

import com.escoltacore.EscoltaCorePlugin;
import com.escoltacore.managers.ItemManager;
import com.escoltacore.tasks.CountdownTask;
import com.escoltacore.tasks.RadiusTask;
import com.escoltacore.utils.ItemBuilder;
import com.escoltacore.utils.MessageUtils;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.*;

public class GameArena {

    // ── Identity ──────────────────────────────────────────────────────────────
    private final EscoltaCorePlugin plugin;
    private final String name;
    private final UUID ownerId;
    private final boolean publicArena;

    // ── Config (mutable — updated by reload) ─────────────────────────────────
    private int maxPlayers;
    private double radius = 15.0;
    private Particle particle = Particle.DUST;
    private Location spawnPoint;

    // ── Runtime state ─────────────────────────────────────────────────────────
    private GameState state = GameState.WAITING;
    private UUID escortId;
    private Material targetItem;
    private final Set<UUID> players = new LinkedHashSet<>();

    // Tasks
    private RadiusTask    radiusTask;
    private CountdownTask countdownTask;

    // BossBars
    private BossBar lobbyBar;
    private BossBar gameBar;

    // Play Again vote
    private final Set<UUID> votePlayAgain = new HashSet<>();
    private final Set<UUID> voteLeave     = new HashSet<>();

    // PDC keys (shared with GameListener for item protection)
    public static final NamespacedKey OBJECTIVE_KEY =
            new NamespacedKey("escoltacore", "objective_display");
    public static final NamespacedKey LEAVE_KEY =
            new NamespacedKey("escoltacore", "leave_item");

    private final ItemManager itemManager;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Public arena (admin-created, persists in arenas.yml) */
    public GameArena(EscoltaCorePlugin plugin, String name, int maxPlayers, Location spawnPoint) {
        this.plugin      = plugin;
        this.name        = name;
        this.ownerId     = null;
        this.publicArena = true;
        this.maxPlayers  = maxPlayers;
        this.spawnPoint  = spawnPoint;
        this.itemManager = new ItemManager(plugin);
    }

    /** Private lobby (player-created, deleted when owner leaves) */
    public GameArena(EscoltaCorePlugin plugin, String name, UUID ownerId, int maxPlayers) {
        this.plugin      = plugin;
        this.name        = name;
        this.ownerId     = ownerId;
        this.publicArena = false;
        this.maxPlayers  = maxPlayers;
        this.itemManager = new ItemManager(plugin);
    }

    // ── Player management ─────────────────────────────────────────────────────

    public boolean addPlayer(Player p) {
        if (state != GameState.WAITING && state != GameState.STARTING) {
            MessageUtils.send(p, "arena-in-progress", "%name%", name);
            return false;
        }
        if (players.size() >= maxPlayers) {
            String msg = MessageUtils.get("arena-full")
                    .replace("%name%",  name)
                    .replace("%count%", String.valueOf(players.size()))
                    .replace("%max%",   String.valueOf(maxPlayers));
            MessageUtils.sendRaw(p, msg);
            return false;
        }

        players.add(p.getUniqueId());
        giveLobbyItems(p);

        if (lobbyBar != null) p.showBossBar(lobbyBar);
        updateLobbyBar();

        String msg = MessageUtils.get("arena-join")
                .replace("%player%", p.getName())
                .replace("%count%",  String.valueOf(players.size()))
                .replace("%max%",    String.valueOf(maxPlayers));
        broadcastRaw(msg);

        if (publicArena && players.size() >= maxPlayers && state == GameState.WAITING) {
            startCountdown();
        }
        return true;
    }

    public void removePlayer(Player p) {
        players.remove(p.getUniqueId());
        p.getInventory().clear();

        if (lobbyBar != null) p.hideBossBar(lobbyBar);
        if (gameBar  != null) p.hideBossBar(gameBar);

        updateLobbyBar();

        String msg = MessageUtils.get("arena-leave").replace("%player%", p.getName());
        broadcastRaw(msg);

        int min = plugin.getConfig().getInt("game-loop.min-players", 2);
        if (state == GameState.RUNNING  && players.size() < min) forceStop();
        if (state == GameState.STARTING && players.size() < min) cancelCountdown();
    }

    // ── Lobby items ───────────────────────────────────────────────────────────

    public void giveLobbyItems(Player p) {
        p.getInventory().clear();

        // Slot 4 — Class selector
        ItemStack classSelector = new ItemBuilder(Material.NETHER_STAR)
                .name(MessageUtils.get("items.class-selector.name"))
                .lore(MessageUtils.getList("items.class-selector.lore").toArray(new String[0]))
                .build();
        p.getInventory().setItem(4, classSelector);

        // Slot 8 — Leave item
        ItemStack leaveItem = new ItemBuilder(Material.REDSTONE)
                .name(MessageUtils.get("items.leave-item.name"))
                .lore(MessageUtils.getList("items.leave-item.lore").toArray(new String[0]))
                .tag(LEAVE_KEY)
                .build();
        p.getInventory().setItem(8, leaveItem);

        // Slot 0 — Config tool (private lobby owner only)
        if (!publicArena && p.getUniqueId().equals(ownerId)) {
            ItemStack configTool = new ItemBuilder(Material.COMPARATOR)
                    .name(MessageUtils.get("items.config-tool.name"))
                    .lore(MessageUtils.getList("items.config-tool.lore").toArray(new String[0]))
                    .build();
            p.getInventory().setItem(0, configTool);
        }
    }

    // ── Lobby BossBar ─────────────────────────────────────────────────────────

    public void initLobbyBar() {
        destroyLobbyBar(); // always rebuild fresh
        String text = MessageUtils.get("lobby-bossbar")
                .replace("%count%", String.valueOf(players.size()))
                .replace("%max%",   String.valueOf(maxPlayers));
        lobbyBar = BossBar.bossBar(
                MessageUtils.component(text),
                players.isEmpty() ? 0f : Math.min(1f, (float) players.size() / maxPlayers),
                BossBar.Color.YELLOW,
                BossBar.Overlay.PROGRESS
        );
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.showBossBar(lobbyBar);
        }
    }

    private void updateLobbyBar() {
        if (lobbyBar == null) return;
        String text = MessageUtils.get("lobby-bossbar")
                .replace("%count%", String.valueOf(players.size()))
                .replace("%max%",   String.valueOf(maxPlayers));
        lobbyBar.name(MessageUtils.component(text));
        lobbyBar.progress(players.isEmpty() ? 0f
                : Math.min(1f, (float) players.size() / maxPlayers));
    }

    private void destroyLobbyBar() {
        if (lobbyBar == null) return;
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.hideBossBar(lobbyBar);
        }
        lobbyBar = null;
    }

    // ── Countdown ─────────────────────────────────────────────────────────────

    private void startCountdown() {
        if (state == GameState.STARTING) return;
        state = GameState.STARTING;
        int seconds = plugin.getConfig().getInt("game-loop.countdown", 10);
        countdownTask = new CountdownTask(plugin, this, seconds);
        countdownTask.runTaskTimer(plugin, 0L, 20L);
    }

    private void cancelCountdown() {
        if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
        state = GameState.WAITING;
    }

    // ── Start ─────────────────────────────────────────────────────────────────

    public void start(Player initiator) {
        if (!publicArena && initiator != null) {
            if (!initiator.getUniqueId().equals(ownerId)
                    && !initiator.hasPermission("escoltacore.admin")) {
                MessageUtils.send(initiator, "no-permission");
                return;
            }
        }
        int min = plugin.getConfig().getInt("game-loop.min-players", 2);
        if (players.size() < min) {
            if (initiator != null)
                MessageUtils.send(initiator, "arena-min-players", "%min%", String.valueOf(min));
            return;
        }
        if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
        destroyLobbyBar();

        state = GameState.STARTING;
        List<UUID> list = new ArrayList<>(players);
        Collections.shuffle(list);
        this.escortId   = list.get(0);
        this.targetItem = itemManager.getRandomTarget();

        Location spawn = resolveSpawn();
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            p.teleport(spawn);
            p.getInventory().clear();
            p.setHealth(20);
            p.setFoodLevel(20);
            p.setGameMode(GameMode.SURVIVAL);

            if (p.getUniqueId().equals(escortId)) {
                sendTitle(p, "game-intro-title", "game-intro-subtitle");
            } else {
                sendTitle(p, "defender-intro-title", "defender-intro-subtitle");
            }
            giveObjectiveItem(p); // todos reciben el mapa del objetivo
        }
        state = GameState.RUNNING;
        broadcastObjective();
        spawnGameBar();
        radiusTask = new RadiusTask(plugin, this, radius, particle);
        radiusTask.runTaskTimer(plugin, 0L, 1L);
    }

    public void start() { start(null); }

    // ── Objective display item ────────────────────────────────────────────────

    private void giveObjectiveItem(Player p) {
        String itemName    = formatItemName(targetItem);
        String displayName = MessageUtils.get("items.objective-display.name");
        List<String> loreLines = MessageUtils.getList("items.objective-display.lore")
                .stream().map(line -> line.replace("%item%", itemName)).toList();

        // Try sprite map first
        com.escoltacore.map.SpriteMapManager smm = plugin.getSpriteMapManager();
        ItemStack obj = null;
        if (smm != null && smm.hasSprite(targetItem)) {
            obj = smm.createMapItem(targetItem, name, displayName,
                    loreLines.toArray(new String[0]));
        }
        // Fallback to KNOWLEDGE_BOOK with PDC tag
        if (obj == null) {
            obj = new ItemBuilder(Material.KNOWLEDGE_BOOK)
                    .name(displayName)
                    .lore(loreLines.toArray(new String[0]))
                    .glow()
                    .tag(OBJECTIVE_KEY)
                    .build();
        }
        p.getInventory().setItem(8, obj);
    }

    // ── Broadcast helpers ─────────────────────────────────────────────────────

    private void broadcastObjective() {
        String itemName   = formatItemName(targetItem);
        Player escortP    = Bukkit.getPlayer(escortId);
        String escortName = escortP != null ? escortP.getName() : "?";
        broadcastRaw(MessageUtils.get("objective-chat"));
        broadcastRaw(MessageUtils.get("objective-chat-item").replace("%item%", itemName));
        broadcastRaw(MessageUtils.get("objective-chat-target").replace("%player%", escortName));
        broadcastRaw(MessageUtils.get("objective-chat-end"));
    }

    // ── Game BossBar ──────────────────────────────────────────────────────────

    private void spawnGameBar() {
        String title = MessageUtils.get("objective-bossbar")
                .replace("%item%", formatItemName(targetItem));
        gameBar = BossBar.bossBar(MessageUtils.component(title), 1.0f,
                BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.showBossBar(gameBar);
        }
    }

    private void clearGameBar() {
        if (gameBar == null) return;
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.hideBossBar(gameBar);
        }
        gameBar = null;
    }

    // ── End game ──────────────────────────────────────────────────────────────

    public void endGame(boolean victory, String triggerName) {
        if (state != GameState.RUNNING) return;
        if (radiusTask != null) { radiusTask.cancel(); radiusTask = null; }
        clearGameBar();
        state = GameState.ENDING;

        // Stats
        UUID winnerUuid = null;
        if (victory) {
            for (UUID uuid : players) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.getName().equals(triggerName)) { winnerUuid = uuid; break; }
            }
        }
        for (UUID uuid : players) {
            plugin.getDatabase().addGamePlayed(uuid);
            if (victory && uuid.equals(winnerUuid)) plugin.getDatabase().addGameWon(uuid);
            else plugin.getDatabase().addGameLost(uuid);
        }

        if (victory) {
            broadcastTitle(MessageUtils.get("victory-title"),
                    MessageUtils.get("victory-subtitle").replace("%player%", triggerName));
            playSoundGlobal(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        } else {
            broadcastTitle(MessageUtils.get("defeat-title"), MessageUtils.get("defeat-subtitle"));
            playSoundGlobal(Sound.ENTITY_WITHER_DEATH, 1f, 0.5f);
        }
        // Show vote after 2 s (players see the title first)
        Bukkit.getScheduler().runTaskLater(plugin, this::startVote, 40L);
    }

    // ── Play Again vote ───────────────────────────────────────────────────────

    private void startVote() {
        if (state != GameState.ENDING) return;
        votePlayAgain.clear();
        voteLeave.clear();

        var leg = LegacyComponentSerializer.legacyAmpersand();
        Component msg = leg.deserialize(MessageUtils.get("vote-title"))
                .append(Component.text(" "))
                .append(leg.deserialize(MessageUtils.get("vote-play-again"))
                        .clickEvent(ClickEvent.runCommand("/escolta user voteplay " + name))
                        .hoverEvent(HoverEvent.showText(Component.text("Vote: Play Again"))))
                .append(Component.text("  "))
                .append(leg.deserialize(MessageUtils.get("vote-leave"))
                        .clickEvent(ClickEvent.runCommand("/escolta user voteleave " + name))
                        .hoverEvent(HoverEvent.showText(Component.text("Vote: Leave"))));

        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage(msg);
        }
        // Auto-resolve after 15 s
        Bukkit.getScheduler().runTaskLater(plugin, this::resolveVote, 20L * 15);
    }

    public void castVote(Player p, boolean playAgain) {
        if (state != GameState.ENDING || !players.contains(p.getUniqueId())) return;
        if (playAgain) { voteLeave.remove(p.getUniqueId());     votePlayAgain.add(p.getUniqueId()); }
        else           { votePlayAgain.remove(p.getUniqueId()); voteLeave.add(p.getUniqueId()); }

        broadcastRaw(MessageUtils.get("vote-status")
                .replace("%play%",  String.valueOf(votePlayAgain.size()))
                .replace("%leave%", String.valueOf(voteLeave.size()))
                .replace("%total%", String.valueOf(players.size())));

        if (votePlayAgain.size() + voteLeave.size() >= players.size()) resolveVote();
    }

    private void resolveVote() {
        if (state != GameState.ENDING) return;
        boolean restart = !votePlayAgain.isEmpty()
                && votePlayAgain.size() >= voteLeave.size();
        votePlayAgain.clear();
        voteLeave.clear();

        if (restart) {
            broadcastRaw(MessageUtils.get("vote-restart"));
            softReset();
        } else {
            broadcastRaw(MessageUtils.get("vote-no-restart"));
            softReset();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (UUID uuid : new HashSet<>(players)) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) plugin.getArenaManager().leaveArena(p);
                }
            }, 20L);
        }
    }

    // ── Soft reset ────────────────────────────────────────────────────────────

    private void softReset() {
        state      = GameState.WAITING;
        escortId   = null;
        targetItem = null;
        votePlayAgain.clear();
        voteLeave.clear();

        // Free old MapViews before giving new lobby items
        if (plugin.getSpriteMapManager() != null)
            plugin.getSpriteMapManager().freeArenaMaps(name);

        clearGameBar();

        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.setHealth(20);
                p.setFoodLevel(20);
                p.setGameMode(GameMode.SURVIVAL);
                p.getInventory().clear();
                giveLobbyItems(p);
            }
        }

        initLobbyBar(); // always rebuilds fresh (destroys old one internally)
        broadcastRaw(MessageUtils.get("arena-wait-reset"));

        // Public + full → restart countdown (Play Again case)
        if (publicArena && players.size() >= maxPlayers) startCountdown();
    }

    // ── Force stop ────────────────────────────────────────────────────────────

    public void forceStop() {
        if (radiusTask    != null) { radiusTask.cancel();    radiusTask    = null; }
        if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
        clearGameBar();
        destroyLobbyBar();
        if (plugin.getSpriteMapManager() != null)
            plugin.getSpriteMapManager().freeArenaMaps(name);
        state = GameState.WAITING;
        votePlayAgain.clear();
        voteLeave.clear();
        for (UUID uuid : new HashSet<>(players)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) { p.getInventory().clear(); p.setHealth(20); p.setFoodLevel(20); }
        }
        players.clear();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Location resolveSpawn() {
        if (spawnPoint != null) return spawnPoint;
        Player escort = Bukkit.getPlayer(escortId);
        return escort != null ? escort.getLocation()
                : Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    private void broadcastRaw(String msg) {
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) MessageUtils.sendRaw(p, msg);
        }
    }

    private void broadcastTitle(String t, String s) {
        Title title = Title.title(MessageUtils.component(t), MessageUtils.component(s),
                Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(3), Duration.ofMillis(600)));
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.showTitle(title);
        }
    }

    private void sendTitle(Player p, String tKey, String sKey) {
        p.showTitle(Title.title(MessageUtils.componentFromKey(tKey), MessageUtils.componentFromKey(sKey),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(500))));
    }

    private void playSoundGlobal(Sound s, float vol, float pitch) {
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.playSound(p.getLocation(), s, vol, pitch);
        }
    }

    public static String formatItemName(Material m) {
        if (m == null) return "?";
        StringBuilder sb = new StringBuilder();
        for (String w : m.name().toLowerCase().replace('_', ' ').split(" "))
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        return sb.toString().trim();
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public String    getName()                 { return name; }
    public UUID      getOwnerId()              { return ownerId; }
    public boolean   isPublic()                { return publicArena; }
    public GameState getState()                { return state; }
    public UUID      getEscortId()             { return escortId; }
    public Material  getTargetItem()           { return targetItem; }
    public double    getRadius()               { return radius; }
    public void      setRadius(double r)       { this.radius = r; }
    public Particle  getParticle()             { return particle; }
    public void      setParticle(Particle p)   { this.particle = p; }
    public int       getMaxPlayers()           { return maxPlayers; }
    public void      setMaxPlayers(int m)      { this.maxPlayers = m; updateLobbyBar(); }
    public Location  getSpawnPoint()           { return spawnPoint; }
    public void      setSpawnPoint(Location l) { this.spawnPoint = l; }
    public boolean   isPlayerInGame(Player p)  { return players.contains(p.getUniqueId()); }
    public int       getPlayerCount()          { return players.size(); }
    public Set<UUID> getPlayers()              { return Collections.unmodifiableSet(players); }
    public EscoltaCorePlugin getPlugin()       { return plugin; }
}
