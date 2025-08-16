package com.minecraft.queueplugin;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import com.minecraft.queueplugin.minigames.TntRun;
import com.minecraft.queueplugin.minigames.Parkour;
import com.minecraft.queueplugin.minigames.Spleef;
import com.minecraft.queueplugin.minigames.HotPotato;
import com.minecraft.queueplugin.minigames.FindButton;
import com.minecraft.queueplugin.minigames.FindTheItem;
import com.minecraft.queueplugin.minigames.SuperSmashSteves;
import com.minecraft.queueplugin.minigames.TryToDie;

import java.util.*;

public class VotingSystem implements Listener {
    
    
    private static class PlayerStats {
        public String name;
        public int points;
        
        public PlayerStats(String name, int points) {
            this.name = name;
            this.points = points;
        }
    }
    
    private final QueuePlugin plugin;
    private boolean votingActive = false;
    private Map<String, Integer> gameVotes = new HashMap<>();
    private Map<String, String> playerVotes = new HashMap<>(); 
    private BukkitTask votingTask;
    private BukkitTask chatUpdateTask;
    private int timeLeft = 120; 
    private String lastPlayedGame = null; 
    
    
    private final TntRun tntRun;
    private final Parkour parkour;
    private final Spleef spleef;
    private final HotPotato hotPotato;
    private final FindButton findButton;
    private final FindTheItem findTheItem;
    private final SuperSmashSteves superSmashSteves;
    private final TryToDie tryToDie;
    
    
    private boolean teamFightActive = false;
    private String currentGame = null; 
    private final Set<Player> blueTeam = new HashSet<>();
    private final Set<Player> redTeam = new HashSet<>();
    private final Set<Player> deadPlayers = new HashSet<>(); 
    private final Map<Player, Integer> teamFightKills = new HashMap<>();
    private BukkitTask teamFightTask = null;
    private final Location blueSpawn = new Location(null, 379.5, 79, 183.5);
    private final Location redSpawn = new Location(null, 333.5, 79, 183.5);
    
    
    private final Map<String, GameModeInfo> gameModes = new LinkedHashMap<>();
    
    private static class GameModeInfo {
        String displayName;
        Material material;
        String description;
        
        GameModeInfo(String displayName, Material material, String description) {
            this.displayName = displayName;
            this.material = material;
            this.description = description;
        }
    }
    
    public VotingSystem(QueuePlugin plugin) {
        this.plugin = plugin;
        this.tntRun = new TntRun(plugin);
        this.parkour = new Parkour(plugin);
        this.spleef = new Spleef(plugin);
        this.hotPotato = new HotPotato(plugin);
        this.findButton = new FindButton(plugin);
        this.findTheItem = new FindTheItem(plugin);
        this.superSmashSteves = new SuperSmashSteves(plugin);
        this.tryToDie = new TryToDie(plugin);
        
        
        this.tntRun.setGameEndCallback(() -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                startVoting();
                Bukkit.broadcastMessage("§a§lA new voting round has started!");
            });
        });
        
        this.parkour.setGameEndCallback(() -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                startVoting();
                Bukkit.broadcastMessage("§a§lA new voting round has started!");
            });
        });
        
        this.spleef.setGameEndCallback(() -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                startVoting();
                Bukkit.broadcastMessage("§a§lA new voting round has started!");
            });
        });
        
        this.hotPotato.setGameEndCallback(() -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                startVoting();
                Bukkit.broadcastMessage("§a§lA new voting round has started!");
            });
        });
        
        this.findButton.setGameEndCallback(() -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                startVoting();
                Bukkit.broadcastMessage("§a§lA new voting round has started!");
            });
        });
        
        this.findTheItem.setGameEndCallback(() -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                startVoting();
                Bukkit.broadcastMessage("§a§lA new voting round has started!");
            });
        });
        
        this.superSmashSteves.setGameEndCallback(() -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                startVoting();
                Bukkit.broadcastMessage("§a§lA new voting round has started!");
            });
        });
        
        this.tryToDie.setGameEndCallback(() -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                startVoting();
                Bukkit.broadcastMessage("§a§lA new voting round has started!");
            });
        });
        
        initializeGameModes();
        
        
        World world = Bukkit.getWorlds().get(0);
        blueSpawn.setWorld(world);
        redSpawn.setWorld(world);
        
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    
    private void initializeGameModes() {
        gameModes.put("tntrun", new GameModeInfo("§c§lTNT Run", Material.TNT, "Run before the ground explodes!"));
        gameModes.put("parkour", new GameModeInfo("§a§lParkour", Material.FEATHER, "Jump your way to victory!"));
        gameModes.put("spleef", new GameModeInfo("§6§lSpleef", Material.DIAMOND_SHOVEL, "Dig blocks to make others fall!"));
        gameModes.put("hotpotato", new GameModeInfo("§e§lHot Potato", Material.BAKED_POTATO, "Don't be holding the potato!"));
        gameModes.put("findbutton", new GameModeInfo("§9§lFind the Button", Material.STONE_BUTTON, "Find the hidden button!"));
        gameModes.put("findtheitem", new GameModeInfo("§d§lFind the Item", Material.CHEST, "Find your assigned item in chests!"));
        gameModes.put("supersmashsteves", new GameModeInfo("§4§lSuper Smash Steves", Material.IRON_SWORD, "Fight to be the last Steve standing!"));
        gameModes.put("pvp", new GameModeInfo("§4§lTeam Fight", Material.DIAMOND_SWORD, "Fight in epic team battles!"));
        gameModes.put("trytodie", new GameModeInfo("§8§lTry to Die", Material.SKELETON_SKULL, "Die in the most creative way!"));
        gameModes.put("lavarising", new GameModeInfo("§c§lLava Rising", Material.LAVA_BUCKET, "Escape the rising lava!"));
        
        
        for (String gameMode : gameModes.keySet()) {
            gameVotes.put(gameMode, 0);
        }
    }
    
    public void startVoting() {
        if (votingActive) {
            return;
        }
        
        votingActive = true;
        timeLeft = 120;
        
        
        gameVotes.clear();
        for (String gameMode : gameModes.keySet()) {
            
            if (!gameMode.equals(lastPlayedGame)) {
                gameVotes.put(gameMode, 0);
            }
        }
        
        playerVotes.clear();
        
        
        if (lastPlayedGame != null) {
            GameModeInfo excludedGame = gameModes.get(lastPlayedGame);
            if (excludedGame != null) {
                Bukkit.broadcastMessage("§7" + excludedGame.displayName + " §7was excluded from this voting round!");
            }
        }
        
        
        votingTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            timeLeft--;
            
            if (timeLeft <= 0) {
                endVoting();
                return;
            }
            
            
            updateTabList();
            
            
            updateActionBar();
            
        }, 0L, 20L); 
        
        
        chatUpdateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (votingActive && timeLeft > 0) {
                sendVotingUpdate();
            }
        }, 300L, 300L); 
        
        
        updateTabList();
    }
    
    private void endVoting() {
        votingActive = false;
        
        if (votingTask != null) {
            votingTask.cancel();
            votingTask = null;
        }
        
        if (chatUpdateTask != null) {
            chatUpdateTask.cancel();
            chatUpdateTask = null;
        }
        
        
        String winnerMode = getWinningGameMode();
        GameModeInfo winner = gameModes.get(winnerMode);
        
        
        if (winner != null) {
            startGameMode(winnerMode);
        } else {
            String randomMode = (String) gameModes.keySet().toArray()[new Random().nextInt(gameModes.size())];
            startGameMode(randomMode);
        }
        
        
        clearTabList();
    }
    
    private void startGameMode(String gameMode) {
        
        lastPlayedGame = gameMode;
        
        GameModeInfo mode = gameModes.get(gameMode);
        if (mode != null) {
            plugin.getLogger().info("Starting game mode: " + gameMode);
            
            
            List<Player> players = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getGameMode() != GameMode.CREATIVE) {
                    players.add(player);
                    plugin.getLogger().info("Adding player to game: " + player.getName() + " (GameMode: " + player.getGameMode() + ")");
                }
            }
            
            plugin.getLogger().info("Total players found for game: " + players.size());
            
            if (players.isEmpty()) {
                Bukkit.broadcastMessage("§cNo players available to start the game!");
                return;
            }
            
            
            switch (gameMode) {
                case "tntrun":
                    tntRun.startGame(players);
                    break;
                case "parkour":
                    parkour.startGame(players);
                    break;
                case "spleef":
                    spleef.startGame(players);
                    break;
                case "hotpotato":
                    hotPotato.startGame(players);
                    break;
                case "findbutton":
                    findButton.startGame(players);
                    break;
                case "findtheitem":
                    findTheItem.startGame(players);
                    break;
                case "supersmashsteves":
                    superSmashSteves.startGame(players);
                    break;
                case "pvp":
                    startPvPGame(players);
                    break;
                case "trytodie":
                    tryToDie.startGame(players);
                    break;
                case "lavarising":
                    startLavaRisingGame(players);
                    break;
                default:
                    Bukkit.broadcastMessage("§cGame mode not implemented yet: " + mode.displayName);
                    break;
            }
        }
    }
    
    private String getWinningGameMode() {
        return gameVotes.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }
    
    private void sendVotingUpdate() {
        List<Map.Entry<String, Integer>> sortedVotes = gameVotes.entrySet().stream()
                .filter(entry -> entry.getValue() > 0) 
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .toList();
        
        int totalVotes = gameVotes.values().stream().mapToInt(Integer::intValue).sum();
        
        Bukkit.broadcastMessage("§6§l=== VOTING UPDATE ===");
        Bukkit.broadcastMessage("§eTime remaining: §c" + formatTime(timeLeft));
        
        if (totalVotes > 0) {
            Bukkit.broadcastMessage("§eGamemodes:");
            
            
            for (Map.Entry<String, Integer> entry : sortedVotes) {
                GameModeInfo mode = gameModes.get(entry.getKey());
                int votes = entry.getValue();
                double percentage = (double) votes / totalVotes * 100;
                String bar = createPercentageBar(percentage);
                
                Bukkit.broadcastMessage("§7- §a" + mode.displayName + " §7- §f" + votes + " votes " + bar + " §b" + String.format("%.1f", percentage) + "%");
            }
        } else {
            Bukkit.broadcastMessage("§7No votes yet! Use §e/vote §7to see options.");
        }
    }
    
    private void updateActionBar() {
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle("§eVoting", "§c" + formatTime(timeLeft) + " §eleft", 0, 40, 10);
        }
    }
    
    private void updateTabList() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;
        
        Scoreboard scoreboard = manager.getNewScoreboard();
        
        
        Objective voting = scoreboard.registerNewObjective("voting", Criteria.DUMMY, "§6§lGAME MODE VOTING");
        voting.setDisplaySlot(DisplaySlot.SIDEBAR);
        
        
        Score timeScore = voting.getScore("§eTime: §c" + formatTime(timeLeft));
        timeScore.setScore(20);
        
        Score separator1 = voting.getScore("§7═══════════════");
        separator1.setScore(19);
        
        
        List<Map.Entry<String, Integer>> sortedVotes = gameVotes.entrySet().stream()
                .filter(entry -> entry.getValue() > 0) 
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .toList();
        
        int totalVotes = gameVotes.values().stream().mapToInt(Integer::intValue).sum();
        
        int scoreValue = 18;
        for (int i = 0; i < sortedVotes.size(); i++) {
            Map.Entry<String, Integer> entry = sortedVotes.get(i);
            GameModeInfo mode = gameModes.get(entry.getKey());
            int votes = entry.getValue();
            double percentage = totalVotes > 0 ? (double) votes / totalVotes * 100 : 0;
            String bar = createPercentageBar(percentage);
            
            String position = i == 0 ? "§61st" : i == 1 ? "§e2nd" : "§c3rd";
            
            
            String modeText = position + ": " + mode.displayName;
            for (int j = 0; j < i; j++) {
                modeText += "§r"; 
            }
            Score modeScore = voting.getScore(modeText);
            modeScore.setScore(scoreValue--);
            
            
            String voteText = "§a" + votes + " votes " + bar;
            for (int j = 0; j < i; j++) {
                voteText += "§r"; 
            }
            Score voteScore = voting.getScore(voteText);
            voteScore.setScore(scoreValue--);
            
            
            if (i < sortedVotes.size() - 1) {
                String sepText = "§7───────────────";
                for (int j = 0; j <= i; j++) {
                    sepText += "§r"; 
                }
                Score separator = voting.getScore(sepText);
                separator.setScore(scoreValue--);
            }
        }
        
        
        String sepText2 = "§7═══════════════";
        sepText2 += "§r§r"; 
        Score separator2 = voting.getScore(sepText2);
        separator2.setScore(scoreValue--);
        
        Score totalScore = voting.getScore("§eTotal: §a" + totalVotes + " votes");
        totalScore.setScore(scoreValue--);
        
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setScoreboard(scoreboard);
        }
    }
    
    private void clearTabList() {
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            Scoreboard mainScoreboard = plugin.getScoreboard();
            if (mainScoreboard != null) {
                player.setScoreboard(mainScoreboard);
            } else {
                ScoreboardManager manager = Bukkit.getScoreboardManager();
                if (manager != null) {
                    player.setScoreboard(manager.getMainScoreboard());
                }
            }
        }
    }
    
    private String createPercentageBar(double percentage) {
        int barLength = 10;
        int filledBars = (int) Math.round(percentage / 10.0);
        
        StringBuilder bar = new StringBuilder("§8[");
        for (int i = 0; i < barLength; i++) {
            if (i < filledBars) {
                bar.append("§a#");
            } else {
                bar.append("§7-");
            }
        }
        bar.append("§8]");
        return bar.toString();
    }
    
    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        return String.format("%d:%02d", minutes, remainingSeconds);
    }
    
    public boolean vote(Player player, String gameMode) {
        if (!votingActive) {
            player.sendMessage("§cVoting is not currently active!");
            return false;
        }
        
        if (!gameModes.containsKey(gameMode.toLowerCase())) {
            player.sendMessage("§cInvalid game mode! Available modes: " + String.join(", ", gameModes.keySet()));
            return false;
        }
        
        String normalizedGameMode = gameMode.toLowerCase();
        String playerName = player.getName();
        
        
        if (playerVotes.containsKey(playerName)) {
            String previousVote = playerVotes.get(playerName);
            gameVotes.put(previousVote, gameVotes.get(previousVote) - 1);
        }
        
        
        playerVotes.put(playerName, normalizedGameMode);
        gameVotes.put(normalizedGameMode, gameVotes.get(normalizedGameMode) + 1);
        
        GameModeInfo mode = gameModes.get(normalizedGameMode);
        player.sendMessage("§aYou voted for " + mode.displayName + "§a!");
        
        return true;
    }
    
    public void openVotingGUI(Player player) {
        if (!votingActive) {
            player.sendMessage("§cVoting is not currently active!");
            return;
        }
        
        Inventory gui = Bukkit.createInventory(null, 54, "§6§lGame Mode Voting");
        
        int slot = 0;
        for (Map.Entry<String, GameModeInfo> entry : gameModes.entrySet()) {
            if (slot >= 45) break; 
            
            String gameMode = entry.getKey();
            GameModeInfo mode = entry.getValue();
            
            ItemStack item = new ItemStack(mode.material);
            ItemMeta meta = item.getItemMeta();
            
            if (meta != null) {
                meta.setDisplayName(mode.displayName);
                
                List<String> lore = new ArrayList<>();
                lore.add("§7" + mode.description);
                lore.add("");
                lore.add("§eVotes: §a" + gameVotes.get(gameMode));
                
                int totalVotes = gameVotes.values().stream().mapToInt(Integer::intValue).sum();
                double percentage = totalVotes > 0 ? (double) gameVotes.get(gameMode) / totalVotes * 100 : 0;
                lore.add("§ePercentage: §b" + String.format("%.1f", percentage) + "%");
                lore.add(createPercentageBar(percentage));
                lore.add("");
                
                if (playerVotes.get(player.getName()) != null && playerVotes.get(player.getName()).equals(gameMode)) {
                    lore.add("§a§l✓ YOU VOTED FOR THIS!");
                } else {
                    lore.add("§7Click to vote!");
                }
                
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            
            gui.setItem(slot, item);
            slot++;
        }
        
        
        ItemStack timeItem = new ItemStack(Material.CLOCK);
        ItemMeta timeMeta = timeItem.getItemMeta();
        if (timeMeta != null) {
            timeMeta.setDisplayName("§6Time Remaining");
            timeMeta.setLore(Arrays.asList("§e" + formatTime(timeLeft)));
            timeItem.setItemMeta(timeMeta);
        }
        gui.setItem(49, timeItem);
        
        ItemStack voteItem = new ItemStack(Material.PAPER);
        ItemMeta voteMeta = voteItem.getItemMeta();
        if (voteMeta != null) {
            voteMeta.setDisplayName("§eYour Vote");
            String currentVote = playerVotes.get(player.getName());
            if (currentVote != null) {
                GameModeInfo mode = gameModes.get(currentVote);
                voteMeta.setLore(Arrays.asList("§7Currently voting for:", mode.displayName));
            } else {
                voteMeta.setLore(Arrays.asList("§7You haven't voted yet!"));
            }
            voteItem.setItemMeta(voteMeta);
        }
        gui.setItem(53, voteItem);
        
        player.openInventory(gui);
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("§6§lGame Mode Voting")) {
            return;
        }
        
        event.setCancelled(true);
        
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        
        if (clicked == null || !clicked.hasItemMeta()) {
            return;
        }
        
        
        for (Map.Entry<String, GameModeInfo> entry : gameModes.entrySet()) {
            if (clicked.getType() == entry.getValue().material) {
                String gameMode = entry.getKey();
                if (vote(player, gameMode)) {
                    
                    openVotingGUI(player);
                }
                break;
            }
        }
    }
    
    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage().toLowerCase();
        
        if (message.startsWith("/vote ")) {
            event.setCancelled(true);
            
            String[] args = message.substring(6).split(" ");
            Player player = event.getPlayer();
            
            if (args.length == 0) {
                player.sendMessage("§cUsage: /vote <gamemode> or /vote gui");
                return;
            }
            
            if (args[0].equals("gui")) {
                openVotingGUI(player);
            } else {
                vote(player, args[0]);
            }
        }
    }
    
    public boolean isVotingActive() {
        return votingActive;
    }
    
    public void forceEndVoting() {
        if (votingActive) {
            timeLeft = 0;
            endVoting();
        }
    }
    
    public boolean isAnyGameActive() {
        return tntRun.isGameActive() || parkour.isGameActive() || spleef.isGameActive() || 
               hotPotato.isGameActive() || findButton.isGameActive() || findTheItem.isGameActive() ||
               superSmashSteves.isGameActive() || tryToDie.isGameActive() || teamFightActive; 
        
    }
    
    
    private void startParkourGame(List<Player> players) {
        parkour.startGame(players);
    }
    
    private void startSpleefGame(List<Player> players) {
        plugin.getLogger().info("Spleef game started with " + players.size() + " players");
    }
    
    private void startHotPotatoGame(List<Player> players) {
        plugin.getLogger().info("Hot Potato game started with " + players.size() + " players");
    }
    
    private void startFindButtonGame(List<Player> players) {
        plugin.getLogger().info("Find the Button game started with " + players.size() + " players");
    }
    
    private void startPvPGame(List<Player> players) {
        plugin.getLogger().info("Team Fight game started with " + players.size() + " players");
        
        
        currentGame = "Team Fight";
        
        if (players.size() < 2) {
            Bukkit.broadcastMessage("§cNeed at least 2 players for Team Fight!");
            currentGame = null;
            return;
        }
        
        teamFightActive = true;
        blueTeam.clear();
        redTeam.clear();
        teamFightKills.clear();
        
        
        Collections.shuffle(players);
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            if (i % 2 == 0) {
                blueTeam.add(player);
            } else {
                redTeam.add(player);
            }
            teamFightKills.put(player, 0);
        }
        
        
        for (Player player : blueTeam) {
            setupTeamPlayer(player, "§9§lBLUE", blueSpawn);
        }
        
        
        for (Player player : redTeam) {
            setupTeamPlayer(player, "§c§lRED", redSpawn);
        }
        
        Bukkit.broadcastMessage("§6§lTEAM FIGHT HAS STARTED!");
        Bukkit.broadcastMessage("§9Blue Team: " + blueTeam.size() + " players");
        Bukkit.broadcastMessage("§cRed Team: " + redTeam.size() + " players");
        Bukkit.broadcastMessage("§7Fight to eliminate the other team!");
        Bukkit.broadcastMessage("§ePoints: 1 for participation, +1 per kill, +1 for survival");
        
        
        startTeamFightMonitoring();
        
        
        updateTeamFightScoreboard();
    }
    
    private void setupTeamPlayer(Player player, String teamName, Location spawn) {
        
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.setGameMode(GameMode.ADVENTURE);
        
        
        player.teleport(spawn);
        
        
        player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 255, false, false));
        
        
        player.getInventory().setItem(0, new ItemStack(Material.IRON_SWORD));
        player.getInventory().setItem(1, new ItemStack(Material.BOW));
        player.getInventory().setItem(2, new ItemStack(Material.GOLDEN_APPLE, 3));
        player.getInventory().setItem(8, new ItemStack(Material.ARROW, 32));
        
        
        if (teamName.contains("BLUE")) {
            player.getInventory().setHelmet(new ItemStack(Material.BLUE_WOOL));
            player.getInventory().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
            player.getInventory().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
            player.getInventory().setBoots(new ItemStack(Material.BLUE_WOOL));
        } else {
            player.getInventory().setHelmet(new ItemStack(Material.RED_WOOL));
            player.getInventory().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
            player.getInventory().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
            player.getInventory().setBoots(new ItemStack(Material.RED_WOOL));
        }
        
        
        player.setDisplayName(teamName + " " + player.getName() + "§r");
        player.setPlayerListName(teamName + " " + player.getName() + "§r");
        
        player.sendMessage("§a§lYou are on the " + teamName + " §a§lTEAM!");
        player.sendMessage("§7Work together to eliminate the other team!");
    }
    
    private void startTeamFightMonitoring() {
        teamFightTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!teamFightActive) {
                return;
            }
            
            
            blueTeam.removeIf(player -> {
                if (!player.isOnline() || player.getGameMode() == GameMode.CREATIVE) {
                    deadPlayers.add(player);
                    return true;
                }
                return false;
            });
            
            redTeam.removeIf(player -> {
                if (!player.isOnline() || player.getGameMode() == GameMode.CREATIVE) {
                    deadPlayers.add(player);
                    return true;
                }
                return false;
            });
            
            
            updateTeamFightScoreboard();
            
            
            checkTeamFightWinCondition();
        }, 20L, 40L); 
    }
    
    private void endTeamFight(String winningTeam) {
        teamFightActive = false;
        
        if (teamFightTask != null && !teamFightTask.isCancelled()) {
            teamFightTask.cancel();
        }
        
        if (winningTeam == null) {
            Bukkit.broadcastMessage("§e§lTEAM FIGHT ENDED IN A DRAW!");
        } else {
            Bukkit.broadcastMessage("§6§l" + winningTeam + " §6§lWON THE TEAM FIGHT!");
        }
        
        
        awardTeamFightPoints(winningTeam);
    }
    
    private void awardTeamFightPoints(Player player, int points, String reason) {
        try {
            
            java.io.File file = new java.io.File(plugin.getDataFolder(), "winners.json");
            Map<String, PlayerStats> playerData = new HashMap<>();
            
            if (file.exists()) {
                try (java.io.FileReader reader = new java.io.FileReader(file)) {
                    com.google.gson.reflect.TypeToken<Map<String, PlayerStats>> typeToken = 
                        new com.google.gson.reflect.TypeToken<Map<String, PlayerStats>>(){};
                    Map<String, PlayerStats> loaded = new com.google.gson.Gson().fromJson(reader, typeToken.getType());
                    if (loaded != null) {
                        playerData = loaded;
                    }
                }
            }
            
            
            PlayerStats stats = playerData.getOrDefault(player.getName(), new PlayerStats(player.getName(), 0));
            stats.points += points;
            playerData.put(player.getName(), stats);
            
            
            try (java.io.FileWriter writer = new java.io.FileWriter(file)) {
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(playerData, writer);
            }
            
            
            player.sendMessage("§a+" + points + " points for " + reason + "!");
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to award individual points: " + e.getMessage());
        }
    }
    
    private void awardTeamFightPoints(String winningTeam) {
        try {
            
            java.io.File file = new java.io.File(plugin.getDataFolder(), "winners.json");
            Map<String, PlayerStats> playerData = new HashMap<>();
            
            if (file.exists()) {
                try (java.io.FileReader reader = new java.io.FileReader(file)) {
                    com.google.gson.reflect.TypeToken<Map<String, PlayerStats>> typeToken = 
                        new com.google.gson.reflect.TypeToken<Map<String, PlayerStats>>(){};
                    Map<String, PlayerStats> loaded = new com.google.gson.Gson().fromJson(reader, typeToken.getType());
                    if (loaded != null) {
                        playerData = loaded;
                    }
                }
            }
            
            
            Set<Player> allPlayers = new HashSet<>();
            allPlayers.addAll(blueTeam);
            allPlayers.addAll(redTeam);
            
            for (Player player : allPlayers) {
                PlayerStats stats = playerData.getOrDefault(player.getName(), new PlayerStats(player.getName(), 0));
                int points = 1; 
                
                
                int kills = teamFightKills.getOrDefault(player, 0);
                points += kills;
                
                
                boolean isWinner = false;
                if (winningTeam != null) {
                    if (winningTeam.contains("BLUE") && blueTeam.contains(player)) {
                        points += 1; 
                        isWinner = true;
                    } else if (winningTeam.contains("RED") && redTeam.contains(player)) {
                        points += 1; 
                        isWinner = true;
                    }
                }
                
                stats.points += points;
                playerData.put(player.getName(), stats);
                
                
                String message = "§a§lTEAM FIGHT RESULTS:";
                message += "\n§e+1 point for participation";
                if (kills > 0) {
                    message += "\n§e+" + kills + " points for " + kills + " kills";
                }
                if (isWinner) {
                    message += "\n§e+1 point for survival";
                }
                message += "\n§a§lTotal: +" + points + " points!";
                
                player.sendMessage(message);
            }
            
            
            try (java.io.FileWriter writer = new java.io.FileWriter(file)) {
                new com.google.gson.Gson().toJson(playerData, writer);
            }
            
            
            Bukkit.getScheduler().runTaskLater(plugin, this::resetTeamFightPlayers, 100L);
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to award Team Fight points: " + e.getMessage());
            e.printStackTrace();
            resetTeamFightPlayers();
        }
    }
    
    private void resetTeamFightPlayers() {
        Location spawn = Bukkit.getWorlds().get(0).getSpawnLocation();
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            
            player.setDisplayName(player.getName());
            player.setPlayerListName(player.getName());
            
            
            if (player.getGameMode() != GameMode.ADVENTURE) {
                player.setGameMode(GameMode.ADVENTURE);
            }
            
            
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            
            
            player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 255, false, false));
            
            
            player.teleport(spawn);
        }
        
        
        blueTeam.clear();
        redTeam.clear();
        deadPlayers.clear();
        teamFightKills.clear();
        teamFightActive = false;
        currentGame = null; 
        
        Bukkit.broadcastMessage("§aAll players have been returned to spawn!");
        
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            startVoting();
            Bukkit.broadcastMessage("§a§lA new voting round has started!");
        }, 60L);
    }
    
    private void startLavaRisingGame(List<Player> players) {
        plugin.getLogger().info("Lava Rising game started with " + players.size() + " players");
    }
    
    
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (currentGame != null && currentGame.equals("Team Fight") && event.getDamager() instanceof Player && event.getEntity() instanceof Player) {
            Player attacker = (Player) event.getDamager();
            Player victim = (Player) event.getEntity();
            
            
            if ((blueTeam.contains(attacker) && blueTeam.contains(victim)) ||
                (redTeam.contains(attacker) && redTeam.contains(victim))) {
                event.setCancelled(true);
                attacker.sendMessage("§cYou cannot attack your teammate!");
            }
        }
    }
    
    
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (currentGame != null && currentGame.equals("Team Fight")) {
            Player victim = event.getEntity();
            Player killer = victim.getKiller();
            
            
            event.setKeepInventory(true);
            event.getDrops().clear();
            
            
            Bukkit.getScheduler().runTask(plugin, () -> {
                victim.spigot().respawn();
                
                
                deadPlayers.add(victim);
                victim.setGameMode(GameMode.SPECTATOR);
                
                
                Location spectatorLoc = new Location(victim.getWorld(), 356, 85, 183);
                victim.teleport(spectatorLoc);
                
                victim.sendMessage("§cYou have been eliminated! You are now spectating.");
                
                
                updateTeamFightScoreboard();
                
                
                checkTeamFightWinCondition();
            });
            
            if (killer != null && killer instanceof Player) {
                
                awardTeamFightPoints((Player) killer, 10, "Kill");
            }
        }
    }
    
    
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (currentGame != null && currentGame.equals("Team Fight")) {
            Player player = event.getPlayer();
            
            
            if (deadPlayers.contains(player)) {
                event.setRespawnLocation(new Location(player.getWorld(), 356, 85, 183));
            }
        }
    }
    
    private void updateTeamFightScoreboard() {
        
        int aliveBlue = 0;
        int aliveRed = 0;
        
        for (Player player : blueTeam) {
            if (!deadPlayers.contains(player) && player.isOnline()) {
                aliveBlue++;
            }
        }
        
        for (Player player : redTeam) {
            if (!deadPlayers.contains(player) && player.isOnline()) {
                aliveRed++;
            }
        }
        
        
        Set<Player> allPlayers = new HashSet<>();
        allPlayers.addAll(blueTeam);
        allPlayers.addAll(redTeam);
        
        for (Player player : allPlayers) {
            
            Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective obj = board.registerNewObjective("teamfight", Criteria.DUMMY, "§6§lTEAM FIGHT");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            
            
            obj.getScore("§9§lBlue Team: §f" + aliveBlue).setScore(4);
            obj.getScore("§c§lRed Team: §f" + aliveRed).setScore(3);
            obj.getScore("").setScore(2);
            obj.getScore("§7§lStatus: " + (deadPlayers.contains(player) ? "§cDead" : "§aAlive")).setScore(1);
            
            player.setScoreboard(board);
        }
    }
    
    private void checkTeamFightWinCondition() {
        
        int aliveBlue = 0;
        int aliveRed = 0;
        
        for (Player player : blueTeam) {
            if (!deadPlayers.contains(player) && player.isOnline()) {
                aliveBlue++;
            }
        }
        
        for (Player player : redTeam) {
            if (!deadPlayers.contains(player) && player.isOnline()) {
                aliveRed++;
            }
        }
        
        
        if (aliveBlue == 0 && aliveRed > 0) {
            endTeamFight("§c§lRED TEAM");
        } else if (aliveRed == 0 && aliveBlue > 0) {
            endTeamFight("§9§lBLUE TEAM");
        } else if (aliveBlue == 0 && aliveRed == 0) {
            endTeamFight(null); 
        }
    }
}
