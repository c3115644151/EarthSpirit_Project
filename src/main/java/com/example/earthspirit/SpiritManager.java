package com.example.earthspirit;

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
    private final File dataFile;

    public SpiritManager(EarthSpiritPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "spirits.yml");
        loadData();
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
}
