package com.minecraft.queueplugin.minigames;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
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
import java.util.stream.Collectors;

public class SuperSmashSteves implements Listener {
    
    
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
    private int gameTimeLeft = 300; 
    
    private final Location teleportLocation = new Location(null, 356.5, 79, 183.5); 
    private final Set<Player> alivePlayers = new HashSet<>();
    private final Map<Player, Integer> playerKnockback = new HashMap<>(); 
    private final Map<Player, ArmorStand> playerHolograms = new HashMap<>(); 
    private BukkitTask gameTask = null;
    private BukkitTask scoreboardUpdateTask = null;
    private BukkitTask positionCheckTask = null;
    private BukkitTask hologramUpdateTask = null;
    
    private static final int DEATH_Y_LEVEL = 75; 
    
    public SuperSmashSteves(JavaPlugin plugin) {
        this.plugin = plugin;
        
        
        World world = Bukkit.getWorlds().get(0);
        teleportLocation.setWorld(world);
    }
    
    public void setGameEndCallback(GameEndCallback callback) {
        this.gameEndCallback = callback;
    }
    
    public void startGame(List<Player> players) {
        plugin.getLogger().info("Starting Super Smash Steves minigame with " + players.size() + " players");
        
        gameActive = true;
        playersAreFrozen = true;
        freezeTimeLeft = 3;
        gameTimeLeft = 300; 
        alivePlayers.clear();
        playerKnockback.clear();
        
        
        clearPlayerHolograms();
        
        
        Bukkit.getPluginManager().registerEvents(this, plugin);
        
        
        for (Player player : players) {
            if (player.isOnline()) {
                alivePlayers.add(player);
                playerKnockback.put(player, 0); 
                
                // Clear ALL potion effects before starting the game
                for (PotionEffect effect : player.getActivePotionEffects()) {
                    player.removePotionEffect(effect.getType());
                }
                
                player.getInventory().clear();
                player.getInventory().setArmorContents(new ItemStack[4]); 
                
                
                player.setGameMode(GameMode.ADVENTURE);
                
                
                player.teleport(teleportLocation);
                
                
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 1, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 255, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 255, false, false));
                
                
                player.getInventory().clear(); 
                player.getInventory().setItem(0, new ItemStack(Material.WOODEN_SWORD, 1));
                
                player.sendMessage("§c§lSUPER SMASH STEVES HAS STARTED!");
                player.sendMessage("§7Fight to be one of the last 5 Steves standing!");
                player.sendMessage("§7If you fall below Y75, you're out!");
                player.sendMessage("§7You have ONLY a wooden sword - no armor, no other items!");
            }
        }
        
        Bukkit.broadcastMessage("§c§lSUPER SMASH STEVES HAS STARTED!");
        Bukkit.broadcastMessage("§7Fight to be one of the last 5 standing! Falling below Y75 = elimination!");
        
        
        startFreezeCountdown();
        
        plugin.getLogger().info("Super Smash Steves game started successfully");
    }
    

    private void startFreezeCountdown() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (freezeTimeLeft > 0) {
                    
                    for (Player player : alivePlayers) {
                        if (player.isOnline()) {
                            player.sendTitle("§c§l" + freezeTimeLeft, "§7Get ready to fight!", 5, 15, 5);
                        }
                    }
                    freezeTimeLeft--;
                } else {
                    
                    playersAreFrozen = false;
                    
                    
                    for (Player player : alivePlayers) {
                        if (player.isOnline()) {
                            player.setGameMode(GameMode.SURVIVAL);
                            
                            
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                player.getInventory().clear();
                                player.getInventory().setArmorContents(new ItemStack[4]); 
                                player.getInventory().setItem(0, new ItemStack(Material.WOODEN_SWORD, 1));
                                
                                
                                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 255, false, false));
                                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 255, false, false));
                            });
                            
                            player.sendTitle("§a§lFIGHT!", "§eThe battle has begun!", 5, 15, 5);
                        }
                    }
                    
                    Bukkit.broadcastMessage("§a§lLET THE BATTLE BEGIN!");
                    
                    
                    startGameTimer();
                    startScoreboardUpdates();
                    startPositionChecking();
                    startHologramUpdates();
                    
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
                
                
                if (gameTimeLeft == 60) {
                    Bukkit.broadcastMessage("§e§l1 minute remaining!");
                } else if (gameTimeLeft == 30) {
                    Bukkit.broadcastMessage("§e§l30 seconds remaining!");
                } else if (gameTimeLeft == 10) {
                    Bukkit.broadcastMessage("§c§l10 seconds remaining!");
                } else if (gameTimeLeft <= 5 && gameTimeLeft > 0) {
                    for (Player player : alivePlayers) {
                        if (player.isOnline()) {
                            player.sendTitle("§c§l" + gameTimeLeft, "§7Time's almost up!", 5, 10, 5);
                        }
                    }
                } else if (gameTimeLeft <= 0) {
                    
                    endGameByTime();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); 
    }
    
    private void startScoreboardUpdates() {
        scoreboardUpdateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (gameActive) {
                updateSuperSmashStevesScoreboard();
            }
        }, 20L, 40L); 
    }
    
    private void startPositionChecking() {
        positionCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameActive) {
                    cancel();
                    return;
                }
                
                
                Iterator<Player> iterator = alivePlayers.iterator();
                while (iterator.hasNext()) {
                    Player player = iterator.next();
                    if (!player.isOnline()) {
                        iterator.remove();
                        continue;
                    }
                    
                    if (player.getLocation().getY() < DEATH_Y_LEVEL) {
                        
                        iterator.remove();
                        eliminatePlayer(player);
                    }
                }
                
                
                checkGameEnd();
            }
        }.runTaskTimer(plugin, 0L, 5L); 
    }
    
    private void clearPlayerHolograms() {
        for (ArmorStand hologram : playerHolograms.values()) {
            if (hologram != null && !hologram.isDead()) {
                hologram.remove();
            }
        }
        playerHolograms.clear();
    }
    
    private void createPlayerHologram(Player player) {
        
        ArmorStand existingHologram = playerHolograms.get(player);
        if (existingHologram != null && !existingHologram.isDead()) {
            existingHologram.remove();
        }
        
        
        Location hologramLoc = player.getLocation().add(0, 2.5, 0);
        ArmorStand hologram = (ArmorStand) player.getWorld().spawnEntity(hologramLoc, EntityType.ARMOR_STAND);
        
        hologram.setVisible(false);
        hologram.setGravity(false);
        hologram.setCanPickupItems(false);
        hologram.setCustomNameVisible(true);
        hologram.setMarker(true); 
        
        
        updateHologramText(player, hologram);
        
        playerHolograms.put(player, hologram);
    }
    
    private void updateHologramText(Player player, ArmorStand hologram) {
        int knockbackStacks = playerKnockback.getOrDefault(player, 0);
        int percentage = 10 + (knockbackStacks * 25); 
        
        String color;
        if (percentage <= 10) {
            color = "§a"; 
        } else if (percentage <= 60) {
            color = "§e"; 
        } else if (percentage <= 110) {
            color = "§6"; 
        } else {
            color = "§c"; 
        }
        
        hologram.setCustomName(color + "+" + percentage + "% Knockback");
        
        
        String originalName = player.getName();
        player.setDisplayName(color + originalName + " §7[+" + percentage + "%]");
        player.setPlayerListName(color + originalName + " §7[+" + percentage + "%]");
    }
    
    private void startHologramUpdates() {
        hologramUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameActive) {
                    cancel();
                    return;
                }
                
                for (Player player : alivePlayers) {
                    if (!player.isOnline()) continue;
                    
                    ArmorStand hologram = playerHolograms.get(player);
                    if (hologram != null && !hologram.isDead()) {
                        
                        Location newLoc = player.getLocation().add(0, 2.5, 0);
                        hologram.teleport(newLoc);
                        
                        
                        updateHologramText(player, hologram);
                    } else {
                        
                        createPlayerHologram(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L); 
    }
    
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!gameActive || playersAreFrozen) return;
        
        if (!(event.getEntity() instanceof Player)) return;
        if (!(event.getDamager() instanceof Player)) return;
        
        Player damaged = (Player) event.getEntity();
        Player damager = (Player) event.getDamager();
        
        if (!alivePlayers.contains(damaged) || !alivePlayers.contains(damager)) return;
        
        
        int currentKnockback = playerKnockback.getOrDefault(damaged, 0);
        int newKnockback = currentKnockback + 1;
        playerKnockback.put(damaged, newKnockback);
        
        
        
        int percentage = 10 + (newKnockback * 25); 
        double knockbackMultiplier = percentage / 100.0; 
        
        
        org.bukkit.util.Vector knockbackDirection = damaged.getLocation().toVector()
                .subtract(damager.getLocation().toVector()).normalize();
        
        
        knockbackDirection.multiply(knockbackMultiplier);
        
        
        double upwardVelocity = 0.3 + (newKnockback * 0.15); 
        knockbackDirection.setY(Math.max(knockbackDirection.getY(), upwardVelocity));
        
        
        damaged.setVelocity(knockbackDirection);
        
        
        ArmorStand hologram = playerHolograms.get(damaged);
        if (hologram != null) {
            updateHologramText(damaged, hologram);
        }
        
        
        int currentPercentage = 10 + (newKnockback * 25);
        damager.sendMessage("§a§lHIT! §7" + damaged.getName() + " is now at §c" + currentPercentage + "% §7knockback!");
        damaged.sendMessage("§c§l" + currentPercentage + "% KNOCKBACK! §7You're getting more vulnerable!");
        
        
        if (currentPercentage >= 110) {
            Bukkit.broadcastMessage("§c§l" + damaged.getName() + " is at §4" + currentPercentage + "% knockback§c - extremely vulnerable!");
        } else if (currentPercentage >= 85) {
            Bukkit.broadcastMessage("§e" + damaged.getName() + " is getting dangerous with §c" + currentPercentage + "% knockback§e!");
        }
        
        
        event.setDamage(0);
        event.setCancelled(false); 
    }
    
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!gameActive) return;
        
        Player player = event.getEntity();
        if (!alivePlayers.contains(player)) return;
        
        
        eliminatePlayer(player);
        
        
        event.getDrops().clear();
        event.setDroppedExp(0);
    }
    
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!gameActive) return;
        
        Player player = event.getPlayer();
        
        
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!alivePlayers.contains(player)) {
                // Clear ALL potion effects before spectator mode
                for (PotionEffect effect : player.getActivePotionEffects()) {
                    player.removePotionEffect(effect.getType());
                }
                
                player.setGameMode(GameMode.SPECTATOR);
                player.teleport(teleportLocation.clone().add(0, 20, 0));
                
                
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 255, false, false));
            }
        });
    }
    
    private void eliminatePlayer(Player player) {
        if (!alivePlayers.contains(player)) return;
        
        alivePlayers.remove(player);
        
        
        ArmorStand hologram = playerHolograms.get(player);
        if (hologram != null && !hologram.isDead()) {
            hologram.remove();
        }
        playerHolograms.remove(player);
        
        // Clear ALL potion effects before elimination teleport
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        
        player.setGameMode(GameMode.SPECTATOR);
        player.sendMessage("§c§lYou have been eliminated from Super Smash Steves!");
        player.sendMessage("§7You are now in spectator mode. You'll be returned to adventure mode when the game ends.");
        
        
        player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 255, false, false));
        
        
        player.teleport(teleportLocation.clone().add(0, 10, 0));
        
        
        String message = "§c" + player.getName() + " §7has been eliminated! §c(" + 
                        alivePlayers.size() + " Steves remaining)";
        
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(message);
        }
        
        
        updateSuperSmashStevesScoreboard();
        
        
        checkGameEnd();
    }
    
    private void checkGameEnd() {
        if (alivePlayers.size() <= 1) {
            
            endGame();
        }
    }
    
    private void endGame() {
        gameActive = false;
        playersAreFrozen = false;
        
        
        if (gameTask != null && !gameTask.isCancelled()) {
            gameTask.cancel();
        }
        if (scoreboardUpdateTask != null && !scoreboardUpdateTask.isCancelled()) {
            scoreboardUpdateTask.cancel();
        }
        if (positionCheckTask != null && !positionCheckTask.isCancelled()) {
            positionCheckTask.cancel();
        }
        if (hologramUpdateTask != null && !hologramUpdateTask.isCancelled()) {
            hologramUpdateTask.cancel();
        }
        
        
        clearPlayerHolograms();
        
        
        PlayerDeathEvent.getHandlerList().unregister(this);
        PlayerRespawnEvent.getHandlerList().unregister(this);
        EntityDamageByEntityEvent.getHandlerList().unregister(this);
        
        Bukkit.broadcastMessage("§c§lSUPER SMASH STEVES HAS ENDED!");
        
        
        awardPointsAndResetPlayers();
    }
    
    private void endGameByTime() {
        gameActive = false;
        playersAreFrozen = false;
        
        
        if (gameTask != null && !gameTask.isCancelled()) {
            gameTask.cancel();
        }
        if (scoreboardUpdateTask != null && !scoreboardUpdateTask.isCancelled()) {
            scoreboardUpdateTask.cancel();
        }
        if (positionCheckTask != null && !positionCheckTask.isCancelled()) {
            positionCheckTask.cancel();
        }
        if (hologramUpdateTask != null && !hologramUpdateTask.isCancelled()) {
            hologramUpdateTask.cancel();
        }
        
        
        clearPlayerHolograms();
        
        
        PlayerDeathEvent.getHandlerList().unregister(this);
        PlayerRespawnEvent.getHandlerList().unregister(this);
        EntityDamageByEntityEvent.getHandlerList().unregister(this);
        
        Bukkit.broadcastMessage("§c§lSUPER SMASH STEVES ENDED - Time's up!");
        Bukkit.broadcastMessage("§7Top 5 surviving Steves win!");
        
        
        awardPointsAndResetPlayers();
    }
    
    private void updateSuperSmashStevesScoreboard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;
        
        Scoreboard board = manager.getNewScoreboard();
        Objective objective = board.registerNewObjective("smashsteves", Criteria.DUMMY, "§c§lSuper Smash Steves");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        
        int score = 15;
        
        
        int minutes = gameTimeLeft / 60;
        int seconds = gameTimeLeft % 60;
        objective.getScore("§7Time: §e" + minutes + ":" + String.format("%02d", seconds)).setScore(score--);
        objective.getScore("").setScore(score--);
        
        
        objective.getScore("§a§lAlive Players: " + alivePlayers.size()).setScore(score--);
        
        
        List<Map.Entry<Player, Integer>> sortedByKnockback = playerKnockback.entrySet().stream()
                .filter(entry -> alivePlayers.contains(entry.getKey()))
                .sorted(Map.Entry.<Player, Integer>comparingByValue().reversed())
                .collect(Collectors.toList());
        
        objective.getScore(" ").setScore(score--);
        objective.getScore("§6§lMost Vulnerable:").setScore(score--);
        
        int rank = 1;
        for (Map.Entry<Player, Integer> entry : sortedByKnockback) {
            if (rank > 5) break; 
            
            Player player = entry.getKey();
            int knockback = entry.getValue();
            
            String status = alivePlayers.contains(player) ? "§a" : "§c";
            objective.getScore(rank + ". " + status + player.getName() + " §7(" + knockback + " stacks)").setScore(score--);
            rank++;
        }
        
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setScoreboard(board);
        }
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
            
            
            if (alivePlayers.size() == 1) {
                Player winner = alivePlayers.iterator().next();
                PlayerStats stats = playerData.getOrDefault(winner.getName(), new PlayerStats(winner.getName(), 0));
                stats.points += 5; 
                playerData.put(winner.getName(), stats);
                
                winner.sendMessage("§a§lVICTORY! §a(+§e5§a points)");
                Bukkit.broadcastMessage("§6§l" + winner.getName() + " §aWON SUPER SMASH STEVES!");
            } else if (alivePlayers.size() > 1) {
                
                for (Player survivor : alivePlayers) {
                    survivor.sendMessage("§e§lYou survived! Game continues until there's one winner!");
                }
                Bukkit.broadcastMessage("§e§lGame continues! " + alivePlayers.size() + " survivors remaining!");
                return; 
            } else {
                
                Bukkit.broadcastMessage("§c§lNo survivors in Super Smash Steves!");
            }
            
            
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.isOnline()) {
                    player.removePotionEffect(PotionEffectType.NIGHT_VISION);
                    
                }
            }
            
            
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(playerData, writer);
            }
            
            plugin.getLogger().info("Awarded points to Super Smash Steves participants");
            
            
            Bukkit.getScheduler().runTaskLater(plugin, this::teleportPlayersToSpawn, 100L);
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to award points: " + e.getMessage());
            e.printStackTrace();
            
            
            teleportPlayersToSpawn();
        }
    }
    
    private String getOrdinal(int number) {
        String[] ordinals = {"1st", "2nd", "3rd", "4th", "5th"};
        if (number >= 1 && number <= 5) {
            return ordinals[number - 1];
        }
        return number + "th";
    }
    
    private void teleportPlayersToSpawn() {
        Location spawn = Bukkit.getWorlds().get(0).getSpawnLocation();
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            
            // Clear ALL potion effects before doing anything else
            for (PotionEffect effect : player.getActivePotionEffects()) {
                player.removePotionEffect(effect.getType());
            }
            
            player.setWalkSpeed(0.2f);
            player.setFlySpeed(0.1f);
            
            
            player.setDisplayName(player.getName());
            player.setPlayerListName(player.getName());
            
            
            if (player.getGameMode() != GameMode.ADVENTURE) {
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
    
    public boolean isGameActive() {
        return gameActive;
    }
    
    public void forceEnd() {
        if (gameActive) {
            gameActive = false;
            playersAreFrozen = false;
            
            
            if (gameTask != null && !gameTask.isCancelled()) {
                gameTask.cancel();
            }
            if (scoreboardUpdateTask != null && !scoreboardUpdateTask.isCancelled()) {
                scoreboardUpdateTask.cancel();
            }
            if (positionCheckTask != null && !positionCheckTask.isCancelled()) {
                positionCheckTask.cancel();
            }
            if (hologramUpdateTask != null && !hologramUpdateTask.isCancelled()) {
                hologramUpdateTask.cancel();
            }
            
            
            clearPlayerHolograms();
            
            
            PlayerDeathEvent.getHandlerList().unregister(this);
            PlayerRespawnEvent.getHandlerList().unregister(this);
            EntityDamageByEntityEvent.getHandlerList().unregister(this);
            
            
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.isOnline()) {
                    player.setWalkSpeed(0.2f);
                    player.setFlySpeed(0.1f);
                    player.removePotionEffect(PotionEffectType.NIGHT_VISION);
                    
                }
            }
            
            
            teleportPlayersToSpawn();
            
            alivePlayers.clear();
            playerKnockback.clear();
            
            plugin.getLogger().info("Super Smash Steves game force ended");
        }
    }
}
