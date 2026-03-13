package com.escoltacore.managers;

import com.escoltacore.EscoltaCorePlugin;
import com.escoltacore.arena.GameArena;
import com.escoltacore.utils.MessageUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class ArenaManager {

    private final EscoltaCorePlugin plugin;

    // arenaKey (lowercase name) → arena
    private final Map<String, GameArena> arenas = new LinkedHashMap<>();

    // playerUUID → arenaKey  — source of truth for "who is where"
    private final Map<UUID, String> playerArenaMap = new HashMap<>();

    // Pending invites: invitee UUID → invite data
    private final Map<UUID, InviteData> pendingInvites = new HashMap<>();

    // ── Persistence ───────────────────────────────────────────────────────────
    private File arenasFile;
    private FileConfiguration arenasConfig;

    public ArenaManager(EscoltaCorePlugin plugin) {
        this.plugin = plugin;
        loadPublicArenas();
    }

    // ── Public arena management (admin) ──────────────────────────────────────

    public boolean createPublicArena(String name) {
        if (arenas.containsKey(name.toLowerCase())) return false;
        int max = plugin.getConfig().getInt("game-loop.max-players-public", 8);
        GameArena arena = new GameArena(plugin, name, max, null);
        arenas.put(name.toLowerCase(), arena);
        arena.initLobbyBar();
        savePublicArenas();
        return true;
    }

    public boolean deletePublicArena(String name) {
        GameArena arena = arenas.get(name.toLowerCase());
        if (arena == null || !arena.isPublic()) return false;

        // Kick all players out cleanly before deleting
        for (UUID uuid : new HashSet<>(arena.getPlayers())) {
            playerArenaMap.remove(uuid);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.getInventory().clear();
                MessageUtils.send(p, "arena-deleted", "%name%", name);
            }
        }
        arena.forceStop();
        arenas.remove(name.toLowerCase());
        savePublicArenas();
        return true;
    }

    public boolean setSpawn(String arenaName, Location loc) {
        GameArena arena = arenas.get(arenaName.toLowerCase());
        if (arena == null || !arena.isPublic()) return false;
        arena.setSpawnPoint(loc);
        savePublicArenas();
        return true;
    }

    public boolean forceJoin(Player p, String arenaName) {
        GameArena arena = arenas.get(arenaName.toLowerCase());
        // Admin can force-join into ANY arena (public or private), not just public ones
        if (arena == null) return false;
        if (playerArenaMap.containsKey(p.getUniqueId())) return false;
        if (arena.addPlayer(p)) {
            playerArenaMap.put(p.getUniqueId(), arenaName.toLowerCase());
            return true;
        }
        return false;
    }

    public Set<String> getPublicArenaNames() {
        Set<String> names = new LinkedHashSet<>();
        for (Map.Entry<String, GameArena> e : arenas.entrySet()) {
            if (e.getValue().isPublic()) names.add(e.getKey());
        }
        return names;
    }

    // ── Private lobby management (user) ──────────────────────────────────────

    public void createPrivateLobby(Player creator, String name) {
        if (arenas.containsKey(name.toLowerCase())) {
            MessageUtils.send(creator, "lobby-exists");
            return;
        }
        if (playerArenaMap.containsKey(creator.getUniqueId())) {
            MessageUtils.send(creator, "arena-already-in");
            return;
        }
        int max = plugin.getConfig().getInt("game-loop.max-players-private", 5);
        GameArena arena = new GameArena(plugin, name, creator.getUniqueId(), max);
        arenas.put(name.toLowerCase(), arena);
        arena.initLobbyBar();

        if (arena.addPlayer(creator)) {
            playerArenaMap.put(creator.getUniqueId(), name.toLowerCase());
        }
        MessageUtils.send(creator, "lobby-created", "%name%", name);
    }

    // ── Joining ───────────────────────────────────────────────────────────────

    public void joinArena(Player p, String name) {
        if (playerArenaMap.containsKey(p.getUniqueId())) {
            MessageUtils.send(p, "arena-already-in");
            return;
        }
        GameArena arena = arenas.get(name.toLowerCase());
        if (arena == null) {
            MessageUtils.send(p, "arena-not-found", "%name%", name);
            return;
        }
        if (arena.addPlayer(p)) {
            playerArenaMap.put(p.getUniqueId(), name.toLowerCase());
        }
    }

    // ── Leaving ───────────────────────────────────────────────────────────────

    public void leaveArena(Player p) {
        String arenaKey = playerArenaMap.remove(p.getUniqueId());
        if (arenaKey == null) {
            MessageUtils.send(p, "arena-not-in");
            return;
        }
        GameArena arena = arenas.get(arenaKey);
        if (arena == null) return;

        arena.removePlayer(p);

        if (!arena.isPublic()) {
            // Private: if owner left, close for everyone
            if (p.getUniqueId().equals(arena.getOwnerId())) {
                for (UUID uuid : new HashSet<>(arena.getPlayers())) {
                    playerArenaMap.remove(uuid);
                    Player member = Bukkit.getPlayer(uuid);
                    if (member != null) member.getInventory().clear();
                }
                arena.forceStop();
                arenas.remove(arenaKey);
                MessageUtils.send(p, "arena-owner-left", "%name%", arena.getName());
            } else if (arena.getPlayerCount() == 0) {
                // Private and empty — remove
                arenas.remove(arenaKey);
            }
        }
        // Public arenas: NEVER removed here — they persist until admin deletes them
    }

    // ── Kick ─────────────────────────────────────────────────────────────────

    public void kickPlayer(Player owner, Player target) {
        GameArena arena = getArena(owner);
        if (arena == null || arena.isPublic()) { MessageUtils.send(owner, "arena-not-in"); return; }
        if (!arena.getOwnerId().equals(owner.getUniqueId())) { MessageUtils.send(owner, "kick-not-owner"); return; }
        if (owner.getUniqueId().equals(target.getUniqueId())) { MessageUtils.send(owner, "kick-self"); return; }
        if (!arena.isPlayerInGame(target)) { MessageUtils.send(owner, "kick-not-in-lobby", "%player%", target.getName()); return; }

        playerArenaMap.remove(target.getUniqueId());
        arena.removePlayer(target);
        MessageUtils.send(owner, "kick-success", "%player%", target.getName());
        MessageUtils.send(target, "kick-target", "%kicker%", owner.getName());
    }

    // ── Invites ───────────────────────────────────────────────────────────────

    public void sendInvite(Player sender, Player target) {
        GameArena arena = getArena(sender);
        if (arena == null || arena.isPublic()) {
            MessageUtils.sendRaw(sender, "&cYou must be in a private lobby to invite.");
            return;
        }
        if (!arena.getOwnerId().equals(sender.getUniqueId())) {
            MessageUtils.send(sender, "kick-not-owner");
            return;
        }
        if (pendingInvites.containsKey(target.getUniqueId())) {
            MessageUtils.send(sender, "invite-already-pending");
            return;
        }

        pendingInvites.put(target.getUniqueId(),
                new InviteData(sender.getUniqueId(), arena.getName()));

        MessageUtils.send(sender, "invite-sent", "%player%", target.getName());

        // Clickable accept/decline buttons
        String acceptCmd  = "/escolta user accept";
        String declineCmd = "/escolta user decline";
        Component msg = LegacyComponentSerializer.legacyAmpersand()
                .deserialize(MessageUtils.get("invite-received")
                        .replace("%sender%", sender.getName())
                        .replace("%arena%",  arena.getName()))
                .append(Component.text(" "))
                .append(LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(MessageUtils.get("invite-accept"))
                        .clickEvent(ClickEvent.runCommand(acceptCmd))
                        .hoverEvent(HoverEvent.showText(Component.text(acceptCmd))))
                .append(Component.text(" "))
                .append(LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(MessageUtils.get("invite-decline"))
                        .clickEvent(ClickEvent.runCommand(declineCmd))
                        .hoverEvent(HoverEvent.showText(Component.text(declineCmd))));
        target.sendMessage(msg);

        // Auto-expire after 60 seconds
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            InviteData data = pendingInvites.get(target.getUniqueId());
            if (data != null && data.senderUuid().equals(sender.getUniqueId())) {
                pendingInvites.remove(target.getUniqueId());
                if (target.isOnline())
                    MessageUtils.send(target, "invite-expired", "%sender%", sender.getName());
            }
        }, 20L * 60);
    }

    public void acceptInvite(Player p) {
        InviteData data = pendingInvites.remove(p.getUniqueId());
        if (data == null) { MessageUtils.send(p, "invite-not-pending"); return; }
        if (playerArenaMap.containsKey(p.getUniqueId())) { MessageUtils.send(p, "arena-already-in"); return; }
        joinArena(p, data.arenaName());
        MessageUtils.send(p, "invite-accepted", "%arena%", data.arenaName());
    }

    public void declineInvite(Player p) {
        InviteData data = pendingInvites.remove(p.getUniqueId());
        if (data == null) { MessageUtils.send(p, "invite-not-pending"); return; }
        MessageUtils.send(p, "invite-declined", "%sender%",
                Objects.toString(Bukkit.getOfflinePlayer(data.senderUuid()).getName(), "?"));
        Player sender = Bukkit.getPlayer(data.senderUuid());
        if (sender != null) MessageUtils.send(sender, "invite-declined-sender", "%player%", p.getName());
    }

    // ── Lobby list ────────────────────────────────────────────────────────────

    public void sendLobbiesList(Player p) {
        MessageUtils.sendRaw(p, MessageUtils.get("lobbies-header"));
        boolean any = false;
        for (GameArena arena : arenas.values()) {
            if (!arena.isPublic()) continue;
            any = true;
            String line = MessageUtils.get("lobbies-entry")
                    .replace("%name%",   arena.getName())
                    .replace("%count%",  String.valueOf(arena.getPlayerCount()))
                    .replace("%max%",    String.valueOf(arena.getMaxPlayers()))
                    .replace("%status%", arena.getState().name());
            Component clickable = LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(line)
                    .clickEvent(ClickEvent.runCommand("/escolta user join " + arena.getName()))
                    .hoverEvent(HoverEvent.showText(
                            LegacyComponentSerializer.legacyAmpersand()
                                    .deserialize(MessageUtils.get("lobbies-entry-click"))));
            p.sendMessage(clickable);
        }
        if (!any) MessageUtils.sendRaw(p, MessageUtils.get("lobbies-empty"));
        MessageUtils.sendRaw(p, MessageUtils.get("lobbies-footer"));
    }

    // ── Lookup ────────────────────────────────────────────────────────────────

    public GameArena getArena(Player p) {
        String key = playerArenaMap.get(p.getUniqueId());
        return key != null ? arenas.get(key) : null;
    }

    public GameArena getArenaByName(String name) {
        return arenas.get(name.toLowerCase());
    }

    // ── Reload ───────────────────────────────────────────────────────────────

    /**
     * Called by /escolta admin reload.
     * Re-reads max-players-public and max-players-private from config
     * and pushes the new values into every arena currently in memory.
     * Also re-initializes the ItemManager blacklist inside each arena (via GameArena.reloadConfig).
     */
    public void reloadConfig() {
        int maxPublic  = plugin.getConfig().getInt("game-loop.max-players-public",  8);
        int maxPrivate = plugin.getConfig().getInt("game-loop.max-players-private", 5);

        for (GameArena arena : arenas.values()) {
            // Only update arenas that are still in WAITING — don't interrupt a running game
            if (arena.getState() == com.escoltacore.arena.GameState.WAITING) {
                arena.setMaxPlayers(arena.isPublic() ? maxPublic : maxPrivate);
            }
        }
    }

    // ── Shutdown ──────────────────────────────────────────────────────────────

    public void shutdown() {
        savePublicArenas();
        arenas.values().forEach(GameArena::forceStop);
        arenas.clear();
        playerArenaMap.clear();
        pendingInvites.clear();
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void loadPublicArenas() {
        arenasFile = new File(plugin.getDataFolder(), "arenas.yml");
        if (!arenasFile.exists()) {
            plugin.getLogger().info("arenas.yml not found — starting fresh.");
            arenasConfig = new YamlConfiguration();
            return;
        }
        arenasConfig = YamlConfiguration.loadConfiguration(arenasFile);
        ConfigurationSection section = arenasConfig.getConfigurationSection("arenas");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            ConfigurationSection as = section.getConfigurationSection(key);
            if (as == null) continue;

            int max = as.getInt("max-players",
                    plugin.getConfig().getInt("game-loop.max-players-public", 8));
            Location spawn = null;

            if (as.contains("spawn")) {
                String worldName = as.getString("spawn.world");
                World world = worldName != null ? Bukkit.getWorld(worldName) : null;
                if (world != null) {
                    spawn = new Location(world,
                            as.getDouble("spawn.x"),
                            as.getDouble("spawn.y"),
                            as.getDouble("spawn.z"),
                            (float) as.getDouble("spawn.yaw"),
                            (float) as.getDouble("spawn.pitch"));
                }
            }

            GameArena arena = new GameArena(plugin, key, max, spawn);
            arenas.put(key.toLowerCase(), arena);
            arena.initLobbyBar();
            plugin.getLogger().info("Loaded public arena: " + key
                    + (spawn == null ? " (no spawn set yet)" : ""));
        }
    }

    private void savePublicArenas() {
        if (arenasConfig == null) arenasConfig = new YamlConfiguration();
        arenasConfig.set("arenas", null); // clear and rewrite

        for (GameArena arena : arenas.values()) {
            if (!arena.isPublic()) continue;
            String path = "arenas." + arena.getName();
            arenasConfig.set(path + ".max-players", arena.getMaxPlayers());

            Location spawn = arena.getSpawnPoint();
            if (spawn != null && spawn.getWorld() != null) {
                arenasConfig.set(path + ".spawn.world", spawn.getWorld().getName());
                arenasConfig.set(path + ".spawn.x",     spawn.getX());
                arenasConfig.set(path + ".spawn.y",     spawn.getY());
                arenasConfig.set(path + ".spawn.z",     spawn.getZ());
                arenasConfig.set(path + ".spawn.yaw",   (double) spawn.getYaw());
                arenasConfig.set(path + ".spawn.pitch", (double) spawn.getPitch());
            }
        }

        try {
            arenasConfig.save(arenasFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save arenas.yml!", e);
        }
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    private record InviteData(UUID senderUuid, String arenaName) {}
}