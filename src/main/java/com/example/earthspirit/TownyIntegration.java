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

public class TownyIntegration {

    @SuppressWarnings("deprecation")
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

    @SuppressWarnings("deprecation")
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

    public static boolean unclaim(Player player) {
        try {
            Town town = getTown(player);
            if (town == null) return false;

            TownyUniverse universe = TownyUniverse.getInstance();
            TownBlock townBlock = universe.getTownBlock(WorldCoord.parseWorldCoord(player.getLocation()));
            
            if (townBlock == null || !townBlock.hasTown() || !townBlock.getTown().equals(town)) {
                return false; // Not in own town
            }
            
            // Check if it's the home block
            if (town.hasHomeBlock() && town.getHomeBlock().equals(townBlock)) {
                player.sendMessage("§c你不能废弃城镇的核心区块！如果想解散城镇，请使用“废弃居所”。");
                return false;
            }

            universe.getDataSource().removeTownBlock(townBlock);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean claimBlock(Player player) {
        try {
            Town town = getTown(player);
            if (town == null) return false;

            if (!TownyAPI.getInstance().isWilderness(player.getLocation())) {
                player.sendMessage("§c这里已经被占领了！");
                return false;
            }

            TownyUniverse universe = TownyUniverse.getInstance();
            WorldCoord worldCoord = WorldCoord.parseWorldCoord(player.getLocation());
            com.palmergames.bukkit.towny.object.TownyWorld townyWorld = worldCoord.getTownyWorld();
            
            TownBlock townBlock = new TownBlock(worldCoord.getX(), worldCoord.getZ(), townyWorld);
            townBlock.setTown(town);
            townBlock.setResident(town.getMayor()); // 归市长所有

            universe.getDataSource().saveTownBlock(townBlock);
            // universe.getDataSource().saveTown(town); // 通常不需要显式保存 Town，但为了更新缓存可以加上

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            player.sendMessage("§c圈地失败: " + e.getMessage());
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
    
    public static void togglePvp(Town town, Player player) {
        player.performCommand("town toggle pvp");
        updateTownPermissions(town);
    }

    public static void toggleMobs(Town town, Player player) {
        player.performCommand("town toggle mobs");
        updateTownPermissions(town);
    }

    public static void toggleExplosion(Town town, Player player) {
        player.performCommand("town toggle explosion");
        updateTownPermissions(town);
    }

    public static void toggleFire(Town town, Player player) {
        player.performCommand("town toggle fire");
        updateTownPermissions(town);
    }
    
    private static void updateTownPermissions(Town town) {
        // 同步所有 TownBlock 的权限状态
        for (TownBlock tb : town.getTownBlocks()) {
            // 显式同步 Town 的权限设置到 TownBlock
            // 注意：TownBlock 的权限通常是独立的，但在“同步”模式下，应该跟随 Town
            // 如果 Towny 版本较新，可能需要直接操作 Permissions 对象
            
            // 尝试直接设置 TownBlock 的状态位 (如果有对应 API)
            // 遗憾的是 TownBlock API 并没有直接的 setHasMobs 等方法，它们通常存储在 Permissions 或 Metadata 中
            
            // 关键：将 TownBlock 的 Permissions 重置为与 Town 一致
            // 通过 setPermissions 触发更新，但需要正确的参数
            // 这里我们采取一个更通用的策略：让 TownBlock 知道它需要更新
            
            // 强制保存 TownBlock 会触发一些内部同步
            tb.getPermissions().pvp = town.isPVP();
            tb.getPermissions().fire = town.isFire();
            tb.getPermissions().explosion = town.isExplosion();
            tb.getPermissions().mobs = town.hasMobs();
            
            // 保存更改
            TownyUniverse.getInstance().getDataSource().saveTownBlock(tb);
        }
    }

    public static void manageMembers(Player player) {
        player.sendMessage("§e[地灵] §f成员管理功能：");
        player.sendMessage("§f- 邀请成员: /town add <玩家名>");
        player.sendMessage("§f- 踢出成员: /town kick <玩家名>");
        player.sendMessage("§f- 查看列表: /town online");
        // 未来可以集成 GUI
    }

    public static boolean isResident(String townName, Player player) {
        try {
            Town town = TownyUniverse.getInstance().getTown(townName);
            if (town == null) return false;
            return town.hasResident(player.getName());
        } catch (Exception e) {
            return false;
        }
    }

    public static Town getTownAt(Location location) {
        try {
            return TownyAPI.getInstance().getTown(location);
        } catch (Exception e) {
            return null;
        }
    }
}
