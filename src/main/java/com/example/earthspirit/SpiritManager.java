package com.example.earthspirit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.io.*;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SpiritManager {
    private final EarthSpiritPlugin plugin;
    private final Map<UUID, SpiritEntity> spiritsByOwner = new HashMap<>(); // Key: Owner UUID
    private final File dataFile;
    private final Gson gson;

    public SpiritManager(EarthSpiritPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "spirits.json");
        
        this.gson = new GsonBuilder()
                .registerTypeAdapter(Location.class, new LocationAdapter())
                .setPrettyPrinting()
                .create();
                
        loadData();
    }

    public void addSpirit(SpiritEntity spirit) {
        spiritsByOwner.put(spirit.getOwnerId(), spirit);
        saveData();
    }

    public SpiritEntity getSpiritByOwner(UUID ownerId) {
        return spiritsByOwner.get(ownerId);
    }

    public SpiritEntity getSpiritByDriver(UUID driverId) {
        for (SpiritEntity s : spiritsByOwner.values()) {
            if (s.getDriverId() != null && s.getDriverId().equals(driverId)) {
                return s;
            }
        }
        return null;
    }

    public SpiritEntity getSpirit(UUID entityId) {
        for (SpiritEntity s : spiritsByOwner.values()) {
            if (s.getEntityId() != null && s.getEntityId().equals(entityId)) {
                return s;
            }
        }
        return null;
    }
    
    public Map<UUID, SpiritEntity> getAllSpirits() {
        return spiritsByOwner;
    }

    public void removeSpirit(UUID ownerId) {
        spiritsByOwner.remove(ownerId);
        saveData();
    }

    public void saveData() {
        // 保存前准备数据 (序列化背包等)
        for (SpiritEntity spirit : spiritsByOwner.values()) {
            spirit.prepareSave();
        }
        
        try (Writer writer = new FileWriter(dataFile)) {
            gson.toJson(spiritsByOwner, writer);
        } catch (IOException e) {
            plugin.getLogger().severe("无法保存地灵数据: " + e.getMessage());
        }
    }

    public void loadData() {
        if (!dataFile.exists()) {
            plugin.getDataFolder().mkdirs();
            return;
        }
        try (Reader reader = new FileReader(dataFile)) {
            Type type = new TypeToken<HashMap<UUID, SpiritEntity>>(){}.getType();
            Map<UUID, SpiritEntity> loaded = gson.fromJson(reader, type);
            if (loaded != null) {
                spiritsByOwner.putAll(loaded);
                // 加载后初始化 (反序列化背包等)
                for (SpiritEntity spirit : spiritsByOwner.values()) {
                    spirit.initAfterLoad();
                }
            }
        } catch (IOException e) {
            plugin.getLogger().severe("无法加载地灵数据: " + e.getMessage());
        }
    }
    
    // 自定义 Location 适配器
    private static class LocationAdapter extends TypeAdapter<Location> {
        @Override
        public void write(JsonWriter out, Location value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }
            out.beginObject();
            if (value.getWorld() != null) {
                out.name("world").value(value.getWorld().getName());
            }
            out.name("x").value(value.getX());
            out.name("y").value(value.getY());
            out.name("z").value(value.getZ());
            out.name("yaw").value(value.getYaw());
            out.name("pitch").value(value.getPitch());
            out.endObject();
        }

        @Override
        public Location read(JsonReader in) throws IOException {
            String worldName = null;
            double x = 0, y = 0, z = 0;
            float yaw = 0, pitch = 0;

            in.beginObject();
            while (in.hasNext()) {
                String name = in.nextName();
                switch (name) {
                    case "world":
                        worldName = in.nextString();
                        break;
                    case "x":
                        x = in.nextDouble();
                        break;
                    case "y":
                        y = in.nextDouble();
                        break;
                    case "z":
                        z = in.nextDouble();
                        break;
                    case "yaw":
                        yaw = (float) in.nextDouble();
                        break;
                    case "pitch":
                        pitch = (float) in.nextDouble();
                        break;
                    default:
                        in.skipValue();
                        break;
                }
            }
            in.endObject();

            World world = null;
            if (worldName != null) {
                world = Bukkit.getWorld(worldName);
            }
            return new Location(world, x, y, z, yaw, pitch);
        }
    }
    
    // 检查某个位置附近是否已经有地灵 (防止重叠圈地)
    public boolean hasSpiritNearby(Location loc, double radius) {
        for (SpiritEntity spirit : spiritsByOwner.values()) {
            if (spirit.getEntityId() == null) continue;
            Entity entity = Bukkit.getEntity(spirit.getEntityId());
            if (entity != null && entity.isValid() && entity.getLocation().getWorld().equals(loc.getWorld())) {
                if (entity.getLocation().distance(loc) < radius) {
                    return true;
                }
            }
        }
        return false;
    }
}
