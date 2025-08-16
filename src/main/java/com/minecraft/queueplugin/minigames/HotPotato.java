package com.minecraft.queueplugin.minigames;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Criteria;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.lang.reflect.Type;
import java.util.*;

public class HotPotato implements Listener {
    
    
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
    private boolean playersAreFrozen = false;
    private int freezeTimeLeft = 3;
    private int gameTimeLeft = 60; 
    
    
    private final Location potatoHoldersSpawn = new Location(null, 789.5, 70, 11.5);
    private final Location nonHoldersSpawn = new Location(null, 776.5, 77, -1.5);
    
    private final Set<Player> allPlayers = new HashSet<>();
    private final Set<Player> potatoHolders = new HashSet<>();
    private BukkitTask countdownTask = null;
    private BukkitTask gameTask = null;
    
    public HotPotato(JavaPlugin plugin) {
        this.plugin = plugin;
        
        
        World world = Bukkit.getWorlds().get(0);
        potatoHoldersSpawn.setWorld(world);
        nonHoldersSpawn.setWorld(world);
    }
    
    public void setGameEndCallback(GameEndCallback callback) {
        this.gameEndCallback = callback;
    }
    
    public void startGame(List<Player> players) {
        plugin.getLogger().info("Starting Hot Potato minigame with " + players.size() + " players");
        
        gameActive = true;
        playersAreFrozen = true;
        freezeTimeLeft = 3;
        gameTimeLeft = 60;
        allPlayers.clear();
        potatoHolders.clear();
        
        allPlayers.addAll(players);
        
        
        int potatoCount = Math.max(1, (int) Math.ceil(players.size() * 0.2));
        
        
        List<Player> shuffledPlayers = new ArrayList<>(players);
        Collections.shuffle(shuffledPlayers);
        for (int i = 0; i < potatoCount && i < shuffledPlayers.size(); i++) {
            potatoHolders.add(shuffledPlayers.get(i));
        }
        
        
        Bukkit.getPluginManager().registerEvents(this, plugin);
        
        
        for (Player player : players) {
            
            player.setGameMode(GameMode.ADVENTURE);
            
            
            player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 255, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 1, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 255, false, false));
            
            
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            
            
            if (potatoHolders.contains(player)) {
                player.teleport(potatoHoldersSpawn);
                player.getInventory().setItemInMainHand(new ItemStack(Material.BAKED_POTATO, 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 1, false, false));
                player.sendMessage("§c§lYou have the HOT POTATO! §eHit someone to pass it!");
            } else {
                player.teleport(nonHoldersSpawn);
                player.sendMessage("§a§lYou're safe! §7Avoid the glowing players with potatoes!");
            }
        }
        
        Bukkit.broadcastMessage("§e§l========== HOT POTATO ==========");
        Bukkit.broadcastMessage("§c" + potatoCount + " players have the hot potato!");
        Bukkit.broadcastMessage("§eHit someone to pass the potato!");
        Bukkit.broadcastMessage("§7Don't be holding a potato when time runs out!");
        Bukkit.broadcastMessage("§e§l===============================");
        
        
        startFreezeCountdown();
        
        
        updateHotPotatoScoreboard();
    }
    
    private void startFreezeCountdown() {
        countdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (freezeTimeLeft > 0) {
                    for (Player player : allPlayers) {
                        if (player.isOnline()) {
                            player.sendTitle("§c§l" + freezeTimeLeft, "§7Get ready to run!", 5, 15, 5);
                        }
                    }
                    freezeTimeLeft--;
                } else {
                    
                    playersAreFrozen = false;
                    
                    for (Player player : allPlayers) {
                        if (player.isOnline()) {
                            player.sendTitle("§a§lGO!", "§eThe game has started!", 5, 15, 5);
                        }
                    }
                    
                    Bukkit.broadcastMessage("§a§lHOT POTATO HAS STARTED!");
                    
                    
                    startGameTimer();
                    
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }
    
    private void startGameTimer() {
        gameTask = new BukkitRunnable() {
            @Override
            public void run() {
                gameTimeLeft--;
                
                
                updateHotPotatoScoreboard();
                
                
                if (gameTimeLeft == 30) {
                    Bukkit.broadcastMessage("§e§l30 seconds remaining!");
                } else if (gameTimeLeft == 10) {
                    Bukkit.broadcastMessage("§c§l10 seconds remaining!");
                } else if (gameTimeLeft <= 5 && gameTimeLeft > 0) {
                    for (Player player : allPlayers) {
                        if (player.isOnline()) {
                            player.sendTitle("§c§l" + gameTimeLeft, "§7Time's almost up!", 5, 10, 5);
                        }
                    }
                } else if (gameTimeLeft <= 0) {
                    
                    endGame();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); 
    }
    
    @EventHandler
    public void onPlayerHit(EntityDamageByEntityEvent event) {
        if (!gameActive || playersAreFrozen) return;
        
        if (!(event.getDamager() instanceof Player damager) || !(event.getEntity() instanceof Player target)) {
            return;
        }
        
        
        event.setCancelled(true);
        
        
        if (!potatoHolders.contains(damager)) {
            
            damager.getInventory().clear();
            damager.sendMessage("§c§lYou don't have a potato to pass!");
            return;
        }
        
        
        if (!allPlayers.contains(target) || !allPlayers.contains(damager)) {
            return;
        }
        
        
        if (potatoHolders.contains(target)) {
            damager.sendMessage("§c§lThat player already has a potato!");
            return;
        }
        
        
        transferPotato(damager, target);
    }
    
    private void transferPotato(Player from, Player to) {
        
        potatoHolders.remove(from);
        from.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        from.removePotionEffect(PotionEffectType.GLOWING);
        from.sendMessage("§a§lYou passed the hot potato to " + to.getName() + "!");
        
        
        potatoHolders.add(to);
        to.getInventory().setItemInMainHand(new ItemStack(Material.BAKED_POTATO, 1));
        to.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 1, false, false));
        to.sendMessage("§c§lYou now have the HOT POTATO! Hit someone to pass it!");
        
        
        String message = "§e" + from.getName() + " §7passed the hot potato to §c" + to.getName() + "§7!";
        for (Player player : allPlayers) {
            if (player.isOnline()) {
                player.sendMessage(message);
            }
        }
        
        
        updateHotPotatoScoreboard();
    }
    
    private void updateHotPotatoScoreboard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;
        
        Scoreboard scoreboard = manager.getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("hotpotato", Criteria.DUMMY, "§c§lHOT POTATO");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        
        
        objective.getScore("§eTime: §f" + gameTimeLeft + "s").setScore(15);
        objective.getScore("").setScore(14);
        
        
        objective.getScore("§c§lPotato Holders:").setScore(13);
        int scoreValue = 12;
        for (Player holder : potatoHolders) {
            if (holder.isOnline() && scoreValue > 0) {
                String name = holder.getName();
                if (name.length() > 14) {
                    name = name.substring(0, 14);
                }
                objective.getScore("§c" + name).setScore(scoreValue);
                scoreValue--;
            }
        }
        
        
        if (scoreValue > 2) {
            objective.getScore(" ").setScore(scoreValue);
            objective.getScore("§a§lSafe Players: §f" + (allPlayers.size() - potatoHolders.size())).setScore(scoreValue - 1);
        }
        
        
        for (Player player : allPlayers) {
            if (player.isOnline()) {
                player.setScoreboard(scoreboard);
            }
        }
    }
    
    private void endGame() {
        gameActive = false;
        playersAreFrozen = false;
        
        
        if (countdownTask != null && !countdownTask.isCancelled()) {
            countdownTask.cancel();
        }
        if (gameTask != null && !gameTask.isCancelled()) {
            gameTask.cancel();
        }
        
        
        EntityDamageByEntityEvent.getHandlerList().unregister(this);
        
        Bukkit.broadcastMessage("§c§l========== TIME'S UP! ==========");
        
        
        if (potatoHolders.isEmpty()) {
            Bukkit.broadcastMessage("§a§lEveryone wins! No one has the potato!");
        } else {
            Bukkit.broadcastMessage("§c§lPotato holders LOSE:");
            for (Player holder : potatoHolders) {
                Bukkit.broadcastMessage("§c- " + holder.getName());
                holder.sendMessage("§c§lYou lost! You were holding the hot potato!");
            }
            
            Bukkit.broadcastMessage("§a§lWinners get 1 point each:");
            for (Player player : allPlayers) {
                if (!potatoHolders.contains(player)) {
                    Bukkit.broadcastMessage("§a- " + player.getName());
                    player.sendMessage("§a§lYou win! +1 point for avoiding the potato!");
                }
            }
        }
        
        Bukkit.broadcastMessage("§c§l===============================");
        
        
        awardPointsAndResetPlayers();
    }
    
    private void awardPointsAndResetPlayers() {
        try {
            
            File file = new File(plugin.getDataFolder(), "winners.json");
            Map<String, PlayerStats> playerData = new HashMap<>();
            
            if (file.exists()) {
                try (FileReader reader = new FileReader(file)) {
                    Type type = new TypeToken<Map<String, PlayerStats>>(){}.getType();
                    Map<String, PlayerStats> loaded = gson.fromJson(reader, type);
                    if (loaded != null) {
                        playerData = loaded;
                    }
                }
            }
            
            
            for (Player player : allPlayers) {
                if (!potatoHolders.contains(player)) {
                    PlayerStats stats = playerData.getOrDefault(player.getName(), new PlayerStats(player.getName(), 0));
                    stats.points += 1;
                    playerData.put(player.getName(), stats);
                    
                    player.sendMessage("§a§lYou avoided the potato! §a(+§e1§a point)");
                }
                
                
                player.removePotionEffect(PotionEffectType.RESISTANCE);
                player.removePotionEffect(PotionEffectType.NIGHT_VISION);
                player.removePotionEffect(PotionEffectType.GLOWING);
                
            }
            
            
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(playerData, writer);
            }
            
            plugin.getLogger().info("Awarded points to Hot Potato winners");
            
            
            Bukkit.getScheduler().runTaskLater(plugin, this::teleportPlayersToSpawn, 100L);
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to award points: " + e.getMessage());
            e.printStackTrace();
            
            
            teleportPlayersToSpawn();
        }
    }
    
    private void teleportPlayersToSpawn() {
        Location spawn = Bukkit.getWorlds().get(0).getSpawnLocation();
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            
            player.setWalkSpeed(0.2f);
            player.setFlySpeed(0.1f);
            
            
            player.setGameMode(GameMode.ADVENTURE);
            player.sendMessage("§aYou have been returned to adventure mode!");
            
            
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            
            
            player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 255, false, false));
            
            
            player.teleport(spawn);
        }
        
        Bukkit.broadcastMessage("§aAll players have been teleported back to spawn!");
        
        
        if (gameEndCallback != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                gameEndCallback.onGameEnd();
            }, 60L); 
        }
    }
    
    public boolean isGameActive() {
        return gameActive;
    }
    
    public void forceEnd() {
        if (gameActive) {
            gameActive = false;
            playersAreFrozen = false;
            
            
            if (countdownTask != null && !countdownTask.isCancelled()) {
                countdownTask.cancel();
            }
            if (gameTask != null && !gameTask.isCancelled()) {
                gameTask.cancel();
            }
            
            
            EntityDamageByEntityEvent.getHandlerList().unregister(this);
            
            
            for (Player player : allPlayers) {
                if (player.isOnline()) {
                    player.setWalkSpeed(0.2f);
                    player.setFlySpeed(0.1f);
                    player.setGameMode(GameMode.ADVENTURE);
                    
                    
                    player.removePotionEffect(PotionEffectType.RESISTANCE);
                    player.removePotionEffect(PotionEffectType.NIGHT_VISION);
                    player.removePotionEffect(PotionEffectType.GLOWING);
                }
            }
            
            
            teleportPlayersToSpawn();
            
            allPlayers.clear();
            potatoHolders.clear();
            
            plugin.getLogger().info("Hot Potato game force ended");
        }
    }
}
