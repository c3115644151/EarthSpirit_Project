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
import java.util.List;

public class TownyIntegration {

    @SuppressWarnings("deprecation")
    public static boolean createTown(Player player, String townName, Location location) {
        if (!TownyAPI.getInstance().isTownyWorld(location.getWorld())) {
            player.sendMessage("§c这个世界不支持创建居所！");
            return false;
        }

        // 检查该区块是否已被占领
        if (!TownyAPI.getInstance().isWilderness(location)) {
            player.sendMessage("§c这里已经有居所了！");
            return false;
        }

        try {
            TownyUniverse universe = TownyUniverse.getInstance();
            Resident resident = universe.getResident(player.getUniqueId());

            if (resident == null) {
                // 理论上玩家在线应该会有 Resident，如果没有则尝试获取或报错
                // Towny 0.96+ 应该会自动处理，但为了安全：
                player.sendMessage("§c无法获取你的居所数据，请重新加入服务器重试。");
                return false;
            }

            if (resident.hasTown()) {
                player.sendMessage("§c你已经拥有或加入了一个居所，无法召唤地灵！");
                return false;
            }

            if (universe.hasTown(townName)) {
                player.sendMessage("§c居所名称 " + townName + " 已存在！");
                return false;
            }

            // 1. 创建城镇
            // 注意：newTown 方法可能会抛出 AlreadyRegisteredException
            universe.newTown(townName);
            Town town = universe.getTown(townName);

            // 1.1 设置为私有 (禁止他人加入)
            town.setPublic(false);
            town.setOpen(false); // Towny usually uses setOpen(false) to close joining

            // 2. 设置市长 (关键修复：先添加居民，再设为市长)
            // 某些版本的 Towny 需要居民先存在于城镇中
            try {
                resident.setTown(town);
            } catch (Exception e) {
                // Ignore if already added
            }
            town.setMayor(resident);

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
            player.sendMessage("§c创建居所失败: " + e.getMessage());
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
            townBlock.setResident(getMayor(town)); // 归市长所有

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

            newOwner.sendMessage("§a你已正式接管居所 " + town.getName() + "！");

        } catch (Exception e) {
            e.printStackTrace();
            newOwner.sendMessage("§c居所交接失败: " + e.getMessage());
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
    
    public static void updateTownPermissions(Town town) {
        // 尝试获取该居所关联的地灵
        // 假设市长就是地灵的主人
        SpiritEntity spirit = null;
        try {
            Resident mayor = getMayor(town);
            if (mayor != null) {
                spirit = EarthSpiritPlugin.getInstance().getManager().getSpiritByOwner(mayor.getUUID());
            }
        } catch (Exception e) {
            // ignore
        }
        
        updateTownPermissions(town, spirit);
    }

    public static void updateTownPermissions(Town town, SpiritEntity spirit) {
        if (town == null || spirit == null) return;
        
        // 自动管理权限:
        // 1. 关闭 PVP
        town.setPVP(false);
        // 2. 开启/关闭 爆炸 (守护:关, 旅伴:开)
        town.setExplosion(spirit.getMode() != SpiritEntity.SpiritMode.GUARDIAN);
        // 3. 开启/关闭 火蔓延 (守护:关, 旅伴:开)
        town.setFire(spirit.getMode() != SpiritEntity.SpiritMode.GUARDIAN);
        // 4. 怪物生成 (守护:关, 旅伴:开)
        town.setHasMobs(spirit.getMode() != SpiritEntity.SpiritMode.GUARDIAN);


        // 保存更改
        TownyUniverse.getInstance().getDataSource().saveTown(town);
    }

    // --- Whitelist / Partner Helpers ---

    public static boolean addTrusted(Town town, String playerName) {
        try {
            Resident r = TownyUniverse.getInstance().getResident(playerName);
            if (r == null) return false;
            r.setTown(town);
            TownyUniverse.getInstance().getDataSource().saveTown(town);
            TownyUniverse.getInstance().getDataSource().saveResident(r);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean removeTrusted(Town town, String playerName) {
        try {
            Resident r = TownyUniverse.getInstance().getResident(playerName);
            if (r == null) return false;
            r.setTown(null); // Remove from town
            TownyUniverse.getInstance().getDataSource().saveTown(town);
            TownyUniverse.getInstance().getDataSource().saveResident(r);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static List<Resident> getResidents(Town town) {
        return town.getResidents();
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

    // Duplicate renameTown removed


    public static Town getTownAt(Location location) {
        try {
            return TownyAPI.getInstance().getTown(location);
        } catch (Exception e) {
            return null;
        }
    }

    public static SpiritEntity getTownSpirit(Town town, EarthSpiritPlugin plugin) {
        if (town == null) return null;
        
        // 1. Try Mayor first (Fastest)
        try {
            Resident mayor = town.getMayor();
            if (mayor != null) {
                SpiritEntity spirit = plugin.getManager().getSpiritByOwner(mayor.getUUID());
                if (spirit != null) return spirit;
            }
        } catch (Exception e) {}

        // 2. Fallback: Iterate all residents to find who owns the spirit for this town
        // This handles cases where:
        // - Mayor data is corrupted (null)
        // - We want to support "Co-Owner" logic implicitly (if we decide to allow partner's spirit to work, though user said "original creator's spirit")
        // - User said: "默认创建居所的那个玩家的地灵作为领地的加成判定" -> The spirit that has this townName recorded.
        try {
            List<Resident> residents = town.getResidents();
            if (residents != null) {
                for (Resident r : residents) {
                    SpiritEntity s = plugin.getManager().getSpiritByOwner(r.getUUID());
                    if (s != null && town.getName().equals(s.getTownName())) {
                        return s;
                    }
                }
            }
        } catch (Exception e) {}
        
        return null;
    }

    public static Resident getMayor(Town town) {
        try {
            Resident mayor = town.getMayor();
            if (mayor != null) return mayor;
            
            // Fallback 1: re-fetch town from universe
            if (TownyUniverse.getInstance().hasTown(town.getName())) {
                 Town uTown = TownyUniverse.getInstance().getTown(town.getName());
                 if (uTown != null) {
                     mayor = uTown.getMayor();
                     if (mayor != null) return mayor;
                 }
            }

            // Fallback 2: Check residents (Deep Search)
            List<Resident> residents = town.getResidents();
            if (residents != null && !residents.isEmpty()) {
                // 2.1: If only one resident, they MUST be the mayor
                if (residents.size() == 1) {
                    return residents.get(0);
                }
                
                // 2.2: Check if any resident name is contained in town name (Heuristic)
                // e.g. "cy311's Town" -> "cy311"
                String tName = town.getName();
                for (Resident r : residents) {
                     if (tName.contains(r.getName())) {
                         return r;
                     }
                }

                // 2.3: Return the first resident as a last resort
                // This ensures we at least get a valid Player UUID to check for Spirit ownership
                return residents.get(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
