package com.example.earthspirit;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SpiritGUI {

    public static final String GUI_TITLE = "§8[ §2地灵羁绊 §8] §0守护面板";
    public static final String SUB_GUI_TITLE = "§8[ §2地灵羁绊 §8] §0居所管理";

    public static void openMenu(Player player, SpiritEntity spirit) {
        // 创建一个 3行 (27格) 的界面
        Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE);

        // 1. 背景板 (用黑色玻璃填充，美观)
        ItemStack bg = createItem(Material.BLACK_STAINED_GLASS_PANE, "§7");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, bg);
        }

        // 2. 核心状态 (中间 - 头颅)
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta headMeta = (SkullMeta) head.getItemMeta();
        if (headMeta != null) {
            headMeta.setOwningPlayer(Bukkit.getOfflinePlayer(spirit.getOwnerId())); // 显示主人的头，或者地灵的皮肤
            headMeta.setDisplayName("§e✦ " + spirit.getName() + " §e✦");
            
            List<String> lore = new ArrayList<>();
            lore.add("§7--------------------");
            lore.add("§f ❖ 形态: §b" + spirit.getType().getDisplayName());
            lore.add("§f ❖ 心情: " + getMoodBar(spirit.getMood()));
            lore.add("§f ❖ 主人: §7" + Bukkit.getOfflinePlayer(spirit.getOwnerId()).getName());
            
            // 尝试获取最新的居所名称
            String displayTownName = spirit.getTownName();
            if (player != null) { // 在 openMenu 调用时传入了 player
                com.palmergames.bukkit.towny.object.Town t = TownyIntegration.getTown(player);
                if (t != null) {
                    displayTownName = t.getName();
                    // 如果名字不一致，顺便更新一下数据
                    if (!displayTownName.equals(spirit.getTownName())) {
                         spirit.setTownName(displayTownName);
                    }
                }
            }
            
            lore.add("§7--------------------");
            if (spirit.isAbandoned()) {
                lore.add("§c [!] 处于被遗弃状态");
            } else {
                lore.add("§a [√] 正在守护这片土地");
            }
            headMeta.setLore(lore);
            head.setItemMeta(headMeta);
        }
        inv.setItem(13, head);

        // 3. 互动按钮 (左侧 - 抚摸)
        ItemStack petBtn = createItem(Material.FEATHER, "§d§l❤ 抚摸", 
            "§7", "§f轻抚地灵的额头...", "§7(每日可提升心情)");
        inv.setItem(11, petBtn);

        // 4. 投喂按钮 (右侧 - 蛋糕)
        ItemStack feedBtn = createItem(Material.CAKE, "§6§l♨ 投喂", 
            "§7", "§f消耗背包里的食物进行投喂", "§7(恢复大量心情)", "", "§e[点击自动消耗背包食物]");
        inv.setItem(15, feedBtn);

        // 5. 居所管理入口 (底部中间)
        // 获取最新的居所名称用于显示
        String townNameForButton = spirit.getTownName();
        if (townNameForButton == null) townNameForButton = "未知居所";
        
        ItemStack manageBtn = createItem(Material.EMERALD, "§2§l⚒ 居所管理", 
            "§7", "§f当前居所: §a" + townNameForButton, "§7", "§f点击进入居所管理面板", "§7(权限/更名/PVP/公告)");
        inv.setItem(22, manageBtn);

        player.openInventory(inv);
    }

    public static void openManagementMenu(Player player, SpiritEntity spirit) {
        Inventory inv = Bukkit.createInventory(null, 27, SUB_GUI_TITLE);

        com.palmergames.bukkit.towny.object.Town town = TownyIntegration.getTown(player);
        if (town == null) {
            player.sendMessage("§c无法获取居所数据！");
            return;
        }

        // 背景
        ItemStack bg = createItem(Material.GRAY_STAINED_GLASS_PANE, "§7");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, bg);
        }

        // 状态获取
        boolean pvp = TownyIntegration.isPvpEnabled(town);
        boolean mobs = TownyIntegration.isMobsEnabled(town);
        boolean expl = TownyIntegration.isExplosionEnabled(town);
        boolean fire = TownyIntegration.isFireEnabled(town);
        String board = TownyIntegration.getTownBoard(town);
        String townName = town.getName();

        // 状态指示灯 (已移除，直接在按钮显示)
        // inv.setItem(1, createStatusPane(pvp, "PVP"));
        // inv.setItem(2, createStatusPane(mobs, "怪物生成"));
        // inv.setItem(3, createStatusPane(!expl, "爆炸保护")); 
        // inv.setItem(4, createStatusPane(!fire, "火焰保护"));


        // PVP 开关
        ItemStack pvpBtn = createItem(Material.DIAMOND_SWORD, "§c§l⚔ PVP开关", 
            "§7", "§f点击切换居所 PVP 状态", 
            "§f当前状态: " + (pvp ? "§a开启" : "§c关闭"));
        inv.setItem(10, pvpBtn);

        // 怪物生成
        ItemStack mobBtn = createItem(Material.ZOMBIE_HEAD, "§2§l☠ 怪物生成", 
            "§7", "§f点击切换居所怪物生成",
            "§f当前状态: " + (mobs ? "§a开启" : "§c关闭"));
        inv.setItem(11, mobBtn);

        // 爆炸开关
        ItemStack tntBtn = createItem(Material.TNT, "§4§l💣 爆炸开关", 
            "§7", "§f点击切换爆炸开启/关闭",
            "§f当前状态: " + (expl ? "§a开启" : "§c关闭"));
        inv.setItem(12, tntBtn);
        
        // 火焰开关
        ItemStack fireBtn = createItem(Material.FLINT_AND_STEEL, "§6§l🔥 火焰开关", 
            "§7", "§f点击切换火焰蔓延开启/关闭",
            "§f当前状态: " + (fire ? "§a开启" : "§c关闭"));
        inv.setItem(13, fireBtn);

        // 修改公告
        ItemStack boardBtn = createItem(Material.OAK_SIGN, "§e§l✎ 修改公告", 
            "§7", "§f点击修改进城公告 (Board)",
            "§f当前公告: §7" + (board.isEmpty() ? "暂无" : board));
        inv.setItem(14, boardBtn);

        // 修改居所名 (改名)
        ItemStack renameBtn = createItem(Material.NAME_TAG, "§b§l✎ 修改居所名", 
            "§7", "§f点击修改居所 (Town) 名称",
            "§f当前名称: §b" + townName);
        inv.setItem(15, renameBtn);

        // 删除居所
        ItemStack deleteBtn = createItem(Material.BARRIER, "§4§l⚠ 废弃居所", 
            "§7", "§f点击解散居所 (慎用！)", "§c此操作不可撤销！");
        inv.setItem(16, deleteBtn);

        // 新功能：领地升级
        ItemStack upgradeBtn = createItem(Material.EXPERIENCE_BOTTLE, "§b§l⬆ 领地升级",
            "§7", "§f查看领地等级及升级条件",
            "§f当前等级: §eLv." + spirit.getLevel(),
            "§f当前经验: §a" + spirit.getExp());
        inv.setItem(19, upgradeBtn);

        // 新功能：成员管理
        ItemStack memberBtn = createItem(Material.PLAYER_HEAD, "§3§l👥 成员管理",
            "§7", "§f管理领地成员 (邀请/踢出)");
        inv.setItem(20, memberBtn);

        // 返回按钮
        ItemStack backBtn = createItem(Material.ARROW, "§f§l⬅ 返回", "§7", "§f返回上一页");
        inv.setItem(26, backBtn);

        player.openInventory(inv);
    }
    
    private static ItemStack createStatusPane(boolean status, String name) {
        Material mat = status ? Material.GREEN_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
        String statusText = status ? "§a开启" : "§c关闭";
        return createItem(mat, "§f" + name + ": " + statusText);
    }

    // 辅助方法：生成心情进度条
    private static String getMoodBar(double mood) {
        int progress = (int) (mood / 10);
        StringBuilder bar = new StringBuilder("§8[");
        for (int i = 0; i < 10; i++) {
            if (i < progress) {
                if (mood > 80) bar.append("§a❚");
                else if (mood > 30) bar.append("§e❚");
                else bar.append("§c❚");
            } else {
                bar.append("§7-");
            }
        }
        bar.append("§8] §f").append((int)mood);
        return bar.toString();
    }

    private static ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }
}
