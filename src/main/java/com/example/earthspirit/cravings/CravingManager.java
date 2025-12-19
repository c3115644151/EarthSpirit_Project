package com.example.earthspirit.cravings;

import com.example.earthspirit.EarthSpiritPlugin;
import com.example.earthspirit.SpiritEntity;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class CravingManager {
    private final Map<String, Integer> itemWeights = new HashMap<>();
    private final Map<String, String> itemDisplayNames = new HashMap<>();

    private final List<RewardEntry> rewardPool = new ArrayList<>();

    public CravingManager(EarthSpiritPlugin plugin) {
        loadConfig();
        initRewards();
    }

    private void initRewards() {
        // Basic rewards (available to all grades)
        rewardPool.add(new RewardEntry(new ItemStack(Material.BREAD), 1, 3, 50, "C"));
        rewardPool.add(new RewardEntry(new ItemStack(Material.APPLE), 1, 3, 40, "C"));
        rewardPool.add(new RewardEntry(new ItemStack(Material.COAL), 1, 5, 40, "C"));
        
        // Better rewards (B grade and up)
        rewardPool.add(new RewardEntry(new ItemStack(Material.IRON_INGOT), 1, 3, 30, "B"));
        rewardPool.add(new RewardEntry(new ItemStack(Material.GOLD_INGOT), 1, 3, 20, "B"));
        rewardPool.add(new RewardEntry(new ItemStack(Material.EMERALD), 1, 2, 15, "B"));
        
        // Rare rewards (A grade and up)
        rewardPool.add(new RewardEntry(new ItemStack(Material.DIAMOND), 1, 1, 10, "A"));
        rewardPool.add(new RewardEntry(new ItemStack(Material.GOLDEN_APPLE), 1, 1, 5, "A"));
        
        // Special Spirit items (A/S grade) - using placeholders for now if BiomeGifts not present
        // In real implementation, check for BiomeGifts items
    }

    private void loadConfig() {
        // Hardcoded for now, ideally from config.yml
        // Vanilla Low Tier (1 point)
        registerItem("CARROT", 1, "胡萝卜");
        registerItem("POTATO", 1, "马铃薯");
        registerItem("WHEAT", 1, "小麦");
        registerItem("MELON_SLICE", 1, "西瓜片");
        registerItem("BEETROOT", 1, "甜菜根");
        
        // Vanilla Mid Tier (3 points)
        registerItem("BREAD", 3, "面包");
        registerItem("BAKED_POTATO", 3, "烤马铃薯");
        registerItem("COOKIE", 3, "曲奇");
        registerItem("PUMPKIN_PIE", 3, "南瓜派");
        registerItem("APPLE", 3, "苹果");
        
        // Vanilla High Tier (5 points)
        registerItem("CAKE", 5, "蛋糕");
        registerItem("GOLDEN_CARROT", 5, "金胡萝卜");
        
        // Custom BiomeGifts Items (10-20 points)
        registerItem("GOLDEN_WHEAT", 10, "黄金麦穗");
        registerItem("TROPICAL_NECTAR", 10, "热带糖蜜");
        registerItem("FROST_BERRY", 10, "霜糖果实");
        registerItem("WATER_GEL", 10, "储水凝胶");
    }

    private void registerItem(String key, int weight, String name) {
        itemWeights.put(key, weight);
        itemDisplayNames.put(key, name);
    }

    public void checkRollover(SpiritEntity spirit) {
        if (spirit.getDailyRequest() == null) {
            forceRefresh(spirit);
        }
        // If exists, we do NOT overwrite it automatically.
        // We wait for user to click "Give Up" or if it was completed?
        // User said: "keep player didn't finish task".
        // If completed, maybe auto refresh? 
        // "Every day, spirit refreshes a list".
        // If I completed yesterday's, I should get today's automatically.
        
        long today = LocalDate.now().toEpochDay();
        Calendar cal = Calendar.getInstance();
        if (cal.get(Calendar.HOUR_OF_DAY) < 4) today--;
        
        if (spirit.getDailyRequest().rewardsClaimed && spirit.getDailyRequest().date < today) {
            // Auto refresh if previous was completed and it's a new day
            forceRefresh(spirit);
        }
    }

    public void claimReward(Player player, SpiritEntity spirit) {
        DailyRequest req = spirit.getDailyRequest();
        if (req == null || req.rewardsClaimed) return;
        
        // Double check all submitted
        boolean allSubmitted = req.items.values().stream().allMatch(t -> t.submitted);
        if (!allSubmitted) return;
        
        req.rewardsClaimed = true;
        
        // 1. Base Mood/Exp Rewards based on Grade
        int mood = 10;
        int exp = 20;
        switch (req.grade) {
            case "B" -> { mood = 20; exp = 40; }
            case "A" -> { mood = 35; exp = 70; }
            case "S" -> { mood = 50; exp = 100; }
        }
        
        spirit.addMood(mood);
        spirit.addExp(exp);
        
        player.sendMessage("§a[地灵] §f好开心！谢谢你的款待！");
        player.sendMessage("§e地灵心情 +" + mood + ", 经验 +" + exp);
        
        // 2. Pick Items from Reward Pool
        List<ItemStack> rewards = pickRewards(req.grade);
        if (!rewards.isEmpty()) {
            player.sendMessage("§f获得了回礼:");
            for (ItemStack is : rewards) {
                // Give item
                HashMap<Integer, ItemStack> left = player.getInventory().addItem(is);
                // Drop if full
                if (!left.isEmpty()) {
                    for (ItemStack drop : left.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), drop);
                    }
                    player.sendMessage("§c背包已满，部分物品掉落在地上！");
                }
                
                String name = is.getType().name();
                if (is.hasItemMeta() && is.getItemMeta().hasDisplayName()) {
                    name = is.getItemMeta().getDisplayName();
                }
                player.sendMessage("§6- " + name + " x" + is.getAmount());
            }
        }
        
        // 3. Special S/A Grade Reward (BiomeGifts - Spirit Wheat Seeds)
        if (req.grade.equals("S") || req.grade.equals("A")) {
             ItemStack seed = getBiomeGiftsItem("SPIRIT_WHEAT_SEEDS");
             if (seed != null) {
                 if (req.grade.equals("S")) seed.setAmount(2);
                 player.getInventory().addItem(seed);
                 player.sendMessage("§d§l[稀有] §f你额外获得了一枚 §d灵契之种§f！");
             }
        }
    }
    
    private List<ItemStack> pickRewards(String grade) {
        List<ItemStack> results = new ArrayList<>();
        List<RewardEntry> valid = new ArrayList<>();
        
        // Filter by grade
        int gradeVal = getGradeValue(grade);
        for (RewardEntry entry : rewardPool) {
            if (getGradeValue(entry.minGrade) <= gradeVal) {
                valid.add(entry);
            }
        }
        
        if (valid.isEmpty()) return results;
        
        // Pick 1-3 items
        int picks = ThreadLocalRandom.current().nextInt(1, 4);
        
        for (int i = 0; i < picks; i++) {
            // Weighted random
            int totalWeight = valid.stream().mapToInt(e -> e.weight).sum();
            int r = ThreadLocalRandom.current().nextInt(totalWeight);
            int current = 0;
            
            for (RewardEntry entry : valid) {
                current += entry.weight;
                if (r < current) {
                    ItemStack is = entry.item.clone();
                    int amount = ThreadLocalRandom.current().nextInt(entry.min, entry.max + 1);
                    is.setAmount(amount);
                    results.add(is);
                    break;
                }
            }
        }
        return results;
    }
    
    private int getGradeValue(String grade) {
        switch (grade) {
            case "C": return 1;
            case "B": return 2;
            case "A": return 3;
            case "S": return 4;
            default: return 0;
        }
    }

    private static class RewardEntry {
        ItemStack item;
        int min;
        int max;
        int weight;
        String minGrade;

        public RewardEntry(ItemStack item, int min, int max, int weight, String minGrade) {
            this.item = item;
            this.min = min;
            this.max = max;
            this.weight = weight;
            this.minGrade = minGrade;
        }
    }
    
    public void forceRefresh(SpiritEntity spirit) {
         long today = LocalDate.now().toEpochDay();
         Calendar cal = Calendar.getInstance();
         if (cal.get(Calendar.HOUR_OF_DAY) < 4) {
             today = today - 1;
         }
         spirit.setDailyRequest(generateNewRequest(today));
    }

    private DailyRequest generateNewRequest(long date) {
        DailyRequest request = new DailyRequest();
        request.date = date;
        request.rewardsClaimed = false;

        List<String> keys = new ArrayList<>(itemWeights.keySet());
        // Shuffle to ensure uniqueness when picking
        Collections.shuffle(keys);
        
        int count = ThreadLocalRandom.current().nextInt(3, 6); // 3-5 items
        // Ensure count doesn't exceed available keys
        if (count > keys.size()) count = keys.size();
        
        int totalScore = 0;

        for (int i = 0; i < count; i++) {
            String key = keys.get(i); // Pick distinct keys sequentially from shuffled list
            int amount = ThreadLocalRandom.current().nextInt(1, 6); // 1-5 amount
            
            // Adjust amount for high value items
            if (itemWeights.get(key) >= 10) amount = Math.max(1, amount / 2);
            if (itemWeights.get(key) >= 5) amount = Math.max(1, amount - 1);

            request.items.put(i, new DailyRequest.TaskItem(key, amount));
            totalScore += itemWeights.get(key) * amount;
        }

        // Calculate Grade
        if (totalScore < 15) request.grade = "C";
        else if (totalScore < 30) request.grade = "B";
        else if (totalScore < 50) request.grade = "A";
        else request.grade = "S";

        return request;
    }

    public ItemStack getItemStack(String key) {
        // Try BiomeGifts first
        ItemStack custom = getBiomeGiftsItem(key);
        if (custom != null) return custom;

        // Fallback to Material
        Material mat = Material.getMaterial(key);
        if (mat != null) return new ItemStack(mat);

        return new ItemStack(Material.BARRIER);
    }
    
    public String getDisplayName(String key) {
        return itemDisplayNames.getOrDefault(key, key);
    }

    private ItemStack getBiomeGiftsItem(String key) {
        Plugin bg = Bukkit.getPluginManager().getPlugin("BiomeGifts");
        if (bg != null && bg.isEnabled()) {
            try {
                Method getMgr = bg.getClass().getMethod("getItemManager");
                Object itemMgr = getMgr.invoke(bg);
                Method getItem = itemMgr.getClass().getMethod("getItem", String.class);
                return (ItemStack) getItem.invoke(itemMgr, key);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }
    
    public ItemStack getDisplayItem(String key) {
        Material m = Material.matchMaterial(key);
        if (m != null) {
            ItemStack is = new ItemStack(m);
            ItemMeta meta = is.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§f" + getDisplayName(key));
                is.setItemMeta(meta);
            }
            return is;
        }
        ItemStack custom = getBiomeGiftsItem(key);
        if (custom != null) {
            return custom;
        }
        return new ItemStack(Material.BARRIER);
    }

    public boolean isItemMatch(ItemStack item, String key) {
        if (item == null) return false;
        Material m = Material.matchMaterial(key);
        if (m != null) {
            return item.getType() == m;
        }
        ItemStack custom = getBiomeGiftsItem(key);
        if (custom != null) {
            return item.isSimilar(custom);
        }
        return false;
    }
}
