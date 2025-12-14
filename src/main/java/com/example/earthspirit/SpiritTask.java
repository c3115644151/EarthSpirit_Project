package com.example.earthspirit;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class SpiritTask extends BukkitRunnable {
    private final EarthSpiritPlugin plugin;

    public SpiritTask(EarthSpiritPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        for (SpiritEntity spiritData : plugin.getManager().getAllSpirits().values()) {
            Entity entity = Bukkit.getEntity(spiritData.getEntityId());
            
            // 如果实体消失（区块未加载或被意外清除），暂时跳过或处理重生逻辑
            if (entity == null || !entity.isValid()) {
                continue; 
            }

            // 1. 播放特效 (心跳效果)
            playEffects(entity, spiritData);

            // 2. 环境扫描与形态进化 (每隔一段时间执行，这里简化为每次都检查，实际应降频)
            // 为了性能，建议用随机数降低频率，例如 10% 概率执行扫描
            if (Math.random() < 0.1) {
                updateSpiritType(entity, spiritData);
            }

            // 3. 状态检查 (遗弃判定与心情衰减)
            updateMoodAndCheckAbandonment(entity, spiritData);
        }
    }

    private void playEffects(Entity entity, SpiritEntity data) {
        if (data.isAbandoned()) {
            // 被遗弃：蓝色哭泣粒子
            entity.getWorld().spawnParticle(Particle.DRIPPING_WATER, entity.getLocation().add(0, 1.5, 0), 5, 0.3, 0.3, 0.3, 0);
        } else if (data.getMood() > 80) {
            // 开心：爱心粒子
            entity.getWorld().spawnParticle(Particle.HEART, entity.getLocation().add(0, 2, 0), 1);
        } else {
            // 普通：快乐音符
            entity.getWorld().spawnParticle(Particle.NOTE, entity.getLocation().add(0, 2, 0), 1);
        }
    }

    private void updateSpiritType(Entity entity, SpiritEntity data) {
        int flowerCount = 0;
        int oreCount = 0;
        
        // 扫描 10x10x10
        for (int x = -5; x <= 5; x++) {
            for (int y = -5; y <= 5; y++) {
                for (int z = -5; z <= 5; z++) {
                    Block b = entity.getLocation().clone().add(x, y, z).getBlock();
                    if (b.getType().name().contains("FLOWER") || b.getType().name().contains("TULIP") || b.getType().name().contains("ROSE")) {
                        flowerCount++;
                    } else if (b.getType().name().contains("ORE") || b.getType() == Material.FURNACE) {
                        oreCount++;
                    }
                }
            }
        }

        if (flowerCount > 20 && data.getType() != SpiritEntity.SpiritType.FLOWER) {
            data.setType(SpiritEntity.SpiritType.FLOWER);
            plugin.getLogger().info("地灵 " + data.getName() + " 进化为花仙子!");
            // TODO: 更换头颅皮肤为花仙子皮肤
            // ((ArmorStand) entity).getEquipment().setHelmet(customSkull);
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 2);
        } else if (oreCount > 20 && data.getType() != SpiritEntity.SpiritType.MINER) {
            data.setType(SpiritEntity.SpiritType.MINER);
            plugin.getLogger().info("地灵 " + data.getName() + " 进化为矿工!");
            // TODO: 更换头颅皮肤为矿工皮肤
        }
    }

    private void updateMoodAndCheckAbandonment(Entity entity, SpiritEntity data) {
        // 心情自然衰减算法
        // 目标：每天(24h)大约衰减 10 点心情，这样玩家不需要太频繁操作，但也不能完全不管
        // 10点 / 24小时 / 60分钟 = 0.007 点/分钟
        // 我们的任务每 5 秒运行一次 (100 ticks)
        // 衰减量 = 10 / (24 * 60 * 12) = 0.00058 每次
        
        double decay = 0.0006; 
        
        // 如果饥饿且未投喂，加速衰减
        if (data.isHungry()) {
            long overdue = System.currentTimeMillis() - data.getNextHungerTime();
            if (overdue > 3600000L) { // 超过1小时
                decay *= 2;
                // 偶尔显示生气符号
                if (Math.random() < 0.05) {
                    entity.getWorld().spawnParticle(Particle.LAVA, entity.getLocation().add(0, 2, 0), 1);
                }
            }
            if (overdue > 18000000L) { // 超过5小时
                decay *= 5;
            }
        }

        if (!data.isAbandoned()) {
            data.setMood(data.getMood() - decay);
        }

        if (data.isAbandoned()) {
            if (data.getType() != SpiritEntity.SpiritType.SAD) {
                data.setType(SpiritEntity.SpiritType.SAD);
                data.setMood(0);
                // 广播或通知
                Player owner = Bukkit.getPlayer(data.getOwnerId());
                if (owner != null) {
                    owner.sendMessage("§c你的地灵 " + data.getName() + " 因为长期被忽视，已经陷入抑郁了！领地保护正在失效！");
                }
                // 这里可以调用 Towny 指令来开放权限
                // Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "town set perm " + data.getName() + " on");
            }
        }
    }
}
