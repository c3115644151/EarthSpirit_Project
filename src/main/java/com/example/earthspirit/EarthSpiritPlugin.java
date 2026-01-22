package com.example.earthspirit;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

import com.example.earthspirit.configuration.ConfigManager;
import com.example.earthspirit.configuration.I18n;
import com.example.earthspirit.configuration.SpiritItemManager;
import com.example.earthspirit.cravings.CravingManager;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

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
        
        // Initialize Configuration and I18n
        ConfigManager.init(this);
        I18n.init(this);
        SpiritItemManager.init(this);
        
        // 初始化 BiomeGifts 集成
        BiomeGiftsHelper.init();

        // 初始化管理器
        this.cravingManager = new CravingManager(this);
        
        // 启动粒子效果任务 (每5秒运行一次)
        new SpiritParticleTask(this).runTaskTimer(this, ConfigManager.get().getParticleTaskInterval(), ConfigManager.get().getParticleTaskInterval());
        this.manager = new SpiritManager(this);

        // 注册监听器
        Bukkit.getPluginManager().registerEvents(new SpiritListener(this), this);

        // 启动主任务 (每1tick运行一次，处理所有地灵逻辑)
        this.task = new SpiritTask(this);
        this.task.runTaskTimer(this, 0L, 1L);

        // 启动自动保存任务 (每5分钟保存一次数据)
        long autoSave = ConfigManager.get().getAutoSaveInterval();
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (manager != null) {
                manager.saveData();
            }
        }, autoSave, autoSave);

        // 注册 NexusCore 集成
        NexusIntegration.register();

        // 注册合成配方：通过 NexusCore
        if (Bukkit.getPluginManager().isPluginEnabled("NexusCore")) {
            try {
                com.nexuscore.recipe.RecipeConfigLoader.load(
                    (com.nexuscore.NexusCore) Bukkit.getPluginManager().getPlugin("NexusCore"),
                    getConfig().getConfigurationSection("recipes")
                );
                getLogger().info("Recipes registered via NexusCore.");
            } catch (Exception e) {
                getLogger().warning("Failed to register recipes via NexusCore: " + e.getMessage());
            }
        }

        I18n.get().send(Bukkit.getConsoleSender(), "messages.startup");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.cleanupSpirits(); // 清理实体
            manager.saveData();
        }
        if (I18n.get() != null) {
            I18n.get().send(Bukkit.getConsoleSender(), "messages.shutdown");
        }
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
            if (args.length > 0) {
                if (args[0].equalsIgnoreCase("reload")) {
                    if (!sender.hasPermission("earthspirit.admin")) {
                        I18n.get().send(sender, "commands.no-permission");
                        return true;
                    }
                    ConfigManager.get().reload();
                    I18n.get().reload();
                    I18n.get().send(sender, "messages.reload");
                    return true;
                }
                
                if (args[0].equalsIgnoreCase("partner")) {
                if (!(sender instanceof Player)) {
                    I18n.get().send(sender, "commands.only-player");
                    return true;
                }
                Player p = (Player) sender;
                if (args.length > 1) {
                    if (args[1].equalsIgnoreCase("accept")) {
                        UUID requesterId = manager.getPartnerRequest(p.getUniqueId());
                        if (requesterId == null) {
                            I18n.get().send(p, "commands.partner.no-request");
                            return true;
                        }

                        SpiritEntity mySpirit = manager.getSpiritByOwner(p.getUniqueId());
                        SpiritEntity targetSpirit = manager.getSpiritByOwner(requesterId);

                        if (mySpirit == null) {
                            I18n.get().send(p, "commands.partner.no-spirit-self");
                            return true;
                        }
                        if (targetSpirit == null) {
                            I18n.get().send(p, "commands.partner.no-spirit-target");
                            manager.removePartnerRequest(p.getUniqueId());
                            return true;
                        }

                        // 建立关系
                        mySpirit.setPartnerId(requesterId);
                        targetSpirit.setPartnerId(p.getUniqueId());
                        manager.saveData();
                        manager.removePartnerRequest(p.getUniqueId());

                        String targetName = Bukkit.getOfflinePlayer(requesterId).getName();
                        I18n.get().send(p, "commands.partner.accept.self", Placeholder.parsed("name", targetName));
                        p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1, 1);

                        Player target = Bukkit.getPlayer(requesterId);
                        if (target != null && target.isOnline()) {
                            I18n.get().send(target, "commands.partner.accept.target", Placeholder.parsed("name", p.getName()));
                            target.playSound(target.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1, 1);
                        }
                        return true;
                    } else if (args[1].equalsIgnoreCase("deny")) {
                        UUID requesterId = manager.getPartnerRequest(p.getUniqueId());
                        if (requesterId == null) {
                            I18n.get().send(p, "commands.partner.no-request");
                            return true;
                        }
                        
                        manager.removePartnerRequest(p.getUniqueId());
                        I18n.get().send(p, "commands.partner.deny.self");
                        
                        Player target = Bukkit.getPlayer(requesterId);
                        if (target != null && target.isOnline()) {
                            I18n.get().send(target, "commands.partner.deny.target", Placeholder.parsed("name", p.getName()));
                        }
                        return true;
                    }
                }
                I18n.get().send(sender, "commands.partner.usage");
                return true;
            }
            }
        }

        if (command.getName().equalsIgnoreCase("getbell")) {
            if (sender instanceof Player) {
                Player p = (Player) sender;
                if (!p.hasPermission("earthspirit.admin")) {
                    I18n.get().send(p, "commands.no-permission");
                    return true;
                }
                p.getInventory().addItem(getSpiritBell());
                I18n.get().send(p, "commands.admin.get-bell");
                return true;
            }
        } else if (command.getName().equalsIgnoreCase("getwand")) {
            if (sender instanceof Player) {
                Player p = (Player) sender;
                if (!p.hasPermission("earthspirit.admin")) {
                    I18n.get().send(p, "commands.no-permission");
                    return true;
                }
                p.getInventory().addItem(getTamingWand());
                I18n.get().send(p, "commands.admin.get-wand");
                return true;
            }
        }
        return false;
    }

    public ItemStack getSpiritBell() {
        return SpiritItemManager.get().getItem("spirit_bell");
    }

    public ItemStack getTamingWand() {
        return SpiritItemManager.get().getItem("taming_wand");
    }
}
