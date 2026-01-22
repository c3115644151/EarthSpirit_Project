package com.example.earthspirit;

import com.example.earthspirit.integration.EarthSpiritProvider;
import org.bukkit.Bukkit;

public class NexusIntegration {

    public static void register() {
        if (Bukkit.getPluginManager().isPluginEnabled("NexusCore")) {
            try {
                com.nexuscore.NexusCore.getInstance().getRegistry()
                    .registerProvider(new EarthSpiritProvider());
                EarthSpiritPlugin.getInstance().getLogger().info("Successfully registered with NexusCore!");
            } catch (Exception e) {
                EarthSpiritPlugin.getInstance().getLogger().warning("Failed to register with NexusCore: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            EarthSpiritPlugin.getInstance().getLogger().warning("NexusCore not found or not enabled.");
        }
    }
}
