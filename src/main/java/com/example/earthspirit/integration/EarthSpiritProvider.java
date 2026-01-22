package com.example.earthspirit.integration;

import com.example.earthspirit.EarthSpiritPlugin;
import com.nexuscore.items.NexusItemProvider;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class EarthSpiritProvider implements NexusItemProvider {

    @Override
    public String getModuleId() {
        return "earth-spirit";
    }

    @Override
    public String getDisplayName() {
        return "地灵";
    }

    @Override
    public ItemStack getIcon() {
        ItemStack icon = EarthSpiritPlugin.getInstance().getSpiritBell();
        return icon != null ? icon : new ItemStack(org.bukkit.Material.BELL);
    }

    @Override
    public List<ItemStack> getItems() {
        List<ItemStack> items = new ArrayList<>();
        
        ItemStack bell = EarthSpiritPlugin.getInstance().getSpiritBell();
        if (bell != null) items.add(bell);
        
        ItemStack wand = EarthSpiritPlugin.getInstance().getTamingWand();
        if (wand != null) items.add(wand);
        
        // Add other items if available in SpiritItemManager
        // For now, we only expose the main tools as per previous logic
        
        return items;
    }

    @Override
    public boolean hasStarSystem(ItemStack item) {
        return false; // EarthSpirit items do not use the star quality system yet
    }
}
