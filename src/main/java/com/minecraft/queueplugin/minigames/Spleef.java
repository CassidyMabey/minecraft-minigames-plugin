package com.minecraft.queueplugin.minigames;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
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
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.lang.reflect.Type;
import java.util.*;

public class Spleef implements Listener {
    
    
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
    private boolean canBreakBlocks = false;
    private int freezeTimeLeft = 3;
    
    private final Set<Player> alivePlayers = new HashSet<>();
    private final List<Player> eliminatedPlayers = new ArrayList<>();
    private BukkitTask countdownTask = null;
    private BukkitTask scoreboardUpdateTask = null;
    
    
    private final Location teleportLocation = new Location(null, 661.5, 91, 5.5);
    private final Location centerBlock1 = new Location(null, 661, 65, 5);
    private final Location centerBlock2 = new Location(null, 661, 78, 5);
    private final Location centerBlock3 = new Location(null, 661, 89, 5);
    
    public Spleef(JavaPlugin plugin) {
        this.plugin = plugin;
        
        
        World world = Bukkit.getWorlds().get(0);
        teleportLocation.setWorld(world);
        centerBlock1.setWorld(world);
        centerBlock2.setWorld(world);
        centerBlock3.setWorld(world);
    }
    
    public void setGameEndCallback(GameEndCallback callback) {
        this.gameEndCallback = callback;
    }
    
    public void startGame(List<Player> players) {
        plugin.getLogger().info("Starting Spleef minigame with " + players.size() + " players");
        
        gameActive = true;
        playersAreFrozen = true;
        canBreakBlocks = false;
        freezeTimeLeft = 3;
        alivePlayers.clear();
        eliminatedPlayers.clear();
        
        
        Bukkit.getPluginManager().registerEvents(this, plugin);
        
        
        generateSnowPlatforms();
        
        
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isOnline() && p.getGameMode() != GameMode.CREATIVE) {
                alivePlayers.add(p);
            }
        }
        
        plugin.getLogger().info("Added " + alivePlayers.size() + " players to Spleef");
        
        
        for (Player player : alivePlayers) {
            
            player.teleport(teleportLocation);
            
            
            player.setGameMode(GameMode.ADVENTURE);
            
            
            player.getInventory().clear();
            player.getInventory().addItem(new ItemStack(Material.IRON_SHOVEL, 1));
            player.getInventory().addItem(new ItemStack(Material.SNOWBALL, 16)); 
            
            
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 255, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 255, false, false));
        }
        
        
        startFreezeCountdown();
        
        
        updateSpleefScoreboard();
        
        
        scoreboardUpdateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateSpleefScoreboard, 0L, 40L); 
        
        Bukkit.broadcastMessage("§e§lSPLEEF §7has started! Wait for the countdown to end before breaking blocks!");
    }
    
    private void generateSnowPlatforms() {
        
        for (Location center : Arrays.asList(centerBlock1, centerBlock2, centerBlock3)) {
            for (int x = -25; x <= 25; x++) {
                for (int z = -25; z <= 25; z++) {
                    Location blockLoc = center.clone().add(x, 0, z);
                    blockLoc.getBlock().setType(Material.SNOW_BLOCK);
                }
            }
        }
        
        plugin.getLogger().info("Generated snow platforms for Spleef arena");
    }
    
    private void startFreezeCountdown() {
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                if (freezeTimeLeft > 0) {
                    
                    String message = "§c§lGame starts in: §e§l" + freezeTimeLeft + "§c§l seconds!";
                    
                    for (Player player : alivePlayers) {
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
                    }
                    
                    freezeTimeLeft--;
                } else {
                    
                    playersAreFrozen = false;
                    canBreakBlocks = true;
                    
                    
                    for (Player player : alivePlayers) {
                        player.setGameMode(GameMode.SURVIVAL);
                        player.sendMessage("§a§lGO! Break the snow blocks with snowballs!");
                    }
                    
                    Bukkit.broadcastMessage("§a§lSPLEEF HAS BEGUN! Break snow blocks by hitting them with snowballs!");
                    
                    
                    if (countdownTask != null) {
                        countdownTask.cancel();
                        countdownTask = null;
                    }
                }
            }
        }, 0L, 20L); 
    }
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!gameActive) return;
        
        Player player = event.getPlayer();
        if (!alivePlayers.contains(player)) return;
        
        Location to = event.getTo();
        if (to == null) return;
        
        
        if (playersAreFrozen) {
            
            double distance = to.distance(teleportLocation);
            if (distance > 2.0) { 
                
                player.teleport(teleportLocation);
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, 
                    new TextComponent("§c§lWait for the game to start! §7(" + 
                        (freezeTimeLeft > 0 ? freezeTimeLeft : 0) + "s)"));
            }
            return;
        }
        
        
        if (to.getY() < 75) {
            eliminatePlayer(player);
        }
    }
    
    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!gameActive || !canBreakBlocks) return;
        
        if (event.getEntity() instanceof Snowball) {
            Snowball snowball = (Snowball) event.getEntity();
            
            
            if (event.getHitBlock() != null && event.getHitBlock().getType() == Material.SNOW_BLOCK) {
                
                event.getHitBlock().setType(Material.AIR);
                
                
                if (snowball.getShooter() instanceof Player) {
                    Player player = (Player) snowball.getShooter();
                    if (alivePlayers.contains(player)) {
                        player.getInventory().addItem(new ItemStack(Material.SNOWBALL, 1));
                    }
                }
            }
        }
    }
    
    private void eliminatePlayer(Player player) {
        if (!alivePlayers.contains(player)) return;
        
        alivePlayers.remove(player);
        eliminatedPlayers.add(player);
        
        
        player.setGameMode(GameMode.SPECTATOR);
        player.sendMessage("§c§lYou have been eliminated from Spleef!");
        player.sendMessage("§7You are now in spectator mode. You'll be returned to adventure mode when the game ends.");
        
        
        player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 255, false, false));
        
        
        updateSpleefScoreboard();
        
        
        player.teleport(teleportLocation.clone().add(0, 10, 0));
        
        
        int placement = eliminatedPlayers.size() + alivePlayers.size();
        String placementText = getPlacementText(placement);
        
        String message = "§e" + player.getName() + " §chas been eliminated! " + placementText + " §7(" + 
                        alivePlayers.size() + " players remaining)";
        
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(message);
        }
        
        
        checkGameEnd();
    }
    
    private void checkGameEnd() {
        if (alivePlayers.size() <= 5 && alivePlayers.size() > 0) {
            
            endGame();
        } else if (alivePlayers.size() == 0) {
            
            endGameWithNoWinners();
        }
    }
    
    private void endGame() {
        gameActive = false;
        playersAreFrozen = false;
        canBreakBlocks = false;
        
        
        if (countdownTask != null && !countdownTask.isCancelled()) {
            countdownTask.cancel();
        }
        if (scoreboardUpdateTask != null && !scoreboardUpdateTask.isCancelled()) {
            scoreboardUpdateTask.cancel();
        }
        
        
        ProjectileHitEvent.getHandlerList().unregister(this);
        PlayerMoveEvent.getHandlerList().unregister(this);
        
        Bukkit.broadcastMessage("§a§lSPLEEF HAS ENDED!");
        
        
        awardPointsAndResetPlayers();
    }
    
    private void endGameWithNoWinners() {
        gameActive = false;
        playersAreFrozen = false;
        canBreakBlocks = false;
        
        
        if (countdownTask != null && !countdownTask.isCancelled()) {
            countdownTask.cancel();
        }
        if (scoreboardUpdateTask != null && !scoreboardUpdateTask.isCancelled()) {
            scoreboardUpdateTask.cancel();
        }
        
        
        ProjectileHitEvent.getHandlerList().unregister(this);
        PlayerMoveEvent.getHandlerList().unregister(this);
        
        Bukkit.broadcastMessage("§c§lSPLEEF ENDED - All players eliminated!");
        Bukkit.broadcastMessage("§7No winners this round. Everyone gets teleported back to spawn.");
        
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOnline()) {
                player.getInventory().clear();
                player.getInventory().setArmorContents(null);
                player.removePotionEffect(PotionEffectType.RESISTANCE);
                player.removePotionEffect(PotionEffectType.NIGHT_VISION);
                
            }
        }
        
        
        Bukkit.getScheduler().runTaskLater(plugin, this::teleportPlayersToSpawn, 100L); 
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
            
            
            List<Player> survivors = new ArrayList<>(alivePlayers);
            for (int i = 0; i < survivors.size(); i++) {
                Player player = survivors.get(i);
                int placement = i + 1; 
                int points = Math.max(0, 6 - placement); 
                
                if (points > 0) {
                    PlayerStats stats = playerData.getOrDefault(player.getName(), new PlayerStats(player.getName(), 0));
                    stats.points += points;
                    playerData.put(player.getName(), stats);
                    
                    String placementText = getPlacementText(placement);
                    player.sendMessage("§a§lGAME FINISHED! " + placementText + " §a(+§e" + points + "§a points)");
                }
                
                
                
                if (player.getGameMode() == GameMode.SURVIVAL) {
                    player.setGameMode(GameMode.ADVENTURE);
                }
                
                
                player.removePotionEffect(PotionEffectType.RESISTANCE);
                player.removePotionEffect(PotionEffectType.NIGHT_VISION);
                
            }
            
            
            for (int i = eliminatedPlayers.size() - 1; i >= 0; i--) {
                Player player = eliminatedPlayers.get(i);
                int placement = survivors.size() + (eliminatedPlayers.size() - i);
                int points = Math.max(0, 6 - placement);
                
                if (points > 0) {
                    PlayerStats stats = playerData.getOrDefault(player.getName(), new PlayerStats(player.getName(), 0));
                    stats.points += points;
                    playerData.put(player.getName(), stats);
                    
                    String placementText = getPlacementText(placement);
                    player.sendMessage("§c§lYou finished in " + placementText + " §c(+§e" + points + "§c points)");
                }
                
                
                
                
                player.removePotionEffect(PotionEffectType.RESISTANCE);
                player.removePotionEffect(PotionEffectType.NIGHT_VISION);
                
            }
            
            
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(playerData, writer);
            }
            
            plugin.getLogger().info("Awarded points to all Spleef participants");
            
            
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
            
            
            if (player.getGameMode() == GameMode.SPECTATOR) {
                player.setGameMode(GameMode.ADVENTURE);
                player.sendMessage("§aYou have been returned to adventure mode!");
            }
            
            
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
    
    private void updateSpleefScoreboard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;
        
        Scoreboard board = manager.getNewScoreboard();
        Objective objective = board.registerNewObjective("spleef", Criteria.DUMMY, "§6§lSPLEEF");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        
        
        objective.getScore("§eAlive Players: §a" + alivePlayers.size()).setScore(1);
        
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setScoreboard(board);
        }
    }
    
    private String getPlacementText(int placement) {
        return switch (placement) {
            case 1 -> "§6§l1st place";
            case 2 -> "§e§l2nd place";
            case 3 -> "§c§l3rd place";
            case 4 -> "§7§l4th place";
            case 5 -> "§7§l5th place";
            default -> "§7§l" + placement + "th place";
        };
    }
    
    public boolean isGameActive() {
        return gameActive;
    }
    
    public void forceEnd() {
        if (gameActive) {
            gameActive = false;
            playersAreFrozen = false;
            canBreakBlocks = false;
            
            
            ProjectileHitEvent.getHandlerList().unregister(this);
            PlayerMoveEvent.getHandlerList().unregister(this);
            
            
            if (countdownTask != null && !countdownTask.isCancelled()) {
                countdownTask.cancel();
            }
            if (scoreboardUpdateTask != null && !scoreboardUpdateTask.isCancelled()) {
                scoreboardUpdateTask.cancel();
            }
            
            
            for (Player player : alivePlayers) {
                if (player.isOnline()) {
                    player.setWalkSpeed(0.2f);
                    player.setFlySpeed(0.1f);
                    
                    
                    player.removePotionEffect(PotionEffectType.RESISTANCE);
                    player.removePotionEffect(PotionEffectType.NIGHT_VISION);
                }
            }
            
            
            for (Player player : eliminatedPlayers) {
                if (player.isOnline()) {
                    player.setWalkSpeed(0.2f);
                    player.setFlySpeed(0.1f);
                    
                    
                    player.removePotionEffect(PotionEffectType.RESISTANCE);
                    player.removePotionEffect(PotionEffectType.NIGHT_VISION);
                }
            }
            
            
            teleportPlayersToSpawn();
            
            alivePlayers.clear();
            eliminatedPlayers.clear();
            
            plugin.getLogger().info("Spleef game force ended");
        }
    }
}
