package org.github.sawervatoryy.mirageTNT.util;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Частицы и звуки по именам из конфига (безопасно к опечаткам). */
public final class Fx {

    private static final Map<String, Optional<Particle>> CACHE = new HashMap<>();

    private Fx() {}

    private static Particle resolve(String name) {
        if (name == null) return null;
        String key = name.toUpperCase();
        Optional<Particle> cached = CACHE.get(key);
        if (cached == null) {
            try {
                cached = Optional.of(Particle.valueOf(key));
            } catch (IllegalArgumentException e) {
                cached = Optional.empty();
            }
            CACHE.put(key, cached);
        }
        return cached.orElse(null);
    }

    public static void particle(Location loc, String name, int count, double ox, double oy, double oz, double speed) {
        Particle p = resolve(name);
        if (p == null || loc.getWorld() == null) return;
        World w = loc.getWorld();
        Class<?> dt = p.getDataType();
        if (dt == Void.class) {
            w.spawnParticle(p, loc, count, ox, oy, oz, speed);
        } else if (dt == Particle.DustOptions.class) {
            w.spawnParticle(p, loc, count, ox, oy, oz, speed, new Particle.DustOptions(Color.fromRGB(0, 172, 255), 1.4f));
        } else if (dt == BlockData.class) {
            w.spawnParticle(p, loc, count, ox, oy, oz, speed, Material.OBSIDIAN.createBlockData());
        }
    }

    public static void particles(Location loc, List<String> names, int count, double spread, double speed) {
        for (String n : names) particle(loc, n, count, spread, spread, spread, speed);
    }

    public static void sound(Location loc, String key, float volume, float pitch) {
        if (key == null || key.isEmpty() || loc.getWorld() == null) return;
        loc.getWorld().playSound(loc, key, SoundCategory.BLOCKS, volume, pitch);
    }

    /** Расходящееся кольцо частиц — «ударная волна». */
    public static void shockwave(Plugin plugin, Location center, double maxRadius, String particle) {
        final int steps = 10;
        final Location c = center.clone();
        new BukkitRunnable() {
            int i = 1;
            @Override public void run() {
                double r = maxRadius * i / steps;
                int points = (int) Math.max(24, r * 10);
                for (int k = 0; k < points; k++) {
                    double a = 2 * Math.PI * k / points;
                    Location l = c.clone().add(Math.cos(a) * r, 0.2, Math.sin(a) * r);
                    particle(l, particle, 1, 0, 0.05, 0, 0.01);
                }
                if (++i > steps) cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
