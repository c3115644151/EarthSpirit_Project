package com.example.earthspirit;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SpiritEntity {
    private UUID entityId; // 实体UUID (ArmorStand)
    private UUID ownerId;  // 主人UUID
    private String name;   // 地灵名字
    private long lastFedTime; // 上次喂食时间戳
    private double mood;      // 心情值 (0-100)
    private SpiritType type;  // 形态
    private Map<UUID, Integer> strangerFeeds; // 陌生人喂食记录 (用于过继)
    private String townName; // 关联的 Towny 城镇名
    private long nextHungerTime; // 下次饥饿时间

    private long lastInteractTime; // 上次互动时间戳
    
    // 动态表情状态
    private SpiritSkinManager.Expression currentExpression = SpiritSkinManager.Expression.NORMAL;
    private long expressionEndTime = 0; // 表情结束时间

    private int level;
    private int exp;

    public SpiritEntity(UUID entityId, UUID ownerId, String name, String townName) {
        this.entityId = entityId;
        this.ownerId = ownerId;
        this.name = name;
        this.townName = townName;
        this.lastFedTime = System.currentTimeMillis();
        this.lastInteractTime = 0;
        this.mood = 60.0;
        this.type = SpiritType.NORMAL;
        this.strangerFeeds = new HashMap<>();
        this.level = 1;
        this.exp = 0;
        scheduleNextHunger();
    }
    
    public void setExpression(SpiritSkinManager.Expression exp, int durationTicks) {
        this.currentExpression = exp;
        this.expressionEndTime = System.currentTimeMillis() + (durationTicks * 50L);
    }
    
    public SpiritSkinManager.Expression getExpression() {
        if (System.currentTimeMillis() > expressionEndTime && currentExpression != SpiritSkinManager.Expression.NORMAL) {
            // 表情过期，恢复正常 (除非是特殊状态如 Sad)
            if (isAbandoned()) return SpiritSkinManager.Expression.SAD;
            return SpiritSkinManager.Expression.NORMAL;
        }
        return currentExpression;
    }

    public void scheduleNextHunger() {
        // 随机 3 - 4.8 小时 (10800s - 17280s)
        long min = 10800000L;
        long max = 17280000L;
        long delay = min + (long)(Math.random() * (max - min));
        this.nextHungerTime = System.currentTimeMillis() + delay;
    }

    public boolean isHungry() {
        return System.currentTimeMillis() >= nextHungerTime;
    }

    public long getNextHungerTime() { return nextHungerTime; }

    public enum SpiritType {
        NORMAL("普通灵"),
        FLOWER("花仙子"),
        MINER("矿灵"),
        SAD("忧郁灵");

        private final String displayName;
        SpiritType(String displayName) {
            this.displayName = displayName;
        }
        public String getDisplayName() { return displayName; }
    }

    // Getters and Setters
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    
    public int getExp() { return exp; }
    public void setExp(int exp) { this.exp = exp; }
    
    public void addExp(int amount) {
        this.exp += amount;
    }
    
    public UUID getEntityId() { return entityId; }
    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getTownName() { return townName; }
    public void setTownName(String townName) { this.townName = townName; }

    public long getLastFedTime() { return lastFedTime; }
    public void feed() { this.lastFedTime = System.currentTimeMillis(); }
    
    public long getLastInteractTime() { return lastInteractTime; }
    public void interact() { this.lastInteractTime = System.currentTimeMillis(); }

    public double getMood() { return mood; }
    public void setMood(double mood) { this.mood = Math.max(0, Math.min(100, mood)); }
    
    public SpiritType getType() { return type; }
    public void setType(SpiritType type) { this.type = type; }
    
    public Map<UUID, Integer> getStrangerFeeds() { return strangerFeeds; }
    
    public boolean isAbandoned() {
        // 7天未喂食视为遗弃 (7 * 24 * 60 * 60 * 1000)
        return System.currentTimeMillis() - lastFedTime > 604800000L;
    }
}
