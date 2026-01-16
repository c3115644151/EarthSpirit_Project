package com.example.earthspirit;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

// import java.net.URL;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.example.earthspirit.configuration.ConfigManager;

public class SpiritSkinManager {

    public enum Expression {
        NORMAL,
        BLINK,
        HAPPY,
        SAD,
        ANGRY,
        FLOWER,
        MINER
    }

    private static final Map<Expression, ItemStack> CACHED_HEADS = new HashMap<>();

    public static ItemStack getHead(Expression exp) {
        if (CACHED_HEADS.containsKey(exp)) {
            return CACHED_HEADS.get(exp).clone(); // 返回克隆对象以防止被意外修改
        }

        String configKey = exp.name().toLowerCase();
        String base64 = ConfigManager.get().getSkin(configKey);
        
        // Fallback to normal skin if specific expression not found
        if (base64 == null) {
            base64 = ConfigManager.get().getSkin("normal");
        }
        
        ItemStack head = createSkull(base64);
        CACHED_HEADS.put(exp, head);
        
        return head.clone();
    }

    private static ItemStack createSkull(String base64) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        
        try {
            // Decode Base64 to get the texture URL
            String decoded = new String(Base64.getDecoder().decode(base64));
            // Extract URL from JSON: {"textures":{"SKIN":{"url":"http://..."}}}
            int urlStartIndex = decoded.indexOf("\"url\":\"");
            if (urlStartIndex != -1) {
                int urlEndIndex = decoded.indexOf("\"", urlStartIndex + 7);
                if (urlEndIndex != -1) {
                    String urlStr = decoded.substring(urlStartIndex + 7, urlEndIndex);
                    
                    // Use modern PlayerProfile API (1.18+) to avoid reflection issues in 1.21+
                    PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
                    PlayerTextures textures = profile.getTextures();
                    textures.setSkin(java.net.URI.create(urlStr).toURL());
                    profile.setTextures(textures);
                    
                    meta.setOwnerProfile(profile);
                    
                    // 设置 CustomModelData 以支持资源包 3D 模型替换
                    meta.setCustomModelData(ConfigManager.get().getCustomModelData());
                }
            }
        } catch (Exception e) {
            // Log error but don't crash the task
            System.err.println("[SpiritSkinManager] Failed to create skull texture: " + e.getMessage());
            e.printStackTrace();
        }

        head.setItemMeta(meta);
        return head;
    }
}
