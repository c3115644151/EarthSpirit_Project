package com.example.earthspirit;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.time.LocalDate;

import com.example.earthspirit.cravings.DailyRequest;
import com.example.earthspirit.cravings.CravingManager;
import com.example.earthspirit.configuration.ConfigManager;
import com.example.earthspirit.configuration.I18n;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class SpiritGUI {

    public static String getGuiTitle() {
        return I18n.get().getLegacy("gui.title");
    }
    
    public static String getSubGuiTitle() {
        return I18n.get().getLegacy("gui.sub-title");
    }

    public static final String GUI_TITLE = "§8[ §2地灵羁绊 §8] §0守护面板"; // Kept for compatibility if needed, but should be deprecated
    public static final String SUB_GUI_TITLE = "§8[ §2地灵羁绊 §8] §0居所管理";

    public static void openMenu(Player player, SpiritEntity spirit) {
        // 创建一个 3行 (27格) 的界面
        Inventory inv = Bukkit.createInventory(null, 27, getGuiTitle());

        // 权限检查
        boolean isOwner = player.getUniqueId().equals(spirit.getOwnerId());
        boolean isResident = TownyIntegration.isResident(spirit.getTownName(), player);
        boolean canInteract = isOwner || isResident;

        // 1. 背景板
        ItemStack bg = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.background", "BLACK_STAINED_GLASS_PANE")), 
            I18n.get().getLegacy("gui.items.background.name"));
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, bg);
        }

        // 2. 核心状态 (中间 - 头颅)
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta headMeta = (SkullMeta) head.getItemMeta();
        if (headMeta != null) {
            headMeta.setOwningPlayer(Bukkit.getOfflinePlayer(spirit.getOwnerId())); // 显示主人的头
            
            headMeta.setDisplayName(I18n.get().getLegacy("gui.items.head.name", 
                Placeholder.parsed("name", spirit.getName())));
            
            String status;
            if (spirit.isAbandoned()) {
                status = I18n.get().getLegacy("status.abandoned");
            } else if (spirit.getTownName() == null) {
                status = I18n.get().getLegacy("status.wandering");
            } else if (spirit.getMode() == SpiritEntity.SpiritMode.COMPANION) {
                status = I18n.get().getLegacy("status.following");
            } else {
                status = I18n.get().getLegacy("status.guarding");
            }

            // 如果是主人查看，且名字不一致，顺便更新一下数据
            if (isOwner) { 
                com.palmergames.bukkit.towny.object.Town t = TownyIntegration.getTown(player);
                if (t != null && !t.getName().equals(spirit.getTownName())) {
                     spirit.setTownName(t.getName());
                }
            }

            List<String> lore = I18n.get().getLegacyList("gui.items.head.lore",
                Placeholder.parsed("mode", spirit.getMode().getDisplayName()),
                Placeholder.parsed("hunger_bar", spirit.getHungerBar()),
                Placeholder.parsed("mood_bar", getMoodBar(spirit.getMood())),
                Placeholder.parsed("level", String.valueOf(spirit.getLevel())),
                Placeholder.parsed("exp", String.valueOf(spirit.getExp())),
                Placeholder.parsed("max_exp", String.valueOf(spirit.getLevel() * 100)),
                Placeholder.parsed("owner", Bukkit.getOfflinePlayer(spirit.getOwnerId()).getName()),
                Placeholder.parsed("status", status)
            );
            
            headMeta.setLore(lore);
            head.setItemMeta(headMeta);
        }
        inv.setItem(13, head);

        // 3. 互动按钮 (左侧 - 抚摸)
        if (canInteract) {
            ItemStack petBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.pet-button", "FEATHER")),
                I18n.get().getLegacy("gui.items.pet-button.name"),
                I18n.get().getLegacyList("gui.items.pet-button.lore").toArray(new String[0]));
            inv.setItem(11, petBtn);

            // 4. 投喂按钮 (右侧 - 蛋糕)
            ItemStack feedBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.feed-button", "CAKE")),
                I18n.get().getLegacy("gui.items.feed-button.name"),
                I18n.get().getLegacyList("gui.items.feed-button.lore").toArray(new String[0]));
            inv.setItem(15, feedBtn);
            
            // 4.5 背包按钮
            ItemStack bagBtn;
            if (spirit.getMode() == SpiritEntity.SpiritMode.COMPANION) {
                 bagBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.bag-button-active", "CHEST")),
                    I18n.get().getLegacy("gui.items.bag-button.name"),
                    I18n.get().getLegacyList("gui.items.bag-button.lore").toArray(new String[0]));
            } else {
                 bagBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.bag-button-inactive", "CHEST")),
                    I18n.get().getLegacy("gui.items.bag-button.name-inactive"),
                    I18n.get().getLegacyList("gui.items.bag-button.lore-inactive").toArray(new String[0]));
            }
            inv.setItem(4, bagBtn);

            // 4.6 嘴馋清单按钮
            List<String> cravingLore = new ArrayList<>();
            cravingLore.add("§7");
            
            DailyRequest req = spirit.getDailyRequest();
            if (req != null) {
                 long today = LocalDate.now().toEpochDay();
                 if (req.date == today) {
                     cravingLore.add(I18n.get().getLegacy("gui.items.craving-button.lore.today-grade", Placeholder.parsed("grade", String.valueOf(req.grade))));
                     cravingLore.add(I18n.get().getLegacy("gui.items.craving-button.lore.needs"));
                     
                     CravingManager cm = EarthSpiritPlugin.getInstance().getCravingManager();
                     for (DailyRequest.TaskItem task : req.items.values()) {
                         String itemName = "未知物品";
                         ItemStack is = cm.getDisplayItem(task.key);
                         if (is != null && is.getItemMeta() != null && is.getItemMeta().hasDisplayName()) {
                             itemName = is.getItemMeta().getDisplayName();
                         } else if (is != null) {
                             itemName = is.getType().name();
                         }
                         
                         String statusKey = task.submitted ? "gui.items.craving-button.lore.status-checked" : "gui.items.craving-button.lore.status-unchecked";
                         String status = I18n.get().getLegacy(statusKey);
                         
                         cravingLore.add(I18n.get().getLegacy("gui.items.craving-button.lore.item-entry",
                            Placeholder.parsed("item", itemName),
                            Placeholder.parsed("amount", String.valueOf(task.amount)),
                            Placeholder.parsed("status", status)
                         ));
                     }
                     
                     if (req.rewardsClaimed) {
                         cravingLore.add("§7");
                         cravingLore.add(I18n.get().getLegacy("gui.items.craving-button.lore.claimed"));
                     } else {
                         cravingLore.add("§7");
                         cravingLore.add(I18n.get().getLegacy("gui.items.craving-button.lore.click-details"));
                     }
                 } else {
                     cravingLore.add(I18n.get().getLegacy("gui.items.craving-button.lore.check-craving"));
                     cravingLore.add(I18n.get().getLegacy("gui.items.craving-button.lore.yesterday-unfinished"));
                     cravingLore.add(I18n.get().getLegacy("gui.items.craving-button.lore.refresh"));
                 }
            } else {
                cravingLore.add(I18n.get().getLegacy("gui.items.craving-button.lore.check-craving"));
                cravingLore.add(I18n.get().getLegacy("gui.items.craving-button.lore.daily-reward"));
                cravingLore.add(I18n.get().getLegacy("gui.items.craving-button.lore.daily-refresh"));
            }

            ItemStack cravingBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.craving-button", "PAPER")),
                I18n.get().getLegacy("gui.items.craving-button.name"),
                cravingLore.toArray(new String[0]));
            inv.setItem(18, cravingBtn);
        } else {
            // 访客模式显示灰色
            ItemStack noPerm = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.visitor-button", "GRAY_DYE")),
                I18n.get().getLegacy("gui.items.visitor-button.name"),
                I18n.get().getLegacyList("gui.items.visitor-button.lore").toArray(new String[0]));
            inv.setItem(11, noPerm);
            
            if (spirit.isAbandoned()) {
                 // 允许投喂被遗弃的地灵
                 ItemStack feedBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.feed-button", "CAKE")),
                    I18n.get().getLegacy("gui.items.feed-comfort-button.name"),
                    I18n.get().getLegacyList("gui.items.feed-comfort-button.lore").toArray(new String[0]));
                 inv.setItem(15, feedBtn);
            } else {
                 inv.setItem(15, noPerm);
            }
        }

        // 5. 居所管理入口 (底部中间)
        String townName = spirit.getTownName();
        
        if (townName == null) {
            // 无居所 -> 显示 "建立居所"
            if (canInteract && isOwner) {
                ItemStack createBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.create-home-button", "OAK_SAPLING")),
                    I18n.get().getLegacy("gui.items.create-home-button.name"),
                    I18n.get().getLegacyList("gui.items.create-home-button.lore").toArray(new String[0]));
                inv.setItem(22, createBtn);
            } else {
                ItemStack noTown = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.no-home-button", "DEAD_BUSH")),
                    I18n.get().getLegacy("gui.items.no-home-button.name"),
                    I18n.get().getLegacyList("gui.items.no-home-button.lore").toArray(new String[0]));
                inv.setItem(22, noTown);
            }
        } else {
            // 有居所 -> 显示 "居所管理" 和 "扩充居所"
            if (canInteract) {
                ItemStack manageBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.manage-home-button", "EMERALD")),
                    I18n.get().getLegacy("gui.items.manage-home-button.name"),
                    I18n.get().getLegacyList("gui.items.manage-home-button.lore", Placeholder.parsed("town", townName)).toArray(new String[0]));
                inv.setItem(22, manageBtn);
                
                if (isOwner) {
                    ItemStack expandBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.expand-home-button", "GOLDEN_SHOVEL")),
                        I18n.get().getLegacy("gui.items.expand-home-button.name"),
                        I18n.get().getLegacyList("gui.items.expand-home-button.lore", Placeholder.parsed("max", String.valueOf(1 + (spirit.getLevel()-1)*2))).toArray(new String[0]));
                    inv.setItem(20, expandBtn);
                }
            }
        }

        // 6. 更多功能 (重命名 & 解除契约)
            if (isOwner) {
                ItemStack renameSpiritBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.rename-button", "NAME_TAG")),
                    I18n.get().getLegacy("gui.items.rename-button.name"),
                    I18n.get().getLegacyList("gui.items.rename-button.lore", Placeholder.parsed("name", spirit.getName())).toArray(new String[0]));
                inv.setItem(24, renameSpiritBtn);
                
                ItemStack releaseBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.release-button", "SKELETON_SKULL")),
                    I18n.get().getLegacy("gui.items.release-button.name"),
                    I18n.get().getLegacyList("gui.items.release-button.lore", Placeholder.parsed("warning", "警告：地灵将永久消失！")).toArray(new String[0])); // Lore for release button incomplete in my memory, assuming similar structure or I can add it to lang file
                inv.setItem(26, releaseBtn);
            }

            player.openInventory(inv);
    }

    public static void openCravingsMenu(Player player, SpiritEntity spirit) {
        DailyRequest req = spirit.getDailyRequest();
        if (req == null) {
             EarthSpiritPlugin.getInstance().getCravingManager().checkRollover(spirit);
             req = spirit.getDailyRequest();
        }
        if (req == null) return;

        Inventory inv = Bukkit.createInventory(null, 45, I18n.get().getLegacy("gui.cravings.title", Placeholder.parsed("name", spirit.getName())));
        
        // Background
        ItemStack bg = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.background", "BLACK_STAINED_GLASS_PANE")), 
            I18n.get().getLegacy("gui.items.background.name"));
        for (int i = 0; i < 45; i++) {
            inv.setItem(i, bg);
        }

        // Info Book at 13
        ItemStack info = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.cravings.info-book", "PAPER")),
            I18n.get().getLegacy("gui.cravings.items.info-book.name"),
            I18n.get().getLegacy("gui.cravings.items.info-book.lore.date", Placeholder.parsed("date", LocalDate.ofEpochDay(req.date).toString())),
            I18n.get().getLegacy("gui.cravings.items.info-book.lore.grade", Placeholder.parsed("grade", String.valueOf(req.grade))),
            I18n.get().getLegacy("gui.cravings.items.info-book.lore.status", Placeholder.parsed("status", req.rewardsClaimed ? 
                I18n.get().getLegacy("gui.cravings.items.info-book.lore.status-claimed") : 
                I18n.get().getLegacy("gui.cravings.items.info-book.lore.status-incomplete")))
        );
        inv.setItem(13, info);

        // Task Items at 20-24
        int slot = 20;
        CravingManager cm = EarthSpiritPlugin.getInstance().getCravingManager();
        
        for (int i = 0; i < 5; i++) {
            if (req.items.containsKey(i)) {
                DailyRequest.TaskItem task = req.items.get(i);
                
                ItemStack displayItem;
                if (task.submitted) {
                    displayItem = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.cravings.submitted", "LIME_STAINED_GLASS_PANE")),
                        I18n.get().getLegacy("gui.cravings.items.submitted.name"));
                    displayItem.setAmount(1); 
                } else {
                    displayItem = cm.getDisplayItem(task.key).clone();
                    displayItem.setAmount(task.amount);
                }
                
                ItemMeta meta = displayItem.getItemMeta();
                List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                
                String originalName = "未知物品";
                ItemStack originalStack = cm.getDisplayItem(task.key);
                if (originalStack != null && originalStack.getItemMeta().hasDisplayName()) {
                    originalName = originalStack.getItemMeta().getDisplayName();
                }

                if (task.submitted) {
                     List<String> submittedLore = I18n.get().getLegacyList("gui.cravings.items.submitted.lore",
                        Placeholder.parsed("name", originalName),
                        Placeholder.parsed("amount", String.valueOf(task.amount)));
                     lore.addAll(submittedLore);
                } else {
                    lore.add(I18n.get().getLegacy("gui.cravings.items.unsubmitted.lore.separator"));
                    lore.add(I18n.get().getLegacy("gui.cravings.items.unsubmitted.lore.need", Placeholder.parsed("amount", String.valueOf(task.amount))));
                    int has = countItems(player, task.key, cm);
                    lore.add(I18n.get().getLegacy("gui.cravings.items.unsubmitted.lore.have", Placeholder.parsed("amount", String.valueOf(has))));
                    
                    lore.add(I18n.get().getLegacy("gui.cravings.items.unsubmitted.lore.status-unchecked"));
                    if (has >= task.amount) {
                        lore.add(I18n.get().getLegacy("gui.cravings.items.unsubmitted.lore.click-submit"));
                    } else {
                        lore.add(I18n.get().getLegacy("gui.cravings.items.unsubmitted.lore.not-enough"));
                    }
                }
                
                meta.setLore(lore);
                displayItem.setItemMeta(meta);
                inv.setItem(slot, displayItem);
            } else {
                inv.setItem(slot, createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.cravings.empty", "BARRIER")),
                    I18n.get().getLegacy("gui.cravings.items.empty.name")));
            }
            slot++;
        }

        // Claim Reward Button at 40
        boolean allSubmitted = req.items.values().stream().allMatch(t -> t.submitted);
        if (allSubmitted && !req.rewardsClaimed) {
             inv.setItem(40, createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.cravings.reward", "CHEST")),
                I18n.get().getLegacy("gui.cravings.items.reward.name"),
                I18n.get().getLegacyList("gui.cravings.items.reward.lore").toArray(new String[0])));
        } else if (req.rewardsClaimed) {
             inv.setItem(40, createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.cravings.claimed", "MINECART")),
                I18n.get().getLegacy("gui.cravings.items.claimed.name"),
                I18n.get().getLegacyList("gui.cravings.items.claimed.lore").toArray(new String[0])));
        }

        // Give Up Button at 44
        long today = LocalDate.now().toEpochDay();
        if (req.date < today && !req.rewardsClaimed) {
            inv.setItem(44, createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.cravings.give-up", "RED_DYE")),
                I18n.get().getLegacy("gui.cravings.items.give-up.name"),
                I18n.get().getLegacyList("gui.cravings.items.give-up.lore").toArray(new String[0])));
        }
        
        // Back Button at 36
        inv.setItem(36, createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.cravings.back", "ARROW")),
            I18n.get().getLegacy("gui.cravings.items.back.name")));

        player.openInventory(inv);
    }
    
    public static int countItems(Player player, String key, CravingManager cm) {
        int count = 0;
        for (ItemStack is : player.getInventory().getContents()) {
            if (is != null && cm.isItemMatch(is, key)) {
                count += is.getAmount();
            }
        }
        return count;
    }

    public static void openTrustMenu(Player player, SpiritEntity spirit) {
        Inventory inv = Bukkit.createInventory(null, 54, I18n.get().getLegacy("gui.trust.title"));

        // Background
        ItemStack bg = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.background", "GRAY_STAINED_GLASS_PANE")), 
            I18n.get().getLegacy("gui.items.background.name"));
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, bg);
        }

        com.palmergames.bukkit.towny.object.Town town = TownyIntegration.getTown(player);
        if (town == null) {
            player.sendMessage("§c无法获取居所数据！");
            return;
        }
        
        // 1. Partner (Slot 4)
        String partnerName = "无";
        if (spirit.getPartnerId() != null) {
            partnerName = Bukkit.getOfflinePlayer(spirit.getPartnerId()).getName();
        }
        ItemStack partnerItem = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.trust.partner", "RED_DYE")),
            I18n.get().getLegacy("gui.trust.items.partner.name"),
            I18n.get().getLegacyList("gui.trust.items.partner.lore", Placeholder.parsed("name", partnerName)).toArray(new String[0]));
        inv.setItem(4, partnerItem);

        // 2. Trusted List (Slot 18-44)
        java.util.Set<UUID> trusted = spirit.getTrustedPlayers();
        int slot = 18;
        if (trusted != null) {
            for (UUID uuid : trusted) {
                if (slot >= 45) break;
                org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                String pName = op.getName() != null ? op.getName() : "Unknown";
                
                ItemStack skull = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.trust.trusted", "PLAYER_HEAD")),
                    I18n.get().getLegacy("gui.trust.items.trusted.name", Placeholder.parsed("name", pName)), 
                    I18n.get().getLegacyList("gui.trust.items.trusted.lore").toArray(new String[0]));
                inv.setItem(slot++, skull);
            }
        }
        
        // 3. Add Trust (Slot 49)
        ItemStack addBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.trust.add", "EMERALD")),
            I18n.get().getLegacy("gui.trust.items.add.name"),
            I18n.get().getLegacyList("gui.trust.items.add.lore").toArray(new String[0]));
        inv.setItem(49, addBtn);
        
        // Return
        ItemStack back = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.trust.back", "ARROW")),
            I18n.get().getLegacy("gui.trust.items.back.name"));
        inv.setItem(45, back);
        
        player.openInventory(inv);
    }

    public static void openManagementMenu(Player player, SpiritEntity spirit) {
        Inventory inv = Bukkit.createInventory(null, 27, I18n.get().getLegacy("gui.management.title"));

        com.palmergames.bukkit.towny.object.Town town = TownyIntegration.getTown(player);
        if (town == null) {
            town = TownyIntegration.getTownAt(player.getLocation()); 
            if (town == null || !town.getName().equals(spirit.getTownName())) {
                 try {
                    town = com.palmergames.bukkit.towny.TownyUniverse.getInstance().getTown(spirit.getTownName());
                } catch (Exception e) {}
            }
        }

        if (town == null) {
            player.sendMessage(I18n.get().getLegacy("messages.town-error"));
            return;
        }

        // 权限判断 (主人或伴侣)
        boolean isOwner = player.getUniqueId().equals(spirit.getOwnerId()) || spirit.isPartner(player.getUniqueId());

        // 背景
        ItemStack bg = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.background", "GRAY_STAINED_GLASS_PANE")), 
            I18n.get().getLegacy("gui.items.background.name"));
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, bg);
        }

        boolean pvp = TownyIntegration.isPvpEnabled(town);
        boolean mobs = TownyIntegration.isMobsEnabled(town);
        boolean expl = TownyIntegration.isExplosionEnabled(town);
        boolean fire = TownyIntegration.isFireEnabled(town);
        // String board = TownyIntegration.getTownBoard(town);
        String townName = town.getName();
        String clickHint = isOwner ? I18n.get().getLegacy("status.click-toggle") : I18n.get().getLegacy("status.owner-only");
        String editHint = isOwner ? I18n.get().getLegacy("status.click-edit") : I18n.get().getLegacy("status.owner-only");

        // 1. PVP 开关 (10)
        ItemStack pvpBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.management.pvp", "DIAMOND_SWORD")),
            I18n.get().getLegacy("gui.management.items.pvp.name"),
            I18n.get().getLegacyList("gui.management.items.pvp.lore", 
                Placeholder.parsed("hint", clickHint),
                Placeholder.parsed("status", pvp ? I18n.get().getLegacy("status.enabled") : I18n.get().getLegacy("status.disabled"))).toArray(new String[0]));
        inv.setItem(10, pvpBtn);

        // 2. 怪物生成 (11)
        ItemStack mobBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.management.mobs", "ZOMBIE_HEAD")),
            I18n.get().getLegacy("gui.management.items.mobs.name"),
            I18n.get().getLegacyList("gui.management.items.mobs.lore", 
                Placeholder.parsed("hint", clickHint),
                Placeholder.parsed("status", mobs ? I18n.get().getLegacy("status.enabled") : I18n.get().getLegacy("status.disabled"))).toArray(new String[0]));
        inv.setItem(11, mobBtn);

        // 3. 爆炸开关 (12)
        ItemStack tntBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.management.explosion", "TNT")),
            I18n.get().getLegacy("gui.management.items.explosion.name"),
            I18n.get().getLegacyList("gui.management.items.explosion.lore", 
                Placeholder.parsed("hint", clickHint),
                Placeholder.parsed("status", expl ? I18n.get().getLegacy("status.enabled") : I18n.get().getLegacy("status.disabled"))).toArray(new String[0]));
        inv.setItem(12, tntBtn);
        
        // 4. 火焰开关 (13)
        ItemStack fireBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.management.fire", "FLINT_AND_STEEL")),
            I18n.get().getLegacy("gui.management.items.fire.name"),
            I18n.get().getLegacyList("gui.management.items.fire.lore", 
                Placeholder.parsed("hint", clickHint),
                Placeholder.parsed("status", fire ? I18n.get().getLegacy("status.enabled") : I18n.get().getLegacy("status.disabled"))).toArray(new String[0]));
        inv.setItem(13, fireBtn);

        // 4.5 入城公告 (14)
        String board = TownyIntegration.getTownBoard(town);
        ItemStack boardBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.management.board", "OAK_SIGN")),
            I18n.get().getLegacy("gui.management.items.board.name"),
            I18n.get().getLegacyList("gui.management.items.board.lore", 
                Placeholder.parsed("hint", editHint),
                Placeholder.parsed("board", board.isEmpty() ? "(暂无)" : board)).toArray(new String[0]));
        inv.setItem(14, boardBtn);

        // 5. 信任与伴侣管理 (24) - 仅主人/伴侣
        if (isOwner) {
            ItemStack memberBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.management.member", "PLAYER_HEAD")),
                I18n.get().getLegacy("gui.management.items.member.name"),
                I18n.get().getLegacyList("gui.management.items.member.lore").toArray(new String[0]));
            inv.setItem(24, memberBtn); 
        }

        // 6. 居所名 (15)
        ItemStack renameBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.management.rename", "NAME_TAG")),
            I18n.get().getLegacy("gui.management.items.rename.name"),
            I18n.get().getLegacyList("gui.management.items.rename.lore", 
                Placeholder.parsed("hint", editHint),
                Placeholder.parsed("name", townName)).toArray(new String[0]));
        inv.setItem(15, renameBtn);

        // 7. 废弃居所 (16)
        if (isOwner) {
            ItemStack deleteBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.management.delete", "BARRIER")),
                I18n.get().getLegacy("gui.management.items.delete.name"),
                I18n.get().getLegacyList("gui.management.items.delete.lore").toArray(new String[0]));
            inv.setItem(16, deleteBtn);
        } else {
             ItemStack roleBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.management.role", "PLAYER_HEAD")),
                I18n.get().getLegacy("gui.management.items.role.name"),
                I18n.get().getLegacyList("gui.management.items.role.lore").toArray(new String[0]));
             inv.setItem(16, roleBtn);
        }

        // 8. 废弃单块 (19)
        if (isOwner) {
            ItemStack unclaimBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.management.unclaim", "IRON_SHOVEL")),
                I18n.get().getLegacy("gui.management.items.unclaim.name"),
                I18n.get().getLegacyList("gui.management.items.unclaim.lore").toArray(new String[0]));
            inv.setItem(19, unclaimBtn);
        }
        
        // 9. 灵域加成信息 (22)
        double mood = spirit.getMood();
        List<String> moodLore = new ArrayList<>();
        moodLore.addAll(I18n.get().getLegacyList("gui.management.items.mood.lore.header", Placeholder.parsed("bar", getMoodBar(mood))));
        
        if (mood < 60) {
            moodLore.addAll(I18n.get().getLegacyList("gui.management.items.mood.lore.level-0"));
        } else if (mood < 80) {
            moodLore.addAll(I18n.get().getLegacyList("gui.management.items.mood.lore.level-1"));
        } else if (mood < 90) {
            moodLore.addAll(I18n.get().getLegacyList("gui.management.items.mood.lore.level-2"));
        } else {
            moodLore.addAll(I18n.get().getLegacyList("gui.management.items.mood.lore.level-3"));
        }
        ItemStack moodBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.management.mood", "NETHER_STAR")),
            I18n.get().getLegacy("gui.management.items.mood.name"),
            moodLore.toArray(new String[0]));
        inv.setItem(22, moodBtn);

        // 10. 返回 (26)
        ItemStack backBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.management.back", "ARROW")),
            I18n.get().getLegacy("gui.management.items.back.name"),
            I18n.get().getLegacyList("gui.management.items.back.lore").toArray(new String[0]));
        inv.setItem(26, backBtn);

        player.openInventory(inv);
    }
    
    // 辅助方法：生成心情进度条
    private static String getMoodBar(double mood) {
        int progress = (int) (mood / 10);
        StringBuilder barBuilder = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            if (i < progress) {
                if (mood >= 90) barBuilder.append(I18n.get().getLegacy("gui.mood-bar.filled-high"));
                else if (mood >= 60) barBuilder.append(I18n.get().getLegacy("gui.mood-bar.filled"));
                else barBuilder.append(I18n.get().getLegacy("gui.mood-bar.filled-low"));
            } else {
                barBuilder.append(I18n.get().getLegacy("gui.mood-bar.empty"));
            }
        }
        return I18n.get().getLegacy("gui.mood-bar.format", 
            Placeholder.parsed("bar", barBuilder.toString()),
            Placeholder.parsed("mood", String.valueOf((int)mood)));
    }
    
    private static ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }
}
