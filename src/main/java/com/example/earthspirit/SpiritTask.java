package com.example.earthspirit;

import org.bukkit.scheduler.BukkitRunnable;

public class SpiritTask extends BukkitRunnable {
    private final EarthSpiritPlugin plugin;

    public SpiritTask(EarthSpiritPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        for (SpiritEntity spiritData : plugin.getManager().getAllSpirits().values()) {
            spiritData.tick();
        }
    }
}
