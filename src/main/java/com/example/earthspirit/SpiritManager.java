package com.example.earthspirit;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.io.*;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SpiritManager {
    private final EarthSpiritPlugin plugin;
    private final Map<UUID, SpiritEntity> spirits = new HashMap<>(); // Key: Entity UUID
    private final File dataFile;
    private final Gson gson = new Gson();

    public SpiritManager(EarthSpiritPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "spirits.json");
        loadData();
    }

    public void addSpirit(SpiritEntity spirit) {
        spirits.put(spirit.getEntityId(), spirit);
        saveData();
    }

    public SpiritEntity getSpirit(UUID entityId) {
        return spirits.get(entityId);
    }
    
    public Map<UUID, SpiritEntity> getAllSpirits() {
        return spirits;
    }

    public void removeSpirit(UUID entityId) {
        spirits.remove(entityId);
        saveData();
    }

    public void saveData() {
        try (Writer writer = new FileWriter(dataFile)) {
            gson.toJson(spirits, writer);
        } catch (IOException e) {
            plugin.getLogger().severe("无法保存地灵数据: " + e.getMessage());
        }
    }

    public void loadData() {
        if (!dataFile.exists()) {
            plugin.getDataFolder().mkdirs();
            return;
        }
        try (Reader reader = new FileReader(dataFile)) {
            Type type = new TypeToken<HashMap<UUID, SpiritEntity>>(){}.getType();
            Map<UUID, SpiritEntity> loaded = gson.fromJson(reader, type);
            if (loaded != null) {
                spirits.putAll(loaded);
            }
        } catch (IOException e) {
            plugin.getLogger().severe("无法加载地灵数据: " + e.getMessage());
        }
    }
    
    // 检查某个位置附近是否已经有地灵 (防止重叠圈地)
    public boolean hasSpiritNearby(Location loc, double radius) {
        for (UUID uuid : spirits.keySet()) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null && entity.isValid() && entity.getLocation().getWorld().equals(loc.getWorld())) {
                if (entity.getLocation().distance(loc) < radius) {
                    return true;
                }
            }
        }
        return false;
    }
}
