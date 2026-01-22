package com.example.earthspirit.configuration;

import com.example.earthspirit.EarthSpiritPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class I18n {
    private static I18n instance;
    private final EarthSpiritPlugin plugin;
    private final MiniMessage miniMessage;
    private Map<String, String> messages;
    private YamlConfiguration config;

    public I18n(EarthSpiritPlugin plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        reload();
    }

    public static void init(EarthSpiritPlugin plugin) {
        instance = new I18n(plugin);
    }

    public static I18n get() {
        if (instance == null) {
            throw new IllegalStateException("I18n not initialized");
        }
        return instance;
    }

    public void reload() {
        File langFile = new File(plugin.getDataFolder(), "lang/zh_CN.yml");
        if (!langFile.exists()) {
            plugin.saveResource("lang/zh_CN.yml", false);
        }
        
        config = YamlConfiguration.loadConfiguration(langFile);
        messages = new HashMap<>();
        
        for (String key : config.getKeys(true)) {
            if (config.isString(key)) {
                messages.put(key, config.getString(key));
            }
        }
    }

    public Component getComponent(String key, TagResolver... resolvers) {
        String msg = messages.getOrDefault(key, "<red>Missing key: " + key);
        if (msg.contains("§")) {
            return LegacyComponentSerializer.legacySection().deserialize(msg);
        }
        return miniMessage.deserialize(msg, resolvers);
    }

    public String getString(String key, TagResolver... resolvers) {
        return miniMessage.serialize(getComponent(key, resolvers));
    }

    public String getRaw(String key) {
        return messages.getOrDefault(key, "Missing key: " + key);
    }
    
    public String getLegacy(String key, TagResolver... resolvers) {
        return LegacyComponentSerializer.legacySection().serialize(getComponent(key, resolvers));
    }
    
    public java.util.List<String> getLegacyList(String key, TagResolver... resolvers) {
        if (!config.contains(key)) {
            return java.util.Collections.singletonList("Missing key: " + key);
        }
        java.util.List<String> rawList = config.getStringList(key);
        if (rawList.isEmpty() && config.isString(key)) {
             rawList = java.util.Collections.singletonList(config.getString(key));
        }
        
        java.util.List<String> result = new java.util.ArrayList<>();
        for (String raw : rawList) {
            Component c;
            if (raw.contains("§")) {
                c = LegacyComponentSerializer.legacySection().deserialize(raw);
            } else {
                c = miniMessage.deserialize(raw, resolvers);
            }
            result.add(LegacyComponentSerializer.legacySection().serialize(c));
        }
        return result;
    }

    public java.util.List<Component> getComponentList(String key, TagResolver... resolvers) {
        if (!config.contains(key)) {
            return java.util.Collections.singletonList(Component.text("Missing key: " + key));
        }
        java.util.List<String> rawList = config.getStringList(key);
        if (rawList.isEmpty() && config.isString(key)) {
             rawList = java.util.Collections.singletonList(config.getString(key));
        }
        
        java.util.List<Component> result = new java.util.ArrayList<>();
        for (String raw : rawList) {
            if (raw.contains("§")) {
                result.add(LegacyComponentSerializer.legacySection().deserialize(raw));
            } else {
                result.add(miniMessage.deserialize(raw, resolvers));
            }
        }
        return result;
    }

    public Component asComponent(String legacyText) {
        if (legacyText == null) return Component.empty();
        return LegacyComponentSerializer.legacySection().deserialize(legacyText);
    }

    public void send(CommandSender sender, String key, TagResolver... resolvers) {
        String prefixStr = messages.getOrDefault("prefix", "");
        Component prefix = miniMessage.deserialize(prefixStr);
        Component message = getComponent(key, resolvers);
        Component fullMessage = prefix.append(message);
        sender.sendMessage(LegacyComponentSerializer.legacySection().serialize(fullMessage));
    }
}
