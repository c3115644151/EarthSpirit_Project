package com.example.earthspirit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

public class SpiritAnimationTask extends BukkitRunnable {
    private final EarthSpiritPlugin plugin;
    private double time = 0;

    public SpiritAnimationTask(EarthSpiritPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        time += 0.1; // 时间增量
        
        for (SpiritEntity spiritData : plugin.getManager().getAllSpirits().values()) {
            Entity entity = Bukkit.getEntity(spiritData.getEntityId());
            
            if (entity == null || !entity.isValid() || !(entity instanceof ArmorStand)) {
                continue; 
            }

            ArmorStand as = (ArmorStand) entity;

            // 1. 悬浮动画 (上下浮动) & 呼吸效果
            double headX = Math.sin(time * 0.5) * 0.1; 
            
            // 2. 头部追踪逻辑 (Look At Player)
            Player target = null;
            double minDist = 15.0; // 增加追踪距离
            
            for (Player p : as.getWorld().getPlayers()) {
                double dist = p.getLocation().distance(as.getLocation());
                if (dist < minDist) {
                    minDist = dist;
                    target = p;
                }
            }

            if (target != null) {
                // 计算朝向
                Location lookLoc = target.getEyeLocation();
                Location spiritLoc = as.getEyeLocation();
                Vector dir = lookLoc.toVector().subtract(spiritLoc.toVector());
                
                // 1. 设置身体朝向 (Yaw) - 让身体正面朝向玩家
                Location newLoc = as.getLocation();
                newLoc.setDirection(dir);
                float targetYaw = newLoc.getYaw();
                
                // 使用 teleport 强制同步朝向
                Location teleportLoc = as.getLocation();
                teleportLoc.setYaw(targetYaw);
                as.teleport(teleportLoc);
                
                // 2. 设置头部俯仰 (Pitch) -> EulerAngle X
                // ArmorStand 的 HeadPose 是相对于身体的。
                // 因为身体已经朝向玩家了，所以 HeadPose 的 Y (Yaw) 应该是 0
                // 我们只需要调整 X (Pitch) 来看上或看下
                double pitch = Math.asin(-dir.getY() / dir.length());
                as.setHeadPose(new EulerAngle(pitch + headX, 0, 0)); 
            } else {
                // 没人时，缓慢旋转
                Location currentLoc = as.getLocation();
                currentLoc.setYaw(currentLoc.getYaw() + 2.0f); 
                as.teleport(currentLoc);
                as.setHeadPose(new EulerAngle(headX, 0, 0));
            }
            
            // 3. 眨眼逻辑 (随机)
            // 每隔 ~4 秒 (40 tick * 2 = 80 runs)
            if (Math.random() < 0.02 && spiritData.getExpression() == SpiritSkinManager.Expression.NORMAL) {
                spiritData.setExpression(SpiritSkinManager.Expression.BLINK, 5); // 眨眼 5 tick (0.25s)
            }
            
            // 4. 更新皮肤 (如果表情变化)
            updateSkin(as, spiritData);
        }
    }
    
    private void updateSkin(ArmorStand as, SpiritEntity data) {
        SpiritSkinManager.Expression targetExp = data.getExpression();
        // 这里需要检查是否需要更新，避免重复设置 ItemStack 导致网络浪费
        // 但 ArmorStand 获取 Helmet 比较轻量，我们比较 NBT 可能比较麻烦
        // 简单策略：存储上一次的 Expression 在 Entity 里，如果不一致才更新
        // 为了简化，这里直接更新 (实际生产环境建议缓存 lastExpression)
        
        // 优化：只有当皮肤真正改变时才 setHelmet
        // 我们可以把 "currentAppliedExpression" 存在 SpiritEntity 里
        // 这里暂时省略该优化，直接设置
        
        as.getEquipment().setHelmet(SpiritSkinManager.getHead(targetExp));
    }
}
