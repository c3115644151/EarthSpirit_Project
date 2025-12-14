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

public class EarthSpiritPlugin extends JavaPlugin {
    private SpiritManager manager;
    private SpiritTask task;

    @Override
    public void onEnable() {
        // 初始化管理器
        this.manager = new SpiritManager(this);

        // 注册监听器
        Bukkit.getPluginManager().registerEvents(new SpiritListener(this), this);

        // 启动定时任务 (每20tick = 1秒，这里设为每5秒运行一次逻辑)
        this.task = new SpiritTask(this);
        this.task.runTaskTimer(this, 100L, 100L);

        // 启动动画任务 (每2tick运行一次，即0.1秒)
        new SpiritAnimationTask(this).runTaskTimer(this, 0L, 2L);

        getLogger().info("EarthSpirit 地灵插件已启动！");
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
        }
        return false;
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
