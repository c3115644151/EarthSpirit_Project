package com.example.earthspirit;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.time.LocalDate;

import com.example.earthspirit.cravings.DailyRequest;
import com.example.earthspirit.cravings.CravingManager;
import com.example.earthspirit.configuration.ConfigManager;
import com.example.earthspirit.configuration.I18n;

public class SpiritGUI {

    public static String getGuiTitle() {
        return I18n.get().getLegacy("gui.title");
    }
    
    public static String getSubGuiTitle() {
        return I18n.get().getLegacy("gui.sub-title");
    }

    public static void openMenu(Player player, SpiritEntity spirit) {
        // 创建一个 3行 (27格) 的界面
        SpiritGuiHolder holder = new SpiritGuiHolder(spirit, "MAIN", getGuiTitle(), 27);
        Inventory inv = holder.getInventory();

        // 权限检查
        boolean isOwner = player.getUniqueId().equals(spirit.getOwnerId());
        boolean isResident = TownyIntegration.isResident(spirit.getTownName(), player);
        boolean canInteract = isOwner || isResident;

        // 1. 背景板
        ItemStack bg = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.background", "BLACK_STAINED_GLASS_PANE")), 
            I18n.get().getComponent("gui.items.background.name"));
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, bg);
        }

        // 2. 核心状态 (中间 - 头颅)
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta headMeta = (SkullMeta) head.getItemMeta();
        if (headMeta != null) {
            headMeta.setOwningPlayer(Bukkit.getOfflinePlayer(spirit.getOwnerId())); // 显示主人的头
            
            headMeta.setDisplayName(LegacyComponentSerializer.legacySection().serialize(
                I18n.get().getComponent("gui.items.head.name", 
                Placeholder.component("name", I18n.get().asComponent(spirit.getName())))
            ));
            
            Component status;
            if (spirit.isAbandoned()) {
                status = I18n.get().getComponent("messages.status.abandoned");
            } else if (spirit.getTownName() == null) {
                status = I18n.get().getComponent("messages.status.wandering");
            } else if (spirit.getMode() == SpiritEntity.SpiritMode.COMPANION) {
                status = I18n.get().getComponent("messages.status.following");
            } else {
                status = I18n.get().getComponent("messages.status.guarding");
            }

            // 如果是主人查看，且名字不一致，顺便更新一下数据
            if (isOwner) { 
                com.palmergames.bukkit.towny.object.Town t = TownyIntegration.getTown(player);
                if (t != null && !t.getName().equals(spirit.getTownName())) {
                     spirit.setTownName(t.getName());
                }
            }

            List<Component> lore = I18n.get().getComponentList("gui.items.head.lore",
                Placeholder.component("mode", MiniMessage.miniMessage().deserialize(spirit.getMode().getDisplayName())),
                Placeholder.component("hunger_bar", MiniMessage.miniMessage().deserialize(spirit.getHungerBar())),
                Placeholder.component("mood_bar", getMoodBar(spirit.getMood())),
                Placeholder.parsed("level", String.valueOf(spirit.getLevel())),
                Placeholder.parsed("exp", String.valueOf(spirit.getExp())),
                Placeholder.parsed("max_exp", String.valueOf(spirit.getLevel() * 100)),
                Placeholder.parsed("owner", Bukkit.getOfflinePlayer(spirit.getOwnerId()).getName()),
                Placeholder.component("status", status)
            );
            
            List<String> legacyLore = new ArrayList<>();
            for (Component c : lore) {
                legacyLore.add(LegacyComponentSerializer.legacySection().serialize(c));
            }
            headMeta.setLore(legacyLore);
            head.setItemMeta(headMeta);
        }
        inv.setItem(13, head);

        // 3. 互动按钮 (左侧 - 抚摸)
        if (canInteract) {
            ItemStack petBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.pet-button", "FEATHER")),
                I18n.get().getComponent("gui.items.pet-button.name"),
                I18n.get().getComponentList("gui.items.pet-button.lore"));
            inv.setItem(11, petBtn);

            // 4. 投喂按钮 (右侧 - 蛋糕)
            ItemStack feedBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.feed-button", "CAKE")),
                I18n.get().getComponent("gui.items.feed-button.name"),
                I18n.get().getComponentList("gui.items.feed-button.lore"));
            inv.setItem(15, feedBtn);
            
            // 4.5 背包按钮
            ItemStack bagBtn;
            if (spirit.getMode() == SpiritEntity.SpiritMode.COMPANION) {
                 bagBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.bag-button-active", "CHEST")),
                    I18n.get().getComponent("gui.items.bag-button.name"),
                    I18n.get().getComponentList("gui.items.bag-button.lore"));
            } else {
                 bagBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.bag-button-inactive", "CHEST")),
                    I18n.get().getComponent("gui.items.bag-button.name-inactive"),
                    I18n.get().getComponentList("gui.items.bag-button.lore-inactive"));
            }
            inv.setItem(4, bagBtn);

            // 4.6 嘴馋清单按钮
            List<Component> cravingLore = new ArrayList<>();
            cravingLore.add(Component.empty());
            
            DailyRequest req = spirit.getDailyRequest();
            if (req != null) {
                long today = LocalDate.now().toEpochDay();
                if (req.date == today) {
                    cravingLore.add(I18n.get().getComponent("gui.items.craving-button.lore.today-grade", Placeholder.parsed("grade", String.valueOf(req.grade))));
                    cravingLore.add(I18n.get().getComponent("gui.items.craving-button.lore.needs"));
                     
                    CravingManager cm = EarthSpiritPlugin.getInstance().getCravingManager();
                    for (DailyRequest.TaskItem task : req.items.values()) {
                        Component nameComp;
                        String rawName = cm.getDisplayName(task.key);
                        if (!rawName.equals(task.key)) {
                             // Use raw name and reconstruct format to avoid legacy serialization issues
                             nameComp = I18n.get().getComponent("cravings.item-format", Placeholder.component("name", I18n.get().asComponent(rawName)));
                        } else {
                            // Fallback to item stack name (e.g. BiomeGifts)
                            ItemStack is = cm.getDisplayItem(task.key);
                            if (is != null && is.getItemMeta() != null && is.getItemMeta().hasDisplayName()) {
                                nameComp = I18n.get().asComponent(is.getItemMeta().getDisplayName());
                            } else if (is != null) {
                                nameComp = I18n.get().asComponent(is.getType().name());
                            } else {
                                nameComp = I18n.get().asComponent("未知物品");
                            }
                        }
                         
                        String statusKey = task.submitted ? "gui.items.craving-button.lore.status-checked" : "gui.items.craving-button.lore.status-unchecked";
                        Component statusComp = I18n.get().getComponent(statusKey);
                         
                        cravingLore.add(I18n.get().getComponent("gui.items.craving-button.lore.item-entry",
                            Placeholder.component("item", nameComp),
                            Placeholder.parsed("amount", String.valueOf(task.amount)),
                            Placeholder.component("status", statusComp)
                        ));
                    }
                     
                    if (req.rewardsClaimed) {
                        cravingLore.add(Component.empty());
                        cravingLore.add(I18n.get().getComponent("gui.items.craving-button.lore.claimed"));
                    } else {
                        cravingLore.add(Component.empty());
                        cravingLore.add(I18n.get().getComponent("gui.items.craving-button.lore.click-details"));
                    }
                 } else {
                     cravingLore.add(I18n.get().getComponent("gui.items.craving-button.lore.check-craving"));
                     cravingLore.add(I18n.get().getComponent("gui.items.craving-button.lore.yesterday-unfinished"));
                     cravingLore.add(I18n.get().getComponent("gui.items.craving-button.lore.refresh"));
                 }
            } else {
                cravingLore.add(I18n.get().getComponent("gui.items.craving-button.lore.check-craving"));
                cravingLore.add(I18n.get().getComponent("gui.items.craving-button.lore.daily-reward"));
                cravingLore.add(I18n.get().getComponent("gui.items.craving-button.lore.daily-refresh"));
            }

            ItemStack cravingBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.craving-button", "PAPER")),
                I18n.get().getComponent("gui.items.craving-button.name"),
                cravingLore);
            inv.setItem(18, cravingBtn);
        } else {
            // 访客模式显示灰色
            ItemStack noPerm = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.visitor-button", "GRAY_DYE")),
                I18n.get().getComponent("gui.items.visitor-button.name"),
                I18n.get().getComponentList("gui.items.visitor-button.lore"));
            inv.setItem(11, noPerm);
            
            if (spirit.isAbandoned()) {
                 // 允许投喂被遗弃的地灵
                 ItemStack feedBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.feed-button", "CAKE")),
                    I18n.get().getComponent("gui.items.feed-comfort-button.name"),
                    I18n.get().getComponentList("gui.items.feed-comfort-button.lore"));
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
                    I18n.get().getComponent("gui.items.create-home-button.name"),
                    I18n.get().getComponentList("gui.items.create-home-button.lore"));
                inv.setItem(22, createBtn);
            } else {
                ItemStack noTown = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.no-home-button", "DEAD_BUSH")),
                    I18n.get().getComponent("gui.items.no-home-button.name"),
                    I18n.get().getComponentList("gui.items.no-home-button.lore"));
                inv.setItem(22, noTown);
            }
        } else {
            // 有居所 -> 显示 "居所管理" 和 "扩充居所"
            if (canInteract) {
                ItemStack manageBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.manage-home-button", "EMERALD")),
                    I18n.get().getComponent("gui.items.manage-home-button.name"),
                    I18n.get().getComponentList("gui.items.manage-home-button.lore", Placeholder.parsed("town", townName)));
                inv.setItem(22, manageBtn);
                
                if (isOwner) {
                    ItemStack expandBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.expand-home-button", "GOLDEN_SHOVEL")),
                        I18n.get().getComponent("gui.items.expand-home-button.name"),
                        I18n.get().getComponentList("gui.items.expand-home-button.lore", Placeholder.parsed("max", String.valueOf(1 + (spirit.getLevel()-1)*2))));
                    inv.setItem(20, expandBtn);
                }
            }
        }

        // 6. 更多功能 (重命名 & 解除契约)
            if (isOwner) {
                ItemStack renameSpiritBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.rename-button", "NAME_TAG")),
                    I18n.get().getComponent("gui.items.rename-button.name"),
                    I18n.get().getComponentList("gui.items.rename-button.lore", Placeholder.component("name", I18n.get().asComponent(spirit.getName()))));
                inv.setItem(24, renameSpiritBtn);
                
                ItemStack releaseBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.release-button", "SKELETON_SKULL")),
                    I18n.get().getComponent("gui.items.release-button.name"),
                    I18n.get().getComponentList("gui.items.release-button.lore", Placeholder.parsed("warning", "警告：地灵将永久消失！"))); 
                inv.setItem(26, releaseBtn);
            }

            player.openInventory(inv);
    }

    public static void openCravingsMenu(Player player, SpiritEntity spirit) {
        // Always check rollover logic first
        EarthSpiritPlugin.getInstance().getCravingManager().checkRollover(spirit);
        
        DailyRequest req = spirit.getDailyRequest();
        if (req == null) return;

        SpiritGuiHolder holder = new SpiritGuiHolder(spirit, "CRAVINGS", 
            LegacyComponentSerializer.legacySection().serialize(
                I18n.get().getComponent("gui.cravings.title", Placeholder.component("name", I18n.get().asComponent(spirit.getName())))
            ), 45);
        Inventory inv = holder.getInventory();
        
        // Background
        ItemStack bg = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.background", "BLACK_STAINED_GLASS_PANE")), 
            I18n.get().getComponent("gui.items.background.name"));
        for (int i = 0; i < 45; i++) {
            inv.setItem(i, bg);
        }

        // Info Book at 13
        Component statusComp = req.rewardsClaimed ? 
            I18n.get().getComponent("gui.cravings.items.info-book.status-claimed") : 
            I18n.get().getComponent("gui.cravings.items.info-book.status-incomplete");

        ItemStack info = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.cravings.info-book", "PAPER")),
            I18n.get().getComponent("gui.cravings.items.info-book.name"),
            I18n.get().getComponentList("gui.cravings.items.info-book.lore", 
                Placeholder.parsed("date", LocalDate.ofEpochDay(req.date).toString()),
                Placeholder.parsed("grade", String.valueOf(req.grade)),
                Placeholder.component("status", statusComp))
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
                        I18n.get().getComponent("gui.cravings.items.submitted.name"));
                    displayItem.setAmount(1); 
                } else {
                    displayItem = cm.getDisplayItem(task.key).clone();
                    displayItem.setAmount(task.amount);
                }
                
                ItemMeta meta = displayItem.getItemMeta();
                List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                if (lore == null) lore = new ArrayList<>();
                
                Component nameComp;
                String rawName = cm.getDisplayName(task.key);
                if (!rawName.equals(task.key)) {
                     // Use raw name and reconstruct format
                     nameComp = I18n.get().getComponent("messages.cravings.item-format", Placeholder.component("name", I18n.get().asComponent(rawName)));
                } else {
                     ItemStack stack = cm.getDisplayItem(task.key);
                     if (stack != null && stack.getItemMeta() != null && stack.getItemMeta().hasDisplayName()) {
                         nameComp = I18n.get().asComponent(stack.getItemMeta().getDisplayName());
                     } else if (stack != null) {
                         nameComp = I18n.get().asComponent(stack.getType().name());
                     } else {
                         nameComp = I18n.get().asComponent("未知物品");
                     }
                }

                if (task.submitted) {
                     List<Component> submittedLore = I18n.get().getComponentList("gui.cravings.items.submitted.lore",
                        Placeholder.component("name", nameComp),
                        Placeholder.parsed("amount", String.valueOf(task.amount)));
                     for (Component c : submittedLore) lore.add(LegacyComponentSerializer.legacySection().serialize(c));
                } else {
                    lore.add(LegacyComponentSerializer.legacySection().serialize(I18n.get().getComponent("gui.cravings.items.unsubmitted.lore.separator")));
                    lore.add(LegacyComponentSerializer.legacySection().serialize(I18n.get().getComponent("gui.cravings.items.unsubmitted.lore.need", Placeholder.parsed("amount", String.valueOf(task.amount)))));
                    int has = countItems(player, task.key, cm);
                    lore.add(LegacyComponentSerializer.legacySection().serialize(I18n.get().getComponent("gui.cravings.items.unsubmitted.lore.have", Placeholder.parsed("amount", String.valueOf(has)))));
                    
                    lore.add(LegacyComponentSerializer.legacySection().serialize(I18n.get().getComponent("gui.cravings.items.unsubmitted.lore.status-unchecked")));
                    if (has >= task.amount) {
                        lore.add(LegacyComponentSerializer.legacySection().serialize(I18n.get().getComponent("gui.cravings.items.unsubmitted.lore.click-submit")));
                    } else {
                        lore.add(LegacyComponentSerializer.legacySection().serialize(I18n.get().getComponent("gui.cravings.items.unsubmitted.lore.not-enough")));
                    }
                }
                
                meta.setLore(lore);
                displayItem.setItemMeta(meta);
                inv.setItem(slot, displayItem);
            } else {
                inv.setItem(slot, createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.cravings.empty", "BARRIER")),
                    I18n.get().getComponent("gui.cravings.items.empty.name")));
            }
            slot++;
        }

        // Claim Reward Button at 40
        boolean allSubmitted = req.items.values().stream().allMatch(t -> t.submitted);
        if (allSubmitted && !req.rewardsClaimed) {
             inv.setItem(40, createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.cravings.reward", "CHEST")),
                I18n.get().getComponent("gui.cravings.items.reward.name"),
                I18n.get().getComponentList("gui.cravings.items.reward.lore")));
        } else if (req.rewardsClaimed) {
             inv.setItem(40, createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.cravings.claimed", "MINECART")),
                I18n.get().getComponent("gui.cravings.items.claimed.name"),
                I18n.get().getComponentList("gui.cravings.items.claimed.lore")));
        }

        // Give Up Button at 44
        long today = LocalDate.now().toEpochDay();
        if (req.date < today && !req.rewardsClaimed) {
            inv.setItem(44, createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.cravings.give-up", "RED_DYE")),
                I18n.get().getComponent("gui.cravings.items.give-up.name"),
                I18n.get().getComponentList("gui.cravings.items.give-up.lore")));
        }
        
        // Back Button at 36
        inv.setItem(36, createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.cravings.back", "ARROW")),
            I18n.get().getComponent("gui.cravings.items.back.name")));

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
        SpiritGuiHolder holder = new SpiritGuiHolder(spirit, "TRUST", I18n.get().getLegacy("gui.trust.title"), 54);
        Inventory inv = holder.getInventory();

        // Background
        ItemStack bg = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.background", "GRAY_STAINED_GLASS_PANE")), 
            I18n.get().getComponent("gui.items.background.name"));
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, bg);
        }

        com.palmergames.bukkit.towny.object.Town town = TownyIntegration.getTown(player);
        if (town == null) {
            I18n.get().send(player, "messages.town-error");
            return;
        }
        
        // 1. Partner (Slot 4)
        String partnerName = "无";
        if (spirit.getPartnerId() != null) {
            partnerName = Bukkit.getOfflinePlayer(spirit.getPartnerId()).getName();
        }
        ItemStack partnerItem = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.trust.partner", "RED_DYE")),
            I18n.get().getComponent("gui.trust.items.partner.name"),
            I18n.get().getComponentList("gui.trust.items.partner.lore", Placeholder.parsed("name", partnerName)));
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
                    I18n.get().getComponent("gui.trust.items.trusted.name", Placeholder.parsed("name", pName)), 
                    I18n.get().getComponentList("gui.trust.items.trusted.lore"));
                inv.setItem(slot++, skull);
            }
        }
        
        // 3. Add Trust (Slot 49)
        ItemStack addBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.trust.add", "EMERALD")),
            I18n.get().getComponent("gui.trust.items.add.name"),
            I18n.get().getComponentList("gui.trust.items.add.lore"));
        inv.setItem(49, addBtn);
        
        // Return
        ItemStack back = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.trust.back", "ARROW")),
            I18n.get().getComponent("gui.trust.items.back.name"));
        inv.setItem(45, back);
        
        player.openInventory(inv);
    }

    public static void openManagementMenu(Player player, SpiritEntity spirit) {
        SpiritGuiHolder holder = new SpiritGuiHolder(spirit, "MANAGEMENT", I18n.get().getLegacy("gui.management.title"), 27);
        Inventory inv = holder.getInventory();

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
            I18n.get().send(player, "messages.town-error");
            return;
        }

        // 权限判断 (主人或伴侣)
        boolean isOwner = player.getUniqueId().equals(spirit.getOwnerId()) || spirit.isPartner(player.getUniqueId());

        // 背景
        ItemStack bg = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.background", "GRAY_STAINED_GLASS_PANE")), 
            I18n.get().getComponent("gui.items.background.name"));
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, bg);
        }

        boolean pvp = TownyIntegration.isPvpEnabled(town);
        boolean mobs = TownyIntegration.isMobsEnabled(town);
        boolean expl = TownyIntegration.isExplosionEnabled(town);
        boolean fire = TownyIntegration.isFireEnabled(town);
        // String board = TownyIntegration.getTownBoard(town);
        String townName = town.getName();
        Component clickHint = isOwner ? I18n.get().getComponent("messages.status.click-toggle") : I18n.get().getComponent("messages.status.owner-only");
        Component editHint = isOwner ? I18n.get().getComponent("messages.status.click-edit") : I18n.get().getComponent("messages.status.owner-only");

        // 1. PVP 开关 (10)
        ItemStack pvpBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.management.pvp", "DIAMOND_SWORD")),
            I18n.get().getComponent("gui.management.items.pvp.name"),
            I18n.get().getComponentList("gui.management.items.pvp.lore", 
                Placeholder.component("hint", clickHint),
                Placeholder.component("status", pvp ? I18n.get().getComponent("messages.status.enabled") : I18n.get().getComponent("messages.status.disabled"))));
        inv.setItem(10, pvpBtn);

        // 2. 怪物生成 (11)
        ItemStack mobBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.management.mobs", "ZOMBIE_HEAD")),
            I18n.get().getComponent("gui.management.items.mobs.name"),
            I18n.get().getComponentList("gui.management.items.mobs.lore", 
                Placeholder.component("hint", clickHint),
                Placeholder.component("status", mobs ? I18n.get().getComponent("messages.status.enabled") : I18n.get().getComponent("messages.status.disabled"))));
        inv.setItem(11, mobBtn);

        // 3. 爆炸开关 (12)
        ItemStack tntBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.management.explosion", "TNT")),
            I18n.get().getComponent("gui.management.items.explosion.name"),
            I18n.get().getComponentList("gui.management.items.explosion.lore", 
                Placeholder.component("hint", clickHint),
                Placeholder.component("status", expl ? I18n.get().getComponent("messages.status.enabled") : I18n.get().getComponent("messages.status.disabled"))));
        inv.setItem(12, tntBtn);
        
        // 4. 火焰开关 (13)
        ItemStack fireBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.management.fire", "FLINT_AND_STEEL")),
            I18n.get().getComponent("gui.management.items.fire.name"),
            I18n.get().getComponentList("gui.management.items.fire.lore", 
                Placeholder.component("hint", clickHint),
                Placeholder.component("status", fire ? I18n.get().getComponent("messages.status.enabled") : I18n.get().getComponent("messages.status.disabled"))));
        inv.setItem(13, fireBtn);

        // 4.5 入城公告 (14)
        String board = TownyIntegration.getTownBoard(town);
        ItemStack boardBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.management.board", "OAK_SIGN")),
            I18n.get().getComponent("gui.management.items.board.name"),
            I18n.get().getComponentList("gui.management.items.board.lore", 
                Placeholder.component("hint", editHint),
                Placeholder.parsed("board", board.isEmpty() ? "(暂无)" : board)));
        inv.setItem(14, boardBtn);

        // 5. 信任与伴侣管理 (24) - 仅主人/伴侣
        if (isOwner) {
            ItemStack memberBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.management.member", "PLAYER_HEAD")),
                I18n.get().getComponent("gui.management.items.member.name"),
                I18n.get().getComponentList("gui.management.items.member.lore"));
            inv.setItem(24, memberBtn); 
        }

        // 6. 居所名 (15)
        ItemStack renameBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.management.rename", "NAME_TAG")),
            I18n.get().getComponent("gui.management.items.rename.name"),
            I18n.get().getComponentList("gui.management.items.rename.lore", 
                Placeholder.component("hint", editHint),
                Placeholder.parsed("name", townName)));
        inv.setItem(15, renameBtn);

        // 7. 废弃居所 (16)
        if (isOwner) {
            ItemStack deleteBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.management.delete", "BARRIER")),
                I18n.get().getComponent("gui.management.items.delete.name"),
                I18n.get().getComponentList("gui.management.items.delete.lore"));
            inv.setItem(16, deleteBtn);
        } else {
             ItemStack roleBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.management.role", "PLAYER_HEAD")),
                I18n.get().getComponent("gui.management.items.role.name"),
                I18n.get().getComponentList("gui.management.items.role.lore"));
             inv.setItem(16, roleBtn);
        }

        // 8. 废弃单块 (19)
        if (isOwner) {
            ItemStack unclaimBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.management.unclaim", "IRON_SHOVEL")),
                I18n.get().getComponent("gui.management.items.unclaim.name"),
                I18n.get().getComponentList("gui.management.items.unclaim.lore"));
            inv.setItem(19, unclaimBtn);
        }
        
        // 9. 灵域加成信息 (22)
        double mood = spirit.getMood();
        List<Component> moodLore = new ArrayList<>();
        moodLore.addAll(I18n.get().getComponentList("gui.management.items.mood.lore.header", Placeholder.component("bar", getMoodBar(mood))));
        
        if (mood < 60) {
            moodLore.addAll(I18n.get().getComponentList("gui.management.items.mood.lore.level-0"));
        } else if (mood < 80) {
            moodLore.addAll(I18n.get().getComponentList("gui.management.items.mood.lore.level-1"));
        } else if (mood < 90) {
            moodLore.addAll(I18n.get().getComponentList("gui.management.items.mood.lore.level-2"));
        } else {
            moodLore.addAll(I18n.get().getComponentList("gui.management.items.mood.lore.level-3"));
        }
        ItemStack moodBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.management.mood", "NETHER_STAR")),
            I18n.get().getComponent("gui.management.items.mood.name"),
            moodLore);
        inv.setItem(22, moodBtn);

        // 10. 返回 (26)
        ItemStack backBtn = createItem(Material.valueOf(ConfigManager.get().getRaw().getString("gui.materials.management.back", "ARROW")),
            I18n.get().getComponent("gui.management.items.back.name"),
            I18n.get().getComponentList("gui.management.items.back.lore"));
        inv.setItem(26, backBtn);

        player.openInventory(inv);
    }
    
    // 辅助方法：生成心情进度条 (Return Component)
    private static Component getMoodBar(double mood) {
        int progress = (int) (mood / 10);
        Component barBuilder = Component.empty();
        for (int i = 0; i < 10; i++) {
            if (i < progress) {
                if (mood >= 90) barBuilder = barBuilder.append(I18n.get().getComponent("gui.mood-bar.filled-high"));
                else if (mood >= 60) barBuilder = barBuilder.append(I18n.get().getComponent("gui.mood-bar.filled"));
                else barBuilder = barBuilder.append(I18n.get().getComponent("gui.mood-bar.filled-low"));
            } else {
                barBuilder = barBuilder.append(I18n.get().getComponent("gui.mood-bar.empty"));
            }
        }
        return I18n.get().getComponent("gui.mood-bar.format", 
            Placeholder.component("bar", barBuilder),
            Placeholder.parsed("mood", String.valueOf((int)mood)));
    }
    
    private static ItemStack createItem(Material mat, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(LegacyComponentSerializer.legacySection().serialize(name));
            List<String> legacyLore = new ArrayList<>();
            for (Component c : lore) {
                legacyLore.add(LegacyComponentSerializer.legacySection().serialize(c));
            }
            meta.setLore(legacyLore);
            item.setItemMeta(meta);
        }
        return item;
    }
    
    // Overload for convenience if needed, or remove if not used. 
    // Just keeping the one we need.
    private static ItemStack createItem(Material mat, Component name, Component... lore) {
        return createItem(mat, name, Arrays.asList(lore));
    }
}
