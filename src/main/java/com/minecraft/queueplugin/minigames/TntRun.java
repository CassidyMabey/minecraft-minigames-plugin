package com.minecraft.queueplugin.minigames;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.lang.reflect.Type;
import java.util.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TntRun implements Listener {
    
    
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
    private final Set<Player> alivePlayers = new HashSet<>();
    private final List<Player> eliminatedPlayers = new ArrayList<>(); 
    private final Set<Location> scheduledRemovals = new HashSet<>();
    private BukkitTask countdownTask;
    
    
    private final Location centerBlock1 = new Location(null, 199, 51, 3);
    private final Location centerBlock2 = new Location(null, 199, 66, 3);
    private final Location centerBlock3 = new Location(null, 199, 79, 3);
    private final Location teleportLocation = new Location(null, 199.5, 80, 3.5); 
    
    public TntRun(JavaPlugin plugin) {
        this.plugin = plugin;
        
        
        World world = Bukkit.getWorlds().get(0);
        centerBlock1.setWorld(world);
        centerBlock2.setWorld(world);
        centerBlock3.setWorld(world);
        teleportLocation.setWorld(world);
    }
    
    public void setGameEndCallback(GameEndCallback callback) {
        this.gameEndCallback = callback;
    }
    
    public void startGame(List<Player> players) {
        if (gameActive) {
            plugin.getLogger().warning("TNT Run game is already active!");
            return;
        }
        
        gameActive = true;
        playersAreFrozen = true; 
        alivePlayers.clear();
        alivePlayers.addAll(players);
        eliminatedPlayers.clear(); 
        scheduledRemovals.clear();
        
        plugin.getLogger().info("Starting TNT Run game with " + players.size() + " players");
        plugin.getLogger().info("Teleport location: " + teleportLocation);
        
        
        Bukkit.getPluginManager().registerEvents(this, plugin);
        
        
        configureGameRules();
        
        
        buildArena();
        
        
        createAlivePlayersScoreboard();
        
        
        for (Player player : players) {
            plugin.getLogger().info("Teleporting player " + player.getName() + " to TNT Run arena");
            
            
            player.setGameMode(GameMode.ADVENTURE);
            
            
            boolean success = player.teleport(teleportLocation);
            if (!success) {
                plugin.getLogger().warning("Failed to teleport " + player.getName() + " to TNT Run arena!");
            } else {
                plugin.getLogger().info("Successfully teleported " + player.getName() + " to " + teleportLocation);
            }
            
            
            player.setWalkSpeed(0f);
            player.setFlySpeed(0f);
            
            
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 255, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 255, false, false));
            
            
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, 
                new TextComponent("§c§lFROZEN! Stay in center until game starts!"));
        }
        
        
        sendRules(players);
        
        
        startCountdown(players);
    }
    
    private void buildArena() {
        World world = centerBlock1.getWorld();
        
        
        buildTntPlatform(world, centerBlock1);
        buildTntPlatform(world, centerBlock2);
        buildTntPlatform(world, centerBlock3);
        
        plugin.getLogger().info("Built TNT Run arenas");
    }
    
    private void buildTntPlatform(World world, Location center) {
        
        world.getBlockAt(center).setType(Material.BEDROCK);
        
        
        for (int x = -25; x <= 25; x++) {
            for (int z = -25; z <= 25; z++) {
                if (x == 0 && z == 0) continue; 
                
                Location blockLoc = center.clone().add(x, 0, z);
                world.getBlockAt(blockLoc).setType(Material.TNT);
            }
        }
    }
    
    private void configureGameRules() {
        World world = centerBlock1.getWorld();
        if (world != null) {
            
            world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
            
            world.setGameRule(GameRule.KEEP_INVENTORY, true);
            
            world.setGameRule(GameRule.SHOW_DEATH_MESSAGES, false);
            
            plugin.getLogger().info("Configured game rules for TNT Run: instant respawn enabled");
        }
    }
    
    private void restoreGameRules() {
        World world = centerBlock1.getWorld();
        if (world != null) {
            
            world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, false);
            world.setGameRule(GameRule.KEEP_INVENTORY, false);
            world.setGameRule(GameRule.SHOW_DEATH_MESSAGES, true);
            
            plugin.getLogger().info("Restored default game rules after TNT Run");
        }
    }
    
    private void sendRules(List<Player> players) {
        String rules = "§c§lTNT RUN RULES:\n" +
                      "§eTNT disappears 0.5 seconds after touching it.\n" +
                      "§eDon't stand still for too long!\n" +
                      "§eLast one to survive wins this game.\n" +
                      "§eIf you touch the lava you lose.";
        
        for (Player player : players) {
            player.sendMessage(rules);
        }
    }
    
    private void startCountdown(List<Player> players) {
        countdownTask = new BukkitRunnable() {
            int countdown = 10;
            
            @Override
            public void run() {
                if (countdown > 0) {
                    
                    String message = "§c§lGame starts in: §f§l" + countdown;
                    for (Player player : players) {
                        if (player.isOnline()) {
                            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, 
                                new TextComponent(message));
                        }
                    }
                    countdown--;
                } else {
                    
                    playersAreFrozen = false; 
                    
                    for (Player player : players) {
                        if (player.isOnline()) {
                            player.setWalkSpeed(0.2f); 
                            player.setFlySpeed(0.1f);
                            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, 
                                new TextComponent("§a§lGO! RUN!"));
                        }
                    }
                    
                    
                    scheduleBedrocRemoval();
                    
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); 
    }
    
    private void scheduleBedrocRemoval() {
        
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : alivePlayers) {
                    if (player.isOnline()) {
                        player.sendMessage("§e§lWarning: Bedrock centers will be removed in 3 seconds!");
                    }
                }
            }
        }.runTaskLater(plugin, 40L); 
        
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : alivePlayers) {
                    if (player.isOnline()) {
                        player.sendMessage("§c§lWarning: Bedrock centers will be removed in 2 seconds!");
                    }
                }
            }
        }.runTaskLater(plugin, 60L); 
        
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : alivePlayers) {
                    if (player.isOnline()) {
                        player.sendMessage("§4§lWarning: Bedrock centers will be removed in 1 second!");
                    }
                }
            }
        }.runTaskLater(plugin, 80L); 
        
        
        new BukkitRunnable() {
            @Override
            public void run() {
                removeBedockCenters();
                for (Player player : alivePlayers) {
                    if (player.isOnline()) {
                        player.sendMessage("§c§lBedrock centers have been removed!");
                    }
                }
            }
        }.runTaskLater(plugin, 100L); 
    }
    
    private void removeBedockCenters() {
        
        centerBlock1.getBlock().setType(Material.AIR);
        centerBlock2.getBlock().setType(Material.AIR);
        centerBlock3.getBlock().setType(Material.AIR);
        
        plugin.getLogger().info("Removed bedrock centers from TNT Run arena");
    }
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!gameActive) return;
        
        Player player = event.getPlayer();
        if (!alivePlayers.contains(player)) return;
        
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        
        
        if (playersAreFrozen) {
            
            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                
                double distanceFromCenter = to.distance(teleportLocation);
                
                
                if (distanceFromCenter > 0.5) {
                    event.setCancelled(true);
                    
                    
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.teleport(teleportLocation);
                    });
                    
                    
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, 
                        new TextComponent("§c§lSTAY IN THE CENTER! §e(" + String.format("%.1f", distanceFromCenter) + "m from center)"));
                } else {
                    
                    event.setCancelled(true);
                }
            }
            return; 
        }
        
        
        Location playerLocation = to.clone();
        
        
        boolean touchingTnt = false;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Location checkLocation = playerLocation.clone().add(x * 0.3, -1, z * 0.3);
                if (checkLocation.getBlock().getType() == Material.TNT) {
                    scheduleTntRemoval(checkLocation);
                    touchingTnt = true;
                }
            }
        }
        
        
        Location blockBelow = to.clone().subtract(0, 1, 0);
        if (blockBelow.getBlock().getType() == Material.TNT) {
            scheduleTntRemoval(blockBelow);
            touchingTnt = true;
        }
        
        
        if (to.getY() < 45) {
            
            event.setCancelled(true);
            eliminatePlayer(player);
            
            
        }
    }
    
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!gameActive) return;
        
        
        Material blockType = event.getBlock().getType();
        if (blockType == Material.TNT || blockType == Material.BEDROCK) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cYou cannot break blocks during TNT Run!");
        }
    }
    
    @EventHandler
    private void scheduleTntRemoval(Location tntLocation) {
        
        if (scheduledRemovals.contains(tntLocation)) {
            return;
        }
        
        scheduledRemovals.add(tntLocation);
        
        new BukkitRunnable() {
            @Override
            public void run() {
                
                if (tntLocation.getBlock().getType() == Material.TNT) {
                    
                    World world = tntLocation.getWorld();
                    if (world != null) {
                        
                        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, 
                            tntLocation.clone().add(0.5, 1, 0.5), 
                            5, 
                            0.3, 0.1, 0.3, 
                            0.02); 
                        
                        
                        world.spawnParticle(Particle.ASH, 
                            tntLocation.clone().add(0.5, 1, 0.5), 
                            3, 
                            0.2, 0.1, 0.2, 
                            0.01); 
                    }
                    
                    
                    tntLocation.getBlock().setType(Material.AIR);
                }
                scheduledRemovals.remove(tntLocation);
            }
        }.runTaskLater(plugin, 10L); 
    }
    
    private void createAlivePlayersScoreboard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;
        
        Scoreboard scoreboard = manager.getNewScoreboard();
        
        
        Objective objective = scoreboard.registerNewObjective("tntrun_alive", org.bukkit.scoreboard.Criteria.DUMMY, "§c§lTNT Run");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setScoreboard(scoreboard);
        }
        
        
        updateAlivePlayersScoreboard();
    }
    
    private void updateAlivePlayersScoreboard() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Scoreboard scoreboard = player.getScoreboard();
            if (scoreboard == null) continue;
            
            Objective objective = scoreboard.getObjective("tntrun_alive");
            if (objective == null) continue;
            
            
            for (String entry : scoreboard.getEntries()) {
                scoreboard.resetScores(entry);
            }
            
            
            int aliveCount = 0;
            for (Player p : alivePlayers) {
                if (p.isOnline() && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)) {
                    aliveCount++;
                }
            }
            
            
            objective.getScore("§ePlayers Alive: §f" + aliveCount).setScore(1);
        }
    }
    
    private void eliminatePlayer(Player player) {
        if (!alivePlayers.contains(player)) return;
        
        alivePlayers.remove(player);
        eliminatedPlayers.add(player); 
        
        
        player.setGameMode(GameMode.SPECTATOR);
        player.sendMessage("§c§lYou have been eliminated from TNT Run!");
        player.sendMessage("§7You are now in spectator mode. You'll be returned to adventure mode when the game ends.");
        
        
        updateAlivePlayersScoreboard();
        
        
        player.teleport(teleportLocation.clone().add(0, 5, 0));
        
        
        int placement = eliminatedPlayers.size() + alivePlayers.size(); 
        String placementText = getPlacementText(placement);
        
        String message = "§e" + player.getName() + " §chas been eliminated! " + placementText + " §7(" + 
                        alivePlayers.size() + " players remaining)";
        
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(message);
        }
        
        
        checkGameEnd();
    }
    
    private String getPlacementText(int placement) {
        switch (placement) {
            case 1: return "§6§l1st Place!";
            case 2: return "§e§l2nd Place!";
            case 3: return "§c§l3rd Place!";
            case 4: return "§74th Place";
            case 5: return "§75th Place";
            default: return "§7" + placement + "th Place";
        }
    }
    
    private void checkGameEnd() {
        if (alivePlayers.size() <= 1) {
            endGame();
        } else if (alivePlayers.size() == 0) {
            
            Bukkit.broadcastMessage("§c§lAll players have been eliminated! Ending TNT Run...");
            endGameWithNoWinners();
        }
    }
    
    private void endGame() {
        gameActive = false;
        playersAreFrozen = false; 
        
        
        PlayerMoveEvent.getHandlerList().unregister(this);
        
        
        if (countdownTask != null && !countdownTask.isCancelled()) {
            countdownTask.cancel();
        }
        
        
        restoreGameRules();
        
        
        awardPointsAndResetPlayers();
        
        
        alivePlayers.clear();
        eliminatedPlayers.clear();
        scheduledRemovals.clear();
        
        plugin.getLogger().info("TNT Run game ended");
    }
    
    private void endGameWithNoWinners() {
        gameActive = false;
        playersAreFrozen = false; 
        
        
        PlayerMoveEvent.getHandlerList().unregister(this);
        
        
        if (countdownTask != null && !countdownTask.isCancelled()) {
            countdownTask.cancel();
        }
        
        
        restoreGameRules();
        
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                player.setGameMode(GameMode.ADVENTURE);
                player.sendMessage("§aYou have been returned to adventure mode!");
                
                
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 255, false, false));
            }
            
            
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            
            
            player.removePotionEffect(PotionEffectType.RESISTANCE);
            
            
            player.setWalkSpeed(0.2f);
            player.setFlySpeed(0.1f);
        }
        
        
        teleportPlayersToSpawn();
        
        
        alivePlayers.clear();
        eliminatedPlayers.clear();
        scheduledRemovals.clear();
        
        plugin.getLogger().info("TNT Run game ended with no winners - all players eliminated");
    }
    
    private void awardPointsAndResetPlayers() {
        try {
            File file = new File(plugin.getDataFolder(), "winners.json");
            
            
            Map<String, PlayerStats> playerData = new HashMap<>();
            
            if (file.exists()) {
                try (FileReader reader = new FileReader(file)) {
                    Type type = new TypeToken<Map<String, PlayerStats>>(){}.getType();
                    Map<String, PlayerStats> existingData = gson.fromJson(reader, type);
                    if (existingData != null) {
                        playerData.putAll(existingData);
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
                
                
                
                
                
                player.removePotionEffect(PotionEffectType.RESISTANCE);
                
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
                
            }
            
            
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(playerData, writer);
            }
            
            
            if (!survivors.isEmpty()) {
                Player winner = survivors.get(0);
                String winMessage = "§6§l" + winner.getName() + " §ewins TNT Run! §6(+5 points)";
                
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendMessage(winMessage);
                }
                
                
                celebrateWinner(winner);
            } else {
                
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendMessage("§c§lTNT Run ended with no survivors!");
                }
                
                
                teleportPlayersToSpawn();
            }
            
            plugin.getLogger().info("Awarded points to all TNT Run participants");
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to award points: " + e.getMessage());
            e.printStackTrace();
            
            
            teleportPlayersToSpawn();
        }
    }
    
    private void celebrateWinner(Player winner) {
        
        Location winnerLoc = winner.getLocation();
        
        
        new BukkitRunnable() {
            int count = 0;
            
            @Override
            public void run() {
                if (count >= 10) {
                    
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            teleportPlayersToSpawn();
                        }
                    }.runTaskLater(plugin, 40L); 
                    
                    cancel();
                    return;
                }
                
                
                Location fireworkLoc = winnerLoc.clone().add(
                    (Math.random() - 0.5) * 6, 
                    Math.random() * 3 + 1,     
                    (Math.random() - 0.5) * 6  
                );
                
                Firework firework = (Firework) fireworkLoc.getWorld().spawnEntity(fireworkLoc, EntityType.FIREWORK_ROCKET);
                FireworkMeta meta = firework.getFireworkMeta();
                
                
                Color[] colors = {Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW, Color.PURPLE, Color.ORANGE, Color.WHITE};
                Color color1 = colors[(int) (Math.random() * colors.length)];
                Color color2 = colors[(int) (Math.random() * colors.length)];
                
                FireworkEffect effect = FireworkEffect.builder()
                    .withColor(color1, color2)
                    .with(FireworkEffect.Type.BALL_LARGE)
                    .withFlicker()
                    .withTrail()
                    .build();
                
                meta.addEffect(effect);
                meta.setPower(1);
                firework.setFireworkMeta(meta);
                
                count++;
            }
        }.runTaskTimer(plugin, 0L, 6L); 
    }
    
    private void teleportPlayersToSpawn() {
        Location spawn = Bukkit.getWorlds().get(0).getSpawnLocation();
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            
            player.setWalkSpeed(0.2f);
            player.setFlySpeed(0.1f);
            
            
            if (player.getGameMode() == GameMode.SPECTATOR) {
                player.setGameMode(GameMode.ADVENTURE);
                player.sendMessage("§aYou have been returned to adventure mode!");
                
                
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 255, false, false));
            }
            
            
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
            
            
            PlayerMoveEvent.getHandlerList().unregister(this);
            
            
            if (countdownTask != null && !countdownTask.isCancelled()) {
                countdownTask.cancel();
            }
            
            
            restoreGameRules();
            
            
            for (Player player : alivePlayers) {
                if (player.isOnline()) {
                    player.setWalkSpeed(0.2f);
                    player.setFlySpeed(0.1f);
                    
                    
                    
                    player.removePotionEffect(PotionEffectType.RESISTANCE);
                }
            }
            
            
            for (Player player : eliminatedPlayers) {
                if (player.isOnline()) {
                    player.setWalkSpeed(0.2f);
                    player.setFlySpeed(0.1f);
                    
                    
                    
                    player.removePotionEffect(PotionEffectType.RESISTANCE);
                }
            }
            
            
            teleportPlayersToSpawn();
            
            alivePlayers.clear();
            eliminatedPlayers.clear();
            scheduledRemovals.clear();
            
            plugin.getLogger().info("TNT Run game force ended");
        }
    }
}
