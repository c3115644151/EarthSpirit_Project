package com.example.earthspirit;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.Location;
import org.bukkit.Particle;

import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.block.data.Ageable;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.MagmaCube;

import com.example.earthspirit.cravings.DailyRequest;
import com.example.earthspirit.cravings.CravingManager;

import org.bukkit.event.block.BlockBreakEvent;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class SpiritListener implements Listener {
    // ... existing fields ...

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // 玩家上线，重置心情衰减计时器，避免离线期间的时间差导致心情骤降
        SpiritEntity spirit = plugin.getManager().getSpiritByOwner(event.getPlayer().getUniqueId());
        if (spirit != null) {
            spirit.resetMoodTimer();
        }
    }

    // Duplicate onInventoryClick removed


    // ... existing code ...
    private final Set<UUID> editingBoard = new HashSet<>();
    private final java.util.Map<UUID, UUID> editingName = new java.util.HashMap<>(); // 地灵改名 (Player -> SpiritID)
    private final java.util.Map<UUID, UUID> editingTownName = new java.util.HashMap<>(); // 居所改名 (Player -> SpiritID)
    private final java.util.Map<UUID, UUID> confirmingDelete = new java.util.HashMap<>(); // 删除居所确认 (Player -> SpiritID)
    private final java.util.Map<UUID, UUID> confirmingRelease = new java.util.HashMap<>(); // 解除契约确认 (Player -> SpiritID)
    private final Set<UUID> switchingMenus = new HashSet<>(); // 正在切换菜单的玩家

    // 记录玩家当前正在查看哪个地灵 (Player UUID -> Spirit Entity UUID)
    private final java.util.Map<UUID, UUID> viewingSpirit = new java.util.HashMap<>();

    private final EarthSpiritPlugin plugin;
    
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

    // 1. 召唤/收回地灵 (右键风铃)
    @EventHandler
    public void onSummon(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getAction().name().contains("RIGHT")) return;

        Player p = event.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();

        // 检查是否是灵契风铃
        // 优先检查 CustomModelData (10001)，其次检查名字兼容旧物品
        boolean isSpiritBell = false;
        if (item.getType() == Material.BELL) {
            if (item.hasItemMeta() && item.getItemMeta().hasCustomModelData() && item.getItemMeta().getCustomModelData() == 10001) {
                isSpiritBell = true;
            } else if (item.hasItemMeta() && item.getItemMeta().hasDisplayName() && item.getItemMeta().getDisplayName().contains("灵契风铃")) {
                isSpiritBell = true;
            }
        }

        if (isSpiritBell) {
            event.setCancelled(true);

            // 获取玩家的地灵数据
            SpiritEntity spirit = plugin.getManager().getSpiritByOwner(p.getUniqueId());

            if (spirit != null) {
                // 已有数据
                if (spirit.getEntityId() != null && Bukkit.getEntity(spirit.getEntityId()) != null) {
                    // 已召唤 -> 收回
                    spirit.recall();
                    p.sendMessage("§e你收回了地灵。");
                    p.playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1f);
                } else {
                    // 未召唤 -> 召唤
                    spirit.summon(p.getLocation());
                    p.sendMessage("§a地灵响应了你的召唤！");
                }
            } else {
                // 新建地灵
                if (plugin.getManager().hasSpiritNearby(p.getLocation(), 2)) {
                    p.sendMessage("§c这里太挤了！");
                    return;
                }

                String defaultName = p.getName() + "的地灵";
                // 构造函数会直接生成实体，所以这里就是“召唤”
                // 注意：这里 townName 默认为 null (流浪状态)
                SpiritEntity newSpirit = new SpiritEntity(p.getUniqueId(), defaultName, p.getLocation());
                
                plugin.getManager().addSpirit(newSpirit);

                p.sendMessage("§a成功召唤了地灵！快给它起个名字吧！");
                p.sendMessage("§e请在聊天栏输入地灵的名字：");
                editingName.put(p.getUniqueId(), newSpirit.getEntityId()); 
                p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1, 1);
                
                // 消耗物品 (第一次召唤消耗，后续收回/召唤不消耗)
                item.setAmount(item.getAmount() - 1);
            }
        }
    }

    // 1.5 驯兽杖功能
    @EventHandler
    public void onWandUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        
        Player p = event.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        
        // 检查物品: 烈焰棒 (驯兽杖)
        // 优先检查 CustomModelData (10002)，其次检查名字
        boolean isWand = false;
        if (item.getType() == Material.BLAZE_ROD) {
            if (item.hasItemMeta() && item.getItemMeta().hasCustomModelData() && item.getItemMeta().getCustomModelData() == 10002) {
                isWand = true;
            } else if (item.hasItemMeta() && item.getItemMeta().hasDisplayName() && item.getItemMeta().getDisplayName().contains("驯兽")) {
                isWand = true;
            }
        }
        
        if (!isWand) return;

        event.setCancelled(true);
        SpiritEntity spirit = plugin.getManager().getSpiritByOwner(p.getUniqueId());
        
        if (spirit == null || spirit.getEntityId() == null) {
            p.sendMessage("§c你还没有召唤地灵！");
            return;
        }
        
        if (spirit.getMode() != SpiritEntity.SpiritMode.COMPANION) {
            p.sendMessage("§c驯兽杖仅在旅伴形态下可用！");
            return;
        }

        // 右键方块 -> 移动
        if (event.getAction().name().contains("RIGHT_CLICK_BLOCK")) {
            org.bukkit.block.Block b = event.getClickedBlock();
            if (b != null) {
                // 移动到方块上方
                Location target = b.getLocation().add(0.5, 1, 0.5);
                spirit.moveTo(target);
                p.sendMessage("§a地灵正在前往目标地点...");
                p.spawnParticle(Particle.HEART, target, 5, 0.2, 0.2, 0.2, 0);
            }
        } 
        // 左键/右键空气 -> 召回
        else if (event.getAction().name().contains("LEFT") || event.getAction().name().contains("RIGHT_CLICK_AIR")) {
             // 召回（取消手动移动）
             spirit.cancelMove();
             p.sendMessage("§e地灵已停止前往目标，重新跟随你。");
             p.playSound(p.getLocation(), Sound.ENTITY_WOLF_WHINE, 1f, 1f);
        }
    }

    // 2. 交互地灵
    @EventHandler
    public void onInteractSpirit(PlayerInteractAtEntityEvent event) {
        Entity entity = event.getRightClicked();
        SpiritEntity spiritData = plugin.getManager().getSpirit(entity.getUniqueId());

        if (spiritData == null) return; // 不是地灵
        event.setCancelled(true);

        Player p = event.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();

        // 检查是否有权交互 (主人或居民)
        boolean isOwner = p.getUniqueId().equals(spiritData.getOwnerId());
        // boolean isResident = TownyIntegration.isResident(spiritData.getTownName(), p); // Unused locally
        
        // Shift + 右键：切换模式 (仅限主人)
        if (p.isSneaking() && isOwner) {
            spiritData.toggleMode();
            String modeStr = (spiritData.getMode() == SpiritEntity.SpiritMode.COMPANION) ? "旅伴" : "守护灵";
            p.sendMessage("§e地灵已切换为" + modeStr + "形态。");
            return;
        }

        // 投喂逻辑 (持有食物)
        if (isFood(hand.getType())) {
            handleFeed(p, spiritData, hand);
            return;
        }
        
        // 其他情况：打开菜单 (访客模式由 GUI 内部处理)
        viewingSpirit.put(p.getUniqueId(), spiritData.getEntityId());
        SpiritGUI.openMenu(p, spiritData);
    }

    // 2.1 关闭 GUI 时清理记录
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        // If switching menus, do NOT remove the viewing spirit session.
        // The switchingMenus set is cleaned up by a delayed task.
        if (switchingMenus.contains(uuid)) {
            return;
        }
        viewingSpirit.remove(uuid);
    }

    /**
     * Safely switches menus by preserving the session for a short duration
     * to allow InventoryCloseEvent to be ignored.
     */
    private void safeSwitch(Player p, Runnable openAction) {
        switchingMenus.add(p.getUniqueId());
        openAction.run();
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                switchingMenus.remove(p.getUniqueId());
            }
        }.runTaskLater(plugin, 5L); // 5 ticks delay to cover transition
    }

    // 3. 处理 GUI 点击事件
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        // Allow clicks in player inventory unless it's a special GUI
        if (!title.equals(SpiritGUI.GUI_TITLE) && !title.equals(SpiritGUI.SUB_GUI_TITLE) && !title.startsWith("嘴馋清单 - ")) return;
        
        event.setCancelled(true); // 禁止拿取物品
        if (event.getCurrentItem() == null) return; // Allow empty clicks but event is cancelled

        Player p = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        SpiritEntity targetSpirit = null;
        if (viewingSpirit.containsKey(p.getUniqueId())) {
            targetSpirit = plugin.getManager().getSpirit(viewingSpirit.get(p.getUniqueId()));
        }
        
        if (targetSpirit == null) {
             p.closeInventory();
             p.sendMessage("§c交互会话已过期，请重新右键地灵。");
             return;
        }

        if (title.equals(SpiritGUI.GUI_TITLE)) {
            // Removed slot 15 interception to allow feed logic in handleMainMenuClick
            handleMainMenuClick(p, clicked, targetSpirit);
        } else if (title.equals(SpiritGUI.SUB_GUI_TITLE)) {
            handleManagementMenuClick(p, clicked, targetSpirit);
        } else if (title.startsWith("嘴馋清单 - ")) {
            handleCravingsMenuClick(p, clicked, targetSpirit, event.getRawSlot());
        }
    }

    private void handleCravingsMenuClick(Player player, ItemStack clicked, SpiritEntity spirit, int slot) {
            DailyRequest req = spirit.getDailyRequest();
            if (req == null) return;

            // Back Button
            if (slot == 36) {
                safeSwitch(player, () -> SpiritGUI.openMenu(player, spirit));
                return;
            }
            
            // Refresh Button
            if (slot == 44 && clicked.getType() == Material.RED_DYE) {
                plugin.getCravingManager().forceRefresh(spirit);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
                safeSwitch(player, () -> SpiritGUI.openCravingsMenu(player, spirit));
                return;
            }
            
            // Claim Reward Button
            if (slot == 40) {
                 plugin.getCravingManager().claimReward(player, spirit);
                 player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
                 safeSwitch(player, () -> SpiritGUI.openCravingsMenu(player, spirit));
                 return;
            }
            
            // Task Items (20-24)
            if (slot >= 20 && slot <= 24) {
                int index = slot - 20;
                DailyRequest.TaskItem task = req.items.get(index);
                if (task != null && !task.submitted) {
                    // Check logic
                    int has = SpiritGUI.countItems(player, task.key, plugin.getCravingManager());
                    if (has >= task.amount) {
                        // Submit!
                        removeItems(player, task.key, task.amount, plugin.getCravingManager());
                        task.submitted = true;
                        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 1, 1);
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 1, 1);
                        
                        // Re-open to refresh view
                        safeSwitch(player, () -> SpiritGUI.openCravingsMenu(player, spirit));
                        
                    } else {
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
                        player.sendMessage("§c物品不足！");
                    }
                }
            }
    }

    private void removeItems(Player p, String key, int amount, CravingManager mgr) {
        ItemStack target = mgr.getItemStack(key);
        int toRemove = amount;
        for (ItemStack is : p.getInventory().getContents()) {
            if (is != null && is.isSimilar(target)) {
                if (is.getAmount() <= toRemove) {
                    toRemove -= is.getAmount();
                    is.setAmount(0);
                } else {
                    is.setAmount(is.getAmount() - toRemove);
                    toRemove = 0;
                }
                if (toRemove <= 0) break;
            }
        }
    }

    private void handleMainMenuClick(Player p, ItemStack clicked, SpiritEntity spirit) {
        if (clicked.getType() == Material.FEATHER) { // 抚摸
            handleInteraction(p, spirit);
            p.closeInventory(); 
        } else if (clicked.getType() == Material.CAKE) { // 投喂
            handleFeed(p, spirit, null);
            p.closeInventory();
        } else if (clicked.getType() == Material.PAPER) { // 嘴馋清单
            safeSwitch(p, () -> SpiritGUI.openCravingsMenu(p, spirit));
        } else if (clicked.getType() == Material.EMERALD) { // 居所管理 / 领地功能
            if (spirit.getMode() == SpiritEntity.SpiritMode.GUARDIAN) {
                // 只有守护模式才能管理领地
                safeSwitch(p, () -> SpiritGUI.openManagementMenu(p, spirit));
            } else {
                p.sendMessage("§c地灵需要处于守护灵形态才能管理领地。(Shift+右键切换)");
            }
        } else if (clicked.getType() == Material.CHEST) { // 背包
             if (spirit.getMode() == SpiritEntity.SpiritMode.COMPANION) {
                 p.closeInventory();
                 spirit.getInventory().open(p);
             } else {
                 p.sendMessage("§c地灵背包仅在旅伴形态下可用。(Shift+右键切换)");
             }
        } else if (clicked.getType() == Material.OAK_SAPLING) { // 建立领地
            if (spirit.getMode() != SpiritEntity.SpiritMode.GUARDIAN) {
                p.closeInventory();
                p.sendMessage("§c请先切换至守护灵模式 (Shift+右键)！");
                return;
            }
            p.closeInventory();
            
            String townName = p.getName() + "的居所";
            boolean success = TownyIntegration.createTown(p, townName, spirit.getLocation());
             if (success) {
                 p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                 p.spawnParticle(org.bukkit.Particle.HEART, spirit.getLocation(), 20, 0.5, 0.5, 0.5, 0.1);
                 spirit.setTownName(townName);
                 plugin.getManager().saveData();
                p.sendMessage("§a§l领地结界已展开！(核心区块)");
            }
        } else if (clicked.getType() == Material.GOLDEN_SHOVEL) { // 扩充领地
            if (spirit.getMode() != SpiritEntity.SpiritMode.GUARDIAN) {
                p.closeInventory();
                p.sendMessage("§c请先切换至守护灵模式 (Shift+右键)！");
                return;
            }
            
            com.palmergames.bukkit.towny.object.Town town = TownyIntegration.getTown(p);
            if (town == null) {
                p.closeInventory();
                p.sendMessage("§c数据同步异常，请重新打开菜单。");
                return;
            }
            
            // 检查等级限制
            int maxBlocks = 1 + (spirit.getLevel() - 1) * 2;
            if (town.getTownBlocks().size() >= maxBlocks) {
                p.closeInventory();
                p.sendMessage("§c领地范围已达上限！(Lv." + spirit.getLevel() + " 上限: " + maxBlocks + "格)");
                p.sendMessage("§e提升地灵等级可解锁更多领地空间。");
                p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 1f, 1f);
                return;
            }
            
            p.closeInventory();
            boolean success = TownyIntegration.claimBlock(p);
            if (success) {
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
                // 注意：town.getTownBlocks().size() 可能还未更新，+1 显示
                p.sendMessage("§a领地扩充成功！");
            }
        } else if (clicked.getType() == Material.DEAD_BUSH) {
             p.closeInventory();
             p.sendMessage("§7这只地灵正在流浪，还没有属于它的家。");
        } else if (clicked.getType() == Material.NAME_TAG) { // 地灵改名
            if (!p.getUniqueId().equals(spirit.getOwnerId())) return;
            p.closeInventory();
            editingName.put(p.getUniqueId(), spirit.getEntityId());
            p.sendMessage("§e请在聊天栏输入地灵的新名字 (输入 'cancel' 取消):");
        } else if (clicked.getType() == Material.SKELETON_SKULL) { // 解除契约
            if (!p.getUniqueId().equals(spirit.getOwnerId())) return;
            p.closeInventory();
            confirmingRelease.put(p.getUniqueId(), spirit.getEntityId());
            p.sendMessage("§c§l警告！你正在尝试解除与地灵的契约！");
            p.sendMessage("§c地灵将永久消失，无法找回！");
            p.sendMessage("§c请在聊天栏输入 'release' 确认解除，输入其他内容取消。");
        }
    }

    private void handleManagementMenuClick(Player p, ItemStack clicked, SpiritEntity spirit) {
        if (clicked.getType() == Material.ARROW) { // 返回
            safeSwitch(p, () -> SpiritGUI.openMenu(p, spirit));
            return;
        }

        if (!p.getUniqueId().equals(spirit.getOwnerId())) {
            p.sendMessage("§c只有居所主人 (Owner) 可以执行此操作。");
            return;
        }
        
        // 圈地功能
        if (clicked.getType() == Material.TOTEM_OF_UNDYING) {
            // 尝试在此处圈地
            String townName = p.getName() + "的居所";
            boolean success = TownyIntegration.createTown(p, townName, spirit.getLocation());
            if (success) {
                p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1f);
                p.spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, spirit.getLocation(), 20, 0.5, 0.5, 0.5, 0.1);
                spirit.setTownName(townName); // 记录领地名
                plugin.getManager().saveData();
                p.sendMessage("§a§l领地结界已展开！");
                p.closeInventory();
            }
            return;
        }

        // 获取已有领地进行管理
        com.palmergames.bukkit.towny.object.Town town = TownyIntegration.getTown(p);
        if (town == null) {
            // 如果没有领地，且不是点击圈地按钮，提示
            p.sendMessage("§c你还没有创建领地！请点击不死图腾图标进行圈地。");
            return;
        }

        if (clicked.getType() == Material.DIAMOND_SWORD) {
            TownyIntegration.togglePvp(town, p);
            safeSwitch(p, () -> SpiritGUI.openManagementMenu(p, spirit));
        } else if (clicked.getType() == Material.ZOMBIE_HEAD) {
            TownyIntegration.toggleMobs(town, p);
            safeSwitch(p, () -> SpiritGUI.openManagementMenu(p, spirit));
        } else if (clicked.getType() == Material.TNT) {
            TownyIntegration.toggleExplosion(town, p);
            safeSwitch(p, () -> SpiritGUI.openManagementMenu(p, spirit));
        } else if (clicked.getType() == Material.FLINT_AND_STEEL) {
            TownyIntegration.toggleFire(town, p);
            safeSwitch(p, () -> SpiritGUI.openManagementMenu(p, spirit));
        } else if (clicked.getType() == Material.OAK_SIGN) {
            p.closeInventory();
            editingBoard.add(p.getUniqueId());
            p.sendMessage("§e请在聊天栏输入新的公告内容 (输入 'cancel' 取消):");
        } else if (clicked.getType() == Material.NAME_TAG) { 
            p.closeInventory();
            editingTownName.put(p.getUniqueId(), spirit.getEntityId());
            p.sendMessage("§e请在聊天栏输入新的居所名称 (输入 'cancel' 取消):");
        } else if (clicked.getType() == Material.BARRIER) { 
            p.closeInventory();
            confirmingDelete.put(p.getUniqueId(), spirit.getEntityId());
            p.sendMessage("§c§l警告！你正在尝试废弃该居所！");
            p.sendMessage("§c这将删除所有领地保护！地灵将变为流浪状态。");
            p.sendMessage("§c请在聊天栏输入 'confirm' 确认删除，输入其他内容取消。");
        } else if (clicked.getType() == Material.IRON_SHOVEL) { 
            if (spirit.getMode() != SpiritEntity.SpiritMode.GUARDIAN) {
                p.closeInventory();
                p.sendMessage("§c地灵必须处于守护灵模式下才能进行此操作！(请将地灵带到目标区块并切换形态)");
                return;
            }
            // 检查地灵是否在脚下的区块
            if (spirit.getLocation().getChunk().getX() != p.getLocation().getChunk().getX() || 
                spirit.getLocation().getChunk().getZ() != p.getLocation().getChunk().getZ()) {
                 p.closeInventory();
                 p.sendMessage("§c请将地灵带到需要废弃的区块上！");
                 return;
            }
            
            p.closeInventory();
            if (TownyIntegration.unclaim(p)) {
                p.sendMessage("§a成功废弃了该地块！");
                p.playSound(p.getLocation(), Sound.BLOCK_GRASS_BREAK, 1f, 1f);
            } else {
                p.sendMessage("§c废弃失败！可能这不是你的领地，或者是核心区块。");
            }
        } else if (clicked.getType() == Material.PLAYER_HEAD) { 
            p.closeInventory();
            TownyIntegration.manageMembers(p);
        }
    }
    
    private void handleFeed(Player p, SpiritEntity spirit, ItemStack specificFood) {
        ItemStack foodToUse = null;
        if (specificFood != null && isFood(specificFood.getType())) {
            foodToUse = specificFood;
        } else {
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
        
        // 陌生人投喂处理
        if (!p.getUniqueId().equals(spirit.getOwnerId())) {
            boolean consumed = spirit.handleStrangerFeed(p);
            if (consumed) {
                foodToUse.setAmount(foodToUse.getAmount() - 1);
                p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EAT, 1, 1);
                plugin.getManager().saveData();
            }
            return;
        }

        boolean isHungry = spirit.isHungry(); // < 20%
        double mood = spirit.getMood();
        // int hungerValue = getFoodHungerValue(foodToUse.getType()); 
        foodToUse.setAmount(foodToUse.getAmount() - 1);
        
        // 投喂逻辑调整
        // 1. 饥饿状态 (isHungry)
        if (isHungry) {
            // 80% +5 mood, 20% +10 mood
            // 80% +5 exp, 20% +10 exp
            // +5 hunger
            boolean bonus = ThreadLocalRandom.current().nextDouble() < 0.2;
            double moodAdd = bonus ? 10 : 5;
            int expAdd = bonus ? 10 : 5;
            int hungerAdd = 5;
            
            spirit.addMood(moodAdd);
            spirit.addExp(expAdd);
            spirit.addHunger(hungerAdd);
            
            p.sendMessage("§e你喂食了" + spirit.getName() + "，它看起来很满足！");
            p.sendMessage("§f(心情 +" + (int)moodAdd + ", 经验 +" + expAdd + ", 饱食度 +" + hungerAdd + ")");
        } 
        // 2. 非饥饿，心情 < 80
        else if (mood < 80) {
            // 心情+1, 饱食度+1
            spirit.addMood(1);
            spirit.addHunger(1);
            
            p.sendMessage("§e你给" + spirit.getName() + " 喂了一些零食。(心情 +1, 饱食度 +1)");
        } 
        // 3. 非饥饿，心情 >= 80
        else {
            // 仅加1点饱食度
            spirit.addHunger(1);
            p.sendMessage("§e" + spirit.getName() + " 吃饱了，心情没有变化。(饱食度 +1)");
        }

        spirit.setExpression(SpiritSkinManager.Expression.HAPPY, 100);
        p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EAT, 1, 1);
        plugin.getManager().saveData();
    }

    private int getFoodHungerValue(Material m) {
        switch (m) {
            // High Tier (10+)
            case RABBIT_STEW: return 10;
            case CAKE: return 14; // Special case for whole cake
            
            // Meat & High Value (8)
            case COOKED_BEEF:
            case COOKED_PORKCHOP:
            case PUMPKIN_PIE:
                return 8;

            // Good Value (6)
            case COOKED_CHICKEN:
            case COOKED_MUTTON:
            case COOKED_SALMON:
            case GOLDEN_CARROT:
            case MUSHROOM_STEW:
            case BEETROOT_SOUP:
            case SUSPICIOUS_STEW:
                return 6;
                
            // Medium Value (5)
            case BREAD:
            case BAKED_POTATO:
            case COOKED_COD:
                return 5;
                
            // Fruit & Others (4)
            case APPLE:
            case GOLDEN_APPLE:
            case ENCHANTED_GOLDEN_APPLE:
            case ROTTEN_FLESH:
                return 4;
                
            // Raw Meat & Low Veg (3)
            case CARROT:
            case BEEF:
            case PORKCHOP:
            case RABBIT:
            case CHICKEN:
            case MUTTON: // Raw
                return 3;
                
            // Snacks (2)
            case COOKIE:
            case MELON_SLICE:
            case SWEET_BERRIES:
            case GLOW_BERRIES:
            case COD:
            case SALMON:
            case SPIDER_EYE:
                return 2;
                
            // Tiny (1)
            case DRIED_KELP:
            case POTATO:
            case BEETROOT:
            case TROPICAL_FISH:
            case PUFFERFISH:
                return 1;
                
            case HONEY_BOTTLE:
                return 6;
                
            default:
                // 如果是其他可食用物品 (模组物品等)，默认给2
                if (m.isEdible()) return 2;
                return 0;
        }
    }

    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        if (!(event.getEntity() instanceof Wolf)) return;
        
        SpiritEntity spirit = plugin.getManager().getSpiritByDriver(event.getEntity().getUniqueId());
        if (spirit == null) return;
        
        // 允许瞄准驯兽杖的诱饵
        if (event.getTarget() != null && spirit.getMoveTargetEntityId() != null && 
            event.getTarget().getUniqueId().equals(spirit.getMoveTargetEntityId())) {
            return; // Allow
        }
        
        Entity target = event.getTarget();
        if (target == null) return;

        // 检查目标原因
        switch (event.getReason()) {
            case TARGET_ATTACKED_OWNER:
            case OWNER_ATTACKED_TARGET:
            case DEFEND_VILLAGE: 
                // 仅允许攻击敌对生物 (Monster + 特殊敌对)
                if (target instanceof Monster || target instanceof Slime || target instanceof Phantom || 
                    target instanceof Shulker || target instanceof Ghast || target instanceof MagmaCube) {
                     return; // Allow
                }
                // 禁止攻击非敌对生物 (如羊、村民、甚至玩家 - 除非我们要支持PVP)
                // 用户要求：只有玩家攻击敌对生物，或被敌对生物攻击才帮忙。
                // 所以如果玩家打羊，OWNER_ATTACKED_TARGET 触发，但羊不是 Monster，这里会被取消。
                event.setCancelled(true);
                break;
            case CUSTOM: // 插件设置的目标 (如 Wand)
                return; // Allow
            default:
                // 其他情况 (如自动寻找最近的骷髅 CLOSEST_ENTITY, RANDOM_TARGET 等) -> 禁止
                // 这实现了“平常不主动攻击”
                event.setCancelled(true);
                break;
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player p = (Player) event.getEntity();
        
        // 检查是否在自己的城镇
        com.palmergames.bukkit.towny.object.Town town = TownyIntegration.getTownAt(p.getLocation());
        if (town == null) return;
        
        if (!TownyIntegration.isResident(town.getName(), p)) return;
        
        // 获取地灵
        SpiritEntity spirit = null;
        try {
             // 假设市长是主人，或者直接通过 Town 获取关联 Spirit (如果 SpiritEntity 有记录 townName)
             // 更好的方式是遍历所有 spirits 找到 townName 匹配的
             // 这里为了性能，我们假设 SpiritManager 可以缓存 Town->Spirit 映射，或者我们直接找玩家的 Spirit (如果他是主人)
             
             // 如果玩家是 Resident，他可能不是 Owner。
             // 我们需要找到这个 Town 的 Owner Spirit。
             // TownyIntegration.getTown(p) 获取的是 p 所属的 Town，但这里我们要获取 p 所在位置的 Town 的 Spirit。
             
             // 暂时通过市长查找
             com.palmergames.bukkit.towny.object.Resident mayor = town.getMayor();
             if (mayor != null) {
                 spirit = plugin.getManager().getSpiritByOwner(mayor.getUUID());
             }
        } catch (Exception e) {}
        
        if (spirit != null && spirit.getMode() == SpiritEntity.SpiritMode.GUARDIAN) {
            double mood = spirit.getMood();
            // 梯度加成:
            // 60-79: 减伤 10%
            // 80-89: 减伤 15%
            // 90-100: 减伤 20%
            
            double reduction = 0;
            if (mood >= 90) {
                reduction = 0.20;
            } else if (mood >= 80) {
                reduction = 0.15;
            } else if (mood >= 60) {
                reduction = 0.10;
            }
            
            if (reduction > 0) {
                double original = event.getDamage();
                event.setDamage(original * (1.0 - reduction));
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH, ignoreCancelled = false)
    public void onCropGrow(BlockGrowEvent event) {
        // 仅处理作物
        if (!(event.getNewState().getBlockData() instanceof Ageable)) return;
        
        // 检查是否被 BiomeGifts 处理过 (跳级生长)
        // 如果事件被取消，我们需要判断是因为"贫瘠惩罚"还是"富饶奖励"
        boolean isBonus = false;
        if (event.isCancelled()) {
            // 检查当前方块的年龄是否已经超过了事件原本要变成的年龄
            // 如果是，说明 BiomeGifts (或其他插件) 已经手动跳级了
            if (event.getBlock().getBlockData() instanceof Ageable worldAge && 
                event.getNewState().getBlockData() instanceof Ageable proposedAge) {
                if (worldAge.getAge() > proposedAge.getAge()) {
                    isBonus = true;
                } else {
                    return; // 是惩罚或普通取消，不进行地灵加成
                }
            } else {
                return;
            }
        }
        
        com.palmergames.bukkit.towny.object.Town town = TownyIntegration.getTownAt(event.getBlock().getLocation());
        if (town == null) return;
        
        SpiritEntity spirit = null;
        try {
             com.palmergames.bukkit.towny.object.Resident mayor = town.getMayor();
             if (mayor != null) {
                 spirit = plugin.getManager().getSpiritByOwner(mayor.getUUID());
             }
        } catch (Exception e) {}
        
        if (spirit != null && spirit.getMode() == SpiritEntity.SpiritMode.GUARDIAN) {
            // 心情 >= 90: 作物生长加速 (独立乘区)
            if (spirit.getMood() >= 90) {
                // 10% 概率触发跳级生长
                if (ThreadLocalRandom.current().nextDouble() < 0.1) {
                    // 获取当前实际的方块数据
                    Ageable ageable = (Ageable) event.getBlock().getBlockData();
                    
                    // 如果事件没被取消，且我们想加成，我们是基于"即将变成的状态"还是"当前状态"?
                    // 如果事件没被取消，系统稍后会将 newState 应用。
                    // 我们想要的是效果叠加。
                    // 1. 如果 BiomeGifts 没触发 (Normal)，事件正常，newState = Age+1。我们想变成 Age+2。
                    // 2. 如果 BiomeGifts 触发 (Bonus)，事件取消，World = Age+2。我们想变成 Age+3。
                    
                    if (!event.isCancelled()) {
                        // 基于 newState 修改
                        Ageable proposed = (Ageable) event.getNewState().getBlockData();
                        if (proposed.getAge() < proposed.getMaximumAge()) {
                            proposed.setAge(proposed.getAge() + 1);
                            event.getNewState().setBlockData(proposed);
                            // 播放特效
                            event.getBlock().getWorld().spawnParticle(Particle.HEART, event.getBlock().getLocation().add(0.5, 0.5, 0.5), 3, 0.3, 0.3, 0.3);
                        }
                    } else if (isBonus) {
                        // 基于 World Block 修改 (因为事件已取消，我们只能手动再改一次)
                        if (ageable.getAge() < ageable.getMaximumAge()) {
                            ageable.setAge(ageable.getAge() + 1);
                            event.getBlock().setBlockData(ageable);
                            // 播放特效
                            event.getBlock().getWorld().spawnParticle(Particle.HEART, event.getBlock().getLocation().add(0.5, 0.5, 0.5), 3, 0.3, 0.3, 0.3);
                        }
                    }
                }
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;
        if (!BiomeGiftsHelper.isEnabled()) return;
        
        com.palmergames.bukkit.towny.object.Town town = TownyIntegration.getTownAt(event.getBlock().getLocation());
        if (town == null) return;
        
        SpiritEntity spirit = null;
        try {
             com.palmergames.bukkit.towny.object.Resident mayor = town.getMayor();
             if (mayor != null) {
                 spirit = plugin.getManager().getSpiritByOwner(mayor.getUUID());
             }
        } catch (Exception e) {}
        
        if (spirit != null && spirit.getMode() == SpiritEntity.SpiritMode.GUARDIAN) {
            if (spirit.getMood() >= 90) {
                 try {
                     Object config = BiomeGiftsHelper.getCropConfig(event.getBlock().getType());
                     boolean isCrop = (config != null);
                     
                     if (config == null) {
                         config = BiomeGiftsHelper.getOreConfig(event.getBlock().getType());
                     }
                     
                     if (config != null) {
                         // 如果是作物配置，必须检查是否成熟
                         if (isCrop) {
                             if (event.getBlock().getBlockData() instanceof Ageable ageable) {
                                 if (ageable.getAge() != ageable.getMaximumAge()) return;
                             } else {
                                 // 如果配置了作物但方块不是可生长的，忽略
                                 return;
                             }
                         }
                         
                         Class<?> configClass = config.getClass();
                         double baseChance = configClass.getField("baseChance").getDouble(config);
                         double richMultiplier = configClass.getField("richMultiplier").getDouble(config);
                         double poorMultiplier = configClass.getField("poorMultiplier").getDouble(config);
                         String dropItemName = (String) configClass.getField("dropItem").get(config);
                         
                         double currentChance = baseChance;
                         String biomeKey = event.getBlock().getWorld().getBiome(event.getBlock().getLocation()).getKey().toString();
                         Method getBiomeType = configClass.getMethod("getBiomeType", String.class);
                         Object biomeTypeObj = getBiomeType.invoke(config, biomeKey);
                         
                         if (biomeTypeObj != null) {
                             String typeName = ((Enum<?>)biomeTypeObj).name();
                             if ("RICH".equals(typeName)) {
                                 currentChance *= richMultiplier;
                             } else if ("POOR".equals(typeName)) {
                                 currentChance *= poorMultiplier;
                             } else {
                                 currentChance *= 0.5; 
                             }
                         }
                         
                         // 独立乘区：额外增加 10% 的掉落概率 (相对于当前概率)
                         double spiritChance = currentChance * 0.1;
                         
                         if (ThreadLocalRandom.current().nextDouble() < spiritChance) {
                             ItemStack item = BiomeGiftsHelper.getItem(dropItemName);
                             if (item != null) {
                                 event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), item);
                                 event.getBlock().getWorld().spawnParticle(Particle.HEART, event.getBlock().getLocation().add(0.5, 0.5, 0.5), 3, 0.3, 0.3, 0.3);
                             }
                         }
                     }
                 } catch (Exception e) {
                     // Reflection error, ignore
                 }
            }
        }
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

        if (editingName.containsKey(uuid)) {
            event.setCancelled(true);
            UUID spiritId = editingName.remove(uuid);
            
            // 切换到主线程执行名字更新，确保实体元数据同步成功
            Bukkit.getScheduler().runTask(plugin, () -> {
                SpiritEntity spirit = plugin.getManager().getSpirit(spiritId);
                if (spirit != null) {
                    // 统一添加颜色代码，保持风格一致
                    String newName = "§a" + msg.replace("&", "§");
                    spirit.setName(newName);
                    plugin.getManager().saveData();
                    p.sendMessage("§a地灵的名字已更新为: " + newName);
                } else {
                    p.sendMessage("§c找不到地灵，改名失败。");
                }
            });
            return;
        }

        if (editingTownName.containsKey(uuid)) {
            event.setCancelled(true);
            UUID spiritId = editingTownName.remove(uuid);
            if (msg.equalsIgnoreCase("cancel")) {
                p.sendMessage("§c已取消操作。");
                return;
            }
            
            SpiritEntity spirit = plugin.getManager().getSpirit(spiritId);
            if (spirit != null) {
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
        
        if (confirmingDelete.containsKey(uuid)) {
            event.setCancelled(true);
            UUID spiritId = confirmingDelete.remove(uuid);
            
            if (msg.equalsIgnoreCase("confirm")) {
                 SpiritEntity spirit = plugin.getManager().getSpirit(spiritId);
                 if (spirit != null) {
                     Bukkit.getScheduler().runTask(plugin, () -> {
                         boolean success = TownyIntegration.deleteTown(p);
                         if (success) {
                             // 删除居所，但不删除地灵，地灵变为流浪
                             spirit.setTownName(null);
                             plugin.getManager().saveData();
                             p.sendMessage("§a居所已废弃，地灵已解除绑定。");
                         } else {
                             p.sendMessage("§c废弃失败！请联系管理员。");
                         }
                     });
                 }
            } else {
                p.sendMessage("§c操作已取消。");
            }
            return;
        }

        if (confirmingRelease.containsKey(uuid)) {
            event.setCancelled(true);
            UUID spiritId = confirmingRelease.remove(uuid);
            
            if (msg.equalsIgnoreCase("release")) {
                 SpiritEntity spirit = plugin.getManager().getSpirit(spiritId);
                 if (spirit != null) {
                     Bukkit.getScheduler().runTask(plugin, () -> {
                         spirit.remove(); // 移除实体
                         plugin.getManager().removeSpirit(p.getUniqueId()); // 移除数据
                         p.sendMessage("§c你解除了契约，地灵化作光点消散了...");
                         p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
                     });
                 }
            } else {
                p.sendMessage("§c操作已取消。");
            }
            return;
        }
    }

    private void handleInteraction(Player p, SpiritEntity data) {
        boolean isOwner = p.getUniqueId().equals(data.getOwnerId());
        boolean isResident = TownyIntegration.isResident(data.getTownName(), p);
        
        if (!isOwner && !isResident) {
            return;
        }

        long now = System.currentTimeMillis();
        
        // 每日凌晨4点刷新 (与嘴馋清单统一)
        LocalDateTime nowTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault());
        LocalDateTime lastTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(data.getLastInteractTime()), ZoneId.systemDefault());
        
        // 将时间平移4小时，这样凌晨4点就变成了"0点" (日期变更线)
        LocalDateTime adjustedNow = nowTime.minusHours(4);
        LocalDateTime adjustedLast = lastTime.minusHours(4);
        
        boolean isNewDay = data.getLastInteractTime() == 0 || adjustedNow.toLocalDate().isAfter(adjustedLast.toLocalDate());
        
        if (isNewDay) { 
            data.interact();
            
            // 独立随机概率:
            // 心情: 80% +10, 20% +20
            // 经验: 80% +10, 20% +20
            double moodAdd = ThreadLocalRandom.current().nextDouble() < 0.2 ? 20 : 10;
            int expAdd = ThreadLocalRandom.current().nextDouble() < 0.2 ? 20 : 10;

            data.addMood(moodAdd); 
            data.addExp(expAdd); 
            data.setExpression(SpiritSkinManager.Expression.HAPPY, 60); 
            p.sendMessage("§d你温柔地抚摸了 " + data.getName() + " 的头。");
            p.sendMessage("§d" + data.getName() + " 蹭了蹭你的手，心情变好了！(心情 +" + (int)moodAdd + ", 经验 +" + expAdd + ")");
            p.playSound(p.getLocation(), Sound.ENTITY_CAT_PURR, 1, 1);
            plugin.getManager().saveData();
        } else {
             // 计算距离下一个凌晨4点的时间
             LocalDateTime nextReset;
             if (nowTime.getHour() < 4) {
                 // 如果现在是凌晨0-3点，下一个4点就是今天
                 nextReset = nowTime.toLocalDate().atTime(4, 0);
             } else {
                 // 否则是明天
                 nextReset = nowTime.toLocalDate().plusDays(1).atTime(4, 0);
             }
             
             long seconds = java.time.Duration.between(nowTime, nextReset).getSeconds();
             long hours = seconds / 3600;
             long minutes = (seconds % 3600) / 60;
             
             String timeStr = "";
             if (hours > 0) timeStr += hours + "小时";
             timeStr += minutes + "分钟";
             
             p.sendMessage("§7" + data.getName() + " 看起来很享受你的陪伴。");
             p.sendMessage("§7(今天已经抚摸过了，请等待 " + timeStr + " 后再来)");
             p.playSound(p.getLocation(), Sound.ENTITY_CAT_AMBIENT, 0.5f, 1f);
        }
    }
    
    private boolean isFood(Material m) {
        return m.isEdible();
    }
}
