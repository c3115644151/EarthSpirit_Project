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

    private static final Map<Expression, String> TEXTURES = new HashMap<>();
    private static final Map<Expression, ItemStack> CACHED_HEADS = new HashMap<>();

    static {
        // TODO: 请在此处替换为实际的 Base64 皮肤数据
        // 这里的 Base64 仅为示例 (史莱姆皮肤)
        String slimeBase = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODk1YWVlYzZiODQyYWRhODY2OWY4NDZkNjViYzQ5OTAyZDNiM2IxOGQ0NDFacmVhZDI1YmRjMzZlOWM0ODBEIn19fQ==";
        String flowerBase = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDYxNzhhZDUxZmQ2ZDNmZGUwNzAzYzU1OWE2MzFjN2MxY2IyMzRiOGM4YmI0ODlkNzQ3N2U4ZDY2NDY5MiJ9fX0="; // 示例花朵皮肤
        String minerBase = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGRhZmIyM2VmYzU3ZDI1MTg3OGU1MzI4ZDExY2I1ZmVjMmYxNDk2NDRjY2Y5YTVkNTI1MjkyNDU2ZTAifX19"; // 示例矿工皮肤

        TEXTURES.put(Expression.NORMAL, slimeBase);
        TEXTURES.put(Expression.BLINK, slimeBase); 
        TEXTURES.put(Expression.HAPPY, slimeBase); 
        TEXTURES.put(Expression.SAD, slimeBase);   
        TEXTURES.put(Expression.ANGRY, slimeBase);
        TEXTURES.put(Expression.FLOWER, flowerBase);
        TEXTURES.put(Expression.MINER, minerBase);
    }

    public static ItemStack getHead(Expression exp) {
        if (CACHED_HEADS.containsKey(exp)) {
            return CACHED_HEADS.get(exp).clone(); // 返回克隆对象以防止被意外修改
        }

        String base64 = TEXTURES.getOrDefault(exp, TEXTURES.get(Expression.NORMAL));
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
                    meta.setCustomModelData(10004);
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
