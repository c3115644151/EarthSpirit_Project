package com.example.earthspirit;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
// import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.Random;

public class SpiritParticleTask extends BukkitRunnable {

    private final EarthSpiritPlugin plugin;
    private final Random random = new Random();

    public SpiritParticleTask(EarthSpiritPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (!BiomeGiftsHelper.isEnabled()) return;

        for (Player p : Bukkit.getOnlinePlayers()) {
            Location loc = p.getLocation();
            int radius = 5; 
            
            for (int x = -radius; x <= radius; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        Block block = loc.clone().add(x, y, z).getBlock();
                        if (block.getBlockData() instanceof Ageable) {
                            processCrop(block);
                        }
                    }
                }
            }
        }
    }

    private void processCrop(Block block) {
        boolean rich = false;
        boolean spiritBonus = false;

        // 1. Rich Biome Check
        if (BiomeGiftsHelper.isRich(block.getType(), block.getLocation())) {
             rich = true;
        }
        
        // 2. Spirit Bonus Check
        com.palmergames.bukkit.towny.object.Town town = TownyIntegration.getTownAt(block.getLocation());
        if (town != null) {
            try {
                com.palmergames.bukkit.towny.object.Resident mayor = town.getMayor();
                if (mayor != null) {
                    SpiritEntity spirit = plugin.getManager().getSpiritByOwner(mayor.getUUID());
                    if (spirit != null && spirit.getMode() == SpiritEntity.SpiritMode.GUARDIAN && spirit.getMood() >= 90) {
                         spiritBonus = true;
                    }
                }
            } catch (Exception e) {}
        }

        if (rich) {
            // Gold particles (Redstone/Dust with RGB)
            Particle.DustOptions dustOptions = new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.0f);
            block.getWorld().spawnParticle(Particle.DUST, block.getLocation().add(0.5, 0.5, 0.5), 1, 0.3, 0.3, 0.3, dustOptions);
        }

        if (spiritBonus) {
            // Blue particles (Soul Fire Flame)
            // Reduced count to avoid visual clutter (10% chance per block)
            if (random.nextDouble() < 0.1) {
                block.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, block.getLocation().add(0.5, 0.8, 0.5), 1, 0.2, 0.2, 0.2, 0.01);
            }
        }
    }
}
