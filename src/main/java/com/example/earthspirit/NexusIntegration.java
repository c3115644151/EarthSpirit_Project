package com.example.earthspirit;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public class NexusIntegration {

    public static void register() {
        Plugin nexusCore = Bukkit.getPluginManager().getPlugin("NexusCore");
        if (nexusCore != null && nexusCore.isEnabled()) {
            try {
                // 1. Get Registry instance
                Method getRegistryMethod = nexusCore.getClass().getMethod("getRegistry");
                Object registry = getRegistryMethod.invoke(nexusCore);

                // 2. Find the 6-parameter register method (supports Star Filter)
                Method registerMethod = null;
                for (Method m : registry.getClass().getMethods()) {
                    if (m.getName().equals("register") && m.getParameterCount() == 6) {
                        registerMethod = m;
                        break;
                    }
                }

                if (registerMethod == null) {
                    EarthSpiritPlugin.getInstance().getLogger().warning("NexusCore version too old, Star Filter not supported.");
                    return;
                }

                // 3. Prepare parameters
                String moduleId = "earth-spirit";
                String displayName = "地灵";

                // Icon Supplier
                Supplier<ItemStack> iconSupplier = () -> EarthSpiritPlugin.getInstance().getSpiritBell();

                // Items Supplier
                Supplier<List<ItemStack>> itemsSupplier = () -> {
                    List<ItemStack> items = new ArrayList<>();
                    items.add(EarthSpiritPlugin.getInstance().getSpiritBell());
                    items.add(EarthSpiritPlugin.getInstance().getTamingWand());
                    return items;
                };

                // Recipe Function (null)
                Function<ItemStack, Object> recipeFunction = null;

                // Star Filter (false for now as we don't use star system yet)
                Function<ItemStack, Boolean> starFilter = (item) -> false;

                // 4. Invoke
                registerMethod.invoke(registry, moduleId, displayName, iconSupplier, itemsSupplier, recipeFunction, starFilter);

                EarthSpiritPlugin.getInstance().getLogger().info("Registered with NexusCore (Reflection + StarFilter).");

            } catch (Exception e) {
                EarthSpiritPlugin.getInstance().getLogger().warning("Failed to register with NexusCore: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
