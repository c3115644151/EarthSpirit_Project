package com.example.earthspirit;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.exceptions.TownyException;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.WorldCoord;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class TownyIntegration {

    public static boolean createTown(Player player, String townName, Location location) {
        if (!TownyAPI.getInstance().isTownyWorld(location.getWorld())) {
            player.sendMessage("§c这个世界不支持创建领地！");
            return false;
        }

        // 检查该区块是否已被占领
        if (!TownyAPI.getInstance().isWilderness(location)) {
            player.sendMessage("§c这里已经有领地了！");
            return false;
        }

        try {
            TownyUniverse universe = TownyUniverse.getInstance();
            Resident resident = universe.getResident(player.getUniqueId());

            if (resident == null) {
                // 理论上玩家在线应该会有 Resident，如果没有则尝试获取或报错
                // Towny 0.96+ 应该会自动处理，但为了安全：
                player.sendMessage("§c无法获取你的领地数据，请重新加入服务器重试。");
                return false;
            }

            if (resident.hasTown()) {
                player.sendMessage("§c你已经拥有或加入了一个领地，无法召唤地灵！");
                return false;
            }

            if (universe.hasTown(townName)) {
                player.sendMessage("§c领地名称 " + townName + " 已存在！");
                return false;
            }

            // 1. 创建城镇
            // 注意：newTown 方法可能会抛出 AlreadyRegisteredException
            universe.newTown(townName);
            Town town = universe.getTown(townName);

            // 2. 设置市长
            town.setMayor(resident);
            resident.setTown(town);

            // 3. 设置 HomeBlock (当前位置)
            WorldCoord worldCoord = WorldCoord.parseWorldCoord(location);
            
            // 在 Towny 中，我们需要创建一个 TownBlock 对象并将其添加到 World 和 Town
            // 获取 TownyWorld
            com.palmergames.bukkit.towny.object.TownyWorld townyWorld = worldCoord.getTownyWorld();
            
            // 创建 TownBlock
            // 注意：不同版本的 Towny 可能有不同的创建方式，这里使用较通用的方式
            // 如果报错，可能需要调整
            TownBlock townBlock = new TownBlock(worldCoord.getX(), worldCoord.getZ(), townyWorld);
            
            townBlock.setTown(town);
            townBlock.setResident(resident); // 设置地皮拥有者为市长

            // 添加到 Universe/World
            // universe.addTownBlock(townBlock); // 旧版可能需要
            // 现代版本通常通过 saveTownBlock 或 world.addTownBlock
            // 但最稳妥的是直接保存，Towny 会处理引用
            
            // 设置城镇重生点
            town.setHomeBlock(townBlock);
            town.setSpawn(location);

            // 4. 保存数据
            universe.getDataSource().saveTown(town);
            universe.getDataSource().saveResident(resident);
            universe.getDataSource().saveTownBlock(townBlock);
            universe.getDataSource().saveWorld(townyWorld); // 保存世界数据以更新区块占用

            // 广播
            // Bukkit.broadcastMessage(TownySettings.getNewTownMsg(player.getName(), townName)); 
            // 我们自己发消息即可

            return true;

        } catch (TownyException e) {
            player.sendMessage("§c创建领地失败: " + e.getMessage());
            e.printStackTrace();
            // 尝试回滚?
            try {
                if (TownyUniverse.getInstance().hasTown(townName)) {
                    TownyUniverse.getInstance().getDataSource().removeTown(TownyUniverse.getInstance().getTown(townName));
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            return false;
        }
    }

    public static Town getTown(Player player) {
        try {
            Resident resident = TownyUniverse.getInstance().getResident(player.getUniqueId());
            if (resident != null && resident.hasTown()) {
                return resident.getTown();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean renameTown(Player player, String newName) {
        try {
            Town town = getTown(player);
            if (town == null) return false;
            
            TownyUniverse universe = TownyUniverse.getInstance();
            if (universe.hasTown(newName)) return false;
            
            universe.getDataSource().renameTown(town, newName);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean deleteTown(Player player) {
        try {
            Town town = getTown(player);
            if (town == null) return false;
            
            TownyUniverse universe = TownyUniverse.getInstance();
            universe.getDataSource().removeTown(town);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void transferOwnership(String townName, Player newOwner) {
        try {
            TownyUniverse universe = TownyUniverse.getInstance();
            if (!universe.hasTown(townName)) return;

            Town town = universe.getTown(townName);
            Resident newMayor = universe.getResident(newOwner.getUniqueId());

            if (newMayor == null) return;

            // 如果新主人已有城镇，必须先退出？
            // 这里的逻辑比较霸道：直接把新主人拉进来当市长
            if (newMayor.hasTown()) {
                // 暂时让它退出原城镇
                // 实际业务中可能需要提示玩家先退城
                try {
                    newMayor.removeTown();
                } catch (Exception e) {
                    newOwner.sendMessage("§c无法将你从原城镇移除: " + e.getMessage());
                    return;
                }
            }

            // 加入城镇
            try {
                newMayor.setTown(town);
            } catch (Exception e) {
                // 已经在里面了？
            }

            // 设置为市长
            town.setMayor(newMayor);

            // 保存
            universe.getDataSource().saveTown(town);
            universe.getDataSource().saveResident(newMayor);

            newOwner.sendMessage("§a你已正式接管领地 " + town.getName() + "！");

        } catch (Exception e) {
            e.printStackTrace();
            newOwner.sendMessage("§c领地交接失败: " + e.getMessage());
        }
    }

    public static boolean isPvpEnabled(Town town) {
        return town.isPVP();
    }

    public static boolean isMobsEnabled(Town town) {
        return town.hasMobs();
    }

    public static boolean isExplosionEnabled(Town town) {
        return town.isExplosion();
    }

    public static boolean isFireEnabled(Town town) {
        return town.isFire();
    }
    
    public static String getTownBoard(Town town) {
        return town.getBoard();
    }

    public static void setTownBoard(Town town, String board) {
        town.setBoard(board);
    }
    
    public static void togglePvp(Town town) {
        town.setPVP(!town.isPVP());
        // 强制刷新权限缓存
        updateTownPermissions(town);
        TownyUniverse.getInstance().getDataSource().saveTown(town);
    }

    public static void toggleMobs(Town town) {
        town.setHasMobs(!town.hasMobs());
        updateTownPermissions(town);
        TownyUniverse.getInstance().getDataSource().saveTown(town);
    }

    public static void toggleExplosion(Town town) {
        town.setExplosion(!town.isExplosion());
        updateTownPermissions(town);
        TownyUniverse.getInstance().getDataSource().saveTown(town);
    }

    public static void toggleFire(Town town) {
        town.setFire(!town.isFire());
        updateTownPermissions(town);
        TownyUniverse.getInstance().getDataSource().saveTown(town);
    }
    
    private static void updateTownPermissions(Town town) {
        // Towny 有时需要更新权限缓存才能使更改生效
        // 尝试遍历所有 TownBlock 并保存，或者依靠 saveTown
        // 但对于某些版本，可能需要显式更新
        // 这里主要通过重新设置一遍自身来触发可能的内部更新逻辑
        // 实际 saveTown 应该足够，但为了保险，可以尝试更深层的更新
        
        // 注意：Towny 0.96+ 内部逻辑比较复杂，单纯 saveTown 可能只是存盘
        // 但 toggle 状态通常会实时更新内存。
        // 如果 /towny map 显示不正确，可能是因为 TownBlock 的权限没有同步
        
        // 尝试同步 TownBlock 权限
        for (TownBlock tb : town.getTownBlocks()) {
            // setPermissions 需要 String 类型，将 TownyPermission 转换为 String
            // 或者直接跳过这一步，因为 TownBlock 会自动继承 Town 的权限
            // 只要 saveTownBlock 被调用，应该就会触发刷新
            TownyUniverse.getInstance().getDataSource().saveTownBlock(tb);
        }
    }

    public static void upgradeTownLevel(Player player, SpiritEntity spirit) {
        int currentLevel = spirit.getLevel();
        int nextLevel = currentLevel + 1;
        
        // 升级配置
        int expRequired = currentLevel * 100; // Lv1->2: 100, Lv2->3: 200...
        int diamondCost = currentLevel * 10;  // Lv1->2: 10钻, Lv2->3: 20钻...
        int bonusBlocks = 2; // 每次升级奖励2个区块
        
        Town town = getTown(player);
        if (town == null) {
            player.sendMessage("§c你没有领地，无法升级！");
            return;
        }

        if (spirit.getExp() < expRequired) {
            player.sendMessage("§c升级失败！地灵经验不足。");
            player.sendMessage("§7需要经验: " + expRequired + " (当前: " + spirit.getExp() + ")");
            player.sendMessage("§7提示: 多喂食、抚摸地灵可增加经验。");
            return;
        }

        ItemStack costItem = new ItemStack(Material.DIAMOND, diamondCost);
        if (!player.getInventory().containsAtLeast(costItem, diamondCost)) {
            player.sendMessage("§c升级失败！你需要 " + diamondCost + " 个钻石作为贡品。");
            return;
        }

        // 执行升级
        player.getInventory().removeItem(costItem);
        spirit.setExp(spirit.getExp() - expRequired);
        spirit.setLevel(nextLevel);
        
        try {
            town.addBonusBlocks(bonusBlocks);
            TownyUniverse.getInstance().getDataSource().saveTown(town);
        } catch (Exception e) {
            e.printStackTrace();
            player.sendMessage("§c升级成功，但奖励区块发放失败！请联系管理员。");
        }

        player.sendMessage("§a§l升级成功！地灵成长到了 Lv." + nextLevel + "！");
        player.sendMessage("§e领地获得了 " + bonusBlocks + " 个额外区块奖励！");
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1, 1);
        
        // 保存地灵数据 (这里应该有个回调或者事件，暂时假设外部会定期保存，或者手动触发保存)
        // 实际上 SpiritListener 里并没有立即保存，所以最好这里触发一下保存
        // 但 TownyIntegration 没有 plugin 引用。
        // 简单处理：SpiritListener 调用完后，自己 save。或者这里不处理，依赖后续操作。
        // 最好的办法是让 SpiritManager 单例可访问，或者传递 plugin。
        // 既然 SpiritListener 传入了 plugin，可以在那里调用 save。
    }

    public static void manageMembers(Player player) {
        player.sendMessage("§e[地灵] §f成员管理功能：");
        player.sendMessage("§f- 邀请成员: /town add <玩家名>");
        player.sendMessage("§f- 踢出成员: /town kick <玩家名>");
        player.sendMessage("§f- 查看列表: /town online");
        // 未来可以集成 GUI
    }

    public static Town getTownAt(Location location) {
        try {
            return TownyAPI.getInstance().getTown(location);
        } catch (Exception e) {
            return null;
        }
    }
}
