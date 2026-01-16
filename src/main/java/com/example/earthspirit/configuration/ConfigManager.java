package com.example.earthspirit.configuration;

import com.example.earthspirit.EarthSpiritPlugin;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {
    private static ConfigManager instance;
    private final EarthSpiritPlugin plugin;
    private FileConfiguration config;

    public ConfigManager(EarthSpiritPlugin plugin) {
        this.plugin = plugin;
        this.plugin.saveDefaultConfig();
        reload();
    }

    public static void init(EarthSpiritPlugin plugin) {
        instance = new ConfigManager(plugin);
    }

    public static ConfigManager get() {
        if (instance == null) {
            throw new IllegalStateException("ConfigManager not initialized");
        }
        return instance;
    }

    public void reload() {
        plugin.reloadConfig();
        config = plugin.getConfig();
    }
    
    public long getParticleTaskInterval() {
        return config.getLong("settings.particle-task-interval", 100L);
    }
    
    public long getAutoSaveInterval() {
        return config.getLong("settings.auto-save-interval", 6000L);
    }

    public long getCacheTTL() {
        return config.getLong("settings.cache-ttl", 60000L);
    }
    
    public double getDefaultMaxMood() {
        return config.getDouble("defaults.spirit.max-mood", 100.0);
    }

    public double getDefaultMaxHunger() {
        return config.getDouble("defaults.spirit.max-hunger", 100.0);
    }

    public double getDefaultMood() {
        return config.getDouble("defaults.spirit.default-mood", 60.0);
    }
    
    public double getDefaultHunger() {
        return config.getDouble("defaults.spirit.default-hunger", 50.0);
    }
    
    public double getMaxHungerBase() {
        return config.getDouble("defaults.spirit.max-hunger-base", 50.0);
    }
    
    public double getMaxHungerPerLevel() {
        return config.getDouble("defaults.spirit.max-hunger-per-level", 10.0);
    }
    
    public long getHungerDecayInterval() {
        return config.getLong("settings.hunger-decay-interval", 3600000L);
    }
    
    public double getHungerDecayAmount() {
        return config.getDouble("settings.hunger-decay-amount", 1.0);
    }
    
    public long getMoodDecayInterval() {
        return config.getLong("settings.mood-decay-interval", 600000L);
    }
    
    public double getMoodDecayAmount() {
        return config.getDouble("settings.mood-decay-amount", 1.0);
    }
    
    public int getCustomModelData() {
        return config.getInt("skins.custom-model-data", 10004);
    }
    
    public String getSkin(String key) {
        return config.getString("skins." + key);
    }
    
    public FileConfiguration getRaw() {
        return config;
    }
}
