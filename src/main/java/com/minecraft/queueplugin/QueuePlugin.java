package com.minecraft.queueplugin;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.scheduler.BukkitTask;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Field;

public class QueuePlugin extends JavaPlugin implements Listener {
    
    
    private static class PlayerStats {
        public int points; 
        
        public PlayerStats() {
            this.points = 0;
        }
        
        public PlayerStats(int points) {
            this.points = points;
        }
    }
    
    
    private static class Winner {
        public String playerName;
        public int points; 
        public int position;
        
        public Winner(String playerName, int points, int position) {
            this.playerName = playerName;
            this.points = points;
            this.position = position;
        }
    }
    
    private Set<Player> queuedPlayers;
    private Scoreboard scoreboard;
    private File winnersFile;
    private int winners;
    private boolean queueActive = false;
    
    
    private final Location FIRST_PLACE = new Location(null, -18.5, 70, -0.5);
    private final Location SECOND_PLACE = new Location(null, -18.5, 69, -4.5);
    private final Location THIRD_PLACE = new Location(null, -18.5, 68, 3.5);
    
    
    private List<Object> spawnedNPCs = new ArrayList<>();
    private List<ArmorStand> holograms = new ArrayList<>();

    private BukkitTask updateTask;
    private BukkitTask playerTrackingTask;
    private BukkitTask leaderboardUpdateTask;
    private long lastPodiumUpdate = 0; 
    private static final long PODIUM_UPDATE_COOLDOWN = 2000; 
    
    
    private VotingSystem votingSystem;

    @Override
    public void onEnable() {
        getLogger().info("MinigamesPlugin is starting up!");
        queuedPlayers = new HashSet<>();
        loadWinnersData();
        
        
        if (!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }
        
        
        if (getCommand("game") == null) {
            getLogger().severe("Failed to register 'game' command! Check plugin.yml");
        } else {
            getLogger().info("Successfully registered 'game' command!");
        }
        
        
        World world = Bukkit.getWorlds().get(0);
        FIRST_PLACE.setWorld(world);
        SECOND_PLACE.setWorld(world);
        THIRD_PLACE.setWorld(world);
        
        
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        scoreboard = manager.getNewScoreboard();
        
        winnersFile = new File(getDataFolder(), "winners.json");
        if (!winnersFile.exists()) {
            try {
                winnersFile.createNewFile();
                saveWinnersData();
            } catch (IOException e) {
                getLogger().severe("Could not create winners.json file!");
            }
        }
        
        
        getServer().getPluginManager().registerEvents(this, this);
        
        
        votingSystem = new VotingSystem(this);
        
        
        
        
        startPlayerTrackingTask();
        
        
        startLeaderboardUpdateTask();
    }
    
    private void startPlayerTrackingTask() {
        if (playerTrackingTask != null) {
            playerTrackingTask.cancel();
        }
        
        playerTrackingTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            trackOnlinePlayers();
        }, 20L, 10L); 
        
        getLogger().info("Started player tracking task (every 10 ticks)");
    }
    
    private void startLeaderboardUpdateTask() {
        if (leaderboardUpdateTask != null) {
            leaderboardUpdateTask.cancel();
        }
        
        leaderboardUpdateTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            spawnPodiumNPCs();
            getLogger().info("Updated leaderboard NPCs (scheduled update)");
        }, 20L, 200L); 
        
        getLogger().info("Started leaderboard update task (every 10 seconds)");
    }

    private void trackOnlinePlayers() {
        File winnersFile = new File(getDataFolder(), "winners.json");
        
        try {
            Map<String, PlayerStats> playerData;
            
            
            if (winnersFile.exists()) {
                try (FileReader reader = new FileReader(winnersFile)) {
                    Gson gson = new Gson();
                    
                    
                    try {
                        playerData = gson.fromJson(reader, 
                            new com.google.gson.reflect.TypeToken<Map<String, PlayerStats>>(){}.getType());
                        
                        
                        if (playerData == null) {
                            playerData = new HashMap<>();
                        }
                    } catch (Exception e) {
                        
                        getLogger().info("Converting old JSON format to new player tracking format");
                        playerData = new HashMap<>();
                    }
                } catch (Exception e) {
                    getLogger().warning("Failed to read JSON file, creating new format: " + e.getMessage());
                    playerData = new HashMap<>();
                }
            } else {
                playerData = new HashMap<>();
            }
            
            boolean dataChanged = false;
            
            
            for (Player player : Bukkit.getOnlinePlayers()) {
                String playerName = player.getName();
                
                
                if (!playerData.containsKey(playerName)) {
                    playerData.put(playerName, new PlayerStats(0));
                    dataChanged = true;
                    getLogger().info("Added new player to rankings: " + playerName + " (0 points)");
                }
            }
            
            
            if (dataChanged) {
                try (FileWriter writer = new FileWriter(winnersFile)) {
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    gson.toJson(playerData, writer);
                    
                    getLogger().info("Updated player tracking data with " + playerData.size() + " players");
                }
            }
            
        } catch (Exception e) {
            getLogger().warning("Failed to track players: " + e.getMessage());
            
            try {
                Map<String, PlayerStats> newData = new HashMap<>();
                
                
                for (Player player : Bukkit.getOnlinePlayers()) {
                    newData.put(player.getName(), new PlayerStats(0));
                }
                
                
                if (!newData.isEmpty()) {
                    try (FileWriter writer = new FileWriter(winnersFile)) {
                        Gson gson = new GsonBuilder().setPrettyPrinting().create();
                        gson.toJson(newData, writer);
                        getLogger().info("Recreated player tracking file with proper format");
                    }
                }
            } catch (Exception recreateError) {
                getLogger().severe("Failed to recreate player tracking file: " + recreateError.getMessage());
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void setupScoreboard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        scoreboard = manager.getNewScoreboard();
        
        Objective objective = scoreboard.registerNewObjective("queue", "dummy");
        objective.setDisplayName("§6§lGame Queuing");  
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    private void updateScoreboard() {
        
        if (votingSystem != null && (votingSystem.isVotingActive() || isAnyGameActive())) {
            return;
        }
        
        Objective objective = scoreboard.getObjective("queue");
        if (objective != null) {
            
            for (String entry : scoreboard.getEntries()) {
                scoreboard.resetScores(entry);
            }

            
            java.time.LocalDate currentDate = java.time.LocalDate.now();
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
            String dateStr = currentDate.format(formatter);
            
            int scoreValue = 5;
            
            
            objective.getScore("§7" + dateStr).setScore(scoreValue--);
            
            
            objective.getScore(" ").setScore(scoreValue--);
            
            
            if (queueActive) {
                objective.getScore("§a§lQUEUE ACTIVE").setScore(scoreValue--);
                objective.getScore("§eAll players queued!").setScore(scoreValue--);
                objective.getScore("§7Waiting for OP to start...").setScore(scoreValue--);
            } else {
                objective.getScore("§7§lQueue inactive").setScore(scoreValue--);
                objective.getScore("§7Use §a/game queue §7to start").setScore(scoreValue--);
            }
            
            
            objective.getScore("  ").setScore(scoreValue--);
            
            
            int totalPlayers = Bukkit.getOnlinePlayers().size();
            objective.getScore("§f☺ Players: " + totalPlayers).setScore(scoreValue--);
            
            
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.setScoreboard(scoreboard);
            }
        }
    }

    private void loadWinnersData() {
        try {
            winnersFile = new File(getDataFolder(), "winners.json");
            if (winnersFile.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(winnersFile));
                Gson gson = new Gson();
                WinnersData data = gson.fromJson(reader, WinnersData.class);
                reader.close();
                if (data != null) {
                    winners = data.getWinners();
                } else {
                    winners = 0;
                }
            } else {
                winners = 0;
            }
        } catch (Exception e) {
            getLogger().severe("Error loading winners data: " + e.getMessage());
            winners = 0;
        }
    }

    private void saveWinnersData() {
        try {
            if (!getDataFolder().exists()) {
                getDataFolder().mkdir();
            }
            
            FileWriter writer = new FileWriter(winnersFile);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            WinnersData data = new WinnersData(winners);
            gson.toJson(data, writer);
            writer.close();
        } catch (IOException e) {
            getLogger().severe("Could not save winners data!");
        }
    }

    
    private void cleanupNPCs() {
        
        for (Object npcEntity : spawnedNPCs) {
            try {
                if (npcEntity instanceof ArmorStand) {
                    
                    ((ArmorStand) npcEntity).remove();
                } else {
                    
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        sendDestroyPacket(player, npcEntity);
                    }
                }
            } catch (Exception e) {
                getLogger().warning("Failed to cleanup NPC: " + e.getMessage());
            }
        }
        spawnedNPCs.clear();
        
        
        for (ArmorStand hologram : holograms) {
            try {
                hologram.remove();
            } catch (Exception e) {
                getLogger().warning("Failed to remove hologram: " + e.getMessage());
            }
        }
        holograms.clear();
        
        
        Location[] podiumLocations = {FIRST_PLACE, SECOND_PLACE, THIRD_PLACE};
        for (Location location : podiumLocations) {
            if (location != null && location.getWorld() != null) {
                
                Collection<ArmorStand> nearbyArmorStands = location.getWorld().getNearbyEntities(location, 5, 5, 5)
                    .stream()
                    .filter(entity -> entity instanceof ArmorStand)
                    .map(entity -> (ArmorStand) entity)
                    .collect(Collectors.toList());
                
                for (ArmorStand armorStand : nearbyArmorStands) {
                    try {
                        armorStand.remove();
                        getLogger().info("Removed existing armor stand near podium at " + location.toString());
                    } catch (Exception e) {
                        getLogger().warning("Failed to remove existing armor stand: " + e.getMessage());
                    }
                }
            }
        }
    }
    
    private void spawnPodiumNPCs() {
        
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastPodiumUpdate < PODIUM_UPDATE_COOLDOWN) {
            getLogger().info("Podium update skipped due to cooldown (" + (currentTime - lastPodiumUpdate) + "ms since last update)");
            return;
        }
        lastPodiumUpdate = currentTime;
        
        
        cleanupNPCs();
        
        
        List<Winner> winners = loadWinners();
        
        getLogger().info("Loading podium NPCs. Found " + winners.size() + " players in rankings:");
        for (int i = 0; i < Math.min(winners.size(), 3); i++) {
            Winner winner = winners.get(i);
            getLogger().info("  Position " + (i+1) + ": " + winner.playerName + " with " + winner.points + " points");
        }
        
        Location[] locations = {FIRST_PLACE, SECOND_PLACE, THIRD_PLACE};
        String[] positions = {"1st Place", "2nd Place", "3rd Place"};
        
        for (int i = 0; i < 3; i++) {
            String playerName = null;
            int playerPoints = 0;
            
            
            if (i < winners.size()) {
                playerName = winners.get(i).playerName;
                playerPoints = winners.get(i).points;
            }
            
            
            if (playerName != null) {
                getLogger().info("Spawning NPC for position " + (i+1) + ": " + playerName + " (" + playerPoints + " points)");
                
                
                spawnPlayerLikeNPC(locations[i], playerName);
                
                
                spawnHologram(locations[i].clone().add(0, 2.5, 0), positions[i], playerName + " (" + playerPoints + " points)");
            } else {
                getLogger().info("No player found for position " + (i+1) + ", skipping NPC spawn");
            }
        }
        
        getLogger().info("Spawned podium NPCs with current rankings");
    }
    
    
    private void spawnNPC(Location location, String displayName) {
        try {
            
            if (createRealNPC(location, displayName)) {
                return;
            }
            
            
            createArmorStandNPC(location, displayName);
            
        } catch (Exception e) {
            getLogger().severe("Failed to spawn NPC: " + e.getMessage());
            e.printStackTrace();
            
            
            try {
                createArmorStandNPC(location, displayName);
            } catch (Exception fallbackError) {
                getLogger().severe("Even fallback NPC creation failed: " + fallbackError.getMessage());
            }
        }
    }
    
    private boolean createRealNPC(Location location, String displayName) {
        try {
            UUID npcId = UUID.randomUUID();
            String npcName = "NPC_" + displayName.replace(" ", "_");
            
            
            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            Object profile = gameProfileClass.getConstructor(UUID.class, String.class)
                .newInstance(npcId, npcName);
            
            
            String texture = "ewogICJ0aW1lc3RhbXAiIDogMTcyMTU2NzA5NjQ5MCwKICAicHJvZmlsZUlkIiA6ICJlYTA4ZjhlZTdiOTg0YmFlYWM3N2JhYzk3ZWVkYzE4NSIsCiAgInByb2ZpbGVOYW1lIiA6ICJXYXlkZXJUTSIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS81N2EwMmNmZmE4MDk4YTA3NjBiY2IyZDg5MzZjMWE0YzljMjdmZjJjZmI0YzlhNWE0NmFmMWFiYjVjMDMwODFkIiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0=";
            String signature = "signature_placeholder"; 
            
            Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
            Object property = propertyClass.getConstructor(String.class, String.class, String.class)
                .newInstance("textures", texture, signature);
            
            Object properties = profile.getClass().getMethod("getProperties").invoke(profile);
            properties.getClass().getMethod("put", Object.class, Object.class)
                .invoke(properties, "textures", property);
            
            
            Object nmsWorld = getNMSWorld(location.getWorld());
            Object minecraftServer = getMinecraftServer();
            
            
            Object npcEntity = createEntityPlayer(minecraftServer, nmsWorld, profile, location);
            
            if (npcEntity != null) {
                
                for (Player player : Bukkit.getOnlinePlayers()) {
                    sendSpawnPackets(player, npcEntity);
                }
                
                spawnedNPCs.add(npcEntity);
                getLogger().info("Spawned EntityPlayer NPC: " + displayName + " at " + location.toString());
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            getLogger().warning("EntityPlayer NPC creation failed, falling back to armor stand: " + e.getMessage());
            return false;
        }
    }
    
    private void createArmorStandNPC(Location location, String displayName) {
        ArmorStand npc = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        
        
        npc.setVisible(false); 
        npc.setGravity(false);
        npc.setBasePlate(false);
        npc.setArms(true);
        npc.setSmall(false);
        npc.setMarker(false); 
        
        
        location.setYaw(270.0f);
        npc.teleport(location);
        
        
        addFullSkinOutfit(npc, displayName);
        
        
        spawnedNPCs.add(npc);
        
        getLogger().info("Spawned Full-Skin Armor Stand NPC: " + displayName + " at " + location.toString());
    }
    
    private void addFullSkinOutfit(ArmorStand npc, String displayName) {
        
        org.bukkit.inventory.EntityEquipment equipment = npc.getEquipment();
        
        
        equipment.setHelmet(createSimpleCustomHead(displayName));
        
        
        equipment.setChestplate(createCustomChestplate(displayName));
        equipment.setLeggings(createCustomLeggings(displayName));  
        equipment.setBoots(createCustomBoots(displayName));
        
        
        equipment.setItemInMainHand(createHandItem(displayName));
        
        
        
    }
    
    private org.bukkit.inventory.ItemStack createCustomChestplate(String displayName) {
        
        org.bukkit.inventory.ItemStack chest = new org.bukkit.inventory.ItemStack(org.bukkit.Material.LEATHER_CHESTPLATE);
        org.bukkit.inventory.meta.LeatherArmorMeta meta = (org.bukkit.inventory.meta.LeatherArmorMeta) chest.getItemMeta();
        
        if (meta != null) {
            
            if (displayName.equals("1st Place")) {
                meta.setColor(org.bukkit.Color.YELLOW); 
            } else if (displayName.equals("2nd Place")) {
                meta.setColor(org.bukkit.Color.SILVER); 
            } else if (displayName.equals("3rd Place")) {
                meta.setColor(org.bukkit.Color.fromRGB(205, 127, 50)); 
            } else {
                meta.setColor(org.bukkit.Color.WHITE); 
            }
            
            meta.setDisplayName("§f" + displayName + " Shirt");
            chest.setItemMeta(meta);
        }
        
        return chest;
    }
    
    private org.bukkit.inventory.ItemStack createCustomLeggings(String displayName) {
        org.bukkit.inventory.ItemStack legs = new org.bukkit.inventory.ItemStack(org.bukkit.Material.LEATHER_LEGGINGS);
        org.bukkit.inventory.meta.LeatherArmorMeta meta = (org.bukkit.inventory.meta.LeatherArmorMeta) legs.getItemMeta();
        
        if (meta != null) {
            
            if (displayName.equals("1st Place")) {
                meta.setColor(org.bukkit.Color.fromRGB(200, 180, 0)); 
            } else if (displayName.equals("2nd Place")) {
                meta.setColor(org.bukkit.Color.GRAY); 
            } else if (displayName.equals("3rd Place")) {
                meta.setColor(org.bukkit.Color.fromRGB(160, 100, 30)); 
            } else {
                meta.setColor(org.bukkit.Color.GRAY); 
            }
            
            meta.setDisplayName("§f" + displayName + " Pants");
            legs.setItemMeta(meta);
        }
        
        return legs;
    }
    
    private org.bukkit.inventory.ItemStack createCustomBoots(String displayName) {
        org.bukkit.inventory.ItemStack boots = new org.bukkit.inventory.ItemStack(org.bukkit.Material.LEATHER_BOOTS);
        org.bukkit.inventory.meta.LeatherArmorMeta meta = (org.bukkit.inventory.meta.LeatherArmorMeta) boots.getItemMeta();
        
        if (meta != null) {
            
            if (displayName.equals("1st Place")) {
                meta.setColor(org.bukkit.Color.fromRGB(150, 120, 0)); 
            } else if (displayName.equals("2nd Place")) {
                meta.setColor(org.bukkit.Color.fromRGB(100, 100, 100)); 
            } else if (displayName.equals("3rd Place")) {
                meta.setColor(org.bukkit.Color.fromRGB(120, 80, 20)); 
            } else {
                meta.setColor(org.bukkit.Color.BLACK); 
            }
            
            meta.setDisplayName("§f" + displayName + " Boots");
            boots.setItemMeta(meta);
        }
        
        return boots;
    }
    
    private org.bukkit.inventory.ItemStack createHandItem(String displayName) {
        
        if (displayName.equals("1st Place")) {
            org.bukkit.inventory.ItemStack trophy = new org.bukkit.inventory.ItemStack(org.bukkit.Material.GOLDEN_SWORD);
            org.bukkit.inventory.meta.ItemMeta meta = trophy.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§6§l1st Place Trophy");
                trophy.setItemMeta(meta);
            }
            return trophy;
        } else if (displayName.equals("2nd Place")) {
            org.bukkit.inventory.ItemStack trophy = new org.bukkit.inventory.ItemStack(org.bukkit.Material.IRON_SWORD);
            org.bukkit.inventory.meta.ItemMeta meta = trophy.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§7§l2nd Place Trophy");
                trophy.setItemMeta(meta);
            }
            return trophy;
        } else if (displayName.equals("3rd Place")) {
            org.bukkit.inventory.ItemStack trophy = new org.bukkit.inventory.ItemStack(org.bukkit.Material.WOODEN_SWORD);
            org.bukkit.inventory.meta.ItemMeta meta = trophy.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§c§l3rd Place Trophy");
                trophy.setItemMeta(meta);
            }
            return trophy;
        }
        
        
        return new org.bukkit.inventory.ItemStack(org.bukkit.Material.AIR);
    }
    
    private org.bukkit.inventory.ItemStack createSimpleCustomHead(String playerName) {
        org.bukkit.inventory.ItemStack head = new org.bukkit.inventory.ItemStack(org.bukkit.Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
        
        if (meta != null) {
            try {
                
                Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile");
                Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
                
                
                UUID headUUID = UUID.nameUUIDFromBytes(("CustomNPC_" + playerName).getBytes());
                Object profile = profileClass.getConstructor(java.util.UUID.class, String.class)
                    .newInstance(headUUID, "NPC_" + playerName.replace(" ", "_"));
                
                String texture;
                
                
                texture = getPlayerSkinTexture(playerName);
                
                
                Object property = propertyClass.getConstructor(String.class, String.class)
                    .newInstance("textures", texture);
                
                
                Object properties = profile.getClass().getMethod("getProperties").invoke(profile);
                properties.getClass().getMethod("put", Object.class, Object.class)
                    .invoke(properties, "textures", property);
                
                
                Field profileField = meta.getClass().getDeclaredField("profile");
                profileField.setAccessible(true);
                profileField.set(meta, profile);
                
            } catch (Exception e) {
                getLogger().warning("Failed to set custom texture for " + playerName + ", using fallback: " + e.getMessage());
                
                try {
                    UUID fallbackUUID = UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes());
                    org.bukkit.OfflinePlayer fallback = Bukkit.getOfflinePlayer(fallbackUUID);
                    meta.setOwningPlayer(fallback);
                } catch (Exception ignored) {
                    
                }
            }
            
            meta.setDisplayName("§f" + playerName);
            head.setItemMeta(meta);
        }
        
        return head;
    }
    
    private String getPlayerSkinTexture(String playerName) {
        
        return "";
    }
    
    
    private void spawnPlayerLikeNPC(Location location, String playerName) {
        try {
            
            if (location.getWorld() == null) {
                location.setWorld(Bukkit.getWorld("world")); 
            }
            
            
            ArmorStand npc = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
            
            
            npc.setVisible(false); 
            npc.setGravity(false);
            npc.setBasePlate(false);
            npc.setArms(true);
            npc.setSmall(false);
            npc.setMarker(false);
            npc.setCustomNameVisible(false); 
            
            
            location.setYaw(270.0f); 
            npc.teleport(location);
            
            
            org.bukkit.inventory.EntityEquipment equipment = npc.getEquipment();
            if (equipment != null) {
                equipment.setHelmet(createPlayerHead(playerName));
            }
            
            
            spawnedNPCs.add(npc);
            
            getLogger().info("Spawned head-only NPC for player: " + playerName + " at " + location.toString());
            
        } catch (Exception e) {
            getLogger().severe("Failed to spawn head-only NPC for " + playerName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private org.bukkit.inventory.ItemStack createPlayerHead(String playerName) {
        org.bukkit.inventory.ItemStack head = new org.bukkit.inventory.ItemStack(org.bukkit.Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
        
        if (meta != null) {
            try {
                
                org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
                meta.setOwningPlayer(offlinePlayer);
                
            } catch (Exception e) {
                getLogger().warning("Failed to set player head for " + playerName + ": " + e.getMessage());
                
            }
            
            meta.setDisplayName("§f" + playerName);
            head.setItemMeta(meta);
        }
        
        return head;
    }
    
    private org.bukkit.inventory.ItemStack createPlayerSkinChestplate(String playerName) {
        
        org.bukkit.inventory.ItemStack chest = new org.bukkit.inventory.ItemStack(org.bukkit.Material.LEATHER_CHESTPLATE);
        org.bukkit.inventory.meta.LeatherArmorMeta meta = (org.bukkit.inventory.meta.LeatherArmorMeta) chest.getItemMeta();
        
        if (meta != null) {
            
            org.bukkit.Color skinColor = getSkinColorForPlayer(playerName);
            meta.setColor(skinColor);
            meta.setDisplayName("§f" + playerName + "'s Skin");
            chest.setItemMeta(meta);
        }
        
        return chest;
    }
    
    private org.bukkit.inventory.ItemStack createPlayerSkinLeggings(String playerName) {
        org.bukkit.inventory.ItemStack legs = new org.bukkit.inventory.ItemStack(org.bukkit.Material.LEATHER_LEGGINGS);
        org.bukkit.inventory.meta.LeatherArmorMeta meta = (org.bukkit.inventory.meta.LeatherArmorMeta) legs.getItemMeta();
        
        if (meta != null) {
            
            org.bukkit.Color skinColor = getSkinColorForPlayer(playerName);
            org.bukkit.Color pantsColor = org.bukkit.Color.fromRGB(
                Math.max(0, skinColor.getRed() - 30),
                Math.max(0, skinColor.getGreen() - 30),
                Math.max(0, skinColor.getBlue() - 30)
            );
            meta.setColor(pantsColor);
            meta.setDisplayName("§f" + playerName + "'s Pants");
            legs.setItemMeta(meta);
        }
        
        return legs;
    }
    
    private org.bukkit.inventory.ItemStack createPlayerSkinBoots(String playerName) {
        org.bukkit.inventory.ItemStack boots = new org.bukkit.inventory.ItemStack(org.bukkit.Material.LEATHER_BOOTS);
        org.bukkit.inventory.meta.LeatherArmorMeta meta = (org.bukkit.inventory.meta.LeatherArmorMeta) boots.getItemMeta();
        
        if (meta != null) {
            
            org.bukkit.Color bootColor = org.bukkit.Color.fromRGB(50, 30, 20); 
            meta.setColor(bootColor);
            meta.setDisplayName("§f" + playerName + "'s Boots");
            boots.setItemMeta(meta);
        }
        
        return boots;
    }
    
    private org.bukkit.Color getSkinColorForPlayer(String playerName) {
        
        
        switch (playerName.toLowerCase()) {
            case "Unknown":
                return org.bukkit.Color.fromRGB(220, 180, 140); 
            case "steve":
                return org.bukkit.Color.fromRGB(200, 160, 120); 
            case "alex":
                return org.bukkit.Color.fromRGB(200, 160, 120); 
            default:
                
                int hash = playerName.hashCode();
                int r = 180 + (Math.abs(hash) % 40); 
                int g = 140 + (Math.abs(hash >> 8) % 40); 
                int b = 100 + (Math.abs(hash >> 16) % 40); 
                return org.bukkit.Color.fromRGB(r, g, b);
        }
    }
    
    private org.bukkit.inventory.ItemStack createPlayerItem(String playerName) {
        
        org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND_SWORD);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName("§6" + playerName + "'s Trophy");
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    private void addPlayerLikeEquipment(ArmorStand npc, String displayName) {
        org.bukkit.inventory.EntityEquipment equipment = npc.getEquipment();
        
        if (equipment != null) {
            
            equipment.setHelmet(createSimpleCustomHead(displayName));
            
            
            if (displayName.equals("1st Place")) {
                
                equipment.setChestplate(new org.bukkit.inventory.ItemStack(org.bukkit.Material.GOLDEN_CHESTPLATE));
                equipment.setLeggings(new org.bukkit.inventory.ItemStack(org.bukkit.Material.GOLDEN_LEGGINGS));
                equipment.setBoots(new org.bukkit.inventory.ItemStack(org.bukkit.Material.GOLDEN_BOOTS));
                equipment.setItemInMainHand(new org.bukkit.inventory.ItemStack(org.bukkit.Material.GOLDEN_SWORD));
            } else if (displayName.equals("2nd Place")) {
                
                equipment.setChestplate(new org.bukkit.inventory.ItemStack(org.bukkit.Material.IRON_CHESTPLATE));
                equipment.setLeggings(new org.bukkit.inventory.ItemStack(org.bukkit.Material.IRON_LEGGINGS));
                equipment.setBoots(new org.bukkit.inventory.ItemStack(org.bukkit.Material.IRON_BOOTS));
                equipment.setItemInMainHand(new org.bukkit.inventory.ItemStack(org.bukkit.Material.IRON_SWORD));
            } else {
                
                equipment.setChestplate(createColoredLeatherArmor(org.bukkit.Material.LEATHER_CHESTPLATE, org.bukkit.Color.fromRGB(139, 69, 19))); 
                equipment.setLeggings(createColoredLeatherArmor(org.bukkit.Material.LEATHER_LEGGINGS, org.bukkit.Color.fromRGB(139, 69, 19)));
                equipment.setBoots(createColoredLeatherArmor(org.bukkit.Material.LEATHER_BOOTS, org.bukkit.Color.fromRGB(139, 69, 19)));
                equipment.setItemInMainHand(new org.bukkit.inventory.ItemStack(org.bukkit.Material.WOODEN_SWORD));
            }
        }
    }
    
    private org.bukkit.inventory.ItemStack createColoredLeatherArmor(org.bukkit.Material material, org.bukkit.Color color) {
        org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(material);
        org.bukkit.inventory.meta.LeatherArmorMeta meta = (org.bukkit.inventory.meta.LeatherArmorMeta) item.getItemMeta();
        
        if (meta != null) {
            meta.setColor(color);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    
    private Object getMinecraftServer() throws Exception {
        return Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
    }
    
    private Object getNMSWorld(World world) throws Exception {
        return world.getClass().getMethod("getHandle").invoke(world);
    }
    
    private Object createEntityPlayer(Object minecraftServer, Object nmsWorld, Object profile, Location location) throws Exception {
        String version = getServerVersion();
        
        try {
            
            Class<?> entityPlayerClass = Class.forName("net.minecraft.server." + version + ".EntityPlayer");
            
            
            Constructor<?> constructor = null;
            Constructor<?>[] constructors = entityPlayerClass.getConstructors();
            
            for (Constructor<?> c : constructors) {
                Class<?>[] paramTypes = c.getParameterTypes();
                if (paramTypes.length >= 3) {
                    constructor = c;
                    break;
                }
            }
            
            if (constructor == null) {
                getLogger().severe("Could not find suitable EntityPlayer constructor");
                return null;
            }
            
            
            Object[] params = new Object[constructor.getParameterCount()];
            params[0] = minecraftServer;
            params[1] = nmsWorld;
            params[2] = profile;
            
            for (int i = 3; i < params.length; i++) {
                params[i] = null;
            }
            
            Object entityPlayer = constructor.newInstance(params);
            
            
            Method setPositionRotation = entityPlayer.getClass().getMethod("setPositionRotation", 
                double.class, double.class, double.class, float.class, float.class);
            setPositionRotation.invoke(entityPlayer, 
                location.getX(), location.getY(), location.getZ(), 270.0f, 0.0f); 
                
            return entityPlayer;
            
        } catch (Exception e) {
            getLogger().warning("Failed to create EntityPlayer NPC, this might be due to version compatibility: " + e.getMessage());
            
            return null;
        }
    }
    
    private void sendSpawnPackets(Player player, Object entityPlayer) {
        try {
            String version = getServerVersion();
            
            
            Object nmsPlayer = player.getClass().getMethod("getHandle").invoke(player);
            Field connectionField = null;
            
            
            String[] connectionFieldNames = {"playerConnection", "connection", "c"};
            for (String fieldName : connectionFieldNames) {
                try {
                    connectionField = nmsPlayer.getClass().getField(fieldName);
                    break;
                } catch (NoSuchFieldException ignored) {}
            }
            
            if (connectionField == null) {
                getLogger().severe("Could not find player connection field");
                return;
            }
            
            Object connection = connectionField.get(nmsPlayer);
            
            
            Class<?> packetClass = Class.forName("net.minecraft.server." + version + ".PacketPlayOutNamedEntitySpawn");
            Constructor<?> constructor = packetClass.getConstructor(entityPlayer.getClass());
            Object packet = constructor.newInstance(entityPlayer);
            
            
            Method sendPacket = connection.getClass().getMethod("sendPacket", 
                Class.forName("net.minecraft.server." + version + ".Packet"));
            sendPacket.invoke(connection, packet);
            
        } catch (Exception e) {
            getLogger().warning("Failed to send spawn packets (version compatibility issue): " + e.getMessage());
        }
    }
    
    private void sendDestroyPacket(Player player, Object npcEntity) {
        try {
            String version = getServerVersion();
            
            
            Method getIdMethod = npcEntity.getClass().getMethod("getId");
            int entityId = (Integer) getIdMethod.invoke(npcEntity);
            
            
            Object nmsPlayer = player.getClass().getMethod("getHandle").invoke(player);
            Field connectionField = nmsPlayer.getClass().getField("playerConnection");
            Object connection = connectionField.get(nmsPlayer);
            
            
            Class<?> packetClass = Class.forName("net.minecraft.server." + version + ".PacketPlayOutEntityDestroy");
            Constructor<?> constructor = packetClass.getConstructor(int[].class);
            Object packet = constructor.newInstance((Object) new int[]{entityId});
            
            
            Method sendPacket = connection.getClass().getMethod("sendPacket", 
                Class.forName("net.minecraft.server." + version + ".Packet"));
            sendPacket.invoke(connection, packet);
            
        } catch (Exception e) {
            getLogger().warning("Failed to send destroy packet: " + e.getMessage());
        }
    }
    
    private String getServerVersion() {
        String packageName = Bukkit.getServer().getClass().getPackage().getName();
        String[] parts = packageName.replace(".", ",").split(",");
        
        
        if (parts.length >= 4) {
            return parts[3]; 
        } else if (parts.length == 3) {
            
            return "v1_21_R1"; 
        }
        
        return "v1_21_R1"; 
    }
    
    private List<Winner> loadWinners() {
        File winnersFile = new File(getDataFolder(), "winners.json");
        
        if (!winnersFile.exists()) {
            
            createDefaultWinnersFile();
            return getDefaultRanking();
        }
        
        try (FileReader reader = new FileReader(winnersFile)) {
            Gson gson = new Gson();
            
            
            try {
                Map<String, PlayerStats> playerData = gson.fromJson(reader, 
                    new com.google.gson.reflect.TypeToken<Map<String, PlayerStats>>(){}.getType());
                
                if (playerData != null && !playerData.isEmpty()) {
                    return rankPlayers(playerData);
                }
            } catch (Exception e) {
                
                getLogger().warning("Old JSON format detected, converting to new format: " + e.getMessage());
                createDefaultWinnersFile();
                return getDefaultRanking();
            }
            
        } catch (Exception e) {
            getLogger().warning("Failed to load winners data: " + e.getMessage());
            
            createDefaultWinnersFile();
        }
        
        return getDefaultRanking();
    }
    
    private void createDefaultWinnersFile() {
        File winnersFile = new File(getDataFolder(), "winners.json");
        
        try (FileWriter writer = new FileWriter(winnersFile)) {
            
            Map<String, PlayerStats> defaultData = new HashMap<>();
            defaultData.put("Unknown", new PlayerStats(0));
            
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(defaultData, writer);
            
            getLogger().info("Created default winners.json file");
        } catch (Exception e) {
            getLogger().severe("Failed to create default winners file: " + e.getMessage());
        }
    }
    
    private List<Winner> rankPlayers(Map<String, PlayerStats> playerData) {
        List<Winner> rankings = new ArrayList<>();
        
        
        for (Map.Entry<String, PlayerStats> entry : playerData.entrySet()) {
            rankings.add(new Winner(entry.getKey(), entry.getValue().points, 0));
        }
        
        
        rankings.sort((a, b) -> {
            if (a.points != b.points) {
                return Integer.compare(b.points, a.points); 
            }
            return a.playerName.compareToIgnoreCase(b.playerName); 
        });
        
        
        for (int i = 0; i < rankings.size(); i++) {
            rankings.get(i).position = i + 1;
        }
        
        return rankings;
    }
    
    private List<Winner> getDefaultRanking() {
        List<Winner> defaults = new ArrayList<>();
        defaults.add(new Winner("Unknown", 0, 1));
        defaults.add(new Winner("Unknown", 0, 2));
        defaults.add(new Winner("Unknown", 0, 3));
        return defaults;
    }
    
    public void addPlayerWin(String playerName) {
        File winnersFile = new File(getDataFolder(), "winners.json");
        
        try {
            Map<String, PlayerStats> playerData;
            
            if (winnersFile.exists()) {
                try (FileReader reader = new FileReader(winnersFile)) {
                    Gson gson = new Gson();
                    playerData = gson.fromJson(reader, 
                        new com.google.gson.reflect.TypeToken<Map<String, PlayerStats>>(){}.getType());
                }
            } else {
                playerData = new HashMap<>();
            }
            
            if (playerData == null) {
                playerData = new HashMap<>();
            }
            
            
            PlayerStats stats = playerData.getOrDefault(playerName, new PlayerStats(0));
            stats.points += 5; 
            playerData.put(playerName, stats);
            
            
            try (FileWriter writer = new FileWriter(winnersFile)) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                gson.toJson(playerData, writer);
            }
            
            getLogger().info("Added win for player: " + playerName + " (Total: " + stats.points + " points)");
            
            
            spawnPodiumNPCs();
            
        } catch (Exception e) {
            getLogger().severe("Failed to add player win: " + e.getMessage());
        }
    }
    
    public void addPlayerPoints(String playerName, int points) {
        File winnersFile = new File(getDataFolder(), "winners.json");
        
        try {
            Map<String, PlayerStats> playerData;
            
            if (winnersFile.exists()) {
                try (FileReader reader = new FileReader(winnersFile)) {
                    Gson gson = new Gson();
                    playerData = gson.fromJson(reader, 
                        new com.google.gson.reflect.TypeToken<Map<String, PlayerStats>>(){}.getType());
                }
            } else {
                playerData = new HashMap<>();
            }
            
            if (playerData == null) {
                playerData = new HashMap<>();
            }
            
            
            PlayerStats stats = playerData.getOrDefault(playerName, new PlayerStats(0));
            stats.points += points;
            playerData.put(playerName, stats);
            
            
            try (FileWriter writer = new FileWriter(winnersFile)) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                gson.toJson(playerData, writer);
            }
            
            getLogger().info("Added " + points + " points for player: " + playerName + " (Total: " + stats.points + " points)");
            
            
            spawnPodiumNPCs();
            
        } catch (Exception e) {
            getLogger().severe("Failed to add player points: " + e.getMessage());
        }
    }
    
    private void saveWinners(List<Winner> winners) {
        File winnersFile = new File(getDataFolder(), "winners.json");
        
        try (FileWriter writer = new FileWriter(winnersFile)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(winners, writer);
        } catch (Exception e) {
            getLogger().severe("Failed to save winners data: " + e.getMessage());
        }
    }
    
    private ArmorStand spawnHologram(Location loc, String position, String playerName) {
        ArmorStand hologram = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        hologram.setVisible(false);
        hologram.setGravity(false);
        hologram.setCustomNameVisible(true);
        hologram.setCustomName("§e" + position + " §7- §a" + playerName);
        hologram.setMarker(true); 
        holograms.add(hologram);
        return hologram;
    }
    
    private ArmorStand spawnHologram(Location loc, String text) {
        ArmorStand hologram = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        hologram.setVisible(false);
        hologram.setGravity(false);
        hologram.setCustomNameVisible(true);
        hologram.setCustomName(text);
        hologram.setMarker(true); 
        holograms.add(hologram);
        return hologram;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("vote")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cThis command can only be used by players!");
                return true;
            }
            
            Player player = (Player) sender;
            
            if (args.length == 0) {
                
                votingSystem.openVotingGUI(player);
                return true;
            }
            
            
            votingSystem.vote(player, args[0]);
            return true;
        }
        
        if (command.getName().equalsIgnoreCase("game")) {
            if (args.length == 0) {
                sender.sendMessage("§cUsage: /game <queue|start|addpoints>");
                return true;
            }
            
            
            if (!args[0].equalsIgnoreCase("queue") && !sender.hasPermission("queueplugin.queue")) {
                sender.sendMessage("§cYou don't have permission to use this command!");
                return true;
            }
            
            if (args[0].equalsIgnoreCase("queue")) {
                
                if (!queueActive) {
                    queueActive = true;
                    setupScoreboard();
                    
                    if (updateTask != null) {
                        updateTask.cancel();
                    }
                    updateTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
                        updateScoreboard();
                    }, 20L, 20L);
                    
                    
                    queuedPlayers.clear();
                    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                        queuedPlayers.add(onlinePlayer);
                        
                        onlinePlayer.setResourcePack("YOUR_TEXTURE_PACK_URL");
                        onlinePlayer.sendMessage("§aYou have been added to the game queue!");
                    }
                    
                    Bukkit.broadcastMessage("§6§l=== GAME QUEUE ACTIVATED ===");
                    Bukkit.broadcastMessage("§eAll players have been added to the queue!");
                    Bukkit.broadcastMessage("§eWaiting for an operator to start the game with §a/game start");
                    
                    updateScoreboard();
                    spawnPodiumNPCs();
                } else {
                    sender.sendMessage("§cQueue is already active! Use §a/game start §cto begin voting.");
                }
                return true;
                
            } else if (args[0].equalsIgnoreCase("addpoints")) {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /game addpoints <playername> <points>");
                    return true;
                }
                
                String playerName = args[1];
                try {
                    int points = Integer.parseInt(args[2]);
                    addPlayerPoints(playerName, points);
                    sender.sendMessage("§aAdded " + points + " points for player: " + playerName);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cInvalid number: " + args[2]);
                }
                return true;
                
            } else if (args[0].equalsIgnoreCase("start")) {
                
                if (!sender.isOp()) {
                    sender.sendMessage("§cOnly operators can start the game!");
                    return true;
                }
                
                if (!queueActive) {
                    sender.sendMessage("§cThe queue hasn't been started yet! Use /game queue first.");
                    return true;
                }
                
                if (votingSystem.isVotingActive()) {
                    sender.sendMessage("§cVoting is already active!");
                    return true;
                }
                
                if (isAnyGameActive()) {
                    sender.sendMessage("§cA game is already running!");
                    return true;
                }
                
                
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    if (!queuedPlayers.contains(onlinePlayer)) {
                        queuedPlayers.add(onlinePlayer);
                        onlinePlayer.sendMessage("§aYou have been added to the game queue!");
                    }
                }
                
                Bukkit.broadcastMessage("§6§l=== GAME VOTING STARTED ===");
                Bukkit.broadcastMessage("§eUse §a/vote gui §eor §a/vote <gamemode> §eto vote!");
                Bukkit.broadcastMessage("§eVoting will last for §c2 minutes§e!");
                votingSystem.startVoting();
                return true;
            }
        }
        return false;
    }

    @Override
    public void onDisable() {
        saveWinnersData();
        
        
        if (updateTask != null) {
            updateTask.cancel();
        }
        
        
        if (playerTrackingTask != null) {
            playerTrackingTask.cancel();
        }
        
        
        if (leaderboardUpdateTask != null) {
            leaderboardUpdateTask.cancel();
        }
        
        
        cleanupNPCs();
    }
    
    
    public Set<Player> getQueuedPlayers() {
        return queuedPlayers;
    }
    
    
    public Scoreboard getScoreboard() {
        return scoreboard;
    }
    
    
    private boolean isAnyGameActive() {
        
        if (votingSystem != null && votingSystem.isAnyGameActive()) {
            return true;
        }
        return false;
    }

    private static class WinnersData {
        private int winners;

        public WinnersData(int winners) {
            this.winners = winners;
        }

        public int getWinners() {
            return winners;
        }
    }
    
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();
        
        
        
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 999999, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 999999, 0, false, false));
        
        getLogger().info("Gave infinite night vision and regeneration to player: " + playerName);
        
        
        if (queueActive && !queuedPlayers.contains(player)) {
            queuedPlayers.add(player);
            player.setResourcePack("YOUR_TEXTURE_PACK_URL");
            player.sendMessage("§6§lWelcome to the server!");
            player.sendMessage("§aYou have been automatically added to the game queue!");
            
            
            Bukkit.getScheduler().runTaskLater(this, () -> {
                updateScoreboard();
                spawnPodiumNPCs();
            }, 10L); 
        }
        
        
        File winnersFile = new File(getDataFolder(), "winners.json");
        
        try {
            Map<String, PlayerStats> playerData = new HashMap<>();
            
            
            if (winnersFile.exists()) {
                try (FileReader reader = new FileReader(winnersFile)) {
                    Gson gson = new Gson();
                    
                    try {
                        playerData = gson.fromJson(reader, 
                            new com.google.gson.reflect.TypeToken<Map<String, PlayerStats>>(){}.getType());
                        
                        if (playerData == null) {
                            playerData = new HashMap<>();
                        }
                    } catch (Exception e) {
                        getLogger().info("Converting old JSON format for new player: " + playerName);
                        playerData = new HashMap<>();
                    }
                } catch (Exception e) {
                    getLogger().warning("Failed to read JSON for new player, creating new: " + e.getMessage());
                    playerData = new HashMap<>();
                }
            }
            
            
            if (!playerData.containsKey(playerName)) {
                playerData.put(playerName, new PlayerStats(0));
                
                
                try (FileWriter writer = new FileWriter(winnersFile)) {
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    gson.toJson(playerData, writer);
                    getLogger().info("Added new player to rankings on join: " + playerName + " (0 points)");
                }
            }
            
        } catch (Exception e) {
            getLogger().warning("Failed to add player on join: " + e.getMessage());
        }
    }
}
