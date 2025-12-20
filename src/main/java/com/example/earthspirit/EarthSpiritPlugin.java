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
import java.util.UUID;

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

        // 启动自动保存任务 (每5分钟保存一次数据)
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (manager != null) {
                manager.saveData();
            }
        }, 6000L, 6000L); // 6000 ticks = 5 minutes

        // 注册合成配方：风铃杖与灵契风铃
        registerRecipes();

        getLogger().info("EarthSpirit 地灵插件已启动！");
    }

    private void registerRecipes() {
        // 风铃杖配方
        ItemStack wand = getTamingWand();
        org.bukkit.NamespacedKey wandKey = new org.bukkit.NamespacedKey(this, "taming_wand");
        org.bukkit.inventory.ShapedRecipe wandRecipe = new org.bukkit.inventory.ShapedRecipe(wandKey, wand);
        // 配方形状：
        //  G 
        // B
        wandRecipe.shape(" G", "B ");
        wandRecipe.setIngredient('G', Material.GOLD_INGOT);
        wandRecipe.setIngredient('B', Material.BLAZE_ROD);
        Bukkit.addRecipe(wandRecipe);

        // 灵契风铃配方 (这里补上风铃的配方，假设是金锭围一圈中间放个铃铛)
        ItemStack bell = getSpiritBell();
        org.bukkit.NamespacedKey bellKey = new org.bukkit.NamespacedKey(this, "spirit_bell");
        org.bukkit.inventory.ShapedRecipe bellRecipe = new org.bukkit.inventory.ShapedRecipe(bellKey, bell);
        // 配方形状:
        // GGG
        // G G
        bellRecipe.shape("GGG", "G G");
        bellRecipe.setIngredient('G', Material.GOLD_INGOT);
        Bukkit.addRecipe(bellRecipe);
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.cleanupSpirits(); // 清理实体
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
        if (command.getName().equalsIgnoreCase("esp") || command.getName().equalsIgnoreCase("earthspirit")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("partner")) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§c只有玩家可以使用此命令！");
                    return true;
                }
                Player p = (Player) sender;
                if (args.length > 1) {
                    if (args[1].equalsIgnoreCase("accept")) {
                        UUID requesterId = manager.getPartnerRequest(p.getUniqueId());
                        if (requesterId == null) {
                            p.sendMessage("§c你目前没有收到的伴侣请求。");
                            return true;
                        }

                        SpiritEntity mySpirit = manager.getSpiritByOwner(p.getUniqueId());
                        SpiritEntity targetSpirit = manager.getSpiritByOwner(requesterId);

                        if (mySpirit == null) {
                            p.sendMessage("§c你还没有地灵，无法建立羁绊！");
                            return true;
                        }
                        if (targetSpirit == null) {
                            p.sendMessage("§c对方似乎没有地灵，无法建立羁绊！");
                            manager.removePartnerRequest(p.getUniqueId());
                            return true;
                        }

                        // 建立关系
                        mySpirit.setPartnerId(requesterId);
                        targetSpirit.setPartnerId(p.getUniqueId());
                        manager.saveData();
                        manager.removePartnerRequest(p.getUniqueId());

                        String targetName = Bukkit.getOfflinePlayer(requesterId).getName();
                        p.sendMessage("§d§l❤ §f你接受了 §e" + targetName + " §f的伴侣请求！二人正式结为灵魂伴侣！");
                        p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1, 1);

                        Player target = Bukkit.getPlayer(requesterId);
                        if (target != null && target.isOnline()) {
                            target.sendMessage("§d§l❤ §e" + p.getName() + " §f接受了你的伴侣请求！二人正式结为灵魂伴侣！");
                            target.playSound(target.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1, 1);
                        }
                        return true;
                    } else if (args[1].equalsIgnoreCase("deny")) {
                        UUID requesterId = manager.getPartnerRequest(p.getUniqueId());
                        if (requesterId == null) {
                            p.sendMessage("§c你目前没有收到的伴侣请求。");
                            return true;
                        }
                        
                        manager.removePartnerRequest(p.getUniqueId());
                        p.sendMessage("§c你拒绝了伴侣请求。");
                        
                        Player target = Bukkit.getPlayer(requesterId);
                        if (target != null && target.isOnline()) {
                            target.sendMessage("§c" + p.getName() + " 拒绝了你的伴侣请求。");
                        }
                        return true;
                    }
                }
                p.sendMessage("§c用法: /esp partner <accept|deny>");
                return true;
            }
        }

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
                p.sendMessage("§a获得了风铃杖！");
                return true;
            }
        }
        return false;
    }

    public ItemStack getTamingWand() {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName("§6§l风铃杖");
        meta.setLore(Collections.singletonList("§7右键方块指挥地灵移动"));
        meta.setCustomModelData(10002);
        wand.setItemMeta(meta);
        return wand;
    }

    public ItemStack getSpiritBell() {
        ItemStack bell = new ItemStack(Material.BELL);
        ItemMeta meta = bell.getItemMeta();
        meta.setDisplayName("§b§l灵契风铃");
        meta.setLore(Collections.singletonList("§7右键召唤地灵"));
        meta.setCustomModelData(10001);
        bell.setItemMeta(meta);
        return bell;
    }
}
