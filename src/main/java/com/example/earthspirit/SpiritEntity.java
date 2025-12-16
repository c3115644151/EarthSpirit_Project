package com.example.earthspirit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
// import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;

public class SpiritEntity {
    private UUID entityId; // 实体UUID (ArmorStand)
    private UUID ownerId;  // 主人UUID
    private String name;   // 地灵名字
    private String townName; // 居所名称 (null 表示流浪)
    private double mood;      // 心情值 (0-100)
    private int level = 1;    // 等级
    private int exp = 0;      // 经验值

    // 状态
    private SpiritMode mode;
    private SpiritType type = SpiritType.NORMAL;
    private String inventoryData; // 序列化后的背包数据
    private transient SpiritInventory inventory;
    
    // 过继系统
    private Map<UUID, Integer> strangerFeedDays; // 记录陌生人连续投喂的天数
    private Map<UUID, Long> lastFeedTime;        // 上次投喂时间
    
    // 运动与物理
    private Location currentLocation;
    // private transient Vector velocity; // Removed unused
    // private transient double bobbingOffset; // Removed unused
    
    // 动态光源缓存
    private transient Location lastLightLocation;
    private transient long lastLightPacketTime;
    private transient Location lastParticleLocation;
    
    // 寻路控制
    private transient Location manualTarget;
    private transient UUID moveTargetEntityId; // 隐形诱饵实体ID

    // 动画状态
    private UUID chassisEntityId; // 底盘实体ID (Guardian模式)
    private UUID driverId; // 驱动实体ID (Wolf)
    
    // 交互与状态
    private long lastFoodTime;
    private long lastInteractTime;
    private long lastMoodUpdateTime; // 记录上次更新心情的现实时间
    private transient SpiritSkinManager.Expression currentExpression;
    private transient long expressionEndTime;

    public enum SpiritMode {
        COMPANION("旅伴"), 
        GUARDIAN("守护灵");
        
        private final String displayName;
        SpiritMode(String displayName) {
            this.displayName = displayName;
        }
        public String getDisplayName() {
            return displayName;
        }
    }

    public enum SpiritType {
        NORMAL, FLOWER, MINER, SAD
    }

    public SpiritEntity(UUID ownerId, String name, Location spawnLocation) {
        this.ownerId = ownerId;
        this.name = name;
        this.mood = 60.0;
        this.mode = SpiritMode.COMPANION;
        this.inventory = new SpiritInventory(name + " 的背包");
        this.strangerFeedDays = new HashMap<>();
        this.lastFeedTime = new HashMap<>();
        
        this.currentLocation = spawnLocation.clone();
        this.lastMoodUpdateTime = System.currentTimeMillis();
        initTransientFields();
        
        spawnEntity(spawnLocation);
    }
    
    // 反序列化后初始化
    public void initAfterLoad() {
        initTransientFields();
        if (lastMoodUpdateTime == 0) {
            lastMoodUpdateTime = System.currentTimeMillis();
        }
        if (inventoryData != null && !inventoryData.isEmpty()) {
            this.inventory = SpiritInventory.fromBase64(inventoryData, name + " 的背包");
        } else {
            this.inventory = new SpiritInventory(name + " 的背包");
        }
    }
    
    // 保存前准备
    public void prepareSave() {
        if (inventory != null) {
            this.inventoryData = inventory.toBase64();
        }
    }

    private void initTransientFields() {
        // this.velocity = new Vector(0, 0, 0);
        // this.bobbingOffset = 0;
        this.currentExpression = SpiritSkinManager.Expression.NORMAL;
        this.lastParticleLocation = null;
        this.lastLightLocation = null;
        this.lastLightPacketTime = 0;
    }

    public void summon(Location loc) {
        if (this.entityId != null && Bukkit.getEntity(this.entityId) != null) {
            return; // 已经存在
        }
        this.currentLocation = loc.clone();
        spawnEntity(loc);
        loc.getWorld().playSound(loc, Sound.BLOCK_BELL_RESONATE, 1f, 1f);
    }

    public void recall() {
        cleanupLight();
        removeChassis(); // 清理底盘
        removeDriver();  // 清理驱动实体
        ArmorStand entity = getEntity();
        if (entity != null) {
            entity.remove();
        }
        this.entityId = null;
    }

    private void removeDriver() {
        if (driverId != null) {
            Entity driver = Bukkit.getEntity(driverId);
            if (driver != null) {
                driver.remove();
            }
            driverId = null;
        }
    }

    private void spawnEntity(Location loc) {
        if (loc.getWorld() == null) return;
        
        // 生成盔甲架
        ArmorStand as = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        as.setVisible(false);
        as.setGravity(false);
        as.setBasePlate(false);
        as.setSmall(true); // 小盔甲架更可爱
        as.setInvulnerable(true);
        as.setCustomName(name);
        as.setCustomNameVisible(true);
        
        // 设置外观 (能量球)
        as.getEquipment().setHelmet(new ItemStack(Material.SEA_LANTERN));
        
        // 手部：灯笼
        as.getEquipment().setItemInMainHand(new ItemStack(Material.LANTERN));
        
        // 锁定装备插槽
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            as.addEquipmentLock(slot, ArmorStand.LockType.REMOVING_OR_CHANGING);
        }

        this.entityId = as.getUniqueId();
        
        spawnDriver(loc);
    }

    private void spawnDriver(Location loc) {
        if (loc.getWorld() == null) return;
        Wolf wolf = (Wolf) loc.getWorld().spawnEntity(loc, EntityType.WOLF);
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null) {
            wolf.setOwner(owner);
            wolf.setTamed(true);
        }
        wolf.setInvisible(true);
        wolf.setSilent(true);
        wolf.setInvulnerable(true);
        wolf.setCollidable(false);
        wolf.setBaby();
        wolf.setAgeLock(true);
        // 提高移动速度，紧跟玩家
        if (wolf.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) {
            wolf.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.45);
        }
        this.driverId = wolf.getUniqueId();
    }

    // 每 tick 调用
    public void tick() {
        // 更新心情 (基于现实时间)
        updateRealTimeStatus();

        ArmorStand entity = getEntity();
        if (entity == null || !entity.isValid()) {
            // 如果实体消失（如区块卸载），尝试重新生成或保持数据状态
            // 暂时不做处理，依赖外部管理
            return;
        }

        Player owner = Bukkit.getPlayer(ownerId);
        
        // 1. 运动逻辑
        if (mode == SpiritMode.COMPANION && owner != null && owner.isOnline()) {
            handleCompanionMovement(entity, owner);
        } else if (mode == SpiritMode.GUARDIAN) {
            handleGuardianLogic(entity);
        }

        // 2. 粒子特效 (Trail)
        spawnParticles(entity.getLocation().add(0, 0.5, 0));

        // 3. 动态光源
        updateDynamicLight(entity.getLocation());

        // 4. 环境扫描与进化 (低频率)
        if (Math.random() < 0.005) {
            updateSpiritType();
        }
        
        // 5. 皮肤更新检查
        checkSkinUpdate();
    }

    private void checkSkinUpdate() {
        // 随机眨眼逻辑 (每 tick 0.5% 概率 => 平均 10 秒眨一次)
        if (currentExpression == null || currentExpression == SpiritSkinManager.Expression.NORMAL) {
            if (Math.random() < 0.005) {
                setExpression(SpiritSkinManager.Expression.BLINK, 5); // 眨眼 0.25秒
                updateSkin();
            }
        }

        if (expressionEndTime > 0 && System.currentTimeMillis() > expressionEndTime) {
            currentExpression = null;
            expressionEndTime = 0;
            updateSkin();
        }
        // 为了确保皮肤正确 (例如心情变化导致皮肤改变)，可以低频强制更新
        if (Math.random() < 0.005) {
             updateSkin();
        }
    }

    public void updateSkin() {
        ArmorStand entity = getEntity();
        if (entity == null) return;
        
        SpiritSkinManager.Expression expr = SpiritSkinManager.Expression.NORMAL;
        
        if (this.type == SpiritType.FLOWER) {
            expr = SpiritSkinManager.Expression.FLOWER;
        } else if (this.type == SpiritType.MINER) {
            expr = SpiritSkinManager.Expression.MINER;
        }
        
        // 心情/状态覆盖
        if (isAbandoned()) {
             expr = SpiritSkinManager.Expression.SAD;
        } else if (this.mood < 20) {
             expr = SpiritSkinManager.Expression.SAD;
        } else if (this.mood > 80) {
             expr = SpiritSkinManager.Expression.HAPPY;
        }
        
        // 临时表情覆盖
        if (currentExpression != null && System.currentTimeMillis() < expressionEndTime) {
             expr = currentExpression;
        }
        
        entity.getEquipment().setHelmet(SpiritSkinManager.getHead(expr));
    }

    private void updateSpiritType() {
        if (currentLocation == null || currentLocation.getWorld() == null) return;
        
        int flowerCount = 0;
        int oreCount = 0;
        
        // 扫描 5x5x5
        for (int x = -3; x <= 3; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -3; z <= 3; z++) {
                    Block b = currentLocation.clone().add(x, y, z).getBlock();
                    String typeName = b.getType().name();
                    if (typeName.contains("FLOWER") || typeName.contains("TULIP") || typeName.contains("ROSE")) {
                        flowerCount++;
                    } else if (typeName.contains("ORE") || b.getType() == Material.FURNACE) {
                        oreCount++;
                    }
                }
            }
        }

        if (flowerCount > 10 && getType() != SpiritType.FLOWER) {
            setType(SpiritType.FLOWER);
            updateSkin();
            currentLocation.getWorld().playSound(currentLocation, Sound.ENTITY_PLAYER_LEVELUP, 1, 2);
        } else if (oreCount > 10 && getType() != SpiritType.MINER) {
            setType(SpiritType.MINER);
            updateSkin();
            currentLocation.getWorld().playSound(currentLocation, Sound.ENTITY_PLAYER_LEVELUP, 1, 2);
        }
    }

    public void updateRealTimeStatus() {
        long now = System.currentTimeMillis();
        long diff = now - lastMoodUpdateTime;
        
        // 为了防止频繁调用导致的浮点误差，我们只有当 diff 足够大才更新
        if (diff > 60000) { // 至少过1分钟
            // 计算过去了几分钟
            double minutesPassed = diff / 60000.0;
            
            // 获取当前衰减速率 (每点心情需要的分钟数)
            double minutesPerPoint = getMoodDecayMinutes(this.mood);
            
            // 计算衰减量
            double decrease = minutesPassed / minutesPerPoint;
            
            // 如果饥饿，衰减加速 (2倍速)
            if (isHungry()) {
                decrease *= 2; 
            }
            
            if (decrease > 0) {
                decreaseMood(decrease);
                lastMoodUpdateTime = now;
            }
        }
    }
    
    // 获取心情衰减速率曲线
    // 高心情(>80): 慢速衰减 (1点/2.5小时)
    // 低心情(<30): 快速衰减 (1点/12.5分钟)
    // 中等心情: 正常衰减 (1点/20分钟)
    private double getMoodDecayMinutes(double currentMood) {
        if (currentMood > 80) {
            return 150.0; // 150分钟 = 2.5小时
        } else if (currentMood < 30) {
            return 12.5; // 12.5分钟
        } else {
            return 20.0; // 20分钟
        }
    }

    public void moveTo(Location target) {
        this.manualTarget = target;
        if (target != null && target.getWorld() != null) {
            target.getWorld().playSound(target, Sound.ENTITY_ARROW_HIT_PLAYER, 0.5f, 2f);
        }
        // 重置诱饵
        removeMoveTargetEntity();
    }

    public void cancelMove() {
        this.manualTarget = null;
        removeMoveTargetEntity();
        
        // 恢复跟随
        if (driverId != null) {
             Entity driver = Bukkit.getEntity(driverId);
             if (driver instanceof Wolf) {
                 ((Wolf) driver).setTarget(null);
             }
        }
    }
    
    private void removeMoveTargetEntity() {
        if (moveTargetEntityId != null) {
            Entity e = Bukkit.getEntity(moveTargetEntityId);
            if (e != null) e.remove();
            moveTargetEntityId = null;
        }
    }

    private void handleCompanionMovement(ArmorStand entity, Player owner) {
        // 如果是从守护模式切换过来，先清理底盘
        if (chassisEntityId != null) {
            removeChassis();
            // 起飞动画效果
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1f, 1f);
        }

        if (driverId == null || Bukkit.getEntity(driverId) == null) {
            spawnDriver(entity.getLocation());
        }
        
        LivingEntity driver = (LivingEntity) Bukkit.getEntity(driverId);
        if (driver == null || !driver.isValid()) return;

        // 确保 Driver 跟随主人
        if (driver instanceof Wolf) {
            Wolf wolf = (Wolf) driver;
            // 确保驯服状态，防止因 Owner 离线生成导致未驯服
            if (!wolf.isTamed() || wolf.getOwner() == null) {
                wolf.setTamed(true);
                wolf.setOwner(owner);
            }
            
            // 寻路逻辑：手动指定 vs 跟随主人
            if (manualTarget != null) {
                 if (manualTarget.getWorld() != wolf.getWorld() || owner.getLocation().distanceSquared(manualTarget) > 900) {
                     // 目标跨世界或离主人太远 (>30格)，取消指令
                     cancelMove();
                 } else {
                     wolf.setSitting(false);
                     
                     // 检查是否到达
                     if (wolf.getLocation().distanceSquared(manualTarget) < 2.25) { // 1.5 blocks
                         // 到达目标，坐下并移除诱饵
                         wolf.setSitting(true);
                         removeMoveTargetEntity();
                     } else {
                         // 未到达，生成诱饵并设置目标
                        if (moveTargetEntityId == null || Bukkit.getEntity(moveTargetEntityId) == null) {
                            // 生成隐形兔子诱饵 (使用 Consumer 在生成前应用属性，防止闪烁)
                            LivingEntity lure = manualTarget.getWorld().spawn(manualTarget, org.bukkit.entity.Rabbit.class, rabbit -> {
                                rabbit.setAI(false);
                                rabbit.setInvulnerable(true);
                                rabbit.setSilent(true);
                                rabbit.setCollidable(false);
                                rabbit.setGravity(false); // 防止掉落
                                // 隐形 (无限时长)
                                rabbit.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY, -1, 1, false, false));
                            });
                            
                            moveTargetEntityId = lure.getUniqueId();
                            wolf.setTarget(lure);
                        } else {
                             // 确保 Wolf 锁定诱饵
                             Entity lure = Bukkit.getEntity(moveTargetEntityId);
                             if (lure instanceof LivingEntity && wolf.getTarget() != lure) {
                                 wolf.setTarget((LivingEntity) lure);
                             }
                             // 确保诱饵在正确位置 (防止被推走)
                             if (lure != null && lure.getLocation().distanceSquared(manualTarget) > 0.01) {
                                 lure.teleport(manualTarget);
                             }
                         }
                     }
                 }
            }
            
            // 如果没有手动目标，Vanilla AI 会自动跟随主人
            if (manualTarget == null) {
                if (wolf.isSitting()) {
                    wolf.setSitting(false);
                }
                // 清理残留诱饵
                removeMoveTargetEntity();
            }
        }

        Location driverLoc = driver.getLocation();
        
        // 世界切换检查
        if (owner.getLocation().getWorld() != driverLoc.getWorld()) {
            driver.teleport(owner.getLocation());
            entity.teleport(owner.getLocation());
            currentLocation = owner.getLocation().clone();
            return;
        }

        // 距离检查 (传送)
        if (driverLoc.distanceSquared(owner.getLocation()) > 400) {
            driver.teleport(owner.getLocation());
            driverLoc = driver.getLocation(); // Update loc
            
            // 如果是因为离得太远被强制传送回来，则自动取消“守卫”指令，防止它又跑回去
            if (manualTarget != null) {
                cancelMove();
            }
        }

        // 呼吸律动 (Bobbing) - 增强漂浮感
        long ticks = System.currentTimeMillis() / 50;
        // 增加幅度和频率
        double bobbing = Math.sin(ticks * 0.15) * 0.15; 
        
        // 目标位置：Driver 上方
        // Baby Wolf 高度较低，我们让灵体悬浮在头顶
        Location targetLoc = driverLoc.clone();
        targetLoc.setY(driverLoc.getY() + 1.2 + bobbing);
        
        // Y轴平滑插值 (Lerp) - 解决狼跳跃时灵体瞬间瞬移的生硬感
        double currentY = entity.getLocation().getY();
        double targetY = targetLoc.getY();
        // 平滑因子 0.15，每一tick修正15%的差距，产生"拖拽"的漂浮感
        double smoothedY = currentY + (targetY - currentY) * 0.15;
        
        // 如果距离过远（例如传送），直接瞬移，不进行平滑
        if (Math.abs(targetY - currentY) > 5) {
            smoothedY = targetY;
        }
        
        targetLoc.setY(smoothedY);

        // 始终看向玩家头部
        targetLoc.setDirection(owner.getEyeLocation().subtract(targetLoc).toVector());

        // 更新实体位置
        entity.teleport(targetLoc);
        currentLocation = targetLoc;
    }

    private void handleGuardianLogic(ArmorStand entity) {
        if (driverId == null || Bukkit.getEntity(driverId) == null) {
            spawnDriver(entity.getLocation());
            return;
        }
        LivingEntity driver = (LivingEntity) Bukkit.getEntity(driverId);
        
        // 让 Driver 坐下
        if (driver instanceof Wolf) {
            Wolf wolf = (Wolf) driver;
            if (!wolf.isSitting()) {
                wolf.setSitting(true);
            }
        }
        
        Location baseLoc = driver.getLocation();

        if (chassisEntityId == null) {
            // 生成底盘
            spawnChassis(baseLoc);
            // 播放落地音效
            if (baseLoc.getWorld() != null)
                baseLoc.getWorld().playSound(baseLoc, Sound.BLOCK_STONE_PLACE, 1f, 1f);
        } else {
             // 有底盘，坐在上面
             Entity chassis = Bukkit.getEntity(chassisEntityId);
             if (chassis == null || !chassis.isValid()) {
                 chassisEntityId = null; // 底盘丢失，重新生成
                 return;
             }
             
             // 保持在底盘上方
             // 调整高度：底盘在 -0.6，我们希望 Spirit 看起来坐在上面
             // Chassis Head Y ~= loc - 0.6 + 0.7 = loc + 0.1
             // Spirit Head Y target should be close to that.
             // previous 0.65 was still a bit high
             // Let's lower it to 0.45
             Location seatLoc = chassis.getLocation().add(0, 0.45, 0); 
             
             // 呼吸律动
             long ticks = System.currentTimeMillis() / 50;
             double bobbing = Math.sin(ticks * 0.05) * 0.03;
             seatLoc.add(0, bobbing, 0);
             
             // 缓慢自转
             float yaw = (ticks % 720) / 2.0f;
             seatLoc.setYaw(yaw);
             
             entity.teleport(seatLoc);
        }
    }

    private void spawnChassis(Location loc) {
        if (loc.getWorld() == null) return;
        // 生成底盘 ArmorStand
        // 位置调整：让头盔部分刚好在地面上
        ArmorStand chassis = (ArmorStand) loc.getWorld().spawnEntity(loc.clone().add(0, -0.6, 0), EntityType.ARMOR_STAND);
        chassis.setVisible(false);
        chassis.setGravity(false);
        chassis.setBasePlate(false);
        chassis.setSmall(true);
        chassis.setMarker(true); 
        
        // 底盘外观：平滑石台阶
        chassis.getEquipment().setHelmet(new ItemStack(Material.SMOOTH_STONE_SLAB));
        // 锁定装备
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            chassis.addEquipmentLock(slot, ArmorStand.LockType.REMOVING_OR_CHANGING);
        }
        
        this.chassisEntityId = chassis.getUniqueId();
    }

    private void removeChassis() {
        if (chassisEntityId != null) {
            Entity e = Bukkit.getEntity(chassisEntityId);
            if (e != null) e.remove();
            chassisEntityId = null;
        }
    }
    
    private void spawnParticles(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;
        
        // 1. 状态检测：是否在移动
        boolean isMoving = false;
        if (lastParticleLocation != null) {
            if (lastParticleLocation.getWorld() == world && lastParticleLocation.distanceSquared(loc) > 0.0025) { // 0.05 blocks
                isMoving = true;
            }
        }
        lastParticleLocation = loc.clone();
        
        // 2. 特殊状态优先
        if (isAbandoned()) {
            // 抑郁：滴水，频率降低
            if (Math.random() < 0.2) {
                world.spawnParticle(Particle.DRIPPING_WATER, loc.clone().add(0, 0.5, 0), 1, 0.2, 0.2, 0.2, 0);
            }
            return; // 抑郁状态不显示其他特效
        } 
        
        // 3. 心情特效 (大幅降低频率，避免刷屏)
        if (getMood() > 80) {
            // 只有 5% 的概率生成爱心，且只在静止时或低频
            if (Math.random() < 0.05) {
                world.spawnParticle(Particle.HEART, loc.clone().add(0, 1.8, 0), 1);
            }
        } else {
            // 普通心情：偶尔有音符
            if (Math.random() < 0.02) {
                world.spawnParticle(Particle.NOTE, loc.clone().add(0, 1.8, 0), 1);
            }
        }
        
        // 4. 移动/静止特效区分
        if (isMoving) {
            // 移动中：拖尾在脚下 (loc 是 Head 位置，往下偏移)
             // 灵体悬浮在 Wolf 头顶，loc 大概是 WolfY + 1.2
             // 现在的粒子偏低了，我们往上调一点
             // END_ROD 粒子往上调到 0.2 (原 -0.1)
             world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 0.2, 0), 1, 0.05, 0.05, 0.05, 0.01);
             
             // 偶尔掉落一点星光
             if (Math.random() < 0.3) {
                 try {
                     world.spawnParticle(Particle.INSTANT_EFFECT, loc.clone().add(0, 0.2, 0), 1, 0.1, 0.1, 0.1, 0);
                 } catch (Exception ignored) {}
             }
        } else {
            // 静止中：缓慢呼吸感
            // 降低频率：每 5 tick 一次
            long ticks = System.currentTimeMillis() / 50;
            if (ticks % 5 == 0) {
                // 漂浮粒子，范围稍大，速度极慢
                // 往上调一点，到 +0.6 (原 +0.3)
                world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 0.6, 0), 1, 0.15, 0.15, 0.15, 0.005);
            }
        }
    }

    private void updateDynamicLight(Location loc) {
        // 获取当前方块位置（整数坐标）
        Location currentBlockLoc = loc.getBlock().getLocation();
        
        // 检查是否移动到了新方块
        boolean isNewBlock = lastLightLocation == null || !lastLightLocation.equals(currentBlockLoc);
        // 检查是否需要定期刷新 (每1秒刷新一次，确保新来的玩家能看到光)
        boolean isPeriodic = System.currentTimeMillis() - lastLightPacketTime > 1000;

        // 1. 优化：如果方块坐标没变，且未到刷新时间，直接返回
        if (!isNewBlock && !isPeriodic) {
            return;
        }
        
        lastLightPacketTime = System.currentTimeMillis();

        // 2. 准备新的光照数据
        BlockData lightData = null;
        Block block = currentBlockLoc.getBlock();
        
        // 修复：在守护模式下，实体可能坐在底盘上，导致其坐标陷入地下 (Y-0.15)
        // 这种情况下 block 是石头/泥土，不发光
        // 我们需要检查头顶位置是否为空，如果是，则在那里发光
        Block headBlock = currentBlockLoc.clone().add(0, 1, 0).getBlock();
        Block targetBlock = block;
        Location targetLoc = currentBlockLoc;
        
        if (block.getType().isSolid() && !headBlock.getType().isSolid()) {
            // 如果脚下是实心方块，但头顶是空的，使用头顶位置发光
            targetBlock = headBlock;
            targetLoc = headBlock.getLocation();
        }
        
        // 参考 DynamicLights: 仅在空气、洞穴空气、虚空空气或水中发光
        if (targetBlock.getType() == Material.AIR || targetBlock.getType() == Material.CAVE_AIR || targetBlock.getType() == Material.VOID_AIR) {
            lightData = Material.LIGHT.createBlockData();
            if (lightData instanceof org.bukkit.block.data.type.Light) {
                ((org.bukkit.block.data.type.Light) lightData).setLevel(15);
            }
        } else if (targetBlock.getType() == Material.WATER) {
            lightData = Material.LIGHT.createBlockData();
            if (lightData instanceof org.bukkit.block.data.type.Light) {
                ((org.bukkit.block.data.type.Light) lightData).setLevel(15);
                ((org.bukkit.block.data.type.Light) lightData).setWaterlogged(true);
            }
        }
        
        // 3. 发送数据包
        // 我们需要处理两个位置：旧位置(还原) 和 新位置(设置光)
        // 为了简化，我们遍历一次玩家，处理所有逻辑
        
        // 如果位置改变了，我们需要还原旧位置的方块
        // 如果是定期刷新，我们不需要还原旧位置（因为旧位置就是当前位置），除非我们想强制同步
        // 但为了防止逻辑混乱，只有 isNewBlock 时才还原旧位置
        
        for (Player p : loc.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(loc) < 2500) { // 50格内
                // A. 还原旧位置 (仅当位置改变且旧位置存在时)
                if (isNewBlock && lastLightLocation != null) {
                    // 发送真实方块数据，清除假光
                    // 注意：这里可能会发送 "Grass" 的真实数据，这是正确的，确保客户端没有残留
                    if (p.getWorld() == lastLightLocation.getWorld()) {
                        p.sendBlockChange(lastLightLocation, lastLightLocation.getBlock().getBlockData());
                    }
                }
                
                // B. 设置新光 (如果有光照数据)
                if (lightData != null) {
                    p.sendBlockChange(targetLoc, lightData);
                } else {
                    // 修复 Ghost Block 问题：如果当前位置不是发光方块（例如陷入地下），
                    // 强制发送一次真实方块数据，确保客户端同步。
                    if (isNewBlock) {
                        p.sendBlockChange(currentBlockLoc, block.getBlockData());
                    }
                }
            }
        }
        
        // 更新缓存
        // 始终记录当前实际发光的位置（如果没发光，就记录脚下位置）
        lastLightLocation = (lightData != null) ? targetLoc : currentBlockLoc;
    }
    
    // 清除光源（在消失或收回时调用）
    public void cleanupLight() {
        if (lastLightLocation != null) {
            for (Player p : lastLightLocation.getWorld().getPlayers()) {
                // 还原为真实方块数据
                if (lastLightLocation.getWorld() == p.getWorld() && p.getLocation().distanceSquared(lastLightLocation) < 2500) {
                     p.sendBlockChange(lastLightLocation, lastLightLocation.getBlock().getBlockData());
                }
            }
            lastLightLocation = null;
        }
    }

    public void remove() {
        cleanupLight();
        removeChassis();
        removeDriver();
        ArmorStand entity = getEntity();
        if (entity != null) {
            entity.remove();
        }
    }

    public void toggleMode() {
        cleanupLight(); // 清除之前的光源，防止位置突变导致 Ghost Block
        if (mode == SpiritMode.COMPANION) {
            // 切换到守护模式
            mode = SpiritMode.GUARDIAN;
            currentLocation = getEntity().getLocation(); // 记录定居点
            // 生成温和的特效
            currentLocation.getWorld().spawnParticle(Particle.END_ROD, currentLocation, 20, 0.5, 0.5, 0.5, 0.1);
            currentLocation.getWorld().playSound(currentLocation, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f);
        } else {
            // 切换到旅伴模式
            mode = SpiritMode.COMPANION;
            currentLocation.getWorld().playSound(currentLocation, Sound.ENTITY_BAT_TAKEOFF, 1f, 1f);
        }
    }
    
    // 过继逻辑
    // 返回 true 表示消耗了食物，false 表示拒绝投喂
    public boolean handleStrangerFeed(Player feeder) {
        if (feeder.getUniqueId().equals(ownerId)) return false; // Should be handled by listener
        
        if (isAbandoned()) { // 只有处于被遗弃状态（心情<=0）时，陌生人才可投喂
             checkAdoption(feeder);
             // 仅播放特效，不增加心情值
             if (currentLocation != null) {
                 currentLocation.getWorld().spawnParticle(Particle.HEART, currentLocation.add(0, 1, 0), 3, 0.2, 0.2, 0.2);
             }
             return true;
        } else {
             feeder.sendMessage("§c这不是你的地灵。");
             return false;
        }
    }
    
    private void checkAdoption(Player feeder) {
        UUID fid = feeder.getUniqueId();
        long now = System.currentTimeMillis();
        
        // 检查上次投喂时间是否是昨天（实现简单的连续天数逻辑）
        Long lastTime = lastFeedTime.getOrDefault(fid, 0L);
        if (now - lastTime > 86400000L) { // 超过24小时
             int days = strangerFeedDays.getOrDefault(fid, 0) + 1;
             strangerFeedDays.put(fid, days);
             lastFeedTime.put(fid, now);
             
             feeder.sendMessage("§e你安抚了这个被遗弃的地灵。(" + days + "/3)");
             
             if (days >= 3) {
                 // 过继成功
                 transferOwnership(feeder);
             }
        } else {
             feeder.sendMessage("§c你今天已经投喂过这个地灵了，明天再来吧。");
        }
    }
    
    private void transferOwnership(Player newOwner) {
        // 地灵过继新逻辑：地灵不换主人，只转让领地，然后地灵消失
        
        com.palmergames.bukkit.towny.object.Town town = TownyIntegration.getTownAt(this.currentLocation);
        if (town != null && town.getName().equals(this.townName)) {
             TownyIntegration.transferOwnership(town.getName(), newOwner);
             newOwner.sendMessage("§a你通过不懈的努力，感化了这片土地，获得了领地的所有权！");
             newOwner.sendMessage("§e原来的地灵完成了它的使命，化作光点消散了...");
        } else {
             newOwner.sendMessage("§c过继异常：当前位置不在地灵管辖的领地内。");
        }
        
        this.strangerFeedDays.clear();
        this.lastFeedTime.clear();
        newOwner.playSound(newOwner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        
        // 地灵消失 (回到旧主人风铃)
        recall();
    }

    private void decreaseMood(double amount) {
        double oldMood = this.mood;
        this.mood = Math.max(0, this.mood - amount);
        
        if (oldMood > 0 && this.mood == 0) {
            // 心情刚归零，触发传送逻辑
            teleportToOwnerTown();
        }
    }
    
    private void teleportToOwnerTown() {
        // 如果当前已经在领地内，不做处理
        if (townName != null && currentLocation != null) {
            com.palmergames.bukkit.towny.object.Town town = TownyIntegration.getTownAt(currentLocation);
            if (town != null && town.getName().equals(townName)) {
                return; 
            }
        }
        
        // 如果有领地，传送到领地重生点
        if (townName != null) {
            try {
                com.palmergames.bukkit.towny.object.Town town = com.palmergames.bukkit.towny.TownyUniverse.getInstance().getTown(townName);
                if (town != null && town.hasSpawn()) {
                    Location spawn = town.getSpawn();
                    this.currentLocation = spawn.clone();
                    ArmorStand entity = getEntity();
                    if (entity != null) {
                        entity.teleport(spawn);
                    } else {
                        // 如果实体未加载，下次加载时会使用 currentLocation
                    }
                    
                    // 强制设为守护模式
                    this.mode = SpiritMode.GUARDIAN;
                    this.type = SpiritType.SAD; // 变更为抑郁状态
                    
                    // 通知主人
                    Player owner = Bukkit.getPlayer(ownerId);
                    if (owner != null) {
                        owner.sendMessage("§c你的地灵 " + name + " 因为长期被忽视，已经陷入抑郁了！它回到了领地中心，并且不再提供保护！");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            // 流浪地灵，心情归零，自动回到风铃（消失）
            recall();
        }
    }

    public ArmorStand getEntity() {
        if (entityId == null) return null;
        return (ArmorStand) Bukkit.getEntity(entityId);
    }
    
    // Missing Methods Implementation
    public UUID getEntityId() { return entityId; }
    public void setName(String name) { 
        this.name = name; 
        ArmorStand entity = getEntity();
        if (entity != null) {
            entity.setCustomName(name);
            entity.setCustomNameVisible(true);
        }
    }
    public void setMood(double mood) { this.mood = Math.max(0, Math.min(100, mood)); }
    
    public boolean isHungry() {
        return System.currentTimeMillis() - lastFoodTime > 4 * 60 * 60 * 1000; // 4 hours
    }
    public long getNextHungerTime() {
        return lastFoodTime + 4 * 60 * 60 * 1000;
    }
    public void scheduleNextHunger() {
        this.lastFoodTime = System.currentTimeMillis();
    }
    
    public long getLastInteractTime() { return lastInteractTime; }
    public void interact() { this.lastInteractTime = System.currentTimeMillis(); }
    
    public boolean isAbandoned() { return this.mood <= 0; }
    
    public SpiritSkinManager.Expression getExpression() { return currentExpression; }
    public long getExpressionEndTime() { return expressionEndTime; }
    public void setExpression(SpiritSkinManager.Expression expr, int ticks) {
        this.currentExpression = expr;
        this.expressionEndTime = System.currentTimeMillis() + ticks * 50;
    }

    public UUID getDriverId() { return driverId; }
    public UUID getMoveTargetEntityId() { return moveTargetEntityId; }
    
    // Getters
    public UUID getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public String getTownName() { return townName; }
    public void setTownName(String townName) { this.townName = townName; }
    public double getMood() { return mood; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public int getExp() { return exp; }
    public void setExp(int exp) { this.exp = exp; }
    
    public void addExp(int amount) {
        if (this.level >= 5) return; // 最高5级
        
        this.exp += amount;
        int maxExp = this.level * 100; // 每级递增 100
        
        if (this.exp >= maxExp) {
            this.exp -= maxExp;
            this.level++;
            
            // 更新领地 Bonus Blocks
            if (townName != null) {
                try {
                    com.palmergames.bukkit.towny.object.Town town = com.palmergames.bukkit.towny.TownyUniverse.getInstance().getTown(townName);
                    if (town != null) {
                        // 1级=1块, 2级=3块, 3级=5块... => blocks = 1 + (level-1)*2
                        // Towny bonus blocks 是额外的块数。
                        // 假设基础是0，那么我们需要设置 bonus blocks 为 total desired - 0
                        // Lv1: 1, Lv2: 3, Lv3: 5, Lv4: 7, Lv5: 9
                        int targetBlocks = 1 + (this.level - 1) * 2;
                        town.setBonusBlocks(targetBlocks); 
                        town.save();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            
            if (currentLocation != null && currentLocation.getWorld() != null) {
                currentLocation.getWorld().playSound(currentLocation, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                currentLocation.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, currentLocation.add(0, 1, 0), 10, 0.5, 0.5, 0.5);
            }
        }
    }

    public SpiritMode getMode() { return mode; }
    public void setMode(SpiritMode mode) { this.mode = mode; }
    public SpiritType getType() { return type; }
    public void setType(SpiritType type) { this.type = type; }
    
    public SpiritInventory getInventory() { 
        if (inventory == null) {
            // 防御性编程，如果 inventory 为空（例如加载失败），尝试重新初始化
            initAfterLoad();
        }
        return inventory; 
    }
    public Location getLocation() { return currentLocation; }
}
