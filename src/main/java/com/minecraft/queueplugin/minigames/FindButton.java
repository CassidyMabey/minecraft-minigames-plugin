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
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
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

public class FindButton implements Listener {
    
    
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
    
    
    private final Location minLocation = new Location(null, 155, 80, 239);
    private final Location maxLocation = new Location(null, 163, 80, 247);
    private final Location teleportLocation = new Location(null, 159, 81, 243); 
    
    
    private final Set<Player> alivePlayers = new HashSet<>();
    private final Map<Player, Material> playerTargetItems = new HashMap<>();
    private final List<Player> finishers = new ArrayList<>(); 
    private final List<Material> availableItems = new ArrayList<>();
    
    
    private BukkitTask gameTimeoutTask = null;
    private BukkitTask finalCountdownTask = null;
    private BukkitTask scoreboardUpdateTask = null;
    
    public FindButton(JavaPlugin plugin) {
        this.plugin = plugin;
        
        
        World world = Bukkit.getWorlds().get(0);
        minLocation.setWorld(world);
        maxLocation.setWorld(world);
        teleportLocation.setWorld(world);
        
        
        initializeAvailableItems();
    }
    
    private void initializeAvailableItems() {
        
        availableItems.addAll(Arrays.asList(
            Material.DIAMOND, Material.EMERALD, Material.GOLD_INGOT, Material.IRON_INGOT,
            Material.REDSTONE, Material.LAPIS_LAZULI, Material.COAL, Material.COPPER_INGOT,
            Material.AMETHYST_SHARD, Material.QUARTZ, Material.NETHERITE_SCRAP, Material.PRISMARINE_SHARD,
            Material.ENDER_PEARL, Material.BLAZE_ROD, Material.GHAST_TEAR, Material.MAGMA_CREAM,
            Material.LEATHER, Material.RABBIT_HIDE, Material.FEATHER, Material.STRING,
            Material.BONE, Material.SPIDER_EYE, Material.GUNPOWDER, Material.SLIME_BALL,
            Material.WHEAT, Material.CARROT, Material.POTATO, Material.BEETROOT,
            Material.APPLE, Material.BREAD, Material.COOKED_BEEF, Material.COOKED_PORKCHOP,
            Material.OAK_LOG, Material.BIRCH_LOG, Material.SPRUCE_LOG, Material.JUNGLE_LOG,
            Material.STONE, Material.COBBLESTONE, Material.DIRT, Material.SAND,
            Material.GLASS, Material.WHITE_WOOL, Material.RED_WOOL, Material.BLUE_WOOL,
            Material.BOOK, Material.PAPER, Material.INK_SAC, Material.COMPASS
        ));
    }
    
    public void setGameEndCallback(GameEndCallback callback) {
        this.gameEndCallback = callback;
    }
    
    public void startGame(List<Player> players) {
        plugin.getLogger().info("Starting Find the Item minigame with " + players.size() + " players");
        
        gameActive = true;
        alivePlayers.clear();
        finishers.clear();
        playerTargetItems.clear();
        
        
        Bukkit.getPluginManager().registerEvents(this, plugin);
        
        
        setupChestArea();
        
        
        for (Player player : players) {
            if (player.isOnline()) {
                alivePlayers.add(player);
                
                
                player.getInventory().clear();
                player.getInventory().setArmorContents(null);
                
                
                player.setGameMode(GameMode.ADVENTURE);
                
                
                player.teleport(teleportLocation);
                
                
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 255, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 255, false, false));
                
                
                Material targetItem = availableItems.get(new Random().nextInt(availableItems.size()));
                playerTargetItems.put(player, targetItem);
                
                player.sendMessage("§6§lFind the Item Game Started!");
                player.sendMessage("§e§lFind: §f" + getItemName(targetItem));
            }
        }
        
        
        startScoreboardUpdates();
        
        
        startGameTimeout();
        
        Bukkit.broadcastMessage("§6§lFIND THE ITEM has started! Find your assigned item!");
    }
    
    private void setupChestArea() {
        World world = teleportLocation.getWorld();
        if (world == null) return;
        
        
        List<ItemStack> allItems = new ArrayList<>();
        
        
        for (int x = (int) minLocation.getX(); x <= maxLocation.getX(); x++) {
            for (int y = (int) minLocation.getY(); y <= maxLocation.getY(); y++) {
                for (int z = (int) minLocation.getZ(); z <= maxLocation.getZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST) {
                        Chest chest = (Chest) block.getState();
                        Inventory inv = chest.getInventory();
                        
                        
                        for (ItemStack item : inv.getContents()) {
                            if (item != null && item.getType() != Material.AIR) {
                                allItems.add(item.clone());
                            }
                        }
                        
                        
                        inv.clear();
                    }
                }
            }
        }
        
        
        List<Chest> chests = new ArrayList<>();
        
        
        for (int x = (int) minLocation.getX(); x <= maxLocation.getX(); x++) {
            for (int y = (int) minLocation.getY(); y <= maxLocation.getY(); y++) {
                for (int z = (int) minLocation.getZ(); z <= maxLocation.getZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST) {
                        chests.add((Chest) block.getState());
                    }
                }
            }
        }
        
        
        int itemIndex = 0;
        for (Chest chest : chests) {
            Inventory inv = chest.getInventory();
            for (int slot = 0; slot < inv.getSize(); slot++) {
                if (itemIndex < allItems.size()) {
                    ItemStack item = allItems.get(itemIndex).clone();
                    item.setAmount(1); 
                    inv.setItem(slot, item);
                    itemIndex++;
                } else {
                    break;
                }
            }
            if (itemIndex >= allItems.size()) break;
        }
    }
    
    private String getItemName(Material material) {
        return material.name().toLowerCase().replace("_", " ");
    }
    
    private void startGameTimeout() {
        gameTimeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (gameActive) {
                Bukkit.broadcastMessage("§c§lTime's up!");
                endGame();
            }
        }, 3600L); 
    }
    
    private void startScoreboardUpdates() {
        scoreboardUpdateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (gameActive) {
                updateFindButtonScoreboard();
            }
        }, 20L, 40L); 
    }
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!gameActive) return;
        
        Player player = event.getPlayer();
        if (!alivePlayers.contains(player)) return;
        
        Location to = event.getTo();
        if (to == null) return;
        
        
        Location blockBelow = to.clone().subtract(0, 1, 0);
        if (blockBelow.getBlock().getType() == Material.GREEN_CONCRETE) {
            finishPlayer(player);
        }
    }
    
    private void finishPlayer(Player player) {
        if (!alivePlayers.contains(player) || finishers.contains(player)) return;
        
        alivePlayers.remove(player);
        finishers.add(player);
        
        
        player.setGameMode(GameMode.SPECTATOR);
        
        int placement = finishers.size();
        String placementText = getPlacementText(placement);
        
        player.sendMessage("§a§lCONGRATULATIONS! " + placementText + "!");
        player.sendMessage("§7You are now in spectator mode. You'll be returned to adventure mode when the game ends.");
        
        
        player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 255, false, false));
        
        
        String message = "§a" + player.getName() + " §7found the button! " + placementText + " §7(" + 
                        (5 - finishers.size()) + " spots remaining)";
        
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(message);
        }
        
        
        updateFindButtonScoreboard();
        
        
        checkGameEnd();
    }
    
    private void checkGameEnd() {
        if (finishers.size() >= 5) {
            
            endGame();
        } else if (alivePlayers.size() + finishers.size() <= 5) {
            
            if (finalCountdownTask == null) {
                startFinalCountdown();
            }
        }
    }
    
    private void startFinalCountdown() {
        Bukkit.broadcastMessage("§e§lLess than 5 players remain!");
        Bukkit.broadcastMessage("§71 minute countdown started!");
        
        finalCountdownTask = new BukkitRunnable() {
            int timeLeft = 60;
            
            @Override
            public void run() {
                if (timeLeft <= 0) {
                    endGame();
                    cancel();
                    return;
                }
                
                
                if (timeLeft == 30) {
                    Bukkit.broadcastMessage("§e§l30 seconds remaining!");
                } else if (timeLeft == 10) {
                    Bukkit.broadcastMessage("§c§l10 seconds remaining!");
                } else if (timeLeft <= 5) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.isOnline()) {
                            player.sendTitle("§c§l" + timeLeft, "§7Time's up!", 5, 10, 5);
                        }
                    }
                }
                
                timeLeft--;
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }
    
    private void updateFindButtonScoreboard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;
        
        Scoreboard board = manager.getNewScoreboard();
        Objective objective = board.registerNewObjective("findbutton", Criteria.DUMMY, "§6§lFind the Button");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        
        
        int score = 10;
        
        objective.getScore("§a§lWinners:").setScore(score--);
        
        for (int i = 0; i < finishers.size() && i < 5; i++) {
            Player winner = finishers.get(i);
            String placementText = getPlacementText(i + 1);
            objective.getScore(placementText + " §7" + winner.getName()).setScore(score--);
        }
        
        if (finishers.size() < 5) {
            objective.getScore("").setScore(score--);
            objective.getScore("§7Spots left: §e" + (5 - finishers.size())).setScore(score--);
            objective.getScore("§7Searching: §c" + alivePlayers.size()).setScore(score--);
        }
        
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setScoreboard(board);
        }
    }
    
    private void endGame() {
        gameActive = false;
        
        
        if (finalCountdownTask != null && !finalCountdownTask.isCancelled()) {
            finalCountdownTask.cancel();
        }
        if (scoreboardUpdateTask != null && !scoreboardUpdateTask.isCancelled()) {
            scoreboardUpdateTask.cancel();
        }
        
        
        PlayerMoveEvent.getHandlerList().unregister(this);
        
        Bukkit.broadcastMessage("§a§lFIND THE BUTTON HAS ENDED!");
        
        
        awardPointsAndResetPlayers();
    }
    
    private void endGameWithNoWinners() {
        gameActive = false;
        
        
        if (finalCountdownTask != null && !finalCountdownTask.isCancelled()) {
            finalCountdownTask.cancel();
        }
        if (scoreboardUpdateTask != null && !scoreboardUpdateTask.isCancelled()) {
            scoreboardUpdateTask.cancel();
        }
        
        
        PlayerMoveEvent.getHandlerList().unregister(this);
        
        Bukkit.broadcastMessage("§c§lFIND THE BUTTON ENDED - No winners!");
        Bukkit.broadcastMessage("§7Everyone gets teleported back to spawn.");
        
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOnline()) {
                player.getInventory().clear();
                player.getInventory().setArmorContents(null);
                player.removePotionEffect(PotionEffectType.INVISIBILITY);
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
            
            
            for (int i = 0; i < finishers.size() && i < 5; i++) {
                Player player = finishers.get(i);
                int placement = i + 1; 
                int points = Math.max(0, 6 - placement); 
                
                if (points > 0) {
                    PlayerStats stats = playerData.getOrDefault(player.getName(), new PlayerStats(player.getName(), 0));
                    stats.points += points;
                    playerData.put(player.getName(), stats);
                    
                    String placementText = getPlacementText(placement);
                    player.sendMessage("§a§lGAME FINISHED! " + placementText + " §a(+§e" + points + "§a points)");
                }
                
                
                player.removePotionEffect(PotionEffectType.INVISIBILITY);
                player.removePotionEffect(PotionEffectType.RESISTANCE);
                player.removePotionEffect(PotionEffectType.NIGHT_VISION);
                
            }
            
            
            for (Player player : alivePlayers) {
                player.sendMessage("§c§lBetter luck next time!");
                
                
                player.removePotionEffect(PotionEffectType.INVISIBILITY);
                player.removePotionEffect(PotionEffectType.RESISTANCE);
                player.removePotionEffect(PotionEffectType.NIGHT_VISION);
                
            }
            
            
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(playerData, writer);
            }
            
            plugin.getLogger().info("Awarded points to Find the Button winners");
            
            
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
    
    private String getPlacementText(int position) {
        return switch (position) {
            case 1 -> "§6§l1st";
            case 2 -> "§e§l2nd";
            case 3 -> "§c§l3rd";
            default -> "§7§l" + position + "th";
        };
    }
    
    public boolean isGameActive() {
        return gameActive;
    }
    
    public void forceEnd() {
        if (gameActive) {
            gameActive = false;
            
            
            if (finalCountdownTask != null && !finalCountdownTask.isCancelled()) {
                finalCountdownTask.cancel();
            }
            if (scoreboardUpdateTask != null && !scoreboardUpdateTask.isCancelled()) {
                scoreboardUpdateTask.cancel();
            }
            
            
            PlayerMoveEvent.getHandlerList().unregister(this);
            
            
            for (Player player : alivePlayers) {
                if (player.isOnline()) {
                    player.setWalkSpeed(0.2f);
                    player.setFlySpeed(0.1f);
                    player.removePotionEffect(PotionEffectType.INVISIBILITY);
                    player.removePotionEffect(PotionEffectType.RESISTANCE);
                    player.removePotionEffect(PotionEffectType.NIGHT_VISION);
                    
                }
            }
            
            for (Player player : finishers) {
                if (player.isOnline()) {
                    player.setWalkSpeed(0.2f);
                    player.setFlySpeed(0.1f);
                    player.removePotionEffect(PotionEffectType.INVISIBILITY);
                    player.removePotionEffect(PotionEffectType.RESISTANCE);
                    player.removePotionEffect(PotionEffectType.NIGHT_VISION);
                    
                }
            }
            
            
            teleportPlayersToSpawn();
            
            alivePlayers.clear();
            finishers.clear();
            
            plugin.getLogger().info("Find the Button game force ended");
        }
    }
}
