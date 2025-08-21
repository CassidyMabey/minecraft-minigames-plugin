package com.minecraft.queueplugin.minigames;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
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

public class Parkour implements Listener {
    
    
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
    
    private final Location teleportLocation = new Location(null, 315.5, 62.0, 8.5);
    private final Set<Player> alivePlayers = new HashSet<>();
    private final List<Player> finishers = new ArrayList<>(); 
    private BukkitTask countdownTask = null;
    private BukkitTask finalCountdownTask = null;
    private BukkitTask scoreboardUpdateTask = null;
    
    public Parkour(JavaPlugin plugin) {
        this.plugin = plugin;
    }
    
    public void setGameEndCallback(GameEndCallback callback) {
        this.gameEndCallback = callback;
    }
    
    public void startGame(List<Player> players) {
        plugin.getLogger().info("Starting Parkour minigame with " + players.size() + " players");
        
        gameActive = true;
        playersAreFrozen = true;
        freezeTimeLeft = 3;
        alivePlayers.clear();
        finishers.clear();
        
        
        World world = Bukkit.getWorlds().get(0);
        teleportLocation.setWorld(world);
        
        
        Bukkit.getPluginManager().registerEvents(this, plugin);
        
        
        for (Player player : players) {
            if (player.isOnline()) {
                alivePlayers.add(player);
                
                
                player.teleport(teleportLocation);
                
                
                player.setGameMode(GameMode.ADVENTURE);
                
                
                player.getInventory().clear();
                player.getInventory().setArmorContents(null);
                
                ItemStack blackBoots = new ItemStack(Material.LEATHER_BOOTS);
                LeatherArmorMeta bootsMeta = (LeatherArmorMeta) blackBoots.getItemMeta();
                if (bootsMeta != null) {
                    bootsMeta.setColor(Color.BLACK);
                    blackBoots.setItemMeta(bootsMeta);
                }
                player.getInventory().setBoots(blackBoots);
                
                
                player.setWalkSpeed(0f);
                player.setFlySpeed(0f);
                
                
                player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 255, false, false));
                
                
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, 
                    new TextComponent("§c§lFROZEN! Stay in center until game starts!"));
            }
        }
        
        
        sendRules(players);
        
        
        startCountdown();
    }
    
    private void sendRules(List<Player> players) {
        for (Player player : players) {
            if (player.isOnline()) {
                player.sendMessage("§6§l   PARKOUR RULES   ");
                player.sendMessage("§e• Complete the parkour course as fast as possible!");
                player.sendMessage("§e• First 5 players to reach the §agreen concrete§e win!");
                player.sendMessage("§e• If you fall or die, you'll respawn at the starting line!");
                player.sendMessage("§e• You only lose if you don't finish before time runs out!");
                player.sendMessage("§e• Stay invisible and don't move until the countdown ends!");
                player.sendMessage("§6§l                     ");
            }
        }
    }
    
    private void startCountdown() {
        countdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (freezeTimeLeft > 0) {
                    
                    for (Player player : alivePlayers) {
                        if (player.isOnline()) {
                            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, 
                                new TextComponent("§c§lSTAY STILL! §e" + freezeTimeLeft + "s until start!"));
                        }
                    }
                    
                    
                    String message = "§e§lParkour starts in " + freezeTimeLeft + "s!";
                    Bukkit.broadcastMessage(message);
                    
                    freezeTimeLeft--;
                } else {
                    
                    playersAreFrozen = false;
                    
                    for (Player player : alivePlayers) {
                        if (player.isOnline()) {
                            
                            player.setWalkSpeed(0.2f);
                            player.setFlySpeed(0.1f);
                            
                            
                            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, 
                                new TextComponent("§a§lGO! Complete the parkour!"));
                        }
                    }
                    
                    Bukkit.broadcastMessage("§a§lPARKOUR HAS STARTED! First 5 to reach the green concrete win!");
                    
                    
                    scoreboardUpdateTask = new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (gameActive && !playersAreFrozen) {
                                updateParkourScoreboard();
                            } else {
                                this.cancel();
                            }
                        }
                    }.runTaskTimer(plugin, 0L, 40L); 
                    
                    plugin.getLogger().info("Parkour freeze period ended, players can now move");
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); 
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
        
        
        if (to.getY() < 55) {
            respawnPlayer(player);
            return;
        }
        
        
        Location checkLoc = to.clone();
        checkLoc.setY(checkLoc.getY() - 1); 
        
        if (checkLoc.getBlock().getType() == Material.GREEN_CONCRETE) {
            finishPlayer(player);
        }
    }
    
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!gameActive) return;
        
        Player player = event.getEntity();
        if (!alivePlayers.contains(player)) return;
        
        
        event.getDrops().clear(); 
        event.setDroppedExp(0); 
        
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && alivePlayers.contains(player)) {
                respawnPlayer(player);
            }
        }, 1L); 
    }
    
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!gameActive) return;
        
        Player player = event.getPlayer();
        if (!alivePlayers.contains(player)) return;
        
        
        event.setRespawnLocation(teleportLocation);
        
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && alivePlayers.contains(player)) {
                
                player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1, true, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 255, true, false));
                
                
                player.sendMessage("§e§lYou died! Respawned at the starting line.");
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, 
                    new TextComponent("§6§lRESPAWNED! §eTry again!"));
            }
        }, 1L);
    }
    
    private void respawnPlayer(Player player) {
        if (!alivePlayers.contains(player)) return;
        
        
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        
        
        player.teleport(teleportLocation);
        
        
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1, true, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 255, true, false));
        
        
        player.sendMessage("§e§lYou fell! Respawned at the starting line.");
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, 
            new TextComponent("§6§lRESPAWNED! §eTry again!"));
    }
    
    private void eliminatePlayer(Player player) {
        if (!alivePlayers.contains(player)) return;
        
        alivePlayers.remove(player);
        
        
        player.setGameMode(GameMode.SPECTATOR);
        
        
        player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 255, true, false));
        
        player.sendMessage("§c§lYou have been eliminated from Parkour!");
        player.sendMessage("§7Time ran out before you could finish. You are now in spectator mode.");
        
        
        player.teleport(teleportLocation.clone().add(0, 10, 0));
        
        
        String message = "§e" + player.getName() + " §chas been eliminated from Parkour! §7(" + 
                        alivePlayers.size() + " players remaining)";
        
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(message);
        }
        
        
        updateParkourScoreboard();
        
        
        checkForFinalCountdown();
    }
    
    private void finishPlayer(Player player) {
        if (!alivePlayers.contains(player) || finishers.contains(player)) return;
        
        alivePlayers.remove(player);
        finishers.add(player);
        
        int placement = finishers.size();
        String placementText = getPlacementText(placement);
        
        
        player.setGameMode(GameMode.SPECTATOR);
        
        
        player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 255, true, false));
        
        
        String message = "§a§l" + player.getName() + " finished in " + placementText + "!";
        Bukkit.broadcastMessage(message);
        
        player.sendMessage("§a§lCongratulations! You finished in " + placementText + "!");
        player.sendMessage("§7You are now in spectator mode watching the remaining players.");
        
        
        updateParkourScoreboard();
        
        
        if (placement == 5) {
            Bukkit.broadcastMessage("§e§lTop 5 have finished! Starting 1-minute countdown for remaining players...");
            startFinalCountdown();
        }
        
        plugin.getLogger().info("Parkour: " + player.getName() + " finished in " + placement + " place");
    }
    
    private String getPlacementText(int placement) {
        return switch (placement) {
            case 1 -> "1st place";
            case 2 -> "2nd place"; 
            case 3 -> "3rd place";
            default -> placement + "th place";
        };
    }
    
    private void checkForFinalCountdown() {
        
        if (alivePlayers.size() == 0) {
            Bukkit.broadcastMessage("§c§lAll players have been eliminated! Ending Parkour...");
            endGameWithNoWinners();
            return;
        }
        
        
        if (alivePlayers.size() <= 5 && finalCountdownTask == null && finishers.size() < 5) {
            Bukkit.broadcastMessage("§e§l5 or fewer players remaining! Starting 1-minute final countdown...");
            startFinalCountdown();
        }
    }
    
    private void startFinalCountdown() {
        if (finalCountdownTask != null) return; 
        
        final int[] timeLeft = {60}; 
        
        finalCountdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (timeLeft[0] > 0) {
                    
                    if (timeLeft[0] % 15 == 0 || timeLeft[0] <= 10) {
                        String message = "§c§lFINAL COUNTDOWN: " + timeLeft[0] + "s remaining!";
                        Bukkit.broadcastMessage(message);
                        
                        
                        for (Player player : alivePlayers) {
                            if (player.isOnline()) {
                                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, 
                                    new TextComponent("§c§l" + timeLeft[0] + "s to finish!"));
                            }
                        }
                    }
                    
                    timeLeft[0]--;
                } else {
                    
                    Bukkit.broadcastMessage("§c§lTIME'S UP! All remaining players are eliminated!");
                    
                    
                    Set<Player> remainingPlayers = new HashSet<>(alivePlayers);
                    for (Player player : remainingPlayers) {
                        if (player.isOnline()) {
                            eliminatePlayer(player);
                        }
                    }
                    
                    
                    endGame();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); 
    }
    
    private void endGame() {
        gameActive = false;
        playersAreFrozen = false;
        
        
        if (countdownTask != null && !countdownTask.isCancelled()) {
            countdownTask.cancel();
        }
        if (finalCountdownTask != null && !finalCountdownTask.isCancelled()) {
            finalCountdownTask.cancel();
        }
        if (scoreboardUpdateTask != null && !scoreboardUpdateTask.isCancelled()) {
            scoreboardUpdateTask.cancel();
        }
        
        Bukkit.broadcastMessage("§a§lParkour has ended!");
        
        
        awardPointsAndResetPlayers();
        
        
        new BukkitRunnable() {
            @Override
            public void run() {
                teleportPlayersToSpawn();
            }
        }.runTaskLater(plugin, 100L); 
        
        plugin.getLogger().info("Parkour game ended");
    }
    
    private void endGameWithNoWinners() {
        gameActive = false;
        playersAreFrozen = false;
        
        
        if (countdownTask != null && !countdownTask.isCancelled()) {
            countdownTask.cancel();
        }
        if (finalCountdownTask != null && !finalCountdownTask.isCancelled()) {
            finalCountdownTask.cancel();
        }
        if (scoreboardUpdateTask != null && !scoreboardUpdateTask.isCancelled()) {
            scoreboardUpdateTask.cancel();
        }
        
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                player.setGameMode(GameMode.ADVENTURE);
                player.sendMessage("§aYou have been returned to adventure mode!");
                
                
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 255, false, false));
            }
            
            
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            
            
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
            
            
            player.setWalkSpeed(0.2f);
            player.setFlySpeed(0.1f);
        }
        
        
        teleportPlayersToSpawn();
        
        
        alivePlayers.clear();
        finishers.clear();
        
        plugin.getLogger().info("Parkour game ended with no winners - all players eliminated");
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
                        playerData = existingData;
                    }
                } catch (IOException e) {
                    plugin.getLogger().severe("Failed to read winners.json: " + e.getMessage());
                }
            }
            
            
            for (int i = 0; i < finishers.size(); i++) {
                Player player = finishers.get(i);
                int placement = i + 1; 
                int points = Math.max(0, 6 - placement); 
                
                if (points > 0) {
                    PlayerStats stats = playerData.getOrDefault(player.getName(), new PlayerStats(player.getName(), 0));
                    stats.points += points;
                    playerData.put(player.getName(), stats);
                    
                    String placementText = getPlacementText(placement);
                    player.sendMessage("§a§lPARKOUR FINISHED! " + placementText + " §a(+§e" + points + "§a points)");
                }
            }
            
            
            List<Player> remainingPlayers = new ArrayList<>(alivePlayers);
            for (Player player : remainingPlayers) {
                if (!finishers.contains(player)) { 
                    int placement = finishers.size() + 1; 
                    int points = Math.max(0, 6 - placement); 
                    
                    if (points > 0) {
                        PlayerStats stats = playerData.getOrDefault(player.getName(), new PlayerStats(player.getName(), 0));
                        stats.points += points;
                        playerData.put(player.getName(), stats);
                        
                        String placementText = getPlacementText(placement);
                        player.sendMessage("§c§lYou finished in " + placementText + " §c(+§e" + points + "§c points)");
                    } else {
                        player.sendMessage("§c§lParkour ended! §7You didn't finish in the top 5.");
                    }
                }
            }
            
            
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(playerData, writer);
            }
            
            plugin.getLogger().info("Awarded points to all Parkour participants");
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to award points: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void updateParkourScoreboard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;
        
        Scoreboard scoreboard = manager.getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("parkour", Criteria.DUMMY, "§6§lPARKOUR");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        
        
        int aliveCount = alivePlayers.size();
        objective.getScore("§e§lAlive Players: §f" + aliveCount).setScore(15);
        objective.getScore("").setScore(14); 
        
        
        List<Player> sortedPlayers = new ArrayList<>(alivePlayers);
        sortedPlayers.sort((p1, p2) -> Double.compare(p2.getLocation().getX(), p1.getLocation().getX()));
        
        
        objective.getScore("§a§lCurrent Positions:").setScore(13);
        
        for (int i = 0; i < Math.min(10, sortedPlayers.size()); i++) {
            Player p = sortedPlayers.get(i);
            int position = i + 1;
            String positionText = getPositionText(position);
            
            
            String displayText = positionText + " §f" + p.getName();
            if (i > 0) {
                Player prevPlayer = sortedPlayers.get(i - 1);
                if (Math.abs(p.getLocation().getX() - prevPlayer.getLocation().getX()) < 0.5) {
                    displayText = "§7Joint " + getPositionText(i) + " §f" + p.getName();
                }
            }
            
            objective.getScore(displayText).setScore(12 - i);
        }
        
        
        if (!finishers.isEmpty()) {
            objective.getScore("§2§lFinished:").setScore(2);
            for (int i = 0; i < finishers.size(); i++) {
                Player finisher = finishers.get(i);
                String finishedText = getPlacementText(i + 1) + " §a" + finisher.getName();
                objective.getScore(finishedText).setScore(1 - i);
            }
        }
        
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setScoreboard(scoreboard);
        }
    }
    
    private String getPositionText(int position) {
        return switch (position) {
            case 1 -> "§6" + position;
            case 2 -> "§e" + position;
            case 3 -> "§c" + position;
            default -> "§7" + position;
        };
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
            
            
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
            
            
            
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
            
            
            PlayerMoveEvent.getHandlerList().unregister(this);
            
            
            if (countdownTask != null && !countdownTask.isCancelled()) {
                countdownTask.cancel();
            }
            if (finalCountdownTask != null && !finalCountdownTask.isCancelled()) {
                finalCountdownTask.cancel();
            }
            if (scoreboardUpdateTask != null && !scoreboardUpdateTask.isCancelled()) {
                scoreboardUpdateTask.cancel();
            }
            
            
            for (Player player : alivePlayers) {
                if (player.isOnline()) {
                    player.setWalkSpeed(0.2f);
                    player.setFlySpeed(0.1f);
                    
                    
                    player.removePotionEffect(PotionEffectType.INVISIBILITY);
                }
            }
            
            
            for (Player player : finishers) {
                if (player.isOnline()) {
                    player.setWalkSpeed(0.2f);
                    player.setFlySpeed(0.1f);
                    
                    
                    player.removePotionEffect(PotionEffectType.INVISIBILITY);
                }
            }
            
            
            teleportPlayersToSpawn();
            
            alivePlayers.clear();
            finishers.clear();
            
            plugin.getLogger().info("Parkour game force ended");
        }
    }
}
