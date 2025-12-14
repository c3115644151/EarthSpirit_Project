package com.example.earthspirit;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SpiritSkinManager {

    public enum Expression {
        NORMAL,
        BLINK,
        HAPPY,
        SAD,
        ANGRY
    }

    private static final Map<Expression, String> TEXTURES = new HashMap<>();
    private static final Map<Expression, ItemStack> CACHED_HEADS = new HashMap<>();

    static {
        // TODO: 请在此处替换为实际的 Base64 皮肤数据
        // 这里的 Base64 仅为示例 (史莱姆皮肤)
        String slimeBase = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODk1YWVlYzZiODQyYWRhODY2OWY4NDZkNjViYzQ5OTAyZDNiM2IxOGQ0NDFacmVhZDI1YmRjMzZlOWM0ODBEIn19fQ==";
        
        TEXTURES.put(Expression.NORMAL, slimeBase);
        TEXTURES.put(Expression.BLINK, slimeBase); // 需要替换为闭眼皮肤
        TEXTURES.put(Expression.HAPPY, slimeBase); // 需要替换为笑眼皮肤
        TEXTURES.put(Expression.SAD, slimeBase);   // 需要替换为哭泣皮肤
        TEXTURES.put(Expression.ANGRY, slimeBase); // 需要替换为生气皮肤
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
        
        GameProfile profile = new GameProfile(UUID.randomUUID(), null);
        profile.getProperties().put("textures", new Property("textures", base64));

        try {
            Field profileField = meta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(meta, profile);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }

        head.setItemMeta(meta);
        return head;
    }
}
