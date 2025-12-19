package com.example.earthspirit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

public class BiomeGiftsHelper {

    private static Object biomeGiftsInstance;
    private static Method getConfigManagerMethod;
    private static Method getItemManagerMethod;
    private static Method getCropConfigMethod;
    private static Method getOreConfigMethod;
    private static Method getItemMethod;
    
    // ResourceConfig methods
    private static Method getBiomeTypeMethod;
    
    private static Class<?> biomeTypeEnum;
    private static Object richEnumValue;

    public static void init() {
        try {
            Plugin bgPlugin = Bukkit.getPluginManager().getPlugin("BiomeGifts");
            if (bgPlugin != null && bgPlugin.isEnabled()) {
                Class<?> bgClass = bgPlugin.getClass();
                Method getInstance = bgClass.getMethod("getInstance");
                biomeGiftsInstance = getInstance.invoke(null);
                
                getConfigManagerMethod = bgClass.getMethod("getConfigManager");
                getItemManagerMethod = bgClass.getMethod("getItemManager");
                
                Class<?> configManagerClass = getConfigManagerMethod.getReturnType();
                getCropConfigMethod = configManagerClass.getMethod("getCropConfig", Material.class);
                getOreConfigMethod = configManagerClass.getMethod("getOreConfig", Material.class);
                
                Class<?> itemManagerClass = getItemManagerMethod.getReturnType();
                getItemMethod = itemManagerClass.getMethod("getItem", String.class);
                
                // Find BiomeType enum
                for (Class<?> cls : configManagerClass.getDeclaredClasses()) {
                    if (cls.getSimpleName().equals("BiomeType")) {
                        biomeTypeEnum = cls;
                        for (Object enumConstant : biomeTypeEnum.getEnumConstants()) {
                            if (enumConstant.toString().equals("RICH")) {
                                richEnumValue = enumConstant;
                                break;
                            }
                        }
                        break;
                    }
                }
                
                // Find ResourceConfig class (return type of getCropConfig)
                Class<?> resourceConfigClass = getCropConfigMethod.getReturnType(); // CropConfig extends ResourceConfig
                // Note: getBiomeType is in ResourceConfig
                getBiomeTypeMethod = resourceConfigClass.getMethod("getBiomeType", String.class);

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static boolean isEnabled() {
        return biomeGiftsInstance != null;
    }

    public static Object getCropConfig(Material mat) {
        if (!isEnabled()) return null;
        try {
            return getCropConfigMethod.invoke(getConfigManagerMethod.invoke(biomeGiftsInstance), mat);
        } catch (Exception e) {
            return null;
        }
    }
    
    public static Object getOreConfig(Material mat) {
        if (!isEnabled()) return null;
        try {
            return getOreConfigMethod.invoke(getConfigManagerMethod.invoke(biomeGiftsInstance), mat);
        } catch (Exception e) {
            return null;
        }
    }
    
    public static ItemStack getItem(String name) {
        if (!isEnabled()) return null;
        try {
            return (ItemStack) getItemMethod.invoke(getItemManagerMethod.invoke(biomeGiftsInstance), name);
        } catch (Exception e) {
            return null;
        }
    }
    
    public static boolean isRich(Material mat, Location loc) {
        if (!isEnabled()) return false;
        try {
            // Try Crop first
            Object config = getCropConfig(mat);
            if (config == null) {
                config = getOreConfig(mat);
            }
            if (config == null) return false;
            
            // Get Biome Key
            String biomeKey = loc.getWorld().getBiome(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()).getKey().toString();
            
            // Call getBiomeType
            Object result = getBiomeTypeMethod.invoke(config, biomeKey);
            
            return result == richEnumValue;
            
        } catch (Exception e) {
            return false;
        }
    }
}
