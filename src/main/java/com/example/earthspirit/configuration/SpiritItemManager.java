package com.example.earthspirit.configuration;

import com.example.earthspirit.EarthSpiritPlugin;
import com.nexuscore.util.NexusKeys;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class SpiritItemManager {
    private static SpiritItemManager instance;
    private final EarthSpiritPlugin plugin;
    private final Map<String, SpiritItemData> itemDataMap = new HashMap<>();

    public SpiritItemManager(EarthSpiritPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public static void init(EarthSpiritPlugin plugin) {
        instance = new SpiritItemManager(plugin);
    }

    public static SpiritItemManager get() {
        if (instance == null) {
            throw new IllegalStateException("SpiritItemManager not initialized");
        }
        return instance;
    }

    public void load() {
        // Path: plugins/CraftEngine/resources/earthspirit/configuration/items/spirit_items.yml
        File pluginsFolder = plugin.getDataFolder().getParentFile();
        File itemFile = new File(pluginsFolder, "CraftEngine/resources/earthspirit/configuration/items/spirit_items.yml");

        if (!itemFile.exists()) {
            plugin.getLogger().warning("CraftEngine item config not found at " + itemFile.getPath() + ". Using defaults.");
            // Add defaults so the plugin still works without the file
            addDefault("taming_wand", Material.BLAZE_ROD, "<!i><gold><bold>风铃杖", 10002);
            addDefault("spirit_bell", Material.BELL, "<!i><aqua><bold>灵契风铃", 10001);
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(itemFile);
        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection == null) return;

        for (String key : itemsSection.getKeys(false)) {
            // key example: earthspirit:spirit_bell
            ConfigurationSection section = itemsSection.getConfigurationSection(key);
            if (section == null) continue;

            String materialName = section.getString("material");
            ConfigurationSection dataSection = section.getConfigurationSection("data");
            
            String name = null;
            int cmd = -1;
            
            if (dataSection != null) {
                name = dataSection.getString("item-name");
                cmd = dataSection.getInt("custom-model-data", -1);
            }
            
            // Extract simple ID from earthspirit:id
            String shortId = key;
            if (key.contains(":")) {
                shortId = key.split(":")[1];
            }
            
            Material material = Material.matchMaterial(materialName);
            if (material == null) material = Material.STONE;
            
            itemDataMap.put(shortId, new SpiritItemData(key, material, name, cmd));
        }
    }
    
    private void addDefault(String id, Material material, String name, int cmd) {
        itemDataMap.put(id, new SpiritItemData("earthspirit:" + id, material, name, cmd));
    }

    public ItemStack getItem(String id) {
        SpiritItemData data = itemDataMap.get(id);
        if (data == null) return null;

        ItemStack item = new ItemStack(data.material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (data.name != null) {
                meta.setDisplayName(LegacyComponentSerializer.legacySection().serialize(MiniMessage.miniMessage().deserialize(data.name)));
            }
            if (data.customModelData != -1) {
                meta.setCustomModelData(data.customModelData);
            }
            
            // Inject Nexus ID
            meta.getPersistentDataContainer().set(NexusKeys.ITEM_ID, PersistentDataType.STRING, data.fullId);
            
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isItem(ItemStack stack, String id) {
        if (stack == null || !stack.hasItemMeta()) return false;
        SpiritItemData data = itemDataMap.get(id);
        if (data == null) return false;
        
        // Check PDC first (Most reliable)
        String itemId = stack.getItemMeta().getPersistentDataContainer().get(NexusKeys.ITEM_ID, PersistentDataType.STRING);
        if (itemId != null && itemId.equals(data.fullId)) {
            return true;
        }

        // Fallback: Strict Material check
        if (stack.getType() != data.material) return false;

        // Fallback: CustomModelData check
        if (data.customModelData != -1) {
            return stack.getItemMeta().hasCustomModelData() && 
                   stack.getItemMeta().getCustomModelData() == data.customModelData;
        }
        
        return true;
    }

    private static class SpiritItemData {
        final String fullId;
        final Material material;
        final String name;
        final int customModelData;

        SpiritItemData(String fullId, Material material, String name, int customModelData) {
            this.fullId = fullId;
            this.material = material;
            this.name = name;
            this.customModelData = customModelData;
        }
    }
}
