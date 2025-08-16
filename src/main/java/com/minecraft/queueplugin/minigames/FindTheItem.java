package com.minecraft.queueplugin.minigames;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
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

public class FindTheItem implements Listener {
    
    
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
    
    
    private final Location minCorner = new Location(null, 155, 80, 239);
    private final Location maxCorner = new Location(null, 163, 80, 247);
    private final Location teleportLocation = new Location(null, 159, 81, 243); 
    
    private final Set<Player> alivePlayers = new HashSet<>();
    private final List<Player> finishers = new ArrayList<>(); 
    private final Map<Player, Material> playerTargetItems = new HashMap<>(); 
    private final List<Material> availableItems = new ArrayList<>();
    
    private BukkitTask gameTimer = null;
    private BukkitTask scoreboardUpdateTask = null;
    private int timeLeft = 180; 
    
    public FindTheItem(JavaPlugin plugin) {
        this.plugin = plugin;
        
        
        World world = Bukkit.getWorlds().get(0);
        minCorner.setWorld(world);
        maxCorner.setWorld(world);
        teleportLocation.setWorld(world);
        
        
        initializeAvailableItems();
    }
    
    private void initializeAvailableItems() {
        
        for (Material material : Material.values()) {
            if (material.isItem() && !material.isAir() && material != Material.BARRIER) {
                
                if (!material.name().contains("SPAWN_EGG") && 
                    !material.name().contains("COMMAND") &&
                    !material.name().contains("STRUCTURE") &&
                    material != Material.DEBUG_STICK &&
                    material != Material.KNOWLEDGE_BOOK) {
                    availableItems.add(material);
                }
            }
        }
        
        plugin.getLogger().info("Loaded " + availableItems.size() + " available items for Find the Item");
    }
    
    public void setGameEndCallback(GameEndCallback callback) {
        this.gameEndCallback = callback;
    }
    
    public boolean isGameActive() {
        return gameActive;
    }
    
    public void startGame(List<Player> players) {
        plugin.getLogger().info("Starting Find the Item minigame with " + players.size() + " players");
        
        gameActive = true;
        alivePlayers.clear();
        finishers.clear();
        playerTargetItems.clear();
        timeLeft = 180;
        
        
        Bukkit.getPluginManager().registerEvents(this, plugin);
        
        
        setupChestsWithItems();
        
        
        assignTargetItemsToPlayers(players);
        
        
        for (Player player : players) {
            if (player.isOnline()) {
                alivePlayers.add(player);
                
                
                player.getInventory().clear();
                player.getInventory().setArmorContents(null);
                
                
                player.setGameMode(GameMode.ADVENTURE);
                
                
                player.teleport(teleportLocation);
                
                
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 255, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 255, false, false));
                
                Material targetItem = playerTargetItems.get(player);
                player.sendMessage("§a§lFIND THE ITEM HAS STARTED!");
                player.sendMessage("§7Find and collect: §e" + formatItemName(targetItem) + "§7!");
                player.sendMessage("§7Search the chests in the area!");
            }
        }
        
        Bukkit.broadcastMessage("§a§lFIND THE ITEM HAS STARTED!");
        Bukkit.broadcastMessage("§7Players must find their assigned items in the chests!");
        Bukkit.broadcastMessage("§7First 5 players to find their item get points!");
        
        
        startGameTimer();
        
        
        startScoreboardUpdates();
        
        plugin.getLogger().info("Find the Item game started successfully");
    }
    
    private void setupChestsWithItems() {
        
        List<Chest> chests = new ArrayList<>();
        
        for (int x = minCorner.getBlockX(); x <= maxCorner.getBlockX(); x++) {
            for (int y = minCorner.getBlockY(); y <= maxCorner.getBlockY(); y++) {
                for (int z = minCorner.getBlockZ(); z <= maxCorner.getBlockZ(); z++) {
                    Block block = minCorner.getWorld().getBlockAt(x, y, z);
                    if (block.getType() == Material.CHEST) {
                        Chest chest = (Chest) block.getState();
                        
                        chest.getInventory().clear();
                        chests.add(chest);
                    }
                }
            }
        }
        
        plugin.getLogger().info("Found " + chests.size() + " chests in the area");
        
        if (chests.isEmpty()) {
            plugin.getLogger().warning("No chests found in the specified area!");
            return;
        }
        
        
        List<ChestSlot> availableSlots = new ArrayList<>();
        for (Chest chest : chests) {
            for (int slot = 0; slot < chest.getInventory().getSize(); slot++) {
                availableSlots.add(new ChestSlot(chest, slot));
            }
        }
        
        plugin.getLogger().info("Total available chest slots: " + availableSlots.size());
        
        
        Collections.shuffle(availableItems);
        
        
        Random random = new Random();
        for (Material item : availableItems) {
            if (availableSlots.isEmpty()) {
                plugin.getLogger().warning("Ran out of chest slots! Only placed " + 
                    (availableItems.indexOf(item)) + " items out of " + availableItems.size());
                break;
            }
            
            
            int randomIndex = random.nextInt(availableSlots.size());
            ChestSlot selectedSlot = availableSlots.remove(randomIndex);
            
            
            selectedSlot.chest.getInventory().setItem(selectedSlot.slot, new ItemStack(item, 1));
        }
        
        plugin.getLogger().info("Successfully distributed " + 
            Math.min(availableItems.size(), chests.size() * 27) + " different items across chests");
    }
    
    
    private static class ChestSlot {
        final Chest chest;
        final int slot;
        
        ChestSlot(Chest chest, int slot) {
            this.chest = chest;
            this.slot = slot;
        }
    }
    
    private void assignTargetItemsToPlayers(List<Player> players) {
        Random random = new Random();
        
        for (Player player : players) {
            if (player.isOnline()) {
                
                Material targetItem = availableItems.get(random.nextInt(availableItems.size()));
                playerTargetItems.put(player, targetItem);
                
                plugin.getLogger().info("Assigned " + targetItem + " to " + player.getName());
            }
        }
    }
    
    private void startGameTimer() {
        gameTimer = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!gameActive) return;
            
            timeLeft--;
            
            if (timeLeft <= 0) {
                endGame("§c§lTIME'S UP!");
            } else if (timeLeft == 60) {
                Bukkit.broadcastMessage("§e§l1 MINUTE REMAINING!");
            } else if (timeLeft == 30) {
                Bukkit.broadcastMessage("§c§l30 SECONDS REMAINING!");
            } else if (timeLeft <= 10 && timeLeft > 0) {
                Bukkit.broadcastMessage("§c§l" + timeLeft + " SECONDS!");
            }
        }, 20L, 20L); 
    }
    
    private void startScoreboardUpdates() {
        scoreboardUpdateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (gameActive) {
                updateFindItemScoreboard();
            }
        }, 20L, 20L); 
    }
    
    private void updateFindItemScoreboard() {
        for (Player player : alivePlayers) {
            if (!player.isOnline()) continue;
            
            
            Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective obj = board.registerNewObjective("finditem", Criteria.DUMMY, "§6§lFIND THE ITEM");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            
            Material targetItem = playerTargetItems.get(player);
            String itemName = targetItem != null ? formatItemName(targetItem) : "Unknown";
            
            
            int scoreValue = 6;
            obj.getScore("§7Time Left: §e" + formatTime(timeLeft)).setScore(scoreValue--);
            obj.getScore("").setScore(scoreValue--);
            obj.getScore("§7Find: §e" + itemName).setScore(scoreValue--);
            obj.getScore(" ").setScore(scoreValue--);
            obj.getScore("§7Finished: §a" + finishers.size()).setScore(scoreValue--);
            obj.getScore("§7Remaining: §c" + alivePlayers.size()).setScore(scoreValue--);
            
            player.setScoreboard(board);
        }
        
        
        for (Player player : finishers) {
            if (!player.isOnline()) continue;
            
            Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective obj = board.registerNewObjective("finditem", Criteria.DUMMY, "§6§lFIND THE ITEM");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            
            int scoreValue = 6;
            obj.getScore("§7Time Left: §e" + formatTime(timeLeft)).setScore(scoreValue--);
            obj.getScore("").setScore(scoreValue--);
            obj.getScore("§a§lCOMPLETED!").setScore(scoreValue--);
            obj.getScore(" ").setScore(scoreValue--);
            obj.getScore("§7Finished: §a" + finishers.size()).setScore(scoreValue--);
            obj.getScore("§7Remaining: §c" + alivePlayers.size()).setScore(scoreValue--);
            
            player.setScoreboard(board);
        }
    }
    
    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!gameActive) return;
        
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        
        if (!alivePlayers.contains(player)) return;
        
        ItemStack item = event.getItem().getItemStack();
        Material targetItem = playerTargetItems.get(player);
        
        if (targetItem != null && item.getType() == targetItem) {
            
            finishPlayer(player);
            event.setCancelled(false); 
        }
    }
    
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!gameActive) return;
        
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        
        if (!alivePlayers.contains(player)) return;
        
        
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem != null && clickedItem.getType() != Material.AIR) {
            Material targetItem = playerTargetItems.get(player);
            
            
            if (targetItem == null || clickedItem.getType() != targetItem) {
                event.setCancelled(true);
                player.sendMessage("§c§lYou can only take your assigned item: §e" + 
                    (targetItem != null ? formatItemName(targetItem) : "Unknown") + "§c§l!");
                return;
            }
            
            
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.getInventory().contains(targetItem)) {
                    finishPlayer(player);
                }
            }, 1L);
        }
    }
    
    
    @EventHandler
    public void onInventoryPickupItem(InventoryPickupItemEvent event) {
        if (!gameActive) return;
        
        
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            checkAllPlayersForTargetItems();
        }, 1L);
    }
    
    private void checkAllPlayersForTargetItems() {
        for (Player player : new HashSet<>(alivePlayers)) {
            Material targetItem = playerTargetItems.get(player);
            if (targetItem != null && player.getInventory().contains(targetItem)) {
                finishPlayer(player);
            }
        }
    }
    
    private void finishPlayer(Player player) {
        if (!alivePlayers.contains(player) || finishers.contains(player)) return;
        
        alivePlayers.remove(player);
        finishers.add(player);
        
        
        player.setGameMode(GameMode.SPECTATOR);
        
        int position = finishers.size();
        Material targetItem = playerTargetItems.get(player);
        String itemName = targetItem != null ? formatItemName(targetItem) : "their item";
        
        
        if (position <= 5) {
            int points = 6 - position; 
            awardPoints(player.getName(), points);
            
            Bukkit.broadcastMessage("§a§l" + player.getName() + " §a§lfound " + itemName + " and finished §e§l#" + position + "§a§l! (+" + points + " points)");
            player.sendMessage("§a§lCongratulations! You finished in position #" + position + " and earned " + points + " points!");
        } else {
            Bukkit.broadcastMessage("§7" + player.getName() + " found " + itemName + " and finished #" + position);
            player.sendMessage("§7You found your item but didn't finish in the top 5 for points.");
        }
        
        
        player.getInventory().clear();
        
        
        if (alivePlayers.isEmpty() || finishers.size() >= 5) {
            endGame("§a§lAll players finished or top 5 completed!");
        }
    }
    
    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        return String.format("%d:%02d", minutes, remainingSeconds);
    }
    
    private String formatItemName(Material material) {
        return material.name().toLowerCase().replace('_', ' ');
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
    
    private void endGame(String reason) {
        if (!gameActive) return;
        
        gameActive = false;
        
        
        if (gameTimer != null && !gameTimer.isCancelled()) {
            gameTimer.cancel();
        }
        if (scoreboardUpdateTask != null && !scoreboardUpdateTask.isCancelled()) {
            scoreboardUpdateTask.cancel();
        }
        
        Bukkit.broadcastMessage("§c§lFIND THE ITEM HAS ENDED!");
        Bukkit.broadcastMessage(reason);
        
        
        if (!finishers.isEmpty()) {
            Bukkit.broadcastMessage("§e§lFINAL RESULTS:");
            for (int i = 0; i < Math.min(finishers.size(), 5); i++) {
                Player player = finishers.get(i);
                int points = 5 - i;
                Bukkit.broadcastMessage("§7#" + (i + 1) + " §a" + player.getName() + " §7(+" + points + " points)");
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
                
                player.sendMessage("§7Thanks for playing Find the Item!");
            }
        }
        
        
        alivePlayers.clear();
        finishers.clear();
        playerTargetItems.clear();
        
        
        if (gameEndCallback != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> gameEndCallback.onGameEnd(), 100L);
        }
        
        plugin.getLogger().info("Find the Item game ended");
    }
}
