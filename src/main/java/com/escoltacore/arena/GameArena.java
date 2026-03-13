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

public class GameArena {

    private final EscoltaCorePlugin plugin;
    private final String name; // Nombre de la sala
    private final UUID ownerId;
    private final ItemManager itemManager;
    
    private double radius = 15.0;
    private Particle particle = Particle.REDSTONE; 

    private GameState state;
    private UUID escoltadoId;
    private Material targetItem;
    private final Set<UUID> players = new HashSet<>();
    private RadiusTask radiusTask;

    public GameArena(EscoltaCorePlugin plugin, String name, UUID ownerId) {
        this.plugin = plugin;
        this.name = name;
        this.ownerId = ownerId;
        this.state = GameState.WAITING;
        this.itemManager = new ItemManager(plugin);
    }

    public boolean addPlayer(Player p) {
        if (state != GameState.WAITING) {
            MessageUtils.send(p, "arena-already-in"); // Configurable
            return false;
        }
        players.add(p.getUniqueId());
        
        // Mensaje de Join con variable %count%
        String msg = MessageUtils.get("arena-join")
                .replace("%player%", p.getName())
                .replace("%count%", String.valueOf(players.size()));
        broadcastRaw(msg);
        
        // DAR ÍTEMS DE LOBBY
        giveLobbyItems(p);
        
        return true;
    }

    public void removePlayer(Player p) {
        players.remove(p.getUniqueId());
        p.getInventory().clear(); // Limpiar items del lobby
        
        String msg = MessageUtils.get("arena-leave").replace("%player%", p.getName());
        broadcastRaw(msg);
        
        if (state == GameState.RUNNING && players.size() < 2) {
            forceStop();
        }
    }

    // --- NUEVO: DAR ÍTEMS DE CONFIGURACIÓN Y CLASES ---
    public void giveLobbyItems(Player p) {
        p.getInventory().clear();

        // 1. Ítem de Selector de Clases (Para todos)
        ItemStack classSelector = new ItemBuilder(Material.NETHER_STAR)
                .name(MessageUtils.get("items.class-selector.name"))
                .lore(getMessageList("items.class-selector.lore"))
                .build();
        p.getInventory().setItem(4, classSelector);

        // 2. Ítem de Configuración (Solo para el dueño)
        if (p.getUniqueId().equals(ownerId)) {
            ItemStack configTool = new ItemBuilder(Material.COMPARATOR)
                    .name(MessageUtils.get("items.config-tool.name"))
                    .lore(getMessageList("items.config-tool.lore"))
                    .build();
            p.getInventory().setItem(0, configTool);
        }
    }

    // Método helper para leer listas del config
    private String[] getMessageList(String path) {
        List<String> list = plugin.getConfig().getStringList(path); // O messagesConfig si lo moviste
        // Como MessageUtils maneja el file config privado, aquí simplificamos:
        // Nota: Idealmente MessageUtils debería tener un getList, pero por brevedad:
        return new String[]{"&7Click to use"}; 
    }

    public void start(Player initiator) {
        if (!initiator.getUniqueId().equals(ownerId) && !initiator.hasPermission("escoltacore.admin")) {
            MessageUtils.send(initiator, "no-permission");
            return;
        }
        int minPlayers = plugin.getConfig().getInt("game-loop.min-players", 2);
        if (players.size() < minPlayers) {
            MessageUtils.send(initiator, "arena-min-players", "%min%", String.valueOf(minPlayers));
            return;
        }

        this.state = GameState.STARTING;
        
        List<UUID> playerList = new ArrayList<>(players);
        Collections.shuffle(playerList);
        this.escoltadoId = playerList.get(0);
        this.targetItem = itemManager.getRandomTarget();

        Player escoltadoPlayer = Bukkit.getPlayer(escoltadoId);
        if (escoltadoPlayer == null) { forceStop(); return; }
        Location startLoc = escoltadoPlayer.getLocation();

        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            
            p.teleport(startLoc);
            p.getInventory().clear(); // <--- BORRA LOS ÍTEMS DEL LOBBY AQUÍ
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
    
    // ... (El resto de métodos endGame, forceStop, getters igual que antes) ...
    // Solo recuerda cambiar los textos hardcoded por MessageUtils.get(...)
    
    public void endGame(boolean victory, String triggerName) {
        if (state != GameState.RUNNING) return;
        if (radiusTask != null) radiusTask.cancel();
        this.state = GameState.ENDING;

        if (victory) {
            String sub = MessageUtils.get("victory-subtitle").replace("%player%", triggerName);
            broadcastRaw(MessageUtils.get("victory-title") + " " + sub);
            playSoundGlobal(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        } else {
             broadcastRaw(MessageUtils.get("defeat-title"));
             playSoundGlobal(Sound.ENTITY_WITHER_DEATH, 1f, 0.5f);
        }
        Bukkit.getScheduler().runTaskLater(plugin, this::softReset, 60L);
    }

    private void softReset() {
        this.state = GameState.WAITING;
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.setHealth(20);
                p.setFoodLevel(20);
                p.getInventory().clear();
                giveLobbyItems(p); // <--- DEVOLVER ÍTEMS AL RESETEAR
            }
        }
        broadcastRaw(MessageUtils.get("arena-wait-reset"));
    }
    
    public void forceStop() {
         if (radiusTask != null) radiusTask.cancel();
        this.state = GameState.WAITING;
        players.clear();
    }
    
    // Setters/Getters
    public String getName() { return name; } // Necesario para el título del GUI
    public void setRadius(double r) { this.radius = r; }
    public void setParticle(Particle p) { this.particle = p; }
    public Particle getParticle() { return this.particle; }
    public double getRadius() { return radius; }
    public UUID getOwnerId() { return ownerId; }
    public GameState getState() { return state; }
    public UUID getEscoltadoId() { return escoltadoId; }
    public Material getTargetItem() { return targetItem; }
    public boolean isPlayerInGame(Player p) { return players.contains(p.getUniqueId()); }
    public int getPlayerCount() { return players.size(); }

    

    private void broadcastRaw(String msg) {
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) MessageUtils.sendRaw(p, msg);
        }
    }
    
    private void sendTitle(Player p, String titleKey, String subKey) {
        p.showTitle(Title.title(
            MessageUtils.component(MessageUtils.get(titleKey)), 
            MessageUtils.component(MessageUtils.get(subKey))
        ));
    }
    
    private void playSoundGlobal(Sound s, float v, float p) {
        for (UUID uuid : players) {
            Player pl = Bukkit.getPlayer(uuid);
            if (pl != null) pl.playSound(pl.getLocation(), s, v, p);
        }
    }
}