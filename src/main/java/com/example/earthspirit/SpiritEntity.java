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
import org.bukkit.inventory.meta.ItemMeta;
// import org.bukkit.util.Vector;

import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display;
import org.bukkit.Color;
import org.joml.Vector3f;
import org.joml.AxisAngle4f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;

import com.example.earthspirit.cravings.DailyRequest;
import com.example.earthspirit.configuration.ConfigManager;
import com.example.earthspirit.configuration.I18n;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

public class SpiritEntity {
    private DailyRequest dailyRequest;
    private UUID entityId; // 实体UUID (ArmorStand)
    private UUID ownerId;  // 主人UUID
    private String name;   // 地灵名字
    private String townName; // 居所名称 (null 表示流浪)
    private double mood;      // 心情值 (0-100)
    private double hunger; // 饱食度
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
    
    // 信任与伴侣
    private java.util.Set<UUID> trustedPlayers = new java.util.HashSet<>();
    private UUID partnerId;
    
    public java.util.Set<UUID> getTrustedPlayers() {
        return trustedPlayers;
    }
    
    public void addTrustedPlayer(UUID uuid) {
        trustedPlayers.add(uuid);
    }
    
    public void removeTrustedPlayer(UUID uuid) {
        trustedPlayers.remove(uuid);
    }
    
    public UUID getPartnerId() {
        return partnerId;
    }
    
    public void setPartnerId(UUID partnerId) {
        this.partnerId = partnerId;
    }
    
    public boolean isPartner(UUID uuid) {
        return partnerId != null && partnerId.equals(uuid);
    }
    
    public boolean isTrusted(UUID uuid) {
        return trustedPlayers.contains(uuid) || (partnerId != null && partnerId.equals(uuid)) || ownerId.equals(uuid);
    }
    
    // 交互与状态
    private long lastFoodTime;
    private long lastInteractTime;
    private long lastMoodUpdateTime; // 记录上次更新心情的现实时间
    private transient SpiritSkinManager.Expression currentExpression;
    private transient long expressionEndTime;
    
    // 饱食度系统 - 字段在上方定义
    private long lastHungerUpdateTime; // 上次饱食度更新时间
    
    // 悬浮气泡
    private transient UUID bubbleEntityId;
    private transient long bubbleEndTime;
    private long lastHungerFeedbackTime; // 非transient，持久化防止重启后立即刷屏 (但当前类设计看似只持久化部分字段，这里先假设 transient 也没事，或者看看 save 逻辑)

    public void addMood(double amount) {
        this.mood += amount;
        double maxMood = ConfigManager.get().getDefaultMaxMood();
        if (this.mood > maxMood) this.mood = maxMood;
        if (this.mood < 0) this.mood = 0;
        updateSkin();
    }
    
    public void addExp(int amount) {
        this.exp += amount;
        int maxExp = this.level * 100;
        while (this.exp >= maxExp) {
            this.exp -= maxExp;
            this.level++;
            maxExp = this.level * 100;
            // Level up effects
            if (this.currentLocation != null && this.currentLocation.getWorld() != null) {
                this.currentLocation.getWorld().playSound(this.currentLocation, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                this.currentLocation.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, this.currentLocation, 20, 0.5, 0.5, 0.5, 0.1);
            }
        }
    }

    public enum SpiritMode {
        COMPANION("mode.companion"), 
        GUARDIAN("mode.guardian");
        
        private final String i18nKey;
        SpiritMode(String i18nKey) {
            this.i18nKey = i18nKey;
        }
        public String getDisplayName() {
            return I18n.get().getString(i18nKey);
        }
    }

    public enum SpiritType {
        NORMAL, FLOWER, MINER, SAD
    }

    public SpiritEntity(UUID ownerId, String name, Location spawnLocation) {
        this(ownerId, name, spawnLocation, true);
    }

    public SpiritEntity(UUID ownerId, String name, Location spawnLocation, boolean spawnNow) {
        this.ownerId = ownerId;
        this.name = name;
        this.mood = ConfigManager.get().getDefaultMood();
        this.hunger = ConfigManager.get().getDefaultHunger();
        this.mode = SpiritMode.COMPANION;
        this.inventory = new SpiritInventory(I18n.get().getLegacy("inventory.title", Placeholder.component("name", I18n.get().asComponent(name))));
        this.strangerFeedDays = new HashMap<>();
        this.lastFeedTime = new HashMap<>();
        
        this.currentLocation = spawnLocation.clone();
        this.lastMoodUpdateTime = System.currentTimeMillis();
        this.lastHungerUpdateTime = System.currentTimeMillis();
        initTransientFields();
        
        if (spawnNow) {
            spawnEntity(spawnLocation);
        }
    }
    
    // 专门用于从配置加载实体，尝试链接现有实体而非直接生成
    public void loadEntity(Location loc, UUID savedEntityId, UUID savedDriverId, UUID savedChassisId) {
        if (loc.getWorld() == null) return;
        
        // 1. 强制加载区块，确保能找到实体
        if (!loc.getChunk().isLoaded()) {
            loc.getChunk().load();
        }
        
        boolean linked = false;
        
        // 2. 尝试通过 UUID 链接主实体
        if (savedEntityId != null) {
            // 注意：getEntity 可能在区块刚加载时返回 null，即使实体存在
            Entity e = Bukkit.getEntity(savedEntityId);
            if (e == null) {
                 // 尝试遍历区块实体 (更可靠)
                 for (Entity chunkEntity : loc.getChunk().getEntities()) {
                     if (chunkEntity.getUniqueId().equals(savedEntityId)) {
                         e = chunkEntity;
                         break;
                     }
                 }
            }
            
            if (e instanceof ArmorStand && e.isValid()) {
                this.entityId = e.getUniqueId();
                linked = true;
            }
        }
        
        // 3. 如果 UUID 链接失败，尝试通过 PDC 空间搜索链接
        if (!linked) {
            linked = tryLinkExistingEntity(loc);
        }
        
        // 4. 如果都失败了，生成新的
        if (!linked) {
            spawnEntity(loc);
        } else {
            // 如果链接成功，尝试恢复组件
            
            // 恢复 Driver
            if (savedDriverId != null) {
                Entity d = Bukkit.getEntity(savedDriverId);
                // 同上，尝试区块搜索
                if (d == null) {
                    for (Entity chunkEntity : loc.getChunk().getEntities()) {
                        if (chunkEntity.getUniqueId().equals(savedDriverId)) {
                            d = chunkEntity;
                            break;
                        }
                    }
                }
                
                if (d instanceof Wolf && d.isValid()) {
                    this.driverId = d.getUniqueId();
                }
            }
            // 如果没找到 Driver，tryLinkExistingEntity 可能已经找过了，或者需要生成
            if (this.driverId == null || Bukkit.getEntity(this.driverId) == null) {
                // 再次尝试空间搜索 Wolf
                NamespacedKey key = new NamespacedKey(EarthSpiritPlugin.getInstance(), "spirit_owner");
                for (Entity d : loc.getWorld().getNearbyEntities(loc, 5, 5, 5)) {
                    if (d instanceof Wolf && d.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                        String dOwner = d.getPersistentDataContainer().get(key, PersistentDataType.STRING);
                        if (dOwner != null && dOwner.equals(ownerId.toString())) {
                            this.driverId = d.getUniqueId();
                            break;
                        }
                    }
                }
                // 还是没有，生成新的
                if (this.driverId == null) {
                    spawnDriver(loc);
                }
            }
            
            // 恢复 Chassis (如果存在)
            if (savedChassisId != null) {
                Entity c = Bukkit.getEntity(savedChassisId);
                 if (c == null) {
                    for (Entity chunkEntity : loc.getChunk().getEntities()) {
                        if (chunkEntity.getUniqueId().equals(savedChassisId)) {
                            c = chunkEntity;
                            break;
                        }
                    }
                }
                if (c instanceof ArmorStand && c.isValid()) {
                    this.chassisEntityId = c.getUniqueId();
                }
            }
        }
    }
    
    // 反序列化后初始化
    public void initAfterLoad() {
        initTransientFields();
        // 每次加载都重置心情更新时间，防止离线/卸载期间计算大量衰减
        lastMoodUpdateTime = System.currentTimeMillis();
        
        if (inventoryData != null && !inventoryData.isEmpty()) {
            this.inventory = SpiritInventory.fromBase64(inventoryData, I18n.get().getLegacy("inventory.title", Placeholder.parsed("name", name)));
        } else {
            this.inventory = new SpiritInventory(I18n.get().getLegacy("inventory.title", Placeholder.parsed("name", name)));
        }
    }
    
    // 保存前准备
    public void prepareSave() {
        if (inventory != null) {
            this.inventoryData = inventory.toBase64();
        }
    }

    public boolean isHungry() {
        // 饱食度低于20% 或者心情过低都算饿/状态不好，用于显示气泡
        // 但 DailyRequest 逻辑里 isHungry 可能指需要投喂
        // 这里为了兼容气泡逻辑：
        return hunger < getMaxHunger() * 0.2;
    }
    
    private void initTransientFields() {
        // this.velocity = new Vector(0, 0, 0);
        // this.bobbingOffset = 0;
        this.currentExpression = SpiritSkinManager.Expression.NORMAL;
        this.lastParticleLocation = null;
        this.lastLightLocation = null;
        this.lastLightPacketTime = 0;
        this.bubbleEntityId = null;
        this.bubbleEndTime = 0;
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
        removeBubble();  // 清理气泡
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

    private boolean tryLinkExistingEntity(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        
        NamespacedKey key = new NamespacedKey(EarthSpiritPlugin.getInstance(), "spirit_owner");
        // 扩大搜索范围，防止实体轻微位移导致找不到
        // 增加到 10 格
        for (Entity e : loc.getWorld().getNearbyEntities(loc, 10, 10, 10)) {
            if (e instanceof ArmorStand) {
                // 优先检查 PDC
                if (e.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                    String ownerStr = e.getPersistentDataContainer().get(key, PersistentDataType.STRING);
                    if (ownerStr != null && ownerStr.equals(ownerId.toString())) {
                        // 找到地灵实体
                        this.entityId = e.getUniqueId();
                        
                        // 尝试寻找 Driver
                        for (Entity d : loc.getWorld().getNearbyEntities(e.getLocation(), 5, 5, 5)) {
                            if (d instanceof Wolf) {
                                if (d.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                                    String dOwner = d.getPersistentDataContainer().get(key, PersistentDataType.STRING);
                                    if (dOwner != null && dOwner.equals(ownerId.toString())) {
                                        this.driverId = d.getUniqueId();
                                        break;
                                    }
                                }
                            }
                        }
                        
                        // 如果 Driver 丢失，重新生成
                        if (driverId == null || Bukkit.getEntity(driverId) == null) {
                            spawnDriver(e.getLocation());
                        }
                        
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void spawnEntity(Location loc) {
        if (loc.getWorld() == null) return;
        
        // 尝试重连现有实体 (持久化支持)
        if (tryLinkExistingEntity(loc)) {
            return;
        }
        
        // 1. 清理该位置可能存在的残留地灵 (Ghost Entities)
        removeNearbyGhosts(loc);

        // 生成盔甲架
        ArmorStand as = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        as.setVisible(false);
        as.setGravity(false);
        as.setBasePlate(false);
        as.setSmall(true); // 小盔甲架更可爱
        as.setInvulnerable(true);
        as.setCustomName(name);
        as.setCustomNameVisible(true);
        
        // 标记为地灵实体 (PDC)
        NamespacedKey key = new NamespacedKey(EarthSpiritPlugin.getInstance(), "spirit_owner");
        as.getPersistentDataContainer().set(key, PersistentDataType.STRING, ownerId.toString());
        
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
    
    // 清理残留的幽灵实体 (重启服务器后可能残留的无主实体)
    private void removeNearbyGhosts(Location loc) {
        if (loc.getWorld() == null) return;
        NamespacedKey key = new NamespacedKey(EarthSpiritPlugin.getInstance(), "spirit_owner");
        
        // 扫描半径 2 格内的实体
        for (Entity e : loc.getWorld().getNearbyEntities(loc, 2, 2, 2)) {
            if (e instanceof ArmorStand || e instanceof Wolf) {
                // 判定条件 1: PDC 标签匹配 (新版逻辑)
                boolean isGhost = false;
                if (e.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                    String ownerStr = e.getPersistentDataContainer().get(key, PersistentDataType.STRING);
                    if (ownerStr != null && ownerStr.equals(ownerId.toString())) {
                        isGhost = true;
                    }
                } 
                // 判定条件 2: 名字匹配 (兼容旧版清理)
                else if (e.getCustomName() != null && e.getCustomName().equals(this.name)) {
                    isGhost = true;
                }
                
                if (isGhost) {
                    e.remove(); // 移除旧的实体
                }
            }
        }
    }

    private void spawnDriver(Location loc) {
        if (loc.getWorld() == null) return;
        Wolf wolf = (Wolf) loc.getWorld().spawnEntity(loc, EntityType.WOLF);
        
        // 标记为地灵实体 (PDC)
        NamespacedKey key = new NamespacedKey(EarthSpiritPlugin.getInstance(), "spirit_owner");
        wolf.getPersistentDataContainer().set(key, PersistentDataType.STRING, ownerId.toString());
        
        // 支持离线主人 (OfflinePlayer)
        org.bukkit.OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerId);
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
        wolf.setPersistent(true); // 允许保存到区块文件，支持重启持久化
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
        updateHunger();

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
        
        // 6. 悬浮气泡更新
        updateBubble();
        
        // 7. 饥饿反馈 (气泡)
        if (isHungry()) {
            long now = System.currentTimeMillis();
            // 缩短反馈间隔为 10秒
            if (now - lastHungerFeedbackTime > 10 * 1000) { 
                showBubble(I18n.get().getLegacy("bubble.hungry"), 60); // 显示3秒 (60 ticks)
                lastHungerFeedbackTime = now;
            }
        }
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
        // 检查玩家是否在线
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null) {
            // 玩家离线，不进行心情衰减，但更新时间戳防止上线瞬间结算大量衰减
            // 或者直接不更新时间戳？
            // 用户要求：玩家只要不上线，地灵的心情值就不会衰减。
            // 如果我们不更新 lastMoodUpdateTime，那么下次上线时 diff 会很大，导致一次性扣除。
            // 所以我们需要把 lastMoodUpdateTime “推进”到当前时间，或者在计算 diff 时排除离线时间。
            // 简单做法：如果离线，直接重置 lastMoodUpdateTime 为当前时间 (相当于这段时间冻结了)
            lastMoodUpdateTime = System.currentTimeMillis();
            return;
        }

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
        
        // 方案一：垂直高度修正
        // 基础高度：狼头顶 (防止穿模地面)
        double baseHeight = driverLoc.getY() + 1.2;
        
        // 默认跟随基础高度 (贴近地面/狼)
        double finalHeight = baseHeight;

        // 只有当高度差超过3格时 (例如玩家爬山)，才向上适配玩家高度
        // 解决平时浮得太高导致玩家需要一直抬头的问题
        if (owner.getLocation().getY() - driverLoc.getY() > 3.0) {
            double ownerHeight = owner.getLocation().getY() + 1.3; // 1.3 约等于胸口/肩膀高度
            finalHeight = Math.max(baseHeight, ownerHeight);
        }
        
        finalHeight += bobbing;
        
        targetLoc.setY(finalHeight);
        
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
             this.currentLocation = seatLoc.clone();
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
        chassis.setPersistent(true); // 允许持久化保存
        
        // 标记 PDC
        NamespacedKey key = new NamespacedKey(EarthSpiritPlugin.getInstance(), "spirit_owner");
        chassis.getPersistentDataContainer().set(key, PersistentDataType.STRING, ownerId.toString());
        
        // 底盘外观：平滑石台阶 (并设置 CustomModelData: 10003 以供资源包覆盖)
        ItemStack baseItem = new ItemStack(Material.SMOOTH_STONE_SLAB);
        ItemMeta baseMeta = baseItem.getItemMeta();
        if (baseMeta != null) {
            baseMeta.setCustomModelData(10003);
            baseItem.setItemMeta(baseMeta);
        }
        chassis.getEquipment().setHelmet(baseItem);

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
        // 地灵过继新逻辑：地灵不换主人，只转让居所，然后地灵消失
        
        com.palmergames.bukkit.towny.object.Town town = TownyIntegration.getTownAt(this.currentLocation);
        if (town != null && town.getName().equals(this.townName)) {
             TownyIntegration.transferOwnership(town.getName(), newOwner);
             newOwner.sendMessage("§a你通过不懈的努力，感化了这片土地，获得了居所的所有权！");
             newOwner.sendMessage("§e原来的地灵完成了它的使命，化作光点消散了...");
        } else {
             newOwner.sendMessage("§c过继异常：当前位置不在地灵管辖的居所内。");
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
        // 如果当前已经在居所内，不做处理
        if (townName != null && currentLocation != null) {
            com.palmergames.bukkit.towny.object.Town town = TownyIntegration.getTownAt(currentLocation);
            if (town != null && town.getName().equals(townName)) {
                return; 
            }
        }
        
        // 如果有居所，传送到居所重生点
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
                        I18n.get().send(owner, "messages.status.depressed-warning", Placeholder.parsed("name", name));
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
    
    // Old hunger methods removed (replaced below)
    
    public double getHunger() { return hunger; }
    
    public double getMaxHunger() {
        return ConfigManager.get().getMaxHungerBase() + (level * ConfigManager.get().getMaxHungerPerLevel());
    }
    
    public void setHunger(double hunger) { 
        double oldHunger = this.hunger;
        this.hunger = Math.max(0, Math.min(getMaxHunger(), hunger)); 
        
        // 状态改变(从0变有，或从有变0)，触发居所保护更新
        boolean wasStarving = (oldHunger <= 0);
        boolean isStarving = (this.hunger <= 0);
        
        if (wasStarving != isStarving) {
            checkProtectionUpdate();
        }
    }
    
    // Duplicate isHungry removed

    
    public void addHunger(double amount) {
        setHunger(this.hunger + amount);
    }
    
    public String getHungerBar() {
        double max = getMaxHunger();
        double ratio = hunger / max;
        int filled = (int) (ratio * 10);
        
        String filledChar = I18n.get().getRaw("gui.hunger-bar.filled");
        String emptyChar = I18n.get().getRaw("gui.hunger-bar.empty");
        
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            if (i < filled) bar.append(filledChar);
            else bar.append(emptyChar);
        }
        return I18n.get().getString("gui.hunger-bar.format", 
            Placeholder.parsed("bar", bar.toString()),
            Placeholder.parsed("current", String.valueOf((int)hunger)),
            Placeholder.parsed("max", String.valueOf((int)max))
        );
    }

    public void updateHunger() {
        long now = System.currentTimeMillis();
        if (lastHungerUpdateTime == 0) {
            lastHungerUpdateTime = now;
            return;
        }
        
        long decayInterval = ConfigManager.get().getHungerDecayInterval();
        long diff = now - lastHungerUpdateTime;
        if (diff > decayInterval) { 
            int intervalsPassed = (int) (diff / decayInterval);
            if (intervalsPassed > 0) {
                double decayAmount = ConfigManager.get().getHungerDecayAmount() * intervalsPassed;
                setHunger(hunger - decayAmount);
                lastHungerUpdateTime = now - (diff % decayInterval); // 保留余数时间
            }
        }
    }

    // 重置心情更新时间（玩家上线时调用，避免离线时间导致心情骤降）
    public void resetMoodTimer() {
        this.lastMoodUpdateTime = System.currentTimeMillis();
    }

    public void updateMood() {
        long now = System.currentTimeMillis();
        if (lastMoodUpdateTime == 0) {
            lastMoodUpdateTime = now;
            return;
        }
        
        long decayInterval = ConfigManager.get().getMoodDecayInterval();
        // 仅当玩家在线时调用此方法
        long diff = now - lastMoodUpdateTime;
        if (diff > decayInterval) {
            int intervalsPassed = (int) (diff / decayInterval);
            if (intervalsPassed > 0) {
                double decayAmount = ConfigManager.get().getMoodDecayAmount() * intervalsPassed;
                setMood(mood - decayAmount);
                lastMoodUpdateTime = now - (diff % decayInterval);
            }
        }
    }

    public void checkProtectionUpdate() {
        if (getTownName() == null) return;
        try {
            com.palmergames.bukkit.towny.object.Town town = com.palmergames.bukkit.towny.TownyUniverse.getInstance().getTown(getTownName());
            if (town != null) {
                TownyIntegration.updateTownPermissions(town);
            }
        } catch (Exception e) {}
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
    
    // public void addExp(int amount) { ... } // Removed duplicate method


    public DailyRequest getDailyRequest() {
        return dailyRequest;
    }

    public void setDailyRequest(DailyRequest dailyRequest) {
        this.dailyRequest = dailyRequest;
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

    // ---------------------------------------------------------
    // 悬浮气泡系统
    // ---------------------------------------------------------

    public void showBubble(String text, int durationTicks) {
        if (currentLocation == null || currentLocation.getWorld() == null) return;
        
        TextDisplay display = null;
        if (bubbleEntityId != null) {
            Entity e = Bukkit.getEntity(bubbleEntityId);
            if (e instanceof TextDisplay) {
                display = (TextDisplay) e;
            } else {
                if (e != null) e.remove();
                bubbleEntityId = null;
            }
        }
        
        if (display == null) {
            display = spawnBubbleEntity();
        }
        
        if (display != null) {
            display.setText(text);
            this.bubbleEndTime = System.currentTimeMillis() + durationTicks * 50L;
            // 播放音效
            currentLocation.getWorld().playSound(currentLocation, Sound.ITEM_BOOK_PAGE_TURN, 0.5f, 1.5f);
        }
    }

    private TextDisplay spawnBubbleEntity() {
        if (currentLocation == null || currentLocation.getWorld() == null) return null;
        
        TextDisplay display = (TextDisplay) currentLocation.getWorld().spawnEntity(
            currentLocation.clone().add(0, 1.5, 0), 
            EntityType.TEXT_DISPLAY
        );
        
        display.setBillboard(Display.Billboard.CENTER); // 始终面向玩家
        display.setBackgroundColor(Color.fromARGB(100, 0, 0, 0)); // 半透明黑色背景
        display.setSeeThrough(false);
        display.setShadowed(false);
        display.setLineWidth(200); // 自动换行宽度
        
        // 调整缩放，稍微小一点更精致
        display.setTransformation(new org.bukkit.util.Transformation(
            new Vector3f(0, 0, 0),
            new AxisAngle4f(0, 0, 0, 1),
            new Vector3f(0.5f, 0.5f, 0.5f), // Scale 0.5
            new AxisAngle4f(0, 0, 0, 1)
        ));
        
        this.bubbleEntityId = display.getUniqueId();
        return display;
    }

    private void updateBubble() {
        if (bubbleEntityId == null) return;
        
        Entity e = Bukkit.getEntity(bubbleEntityId);
        if (e == null || !e.isValid() || !(e instanceof TextDisplay)) {
            this.bubbleEntityId = null;
            return;
        }
        
        // 检查过期
        if (System.currentTimeMillis() > bubbleEndTime) {
            removeBubble();
            return;
        }
        
        // 跟随位置 (在头顶漂浮)
        if (currentLocation != null) {
            double heightOffset = (mode == SpiritMode.GUARDIAN) ? 1.2 : 1.8;
            Location target = currentLocation.clone().add(0, heightOffset, 0); // 调整高度
            e.teleport(target);
        }
    }

    private void removeBubble() {
        if (bubbleEntityId != null) {
            Entity e = Bukkit.getEntity(bubbleEntityId);
            if (e != null) {
                e.remove();
            }
            bubbleEntityId = null;
        }
    }

    // ==========================================
    // YAML Persistence Methods
    // ==========================================

    public void saveToConfig(ConfigurationSection section) {
        section.set("ownerId", ownerId.toString());
        section.set("name", name);
        if (townName != null) section.set("townName", townName);
        section.set("mood", mood);
        section.set("hunger", hunger);
        section.set("level", level);
        section.set("exp", exp);
        section.set("mode", mode.name());
        section.set("type", type.name());
        
        // Save Entity IDs
        if (entityId != null) section.set("entityId", entityId.toString());
        if (driverId != null) section.set("driverId", driverId.toString());
        if (chassisEntityId != null) section.set("chassisEntityId", chassisEntityId.toString());
        
        prepareSave(); // Prepare inventoryData
        if (inventoryData != null) section.set("inventoryData", inventoryData);
        
        // Save Trust & Partner
        if (!trustedPlayers.isEmpty()) {
            java.util.List<String> trustedList = new java.util.ArrayList<>();
            for (UUID uuid : trustedPlayers) {
                trustedList.add(uuid.toString());
            }
            section.set("trustedPlayers", trustedList);
        }
        if (partnerId != null) {
            section.set("partnerId", partnerId.toString());
        }
        
        // Save maps
        ConfigurationSection feedDays = section.createSection("strangerFeedDays");
        strangerFeedDays.forEach((k, v) -> feedDays.set(k.toString(), v));
        
        ConfigurationSection feedTime = section.createSection("lastFeedTime");
        lastFeedTime.forEach((k, v) -> feedTime.set(k.toString(), v));
        
        if (currentLocation != null) section.set("currentLocation", currentLocation);
        
        section.set("lastFoodTime", lastFoodTime);
        section.set("lastInteractTime", lastInteractTime);
        section.set("lastMoodUpdateTime", lastMoodUpdateTime);
        section.set("lastHungerUpdateTime", lastHungerUpdateTime);
        
        if (dailyRequest != null) {
            ConfigurationSection daily = section.createSection("dailyRequest");
            daily.set("date", dailyRequest.date);
            daily.set("grade", dailyRequest.grade);
            daily.set("rewardsClaimed", dailyRequest.rewardsClaimed);
            ConfigurationSection items = daily.createSection("items");
            dailyRequest.items.forEach((id, item) -> {
                ConfigurationSection itemSec = items.createSection(String.valueOf(id));
                itemSec.set("key", item.key);
                itemSec.set("amount", item.amount);
                itemSec.set("submitted", item.submitted);
            });
        }
    }

    public static SpiritEntity fromConfig(ConfigurationSection section) {
        try {
            UUID ownerId = UUID.fromString(section.getString("ownerId"));
            String name = section.getString("name");
            Location loc = section.getLocation("currentLocation");
            
            if (loc == null) {
                // Prevent NPE in constructor
                loc = new Location(Bukkit.getWorld("world"), 0, 100, 0); 
            }
            
            // 读取保存的实体 ID
            UUID savedEntityId = null;
            if (section.contains("entityId")) savedEntityId = UUID.fromString(section.getString("entityId"));
            
            UUID savedDriverId = null;
            if (section.contains("driverId")) savedDriverId = UUID.fromString(section.getString("driverId"));
            
            UUID savedChassisId = null;
            if (section.contains("chassisEntityId")) savedChassisId = UUID.fromString(section.getString("chassisEntityId"));

            // 使用新构造函数，暂时不生成实体 (false)
            SpiritEntity spirit = new SpiritEntity(ownerId, name, loc, false);
            
            spirit.townName = section.getString("townName");
            spirit.mood = section.getDouble("mood");
            spirit.hunger = section.getDouble("hunger");
            spirit.level = section.getInt("level");
            spirit.exp = section.getInt("exp");
            spirit.mode = SpiritMode.valueOf(section.getString("mode", "COMPANION"));
            spirit.type = SpiritType.valueOf(section.getString("type", "NORMAL"));
            spirit.inventoryData = section.getString("inventoryData");
            
            // Load Trust & Partner
            if (section.contains("trustedPlayers")) {
                for (String uuidStr : section.getStringList("trustedPlayers")) {
                    try {
                        spirit.trustedPlayers.add(UUID.fromString(uuidStr));
                    } catch (IllegalArgumentException e) {}
                }
            }
            if (section.contains("partnerId")) {
                try {
                    spirit.partnerId = UUID.fromString(section.getString("partnerId"));
                } catch (IllegalArgumentException e) {}
            }
            
            ConfigurationSection feedDays = section.getConfigurationSection("strangerFeedDays");
            if (feedDays != null) {
                for (String key : feedDays.getKeys(false)) {
                    spirit.strangerFeedDays.put(UUID.fromString(key), feedDays.getInt(key));
                }
            }
            
            ConfigurationSection feedTime = section.getConfigurationSection("lastFeedTime");
            if (feedTime != null) {
                for (String key : feedTime.getKeys(false)) {
                    spirit.lastFeedTime.put(UUID.fromString(key), feedTime.getLong(key));
                }
            }
            
            spirit.lastFoodTime = section.getLong("lastFoodTime");
            spirit.lastInteractTime = section.getLong("lastInteractTime");
            spirit.lastMoodUpdateTime = section.getLong("lastMoodUpdateTime");
            spirit.lastHungerUpdateTime = section.getLong("lastHungerUpdateTime");
            
            if (section.isConfigurationSection("dailyRequest")) {
                ConfigurationSection daily = section.getConfigurationSection("dailyRequest");
                spirit.dailyRequest = new DailyRequest();
                spirit.dailyRequest.date = daily.getLong("date");
                spirit.dailyRequest.grade = daily.getString("grade");
                spirit.dailyRequest.rewardsClaimed = daily.getBoolean("rewardsClaimed");
                
                ConfigurationSection items = daily.getConfigurationSection("items");
                if (items != null) {
                    for (String key : items.getKeys(false)) {
                        ConfigurationSection itemSec = items.getConfigurationSection(key);
                        DailyRequest.TaskItem item = new DailyRequest.TaskItem();
                        item.key = itemSec.getString("key");
                        item.amount = itemSec.getInt("amount");
                        item.submitted = itemSec.getBoolean("submitted");
                        spirit.dailyRequest.items.put(Integer.parseInt(key), item);
                    }
                }
            }
            
            // 加载并链接实体
            spirit.loadEntity(loc, savedEntityId, savedDriverId, savedChassisId);
            
            spirit.initAfterLoad();
            return spirit;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
