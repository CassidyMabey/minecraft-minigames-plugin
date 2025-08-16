package com.minecraft.queueplugin.minigames;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Criteria;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.lang.reflect.Type;
import java.util.*;

public class TryToDie implements Listener {
    
    
    public interface GameEndCallback {
        void onGameEnd();
    }
    
    
    private static class PlayerStats {
        public String name;
        public int points;
        
        public PlayerStats(String name, int points) {
            this.name = name;
            this.points = points;
        }
    }
    
    private final JavaPlugin plugin;
    private GameEndCallback gameEndCallback;
    private final Gson gson = new Gson();
    private boolean gameActive = false;
    
    private final Location teleportLocation = new Location(null, 199.5, 76, -158.5);
    
    private final Set<Player> alivePlayers = new HashSet<>();
    private final List<Player> finishers = new ArrayList<>(); 
    private BukkitTask scoreboardUpdateTask = null;
    
    public TryToDie(JavaPlugin plugin) {
        this.plugin = plugin;
        
        
        World world = Bukkit.getWorlds().get(0);
        teleportLocation.setWorld(world);
    }
    
    public void setGameEndCallback(GameEndCallback callback) {
        this.gameEndCallback = callback;
    }
    
    public boolean isGameActive() {
        return gameActive;
    }
    
    public void startGame(List<Player> players) {
        plugin.getLogger().info("Starting Try to Die minigame with " + players.size() + " players");
        
        gameActive = true;
        alivePlayers.clear();
        finishers.clear();
        
        
        Bukkit.getPluginManager().registerEvents(this, plugin);
        
        
        for (Player player : players) {
            if (player.isOnline()) {
                alivePlayers.add(player);
                
                
                for (PotionEffect effect : player.getActivePotionEffects()) {
                    player.removePotionEffect(effect.getType());
                }
                
                
                player.getInventory().clear();
                player.getInventory().setArmorContents(null);
                
                
                player.setGameMode(GameMode.ADVENTURE);
                
                
                player.teleport(teleportLocation);
                
                
                player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1, false, false));
                
                player.sendMessage("§c§lTRY TO DIE HAS STARTED!");
                player.sendMessage("§7Try to die as fast as possible!");
                player.sendMessage("§7First to die gets the most points!");
            }
        }
        
        Bukkit.broadcastMessage("§c§lTRY TO DIE HAS STARTED!");
        Bukkit.broadcastMessage("§7Players must try to die as fast as possible!");
        Bukkit.broadcastMessage("§7First 5 players to die get points!");
        
        
        startScoreboardUpdates();
        
        plugin.getLogger().info("Try to Die game started successfully");
    }
    
    private void startScoreboardUpdates() {
        scoreboardUpdateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (gameActive) {
                updateTryToDieScoreboard();
            }
        }, 20L, 40L); 
    }
    
    private void updateTryToDieScoreboard() {
        Set<Player> allPlayers = new HashSet<>();
        allPlayers.addAll(alivePlayers);
        allPlayers.addAll(finishers);
        
        for (Player player : allPlayers) {
            if (!player.isOnline()) continue;
            
            
            Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective obj = board.registerNewObjective("trytodie", Criteria.DUMMY, "§c§lTRY TO DIE");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            
            
            int scoreValue = 5;
            obj.getScore("§7Dead: §c" + finishers.size()).setScore(scoreValue--);
            obj.getScore("§7Alive: §a" + alivePlayers.size()).setScore(scoreValue--);
            obj.getScore("").setScore(scoreValue--);
            
            if (finishers.contains(player)) {
                int position = finishers.indexOf(player) + 1;
                int points = Math.max(0, 6 - position); 
                obj.getScore("§a§lYOU DIED #" + position + "!").setScore(scoreValue--);
                if (points > 0) {
                    obj.getScore("§e+" + points + " points").setScore(scoreValue--);
                }
            } else {
                obj.getScore("§7Status: §aAlive").setScore(scoreValue--);
                obj.getScore("§7Try to die first!").setScore(scoreValue--);
            }
            
            player.setScoreboard(board);
        }
    }
    
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!gameActive) return;
        
        Player player = event.getEntity();
        if (!alivePlayers.contains(player)) return;
        
        
        event.setKeepInventory(true);
        event.getDrops().clear();
        
        
        alivePlayers.remove(player);
        finishers.add(player);
        
        int position = finishers.size();
        
        
        if (position <= 5) {
            int points = 6 - position; 
            awardPoints(player.getName(), points);
            
            Bukkit.broadcastMessage("§c§l" + player.getName() + " §c§ldied and finished §e§l#" + position + "§c§l! (+" + points + " points)");
            player.sendMessage("§a§lCongratulations! You died in position #" + position + " and earned " + points + " points!");
        } else {
            Bukkit.broadcastMessage("§7" + player.getName() + " died and finished #" + position);
            player.sendMessage("§7You died but didn't finish in the top 5 for points.");
        }
        
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.spigot().respawn();
        });
    }
    
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!gameActive) return;
        
        Player player = event.getPlayer();
        if (finishers.contains(player)) {
            
            event.setRespawnLocation(teleportLocation);
            
            Bukkit.getScheduler().runTask(plugin, () -> {
                
                for (PotionEffect effect : player.getActivePotionEffects()) {
                    player.removePotionEffect(effect.getType());
                }
                
                player.setGameMode(GameMode.SPECTATOR);
                player.sendMessage("§7You are now spectating. Watch the remaining players!");
            });
        }
        
        
        if (alivePlayers.size() <= 1) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                endGame();
            }, 20L); 
        }
    }
    
    private void awardPoints(String playerName, int points) {
        try {
            
            File file = new File(plugin.getDataFolder(), "winners.json");
            Map<String, PlayerStats> playerData = new HashMap<>();
            
            if (file.exists()) {
                try (FileReader reader = new FileReader(file)) {
                    Type typeToken = new TypeToken<Map<String, PlayerStats>>(){}.getType();
                    Map<String, PlayerStats> loaded = gson.fromJson(reader, typeToken);
                    if (loaded != null) {
                        playerData = loaded;
                    }
                }
            }
            
            
            PlayerStats stats = playerData.getOrDefault(playerName, new PlayerStats(playerName, 0));
            stats.points += points;
            playerData.put(playerName, stats);
            
            
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(playerData, writer);
            }
            
            plugin.getLogger().info("Awarded " + points + " points to " + playerName);
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to award points to " + playerName + ": " + e.getMessage());
        }
    }
    
    private void endGame() {
        if (!gameActive) return;
        
        gameActive = false;
        
        
        if (scoreboardUpdateTask != null && !scoreboardUpdateTask.isCancelled()) {
            scoreboardUpdateTask.cancel();
        }
        
        Bukkit.broadcastMessage("§c§lTRY TO DIE HAS ENDED!");
        
        
        if (!finishers.isEmpty()) {
            Bukkit.broadcastMessage("§e§lFINAL RESULTS:");
            for (int i = 0; i < Math.min(finishers.size(), 5); i++) {
                Player player = finishers.get(i);
                int points = 5 - i;
                Bukkit.broadcastMessage("§7#" + (i + 1) + " §c" + player.getName() + " §7(+" + points + " points)");
            }
        }
        
        
        Location spawn = Bukkit.getWorlds().get(0).getSpawnLocation();
        Set<Player> allGamePlayers = new HashSet<>();
        allGamePlayers.addAll(alivePlayers);
        allGamePlayers.addAll(finishers);
        
        for (Player player : allGamePlayers) {
            if (player.isOnline()) {
                
                for (PotionEffect effect : player.getActivePotionEffects()) {
                    player.removePotionEffect(effect.getType());
                }
                
                
                player.setGameMode(GameMode.ADVENTURE);
                player.getInventory().clear();
                player.getInventory().setArmorContents(null);
                
                
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 255, false, false));
                
                
                player.teleport(spawn);
                
                player.sendMessage("§7Thanks for playing Try to Die!");
            }
        }
        
        
        alivePlayers.clear();
        finishers.clear();
        
        
        if (gameEndCallback != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> gameEndCallback.onGameEnd(), 100L);
        }
        
        plugin.getLogger().info("Try to Die game ended");
    }
}
