package org.github.sawervatoryy.mirageTNT.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;
import org.github.sawervatoryy.mirageTNT.MirageTNT;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Статистика рейдеров: stats.yml. */
public final class StatsStore {

    public static final class PlayerStats {
        public String name = "?";
        public int blasts, destroyed, damage, stolen;
    }

    private final MirageTNT plugin;
    private final Map<UUID, PlayerStats> map = new ConcurrentHashMap<>();
    private final File file;
    private BukkitTask task;

    public StatsStore(MirageTNT plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
        load();
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("stats.enabled", true);
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = y.getConfigurationSection("players");
        if (sec == null) return;
        for (String k : sec.getKeys(false)) {
            try {
                UUID id = UUID.fromString(k);
                ConfigurationSection s = sec.getConfigurationSection(k);
                if (s == null) continue;
                PlayerStats ps = new PlayerStats();
                ps.name = s.getString("name", "?");
                ps.blasts = s.getInt("blasts");
                ps.destroyed = s.getInt("destroyed");
                ps.damage = s.getInt("damage");
                ps.stolen = s.getInt("stolen");
                map.put(id, ps);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::save, 6000L, 6000L);
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerStats> e : map.entrySet()) {
            String p = "players." + e.getKey();
            PlayerStats s = e.getValue();
            y.set(p + ".name", s.name);
            y.set(p + ".blasts", s.blasts);
            y.set(p + ".destroyed", s.destroyed);
            y.set(p + ".damage", s.damage);
            y.set(p + ".stolen", s.stolen);
        }
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Не удалось сохранить stats.yml: " + ex.getMessage());
        }
    }

    public void shutdown() {
        if (task != null) task.cancel();
        save();
    }

    private PlayerStats of(UUID id) {
        PlayerStats ps = map.computeIfAbsent(id, k -> new PlayerStats());
        org.bukkit.OfflinePlayer op = plugin.getServer().getOfflinePlayer(id);
        if (op.getName() != null) ps.name = op.getName();
        return ps;
    }

    public void blast(UUID id) { if (id != null && enabled()) of(id).blasts++; }
    public void damage(UUID id, int d) { if (id != null && enabled()) of(id).damage += d; }
    public void destroyed(UUID id) { if (id != null && enabled()) of(id).destroyed++; }
    public void stolen(UUID id) { if (id != null && enabled()) of(id).stolen++; }

    public PlayerStats get(UUID id) {
        return map.get(id);
    }

    public PlayerStats find(String name) {
        for (PlayerStats s : map.values()) if (s.name.equalsIgnoreCase(name)) return s;
        return null;
    }

    public List<PlayerStats> top(int limit) {
        List<PlayerStats> list = new ArrayList<>(map.values());
        list.removeIf(s -> s.destroyed <= 0);
        list.sort((a, b) -> b.destroyed != a.destroyed ? Integer.compare(b.destroyed, a.destroyed) : Integer.compare(b.damage, a.damage));
        return list.size() > limit ? list.subList(0, limit) : list;
    }
}
