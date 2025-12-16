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

        // 权限检查
        boolean isOwner = player.getUniqueId().equals(spirit.getOwnerId());
        boolean isResident = TownyIntegration.isResident(spirit.getTownName(), player);
        boolean canInteract = isOwner || isResident;

        // 1. 背景板 (用黑色玻璃填充，美观)
        ItemStack bg = createItem(Material.BLACK_STAINED_GLASS_PANE, "§7");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, bg);
        }

        // 2. 核心状态 (中间 - 头颅)
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta headMeta = (SkullMeta) head.getItemMeta();
        if (headMeta != null) {
            headMeta.setOwningPlayer(Bukkit.getOfflinePlayer(spirit.getOwnerId())); // 显示主人的头
            headMeta.setDisplayName("§e✦ " + spirit.getName() + " §e✦");
            
            List<String> lore = new ArrayList<>();
            lore.add("§7--------------------");
            lore.add("§f ❖ 形态: §b" + spirit.getMode().getDisplayName());
            lore.add("§f ❖ 心情: " + getMoodBar(spirit.getMood()));
            lore.add("§f ❖ 等级: §bLv." + spirit.getLevel());
            lore.add("§f ❖ 经验: §a" + spirit.getExp() + " / " + (spirit.getLevel() * 100));
            lore.add("§f ❖ 主人: §7" + Bukkit.getOfflinePlayer(spirit.getOwnerId()).getName());
            
            // 显示居所名称 (直接使用地灵记录的，或者是 Towny 里的)
            // String displayTownName = spirit.getTownName();
            
            // 如果是主人查看，且名字不一致，顺便更新一下数据
            if (isOwner) { 
                com.palmergames.bukkit.towny.object.Town t = TownyIntegration.getTown(player);
                if (t != null && !t.getName().equals(spirit.getTownName())) {
                     spirit.setTownName(t.getName());
                }
            }
            
            lore.add("§7--------------------");
            if (spirit.isAbandoned()) {
                lore.add("§c [!] 处于被遗弃状态");
            } else if (spirit.getTownName() == null) {
                lore.add("§b [✈] 正在流浪");
            } else if (spirit.getMode() == SpiritEntity.SpiritMode.COMPANION) {
                lore.add("§6 [👣] 正在跟随主人");
            } else {
                lore.add("§a [√] 正在守护这片土地");
            }
            headMeta.setLore(lore);
            head.setItemMeta(headMeta);
        }
        inv.setItem(13, head);

        // 3. 互动按钮 (左侧 - 抚摸)
        if (canInteract) {
            ItemStack petBtn = createItem(Material.FEATHER, "§d§l❤ 抚摸", 
                "§7", "§f轻抚地灵的额头...", "§7(每日可提升心情)");
            inv.setItem(11, petBtn);

            // 4. 投喂按钮 (右侧 - 蛋糕)
            ItemStack feedBtn = createItem(Material.CAKE, "§6§l♨ 投喂", 
                "§7", "§f消耗背包里的食物进行投喂", "§7(恢复大量心情)", "", "§e[点击自动消耗背包食物]");
            inv.setItem(15, feedBtn);
            
            // 4.5 背包按钮
            ItemStack bagBtn;
            if (spirit.getMode() == SpiritEntity.SpiritMode.COMPANION) {
                 bagBtn = createItem(Material.CHEST, "§6§l🎒 地灵背包", "§7", "§f点击打开背包", "§7(仅旅伴模式可用)");
            } else {
                 bagBtn = createItem(Material.CHEST, "§7§l🎒 地灵背包", "§7", "§c仅旅伴形态可用");
            }
            inv.setItem(14, bagBtn);
        } else {
            // 访客模式显示灰色
            ItemStack noPerm = createItem(Material.GRAY_DYE, "§7§l🔒 访客模式", "§7", "§f你需要成为该城镇的居民", "§f才能与地灵互动。");
            inv.setItem(11, noPerm);
            
            if (spirit.isAbandoned()) {
                 // 允许投喂被遗弃的地灵
                 ItemStack feedBtn = createItem(Material.CAKE, "§6§l♨ 投喂 (安抚)", 
                    "§7", "§f这个地灵看起来很孤独...", "§f给它一点食物安抚它吧。", "§7(每日限一次，不增加心情)");
                 inv.setItem(15, feedBtn);
            } else {
                 inv.setItem(15, noPerm);
            }
        }

        // 5. 居所管理入口 (底部中间)
        String townName = spirit.getTownName();
        
        if (townName == null) {
            // 无居所 -> 显示 "建立领地"
            if (canInteract && isOwner) {
                ItemStack createBtn = createItem(Material.OAK_SAPLING, "§a§l🌱 建立领地", 
                    "§7", "§f这只地灵还没有守护的土地。", 
                    "§f点击将脚下区块设为 §e核心领地§f！", 
                    "§c(仅限守护灵模式)");
                inv.setItem(22, createBtn);
            } else {
                ItemStack noTown = createItem(Material.DEAD_BUSH, "§7§l未知居所", "§7", "§f这只地灵还在流浪...");
                inv.setItem(22, noTown);
            }
        } else {
            // 有居所 -> 显示 "居所管理" 和 "扩充领地"
            if (canInteract) {
                ItemStack manageBtn = createItem(Material.EMERALD, "§2§l⚒ 居所管理", 
                    "§7", "§f当前居所: §a" + townName, "§7", "§f点击查看或管理居所", "§7(权限/公告/升级)");
                inv.setItem(22, manageBtn);
                
                if (isOwner) {
                    ItemStack expandBtn = createItem(Material.GOLDEN_SHOVEL, "§6§l🚩 扩充领地", 
                        "§7", "§f将脚下区块纳入领地范围", 
                        "§f当前等级上限: §e" + (1 + (spirit.getLevel()-1)*2) + " 格",
                        "§c(仅限守护灵模式)");
                    inv.setItem(20, expandBtn);
                }
            }
        }

        // 6. 更多功能 (重命名 & 解除契约)
            if (isOwner) {
                ItemStack renameSpiritBtn = createItem(Material.NAME_TAG, "§e§l✎ 地灵改名", 
                    "§7", "§f给地灵起个新名字", "§f当前名字: §e" + spirit.getName());
                inv.setItem(24, renameSpiritBtn);
                
                ItemStack releaseBtn = createItem(Material.SKELETON_SKULL, "§4§l☠ 解除契约", 
                    "§7", "§f释放地灵，解除契约", 
                    "§c警告：地灵将永久消失！", 
                    "§c你可以使用风铃召唤新的地灵。");
                inv.setItem(26, releaseBtn);
            }

            player.openInventory(inv);
    }

    public static void openManagementMenu(Player player, SpiritEntity spirit) {
        Inventory inv = Bukkit.createInventory(null, 27, SUB_GUI_TITLE);

        com.palmergames.bukkit.towny.object.Town town = TownyIntegration.getTown(player);
        // 如果玩家是居民但不是主人，尝试获取其所属城镇
        if (town == null) {
            town = TownyIntegration.getTownAt(player.getLocation()); // 尝试获取脚下城镇
            if (town == null || !town.getName().equals(spirit.getTownName())) {
                // 如果脚下不是或者不对，尝试直接获取 Spirit 记录的城镇
                 try {
                    town = com.palmergames.bukkit.towny.TownyUniverse.getInstance().getTown(spirit.getTownName());
                } catch (Exception e) {}
            }
        }

        if (town == null) {
            player.sendMessage("§c无法获取居所数据！");
            return;
        }

        // 权限判断
        boolean isOwner = player.getUniqueId().equals(spirit.getOwnerId());

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

        // 辅助Lore生成
        String clickHint = isOwner ? "§f点击切换状态" : "§7(仅主人可修改)";
        String editHint = isOwner ? "§f点击修改" : "§7(仅主人可修改)";

        // PVP 开关
        ItemStack pvpBtn = createItem(Material.DIAMOND_SWORD, "§c§l⚔ PVP状态", 
            "§7", clickHint, 
            "§f当前状态: " + (pvp ? "§a开启" : "§c关闭"));
        inv.setItem(10, pvpBtn);

        // 怪物生成
        ItemStack mobBtn = createItem(Material.ZOMBIE_HEAD, "§2§l☠ 怪物生成", 
            "§7", clickHint,
            "§f当前状态: " + (mobs ? "§a开启" : "§c关闭"));
        inv.setItem(11, mobBtn);

        // 爆炸开关
        ItemStack tntBtn = createItem(Material.TNT, "§4§l💣 爆炸保护", 
            "§7", clickHint,
            "§f当前状态: " + (expl ? "§a开启" : "§c关闭"));
        inv.setItem(12, tntBtn);
        
        // 火焰开关
        ItemStack fireBtn = createItem(Material.FLINT_AND_STEEL, "§6§l🔥 火焰保护", 
            "§7", clickHint,
            "§f当前状态: " + (fire ? "§a开启" : "§c关闭"));
        inv.setItem(13, fireBtn);

        // 公告
        ItemStack boardBtn = createItem(Material.OAK_SIGN, "§e§l✎ 进城公告", 
            "§7", editHint,
            "§f当前公告: §7" + (board.isEmpty() ? "暂无" : board));
        inv.setItem(14, boardBtn);

        // 居所名 (仅主人显示修改提示，居民只显示名字)
        ItemStack renameBtn = createItem(Material.NAME_TAG, "§b§l✎ 居所名称", 
            "§7", editHint,
            "§f当前名称: §b" + townName);
        inv.setItem(15, renameBtn);

        // 删除居所 (仅主人可见)
        if (isOwner) {
            ItemStack deleteBtn = createItem(Material.BARRIER, "§4§l⚠ 废弃居所", 
                "§7", "§f点击解散居所 (慎用！)", "§c此操作不可撤销！");
            inv.setItem(16, deleteBtn);
        } else {
            // 居民显示身份信息
            ItemStack roleBtn = createItem(Material.PLAYER_HEAD, "§3§l👤 您的身份",
                "§7", "§f您是这片灵域的: §b居民",
                "§f拥有基础交互权限");
             inv.setItem(16, roleBtn);
        }

        // 废弃单块土地
        if (isOwner) {
            ItemStack unclaimBtn = createItem(Material.IRON_SHOVEL, "§c§l⚒ 废弃当前地块",
                "§7", "§f删除脚下的领地区块",
                "§c仅限守护灵模式下操作",
                "§c不可删除核心区块");
            inv.setItem(19, unclaimBtn);
        }

        // 成员管理 (仅主人可见管理，居民可能看到列表或者直接隐藏)
        if (isOwner) {
            ItemStack memberBtn = createItem(Material.PLAYER_HEAD, "§3§l👥 成员管理",
                "§7", "§f管理领地成员 (邀请/踢出)");
            inv.setItem(20, memberBtn);
        }

        // 6. 返回按钮
        ItemStack backBtn = createItem(Material.ARROW, "§f§l⬅ 返回", "§7", "§f返回上一级菜单");
        inv.setItem(26, backBtn);

        player.openInventory(inv);
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
