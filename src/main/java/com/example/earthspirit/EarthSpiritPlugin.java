package com.example.earthspirit;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;

import com.example.earthspirit.cravings.CravingManager;

public class EarthSpiritPlugin extends JavaPlugin {
    private static EarthSpiritPlugin instance;

    public static EarthSpiritPlugin getInstance() {
        return instance;
    }

    private SpiritManager manager;
    private SpiritTask task;
    private CravingManager cravingManager;

    @Override
    public void onEnable() {
        instance = this;
        
        // 初始化 BiomeGifts 集成
        BiomeGiftsHelper.init();

        // 初始化管理器
        this.cravingManager = new CravingManager(this);
        
        // 启动粒子效果任务 (每5秒运行一次)
        new SpiritParticleTask(this).runTaskTimer(this, 100L, 100L);
        this.manager = new SpiritManager(this);

        // 注册监听器
        Bukkit.getPluginManager().registerEvents(new SpiritListener(this), this);

        // 启动主任务 (每1tick运行一次，处理所有地灵逻辑)
        this.task = new SpiritTask(this);
        this.task.runTaskTimer(this, 0L, 1L);

        // 注册合成配方：驯兽杖
        registerRecipes();

        getLogger().info("EarthSpirit 地灵插件已启动！");
    }

    private void registerRecipes() {
        ItemStack wand = getTamingWand();
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(this, "taming_wand");
        org.bukkit.inventory.ShapedRecipe recipe = new org.bukkit.inventory.ShapedRecipe(key, wand);
        // 配方形状：
        //  G 
        // B
        recipe.shape(" G", "B ");
        recipe.setIngredient('G', Material.GOLD_INGOT);
        recipe.setIngredient('B', Material.BLAZE_ROD);
        Bukkit.addRecipe(recipe);
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.saveData();
        }
        getLogger().info("EarthSpirit 地灵插件已关闭！");
    }

    public SpiritManager getManager() {
        return manager;
    }

    public CravingManager getCravingManager() {
        return cravingManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("getbell")) {
            if (sender instanceof Player) {
                Player p = (Player) sender;
                if (!p.hasPermission("earthspirit.admin")) {
                    p.sendMessage("§c你没有权限！");
                    return true;
                }
                p.getInventory().addItem(getSpiritBell());
                p.sendMessage("§a获得了灵契风铃！");
                return true;
            }
        } else if (command.getName().equalsIgnoreCase("getwand")) {
            if (sender instanceof Player) {
                Player p = (Player) sender;
                if (!p.hasPermission("earthspirit.admin")) {
                    p.sendMessage("§c你没有权限！");
                    return true;
                }
                p.getInventory().addItem(getTamingWand());
                p.sendMessage("§a获得了驯兽杖！");
                return true;
            }
        }
        return false;
    }

    public ItemStack getTamingWand() {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName("§6§l驯兽杖");
        meta.setLore(Collections.singletonList("§7右键方块指挥地灵移动"));
        wand.setItemMeta(meta);
        return wand;
    }

    public ItemStack getSpiritBell() {
        ItemStack bell = new ItemStack(Material.BELL);
        ItemMeta meta = bell.getItemMeta();
        meta.setDisplayName("§b§l灵契风铃");
        meta.setLore(Collections.singletonList("§7右键点击地面召唤地灵"));
        bell.setItemMeta(meta);
        return bell;
    }
}
