package com.example.earthspirit;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class SpiritInventory {
    private Inventory inventory;
    private final int size = 27; // 27 slots like a small chest

    public SpiritInventory(String title) {
        this.inventory = Bukkit.createInventory(null, size, title);
    }

    public Inventory getInventory() {
        return inventory;
    }

    public void open(org.bukkit.entity.Player player) {
        player.openInventory(inventory);
    }

    // 序列化 Inventory 到 Base64 字符串
    public String toBase64() {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            
            // Write the size of the inventory
            dataOutput.writeInt(inventory.getSize());
            
            // Save every element in the list
            for (int i = 0; i < inventory.getSize(); i++) {
                dataOutput.writeObject(inventory.getItem(i));
            }
            
            dataOutput.close();
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    // 从 Base64 字符串反序列化
    public static SpiritInventory fromBase64(String data, String title) {
        SpiritInventory spiritInventory = new SpiritInventory(title);
        if (data == null || data.isEmpty()) return spiritInventory;

        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            
            int size = dataInput.readInt();
            // Assuming size matches, or we handle resize. For now assume fixed size.
            
            Inventory inv = spiritInventory.getInventory();
            
            // Read the serialized inventory
            for (int i = 0; i < size; i++) {
                if (i < inv.getSize()) {
                    inv.setItem(i, (ItemStack) dataInput.readObject());
                } else {
                    dataInput.readObject(); // Skip if inventory shrank
                }
            }
            
            dataInput.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return spiritInventory;
    }
}
