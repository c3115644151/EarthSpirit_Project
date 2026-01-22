package com.example.earthspirit;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class SpiritGuiHolder implements InventoryHolder {
    private final Inventory inventory;
    private final SpiritEntity spirit;
    private final String menuType; // "MAIN", "MANAGEMENT", "TRUST", "CRAVINGS"

    public SpiritGuiHolder(SpiritEntity spirit, String menuType, String title, int size) {
        this.spirit = spirit;
        this.menuType = menuType;
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public SpiritEntity getSpirit() {
        return spirit;
    }

    public String getMenuType() {
        return menuType;
    }
}
