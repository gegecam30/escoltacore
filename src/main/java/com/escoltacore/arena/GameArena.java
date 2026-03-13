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

    // ── Config (mutable so reload can update them) ────────────────────────────
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

    // BossBars — one for lobby (waiting), one for in-game (objective)
    private BossBar lobbyBar;
    private BossBar gameBar;

    // Play Again vote — populated during ENDING phase
    private final Set<UUID> votePlayAgain = new HashSet<>();
    private final Set<UUID> voteLeave     = new HashSet<>();

    // PDC key for the objective display item in slot 8 during game
    public static final NamespacedKey OBJECTIVE_KEY =
            new NamespacedKey("escoltacore", "objective_display");
    // PDC key for the leave item in slot 8 during lobby
    public static final NamespacedKey LEAVE_KEY =
            new NamespacedKey("escoltacore", "leave_item");

    private final ItemManager itemManager;

    // ── Constructors ──────────────────────────────────────────────────────────

    public GameArena(EscoltaCorePlugin plugin, String name, int maxPlayers, Location spawnPoint) {
        this.plugin      = plugin;
        this.name        = name;
        this.ownerId     = null;
        this.publicArena = true;
        this.maxPlayers  = maxPlayers;
        this.spawnPoint  = spawnPoint;
        this.itemManager = new ItemManager(plugin);
    }

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

        // Show existing lobby bossbar to new player, then refresh text
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

        // Hide both bars from leaving player
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

        // Slot 8 — Leave item (Redstone Dust)
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
        // Destroy old bar first so it always reflects current state
        destroyLobbyBar();
        String text = MessageUtils.get("lobby-bossbar")
                .replace("%count%", String.valueOf(players.size()))
                .replace("%max%",   String.valueOf(maxPlayers));
        lobbyBar = BossBar.bossBar(
                MessageUtils.component(text),
                players.isEmpty() ? 0f : (float) players.size() / maxPlayers,
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

        // Destroy lobby bar — game bar takes over
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
                giveObjectiveItem(p);
            }
        }

        state = GameState.RUNNING;
        broadcastObjective();
        spawnGameBar();

        radiusTask = new RadiusTask(plugin, this, radius, particle);
        radiusTask.runTaskTimer(plugin, 0L, 1L);
    }

    public void start() { start(null); }

    // ── Objective display item (slot 8, defenders only) ───────────────────────

    private void giveObjectiveItem(Player p) {
        String itemName = formatItemName(targetItem);

        List<String> loreLines = MessageUtils.getList("items.objective-display.lore")
                .stream()
                .map(line -> line.replace("%item%", itemName))
                .toList();

        ItemStack obj = new ItemBuilder(Material.KNOWLEDGE_BOOK)
                .name(MessageUtils.get("items.objective-display.name"))
                .lore(loreLines.toArray(new String[0]))
                .glow()
                .tag(OBJECTIVE_KEY)
                .build();
        p.getInventory().setItem(8, obj);
    }

    // ── Broadcast ─────────────────────────────────────────────────────────────

    private void broadcastObjective() {
        String itemName     = formatItemName(targetItem);
        Player escortPlayer = Bukkit.getPlayer(escortId);
        String escortName   = escortPlayer != null ? escortPlayer.getName() : "?";

        broadcastRaw(MessageUtils.get("objective-chat"));
        broadcastRaw(MessageUtils.get("objective-chat-item").replace("%item%", itemName));
        broadcastRaw(MessageUtils.get("objective-chat-target").replace("%player%", escortName));
        broadcastRaw(MessageUtils.get("objective-chat-end"));
    }

    // ── Game BossBar ──────────────────────────────────────────────────────────

    private void spawnGameBar() {
        String title = MessageUtils.get("objective-bossbar")
                .replace("%item%", formatItemName(targetItem));
        gameBar = BossBar.bossBar(
                MessageUtils.component(title),
                1.0f,
                BossBar.Color.BLUE,
                BossBar.Overlay.PROGRESS
        );
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

        // Record stats
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

        // Start Play Again vote after 2s (give players time to see the title)
        Bukkit.getScheduler().runTaskLater(plugin, this::startVote, 40L);
    }

    // ── Play Again vote ───────────────────────────────────────────────────────

    private void startVote() {
        if (state != GameState.ENDING) return;
        votePlayAgain.clear();
        voteLeave.clear();

        // Build clickable message
        Component voteMsg = LegacyComponentSerializer.legacyAmpersand()
                .deserialize(MessageUtils.get("vote-title"))
                .append(Component.text(" "))
                .append(LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(MessageUtils.get("vote-play-again"))
                        .clickEvent(ClickEvent.runCommand("/escolta user voteplay " + name))
                        .hoverEvent(HoverEvent.showText(Component.text("Play Again!"))))
                .append(Component.text(" "))
                .append(LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(MessageUtils.get("vote-leave"))
                        .clickEvent(ClickEvent.runCommand("/escolta user voteleave " + name))
                        .hoverEvent(HoverEvent.showText(Component.text("Leave"))));

        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage(voteMsg);
        }

        // Auto-resolve after 15 seconds
        Bukkit.getScheduler().runTaskLater(plugin, this::resolveVote, 20L * 15);
    }

    public void castVote(Player p, boolean playAgain) {
        if (state != GameState.ENDING) return;
        if (!players.contains(p.getUniqueId())) return;

        // Remove from opposite bucket first
        if (playAgain) { voteLeave.remove(p.getUniqueId());     votePlayAgain.add(p.getUniqueId()); }
        else            { votePlayAgain.remove(p.getUniqueId()); voteLeave.add(p.getUniqueId()); }

        String status = MessageUtils.get("vote-status")
                .replace("%play%",  String.valueOf(votePlayAgain.size()))
                .replace("%leave%", String.valueOf(voteLeave.size()))
                .replace("%total%", String.valueOf(players.size()));
        broadcastRaw(status);

        // Resolve early if everyone voted
        if (votePlayAgain.size() + voteLeave.size() >= players.size()) {
            resolveVote();
        }
    }

    private void resolveVote() {
        if (state != GameState.ENDING) return; // already resolved

        boolean restart = votePlayAgain.size() >= voteLeave.size()
                && !votePlayAgain.isEmpty();

        votePlayAgain.clear();
        voteLeave.clear();

        if (restart) {
            broadcastRaw(MessageUtils.get("vote-restart"));
            softReset();
        } else {
            broadcastRaw(MessageUtils.get("vote-no-restart"));
            // Kick everyone — they chose to leave (or nobody voted)
            softReset();
            // Kick all players out of the arena after reset
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (UUID uuid : new HashSet<>(players)) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        plugin.getArenaManager().leaveArena(p);
                    }
                }
            }, 20L);
        }
    }

    // ── Soft reset (back to WAITING, same player list) ────────────────────────

    private void softReset() {
        // Clear all game state
        state      = GameState.WAITING;
        escortId   = null;
        targetItem = null;
        votePlayAgain.clear();
        voteLeave.clear();

        // Clear any lingering bars
        clearGameBar();

        // Restore players to lobby state
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

        // Restore lobby bossbar
        initLobbyBar();

        broadcastRaw(MessageUtils.get("arena-wait-reset"));

        // Public arena: if already full after reset (Play Again case), restart countdown
        // Private arena: owner starts manually via the config comparator
        if (publicArena && players.size() >= maxPlayers) {
            startCountdown();
        }
    }

    // ── Force stop (hard shutdown) ────────────────────────────────────────────

    public void forceStop() {
        if (radiusTask    != null) { radiusTask.cancel();    radiusTask    = null; }
        if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
        clearGameBar();
        destroyLobbyBar();
        state = GameState.WAITING;
        votePlayAgain.clear();
        voteLeave.clear();
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

    private void broadcastTitle(String titleStr, String subStr) {
        Title title = Title.title(
                MessageUtils.component(titleStr),
                MessageUtils.component(subStr),
                Title.Times.times(
                        Duration.ofMillis(400), Duration.ofSeconds(3), Duration.ofMillis(600))
        );
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.showTitle(title);
        }
    }

    private void sendTitle(Player p, String titleKey, String subKey) {
        p.showTitle(Title.title(
                MessageUtils.componentFromKey(titleKey),
                MessageUtils.componentFromKey(subKey),
                Title.Times.times(
                        Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(500))
        ));
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
        for (String word : m.name().toLowerCase().replace('_', ' ').split(" ")) {
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public String    getName()                { return name; }
    public UUID      getOwnerId()             { return ownerId; }
    public boolean   isPublic()               { return publicArena; }
    public GameState getState()               { return state; }
    public UUID      getEscortId()            { return escortId; }
    public Material  getTargetItem()          { return targetItem; }
    public double    getRadius()              { return radius; }
    public void      setRadius(double r)      { this.radius = r; }
    public Particle  getParticle()            { return particle; }
    public void      setParticle(Particle p)  { this.particle = p; }
    public int       getMaxPlayers()          { return maxPlayers; }
    public void      setMaxPlayers(int m)     { this.maxPlayers = m; updateLobbyBar(); }
    public Location  getSpawnPoint()          { return spawnPoint; }
    public void      setSpawnPoint(Location l){ this.spawnPoint = l; }
    public boolean   isPlayerInGame(Player p) { return players.contains(p.getUniqueId()); }
    public int       getPlayerCount()         { return players.size(); }
    public Set<UUID> getPlayers()             { return Collections.unmodifiableSet(players); }
    public EscoltaCorePlugin getPlugin()      { return plugin; }
}