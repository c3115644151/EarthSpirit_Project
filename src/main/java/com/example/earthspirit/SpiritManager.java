package com.example.earthspirit;

import com.example.earthspirit.configuration.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class SpiritManager {
    private final EarthSpiritPlugin plugin;
    private final Map<UUID, SpiritEntity> spiritsByOwner = new HashMap<>(); // Key: Owner UUID
    private final Map<UUID, UUID> pendingPartnerRequests = new HashMap<>(); // Target -> Sender
    
    // Caching for external plugins (CuisineFarming)
    private final Map<Long, Double> chunkGrowthBonusCache = new HashMap<>();
    private final Map<Long, Long> chunkCacheTimestamp = new HashMap<>();
    private static final long CACHE_TTL_MS = 60000; // 1 minute cache

    private final File dataFile;
    private final File requestsFile;

    public SpiritManager(EarthSpiritPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "spirits.yml");
        this.requestsFile = new File(plugin.getDataFolder(), "requests.yml");
        loadData();
        loadRequests();
    }

    public void addSpirit(SpiritEntity spirit) {
        spiritsByOwner.put(spirit.getOwnerId(), spirit);
        saveData();
    }

    public SpiritEntity getSpiritByOwner(UUID ownerId) {
        return spiritsByOwner.get(ownerId);
    }

    public SpiritEntity getSpiritByDriver(UUID driverId) {
        for (SpiritEntity s : spiritsByOwner.values()) {
            if (s.getDriverId() != null && s.getDriverId().equals(driverId)) {
                return s;
            }
        }
        return null;
    }

    public SpiritEntity getSpirit(UUID entityId) {
        for (SpiritEntity s : spiritsByOwner.values()) {
            if (s.getEntityId() != null && s.getEntityId().equals(entityId)) {
                return s;
            }
        }
        return null;
    }
    
    public Map<UUID, SpiritEntity> getAllSpirits() {
        return spiritsByOwner;
    }

    public void removeSpirit(UUID ownerId) {
        spiritsByOwner.remove(ownerId);
        saveData();
    }

    // 关闭服务器时清理所有地灵实体 (防止保存到存档中变成 Ghost 实体)
    public void cleanupSpirits() {
        // 不再强制召回，允许实体持久化保存
        // for (SpiritEntity spirit : spiritsByOwner.values()) {
        //     spirit.recall(); 
        // }
    }

    public void saveData() {
        YamlConfiguration config = new YamlConfiguration();
        
        for (SpiritEntity spirit : spiritsByOwner.values()) {
            String key = spirit.getOwnerId().toString();
            if (key != null) {
                spirit.saveToConfig(config.createSection(key));
            }
        }
        
        try {
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "无法保存地灵数据 (spirits.yml)", e);
        }
    }

    public void loadData() {
        if (!dataFile.exists()) {
            return;
        }
        
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        
        for (String key : config.getKeys(false)) {
            try {
                SpiritEntity spirit = SpiritEntity.fromConfig(config.getConfigurationSection(key));
                if (spirit != null) {
                    spiritsByOwner.put(spirit.getOwnerId(), spirit);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "无法加载地灵数据: " + key, e);
            }
        }
    }
    
    // 检查某个位置附近是否已经有地灵 (防止重叠圈地)
    public boolean hasSpiritNearby(Location loc, double radius) {
        for (SpiritEntity spirit : spiritsByOwner.values()) {
            if (spirit.getEntityId() == null) continue;
            Entity entity = Bukkit.getEntity(spirit.getEntityId());
            if (entity != null && entity.isValid() && entity.getLocation().getWorld().equals(loc.getWorld())) {
                if (entity.getLocation().distance(loc) < radius) {
                    return true;
                }
            }
        }
        return false;
    }

    // Partner Requests
    public void addPartnerRequest(UUID target, UUID sender) {
        pendingPartnerRequests.put(target, sender);
        saveRequests();
    }

    public UUID getPartnerRequest(UUID target) {
        return pendingPartnerRequests.get(target);
    }

    public void removePartnerRequest(UUID target) {
        pendingPartnerRequests.remove(target);
        saveRequests();
    }

    /**
     * Optimized API for CuisineFarming to get growth bonus.
     * Uses caching to minimize Towny lookups.
     * Returns a bonus value (e.g. 0.1 for +10% speed).
     */
    public double getSpiritGrowthBonus(Location loc) {
        if (loc == null || loc.getWorld() == null) return 0.0;
        
        long chunkKey = getChunkKey(loc);
        long now = System.currentTimeMillis();
        
        if (chunkCacheTimestamp.containsKey(chunkKey)) {
            if (now - chunkCacheTimestamp.get(chunkKey) < ConfigManager.get().getCacheTTL()) {
                return chunkGrowthBonusCache.getOrDefault(chunkKey, 0.0);
            }
        }
        
        // Calculate and Cache
        double bonus = 0.0;
        try {
            com.palmergames.bukkit.towny.object.Town town = TownyIntegration.getTownAt(loc);
            if (town != null) {
                SpiritEntity spirit = TownyIntegration.getTownSpirit(town, plugin);
                if (spirit != null && spirit.getMode() == SpiritEntity.SpiritMode.GUARDIAN) {
                    if (spirit.getMood() >= 90) {
                        bonus = 0.1; // +10% Efficiency
                    }
                }
            }
        } catch (Exception e) {
            // Towny might not be loaded or error
        }
        
        chunkGrowthBonusCache.put(chunkKey, bonus);
        chunkCacheTimestamp.put(chunkKey, now);
        
        return bonus;
    }

    /**
     * API for CuisineFarming to get specialty drop bonus.
     * Currently reuses the growth bonus logic (Guardian Mode + High Mood = +10%).
     */
    public double getSpiritDropBonus(Location loc) {
        return getSpiritGrowthBonus(loc);
    }
    
    private long getChunkKey(Location loc) {
        return ((long) loc.getBlockX() >> 4) | (((long) loc.getBlockZ() >> 4) << 32);
    }

    private void saveRequests() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, UUID> entry : pendingPartnerRequests.entrySet()) {
            config.set(entry.getKey().toString(), entry.getValue().toString());
        }
        try {
            config.save(requestsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "无法保存伴侣请求数据", e);
        }
    }

    private void loadRequests() {
        if (!requestsFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(requestsFile);
        for (String key : config.getKeys(false)) {
            try {
                UUID target = UUID.fromString(key);
                UUID sender = UUID.fromString(config.getString(key));
                pendingPartnerRequests.put(target, sender);
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid partner request: " + key);
            }
        }
    }
}
