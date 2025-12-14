package com.example.earthspirit;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;

import java.util.UUID;
import java.util.Set;
import java.util.HashSet;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import org.bukkit.event.player.PlayerMoveEvent;

public class SpiritListener implements Listener {
    private final EarthSpiritPlugin plugin;
    private final Set<UUID> editingBoard = new HashSet<>();
    private final Set<UUID> editingName = new HashSet<>(); // 地灵改名
    private final Set<UUID> editingTownName = new HashSet<>(); // 居所改名
    private final Set<UUID> confirmingDelete = new HashSet<>(); // 删除确认

    public SpiritListener(EarthSpiritPlugin plugin) {
        this.plugin = plugin;
    }
    
    // 进城提示 (Board)
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() && 
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return; // 忽略非方块移动
        }
        
        Player p = event.getPlayer();
        com.palmergames.bukkit.towny.object.Town toTown = TownyIntegration.getTownAt(event.getTo());
        com.palmergames.bukkit.towny.object.Town fromTown = TownyIntegration.getTownAt(event.getFrom());
        
        if (toTown != null && !toTown.equals(fromTown)) {
            // 玩家进入了新的城镇
            String board = toTown.getBoard();
            if (board == null || board.isEmpty()) board = "欢迎来到 " + toTown.getName();
            
            p.sendTitle("§a" + toTown.getName(), "§e" + board, 10, 70, 20);
        }
    }

    // 1. 召唤地灵 (右键风铃)
    @EventHandler
    public void onSummon(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getAction().name().contains("RIGHT")) return;

        Player p = event.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();

        // 检查是否是灵契风铃
        if (item.getType() == Material.BELL && item.hasItemMeta() && item.getItemMeta().hasDisplayName() && item.getItemMeta().getDisplayName().contains("灵契风铃")) {
            
            // 检查该位置是否允许创建
            if (plugin.getManager().hasSpiritNearby(p.getLocation(), 50)) {
                p.sendMessage("§c这里离其他地灵太近了，请换个地方！");
                return;
            }

            // 尝试创建 Towny 领地
            String townName = p.getName() + "的居所";
            boolean success = TownyIntegration.createTown(p, townName, p.getLocation());
            if (!success) {
                // 如果创建失败，消息已在 createTown 中发送
                return;
            }

            // 创建地灵实体 (ArmorStand)
            ArmorStand as = (ArmorStand) p.getWorld().spawnEntity(p.getLocation(), EntityType.ARMOR_STAND);
            as.setVisible(false); // 隐形
            as.setGravity(false);
            as.setCustomName("§a" + p.getName() + "的地灵");
            as.setCustomNameVisible(true);
            as.setSmall(true); // 小号盔甲架更可爱
            
            as.getEquipment().setHelmet(new ItemStack(Material.JACK_O_LANTERN)); // 临时用南瓜头

            // 保存数据
            SpiritEntity spirit = new SpiritEntity(as.getUniqueId(), p.getUniqueId(), "地灵", townName);
            plugin.getManager().addSpirit(spirit);

            p.sendMessage("§a成功召唤了地灵！快给它起个名字吧！");
            p.sendMessage("§e请在聊天栏输入地灵的名字：");
            editingName.add(p.getUniqueId()); // 立即进入改名状态
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1, 1);
            
            // 消耗物品
            item.setAmount(item.getAmount() - 1);
        }
    }

    // 2. 交互地灵 (打开GUI)
    @EventHandler
    public void onInteractSpirit(PlayerInteractAtEntityEvent event) {
        Entity entity = event.getRightClicked();
        SpiritEntity spiritData = plugin.getManager().getSpirit(entity.getUniqueId());

        if (spiritData == null) return; // 不是地灵
        event.setCancelled(true); // 禁止普通交互（如下马、换装备）

        Player p = event.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();

        // 如果玩家手里拿着食物，且是主人，优先尝试快捷喂食
        if (isFood(hand.getType()) && p.getUniqueId().equals(spiritData.getOwnerId())) {
            handleFeed(p, spiritData, hand);
            return;
        }

        // 快捷抚摸 (Shift + 空手 + 右键)
        if (p.isSneaking() && hand.getType() == Material.AIR && p.getUniqueId().equals(spiritData.getOwnerId())) {
            handleInteraction(p, spiritData);
            return;
        }

        // 其他情况 (空手或非食物)，打开控制面板
        SpiritGUI.openMenu(p, spiritData);
    }

    // 3. 处理 GUI 点击事件
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.equals(SpiritGUI.GUI_TITLE) && !title.equals(SpiritGUI.SUB_GUI_TITLE)) return;
        event.setCancelled(true); // 禁止拿取物品

        Player p = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // 获取 Spirit
        SpiritEntity targetSpirit = getTargetSpirit(p);
        if (targetSpirit == null) {
             p.closeInventory();
             p.sendMessage("§c未找到你的地灵。");
             return;
        }

        if (title.equals(SpiritGUI.GUI_TITLE)) {
            handleMainMenuClick(p, clicked, targetSpirit);
        } else if (title.equals(SpiritGUI.SUB_GUI_TITLE)) {
            handleManagementMenuClick(p, clicked, targetSpirit);
        }
    }
    
    private SpiritEntity getTargetSpirit(Player p) {
        // 简化：查找归属该玩家的第一个地灵
        for (SpiritEntity s : plugin.getManager().getAllSpirits().values()) {
            if (s.getOwnerId().equals(p.getUniqueId())) {
                return s;
            }
        }
        return null;
    }

    private void handleMainMenuClick(Player p, ItemStack clicked, SpiritEntity spirit) {
        if (clicked.getType() == Material.FEATHER) { // 抚摸
            handleInteraction(p, spirit);
            p.closeInventory(); 
        } else if (clicked.getType() == Material.CAKE) { // 投喂
            handleFeed(p, spirit, null);
            p.closeInventory();
        } else if (clicked.getType() == Material.EMERALD) { // 居所管理
            p.closeInventory();
            SpiritGUI.openManagementMenu(p, spirit);
        }
    }

    private void handleManagementMenuClick(Player p, ItemStack clicked, SpiritEntity spirit) {
        if (clicked.getType() == Material.ARROW) { // 返回
            p.closeInventory();
            SpiritGUI.openMenu(p, spirit);
            return;
        }
        
        com.palmergames.bukkit.towny.object.Town town = TownyIntegration.getTown(p);
        if (town == null) {
            p.sendMessage("§c无法获取居所数据！");
            p.closeInventory();
            return;
        }

        if (clicked.getType() == Material.DIAMOND_SWORD) {
            TownyIntegration.togglePvp(town);
            SpiritGUI.openManagementMenu(p, spirit); // 刷新
        } else if (clicked.getType() == Material.ZOMBIE_HEAD) {
            TownyIntegration.toggleMobs(town);
            SpiritGUI.openManagementMenu(p, spirit);
        } else if (clicked.getType() == Material.TNT) {
            TownyIntegration.toggleExplosion(town);
            SpiritGUI.openManagementMenu(p, spirit);
        } else if (clicked.getType() == Material.FLINT_AND_STEEL) {
            TownyIntegration.toggleFire(town);
            SpiritGUI.openManagementMenu(p, spirit);
        } else if (clicked.getType() == Material.OAK_SIGN) {
            p.closeInventory();
            editingBoard.add(p.getUniqueId());
            p.sendMessage("§e请在聊天栏输入新的公告内容 (输入 'cancel' 取消):");
        } else if (clicked.getType() == Material.NAME_TAG) { // 修改居所名
            p.closeInventory();
            editingTownName.add(p.getUniqueId());
            p.sendMessage("§e请在聊天栏输入新的居所名称 (输入 'cancel' 取消):");
        } else if (clicked.getType() == Material.BARRIER) { // 删除居所
            p.closeInventory();
            confirmingDelete.add(p.getUniqueId());
            p.sendMessage("§c§l警告！你正在尝试废弃该居所！");
            p.sendMessage("§c这将删除所有领地保护和地灵数据！");
            p.sendMessage("§c请在聊天栏输入 'confirm' 确认删除，输入其他内容取消。");
        } else if (clicked.getType() == Material.EXPERIENCE_BOTTLE) { // 升级
            p.closeInventory();
            TownyIntegration.upgradeTownLevel(p, spirit);
            plugin.getManager().saveData();
        } else if (clicked.getType() == Material.PLAYER_HEAD) { // 成员
            p.closeInventory();
            TownyIntegration.manageMembers(p);
        }
    }
    
    private void handleFeed(Player p, SpiritEntity spirit, ItemStack specificFood) {
        // 1. 寻找食物
        ItemStack foodToUse = null;
        if (specificFood != null && isFood(specificFood.getType())) {
            foodToUse = specificFood;
        } else {
            // 扫描背包
            for (ItemStack item : p.getInventory().getContents()) {
                if (item != null && isFood(item.getType())) {
                    foodToUse = item;
                    break;
                }
            }
        }

        if (foodToUse == null) {
            p.sendMessage("§c你背包里没有食物！(面包/蛋糕/蜂蜜等)");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            return;
        }

        // 2. 检查状态 (饥饿 或 心情低落)
        // 统一逻辑：
        // - 如果饥饿 (isHungry): 进食正餐，心情+5，重置饥饿计时
        // - 如果不饿但心情 < 80: 进食零食，心情+2，不重置计时
        // - 如果不饿且心情 >= 80: 拒绝
        
        boolean isHungry = spirit.isHungry();
        double mood = spirit.getMood();

        if (!isHungry && mood >= 80) {
            p.sendMessage("§a" + spirit.getName() + "：我不饿，待会再来吧！");
            return;
        }

        // 3. 执行喂食
        foodToUse.setAmount(foodToUse.getAmount() - 1);
        spirit.feed(); // 更新最后喂食时间戳

        if (isHungry) {
            spirit.setMood(mood + 5);
            spirit.addExp(5); // 增加经验
            spirit.scheduleNextHunger(); // 重置下次饥饿时间 (3-4小时)
            p.sendMessage("§e你喂食了" + spirit.getName() + "，它看起来很满足！(心情 +5, 经验 +5)");
        } else {
            // 零食模式
            spirit.setMood(mood + 2);
            spirit.addExp(2); // 增加经验
            p.sendMessage("§e你给" + spirit.getName() + " 喂了一些零食。(心情 +2, 经验 +2)");
        }

        spirit.setExpression(SpiritSkinManager.Expression.HAPPY, 100);
        p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EAT, 1, 1);
        plugin.getManager().saveData();
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player p = event.getPlayer();
        String msg = event.getMessage();
        UUID uuid = p.getUniqueId();

        if (editingBoard.contains(uuid)) {
            event.setCancelled(true);
            editingBoard.remove(uuid);
            if (msg.equalsIgnoreCase("cancel")) {
                p.sendMessage("§c已取消操作。");
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> p.performCommand("town set board " + msg));
            return;
        }

        if (editingName.contains(uuid)) {
            event.setCancelled(true);
            editingName.remove(uuid);
            
            SpiritEntity spirit = getTargetSpirit(p);
            if (spirit != null) {
                spirit.setName(msg);
                plugin.getManager().saveData();
                p.sendMessage("§a地灵的名字已更新为: " + msg);
                // 更新头顶显示
                Entity entity = Bukkit.getEntity(spirit.getEntityId());
                if (entity != null) {
                    entity.setCustomName("§a" + msg);
                }
            } else {
                p.sendMessage("§c找不到地灵，改名失败。");
            }
            return;
        }

        if (editingTownName.contains(uuid)) {
            event.setCancelled(true);
            editingTownName.remove(uuid);
            if (msg.equalsIgnoreCase("cancel")) {
                p.sendMessage("§c已取消操作。");
                return;
            }
            
            SpiritEntity spirit = getTargetSpirit(p);
            if (spirit != null) {
                // 异步调用 Towny 改名
                Bukkit.getScheduler().runTask(plugin, () -> {
                    boolean success = TownyIntegration.renameTown(p, msg);
                    if (success) {
                        spirit.setTownName(msg);
                        plugin.getManager().saveData();
                        p.sendMessage("§a居所名称已更新为: " + msg);
                    } else {
                        p.sendMessage("§c改名失败！可能名字已被占用或格式错误。");
                    }
                });
            }
            return;
        }
        
        if (confirmingDelete.contains(uuid)) {
            event.setCancelled(true);
            confirmingDelete.remove(uuid);
            
            if (msg.equalsIgnoreCase("confirm")) {
                 SpiritEntity spirit = getTargetSpirit(p);
                 if (spirit != null) {
                     Bukkit.getScheduler().runTask(plugin, () -> {
                         boolean success = TownyIntegration.deleteTown(p);
                         if (success) {
                             // 删除地灵
                             Entity entity = Bukkit.getEntity(spirit.getEntityId());
                             if (entity != null) {
                                 entity.remove();
                             } else {
                                 // 如果 getEntity 失败（可能区块未加载或 ID 索引问题），尝试暴力搜索
                                 for (Entity e : p.getNearbyEntities(10, 10, 10)) {
                                     if (e.getUniqueId().equals(spirit.getEntityId())) {
                                         e.remove();
                                         break;
                                     }
                                 }
                             }
                             plugin.getManager().removeSpirit(spirit.getEntityId());
                             p.sendMessage("§a居所已废弃，地灵已回归自然。");
                         } else {
                             // 如果 Towny 删除失败，但实际上 Towny 里已经没有这个 Town 了 (返回 false)
                             // 这种情况下我们也应该清理地灵，因为地灵是绑定在 Town 上的
                             // 我们可以再次检查 Towny 是否真的还有这个玩家的 Town
                             if (TownyIntegration.getTown(p) == null) {
                                 // Towny 里已经没了，说明是残留的地灵
                                 Entity entity = Bukkit.getEntity(spirit.getEntityId());
                                 if (entity != null) entity.remove();
                                 else {
                                      for (Entity e : p.getNearbyEntities(10, 10, 10)) {
                                         if (e.getUniqueId().equals(spirit.getEntityId())) {
                                             e.remove();
                                             break;
                                         }
                                     }
                                 }
                                 plugin.getManager().removeSpirit(spirit.getEntityId());
                                 p.sendMessage("§a检测到居所已不存在，地灵已消散。");
                             } else {
                                 p.sendMessage("§c废弃失败！请联系管理员。");
                             }
                         }
                     });
                 }
            } else {
                p.sendMessage("§c操作已取消。");
            }
            return;
        }
    }

    private void handleInteraction(Player p, SpiritEntity data) {
        if (!p.getUniqueId().equals(data.getOwnerId())) {
            return;
        }

        long now = System.currentTimeMillis();
        long cooldown = 72000000L; // 20 hours
        long passed = now - data.getLastInteractTime();

        if (passed > cooldown) { 
            data.interact();
            data.setMood(data.getMood() + 5); 
            data.addExp(5); // 增加经验
            data.setExpression(SpiritSkinManager.Expression.HAPPY, 60); // 开心3秒
            p.sendMessage("§d你温柔地抚摸了 " + data.getName() + " 的头。");
            p.sendMessage("§d" + data.getName() + " 蹭了蹭你的手，心情变好了！(心情 +5, 经验 +5)");
            p.playSound(p.getLocation(), Sound.ENTITY_CAT_PURR, 1, 1);
            plugin.getManager().saveData();
        } else {
            long remaining = cooldown - passed;
            long hours = remaining / 3600000L;
            long minutes = (remaining % 3600000L) / 60000L;
            p.sendMessage("§c你今天已经抚摸过它了！");
            p.sendMessage("§c请等待 " + hours + " 小时 " + minutes + " 分钟后再来。");
        }
    }

    private boolean isFood(Material m) {
        return m.isEdible() || m == Material.CAKE || m == Material.HONEY_BOTTLE;
    }

    private void handleStrangerFeed(Player p, SpiritEntity spirit) {
        if (p.getUniqueId().equals(spirit.getOwnerId())) {
            // 主人回来了
            spirit.feed();
            spirit.setMood(50); // 恢复一部分心情
            spirit.setType(SpiritEntity.SpiritType.NORMAL); // 恢复正常形态
            p.sendMessage("§a你终于回来了！地灵重新焕发了生机！");
            plugin.getManager().saveData();
            return;
        }

        // 陌生人喂食
        UUID pid = p.getUniqueId();
        int count = spirit.getStrangerFeeds().getOrDefault(pid, 0) + 1;
        spirit.getStrangerFeeds().put(pid, count);
        
        p.sendMessage("§e你安抚了这只被遗弃的地灵 (" + count + "/3)");
        p.playSound(p.getLocation(), Sound.ENTITY_CAT_PURR, 1, 1);

        if (count >= 3) {
            // 过继逻辑
            spirit.setOwnerId(pid);
            spirit.setMood(100);
            spirit.setType(SpiritEntity.SpiritType.NORMAL);
            spirit.getStrangerFeeds().clear();
            spirit.feed();
            
            Bukkit.broadcastMessage("§6[地灵羁绊] §f玩家 §e" + p.getName() + " §f感动了地灵，成为了这块土地的新主人！");
            
            // 调用 Towny 转移市长权限
            if (spirit.getTownName() != null) {
                TownyIntegration.transferOwnership(spirit.getTownName(), p);
            }
        }
        plugin.getManager().saveData();
    }

    private void showStatus(Player p, SpiritEntity data) {
        p.sendMessage("§8[========================]");
        p.sendMessage("§7 名字: §f" + data.getName());
        p.sendMessage("§7 心情: §d" + (int)data.getMood() + "/100");
        p.sendMessage("§7 状态: §b" + data.getType().getDisplayName());
        if (data.isAbandoned()) {
            p.sendMessage("§c [被遗弃] - 它可以被任何人领养！");
        } else {
            p.sendMessage("§a [健康] - 领地保护中");
        }
        p.sendMessage("§8[========================]");
    }
}
