package org.github.sawervatoryy.mirageTNT.priv;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.github.sawervatoryy.mirageTNT.MirageTNT;
import org.github.sawervatoryy.mirageTNT.event.PrivateDamageEvent;
import org.github.sawervatoryy.mirageTNT.event.PrivateDestroyEvent;
import org.github.sawervatoryy.mirageTNT.hook.ShieldyHook;
import org.github.sawervatoryy.mirageTNT.hook.WorldGuardHook;
import org.github.sawervatoryy.mirageTNT.tnt.Tier;
import org.github.sawervatoryy.mirageTNT.util.Fx;
import org.github.sawervatoryy.mirageTNT.util.Text;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Приват-блоки: определение, прочность, трещины, регенерация, уничтожение.
 * Прочность хранится в damage.yml (переживает рестарт).
 */
public final class PrivateManager {

    public enum Mode { WG_CENTER, WG_ANY, MATERIAL }

    public static final class Found {
        public final PrivateType type;
        public final WorldGuardHook.Region region;
        public final ShieldyHook.Rec rec;

        Found(PrivateType type, WorldGuardHook.Region region) {
            this(type, region, null);
        }

        Found(PrivateType type, WorldGuardHook.Region region, ShieldyHook.Rec rec) {
            this.type = type;
            this.region = region;
            this.rec = rec;
        }
    }

    public static final class Result {
        public final boolean destroyed;
        public final boolean blocked;
        public final String reason;
        public final int left;
        public final int max;

        Result(boolean destroyed, boolean blocked, String reason, int left, int max) {
            this.destroyed = destroyed;
            this.blocked = blocked;
            this.reason = reason;
            this.left = left;
            this.max = max;
        }
    }

    private static final class Entry {
        String world;
        int x, y, z;
        int damage;
        long lastHit;
    }

    private final MirageTNT plugin;
    private final Map<Material, PrivateType> types = new EnumMap<>(Material.class);
    private final Map<String, Entry> damage = new ConcurrentHashMap<>();
    private final File file;
    private final Map<String, PrivateType> byId = new java.util.HashMap<>();
    private final Map<String, Long> shieldyHits = new ConcurrentHashMap<>();
    private Mode mode = Mode.WG_CENTER;
    private ShieldyHook shieldy;
    private BukkitTask maintenance;

    public PrivateManager(MirageTNT plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "damage.yml");
        reload();
        load();
    }

    /** Режим ShieldyBlocks: прочность считает он сам (голограмма обновляется). */
    public boolean isShieldy() {
        return shieldy != null;
    }

    public void reload() {
        types.clear();
        byId.clear();
        FileConfiguration cfg = plugin.getConfig();
        String pm = cfg.getString("privates.mode", "AUTO").toUpperCase(Locale.ROOT);
        shieldy = pm.equals("MIRAGE") ? null : ShieldyHook.create();
        if (pm.equals("SHIELDY") && shieldy == null) {
            plugin.getLogger().warning("privates.mode=SHIELDY, но ShieldyBlocks не найден — работаю в режиме MIRAGE.");
        }
        try {
            mode = Mode.valueOf(cfg.getString("privates.detection", "WG_CENTER").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            mode = Mode.WG_CENTER;
        }
        ConfigurationSection sec = cfg.getConfigurationSection("privates.blocks");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(id);
            if (s == null) continue;
            Material m = Material.matchMaterial(s.getString("material", ""));
            if (m == null) {
                plugin.getLogger().warning("privates.blocks." + id + ": неизвестный материал, пропущено.");
                continue;
            }
            PrivateType pt = new PrivateType(id, m, s.getInt("durability", 1), s.getBoolean("instant", false),
                    s.getString("name", id));
            types.put(m, pt);
            byId.put(id, pt);
        }
        syncFromShieldy();
        plugin.getLogger().info("Режим приватов: " + (shieldy != null ? "ShieldyBlocks (интеграция)" : "MirageTNT (собственная прочность)")
                + ", типов: " + types.size());
    }

    /** Подтягивает материалы и прочность из config.yml ShieldyBlocks, чтобы таблицы не расходились. */
    private void syncFromShieldy() {
        if (shieldy == null) return;
        File f = new File(shieldy.dataFolder(), "config.yml");
        if (!f.exists()) return;
        ConfigurationSection blocks = YamlConfiguration.loadConfiguration(f).getConfigurationSection("blocks");
        if (blocks == null) return;
        for (String id : blocks.getKeys(false)) {
            ConfigurationSection s = blocks.getConfigurationSection(id);
            if (s == null) continue;
            Material m = Material.matchMaterial(s.getString("material", ""));
            if (m == null) continue;
            PrivateType old = byId.get(id);
            if (old != null) types.remove(old.material);
            PrivateType pt = new PrivateType(id, m, s.getInt("durability", 1), old != null && old.instant,
                    old != null ? old.name : s.getString("display-name", id));
            types.put(m, pt);
            byId.put(id, pt);
        }
    }

    public int typeCount() {
        return types.size();
    }

    public PrivateType typeOf(Material m) {
        return types.get(m);
    }

    // ───────────────────────── определение ─────────────────────────

    public Found detect(Block b) {
        PrivateType t = types.get(b.getType());
        if (t == null) return null;
        if (shieldy != null) {
            ShieldyHook.Rec rec = shieldy.find(b);
            if (rec == null) return null;
            PrivateType byBlockId = byId.get(rec.blockId);
            return new Found(byBlockId != null ? byBlockId : t, null, rec);
        }
        if (mode == Mode.MATERIAL) return new Found(t, null);
        WorldGuardHook wg = plugin.worldGuard();
        if (wg == null) return null;
        List<String> ignored = plugin.getConfig().getStringList("protection.ignored-regions");
        int tol = plugin.getConfig().getInt("privates.center-tolerance", 1);
        for (WorldGuardHook.Region r : wg.regionsAt(b.getLocation())) {
            if (r.global || ignored.contains(r.id)) continue;
            if (mode == Mode.WG_ANY || r.isCenter(b.getX(), b.getZ(), tol)) return new Found(t, r);
        }
        return null;
    }

    // ───────────────────────── прочность ─────────────────────────

    private static String key(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    public int currentDamage(Block b) {
        if (shieldy != null) {
            ShieldyHook.Rec r = shieldy.find(b);
            PrivateType t = r == null ? null : byId.get(r.blockId);
            return r == null || t == null ? 0 : Math.max(0, t.durability - r.durability);
        }
        Entry e = damage.get(key(b));
        return e == null ? 0 : e.damage;
    }

    public int remaining(Block b, PrivateType t) {
        return Math.max(0, t.durability - currentDamage(b));
    }

    public void repair(Block b) {
        if (shieldy != null) return;
        damage.remove(key(b));
        clearCracks(b);
    }

    private boolean allOffline(WorldGuardHook.Region r) {
        Set<UUID> ids = new HashSet<>(r.owners);
        if (plugin.getConfig().getBoolean("privates.offline-protection.include-members", true)) ids.addAll(r.members);
        if (ids.isEmpty()) return false;
        for (UUID u : ids) if (Bukkit.getPlayer(u) != null) return false;
        return true;
    }


    // ───────────────────────── режим ShieldyBlocks ─────────────────────────

    private boolean allOfflineNames(ShieldyHook.Rec r) {
        if (Bukkit.getPlayerExact(r.owner) != null) return false;
        if (plugin.getConfig().getBoolean("privates.offline-protection.include-members", true)) {
            for (String n : r.participants) if (Bukkit.getPlayerExact(n) != null) return false;
        }
        return true;
    }

    /**
     * Урон привату наносит сам ShieldyBlocks: мы вызываем для блока события взрыва от нашего TNT
     * (он слушает EntityExplodeEvent и снимает прочность, обновляет голограмму, удаляет регион).
     */
    private Result hitShieldy(Block b, Found f, Tier tier, UUID attacker, Entity source) {
        PrivateType t = f.type;
        ShieldyHook.Rec rec = f.rec;
        Player att = attacker == null ? null : Bukkit.getPlayer(attacker);
        FileConfiguration cfg = plugin.getConfig();
        String pname = Text.strip(t.name);

        if (!t.instant && tier.pierce < t.durability) {
            int need = plugin.tiers().neededTier(t.durability);
            tell(att, plugin.messages().raw("private-weak", "type", pname, "need", need < 0 ? "?" : need));
            return new Result(false, true, "weak", rec.durability, t.durability);
        }
        if (cfg.getBoolean("privates.offline-protection.enabled", false) && allOfflineNames(rec)) {
            tell(att, plugin.messages().raw("private-offline"));
            return new Result(false, true, "offline", rec.durability, t.durability);
        }
        String k = key(b);
        long now = System.currentTimeMillis();
        int cd = cfg.getInt("privates.hit-cooldown-ticks", 0);
        Long last = shieldyHits.get(k);
        if (cd > 0 && !t.instant && last != null && now - last < cd * 50L) {
            tell(att, plugin.messages().raw("private-cooldown"));
            return new Result(false, true, "cooldown", rec.durability, t.durability);
        }
        if (source == null) {
            plugin.debug("Нет сущности-источника для удара по привату " + rec.regionName);
            return new Result(false, true, "no-source", rec.durability, t.durability);
        }
        PrivateDamageEvent ev = new PrivateDamageEvent(b, t.id, attacker, t.instant ? 99 : tier.privateDamage);
        Bukkit.getPluginManager().callEvent(ev);
        if (ev.isCancelled()) return new Result(false, true, "cancelled", rec.durability, t.durability);
        int dmg = Math.max(1, ev.getDamage());
        shieldyHits.put(k, now);

        boolean destroyed = false;
        int before = rec.durability;
        for (int i = 0; i < dmg; i++) {
            plugin.engine().fireImpact(source, b);
            ShieldyHook.Rec cur = shieldy.find(b);
            if (cur == null) {
                destroyed = true;
                break;
            }
            rec = cur;
        }
        plugin.debug("Приват " + f.rec.regionName + ": было " + before + ", теперь " + (destroyed ? "уничтожен" : rec.durability)
                + " (ударов: " + dmg + ", Tier " + tier.id + ")");
        plugin.stats().damage(attacker, Math.min(dmg, t.durability));
        Location loc = b.getLocation().add(0.5, 0.5, 0.5);
        Set<UUID> owners = new HashSet<>();
        owners.add(Bukkit.getOfflinePlayer(f.rec.owner).getUniqueId());

        if (destroyed) {
            shieldyHits.remove(k);
            clearCracks(b);
            Bukkit.getPluginManager().callEvent(new PrivateDestroyEvent(b, t.id, f.rec.regionName, owners, attacker));
            plugin.stats().destroyed(attacker);
            tell(att, plugin.messages().raw("private-destroyed", "type", pname));
            plugin.raidLog().log("ПРИВАТ УНИЧТОЖЕН: " + t.id + " (" + b.getWorld().getName() + " " + b.getX() + " " + b.getY() + " "
                    + b.getZ() + "), регион: " + f.rec.regionName + ", владелец: " + f.rec.owner + ", игрок: " + name(attacker));
            Fx.particle(loc, "EXPLOSION_EMITTER", 1, 0, 0, 0, 0);
            Fx.sound(loc, "entity.wither.break_block", 1f, 1f);
            return new Result(true, false, "destroyed", 0, t.durability);
        }
        int left = rec.durability;
        showCracks(b, t, Math.max(0, t.durability - left));
        tell(att, plugin.messages().raw("private-damaged", "type", pname, "left", left, "max", t.durability));
        if (cfg.getBoolean("privates.notify-owners.enabled", true)) {
            String sound = cfg.getString("privates.notify-owners.sound", "");
            Set<String> names = new HashSet<>(f.rec.participants);
            names.add(f.rec.owner);
            for (String n : names) {
                Player p = Bukkit.getPlayerExact(n);
                if (p == null) continue;
                p.sendMessage(Text.color(plugin.messages().raw("private-attacked", "type", pname, "x", b.getX(), "y", b.getY(),
                        "z", b.getZ(), "left", left, "max", t.durability)));
                if (!sound.isEmpty()) p.playSound(p.getLocation(), sound, 1f, 1f);
            }
        }
        plugin.raidLog().log("Приват " + t.id + " (" + b.getWorld().getName() + " " + b.getX() + " " + b.getY() + " " + b.getZ()
                + ") получил урон, осталось " + left + "/" + t.durability + ", Tier " + tier.id + ", игрок: " + name(attacker));
        return new Result(false, false, "damaged", left, t.durability);
    }

    // ───────────────────────── собственная прочность (режим MIRAGE) ─────────────────────────

    /** Один удар динамита по привату. */
    public Result hit(Block b, Found f, Tier tier, UUID attacker, Entity source) {
        if (f.rec != null) return hitShieldy(b, f, tier, attacker, source);
        PrivateType t = f.type;
        Player att = attacker == null ? null : Bukkit.getPlayer(attacker);
        FileConfiguration cfg = plugin.getConfig();

        if (!t.instant && tier.pierce < t.durability) {
            int need = plugin.tiers().neededTier(t.durability);
            tell(att, plugin.messages().raw("private-weak", "type", Text.strip(t.name), "need", need < 0 ? "?" : need));
            return new Result(false, true, "weak", remaining(b, t), t.durability);
        }
        if (cfg.getBoolean("privates.offline-protection.enabled", false) && f.region != null && allOffline(f.region)) {
            tell(att, plugin.messages().raw("private-offline"));
            return new Result(false, true, "offline", remaining(b, t), t.durability);
        }

        String k = key(b);
        Entry e = damage.get(k);
        long now = System.currentTimeMillis();
        int cd = cfg.getInt("privates.hit-cooldown-ticks", 0);
        if (cd > 0 && !t.instant && e != null && now - e.lastHit < cd * 50L) {
            tell(att, plugin.messages().raw("private-cooldown"));
            return new Result(false, true, "cooldown", remaining(b, t), t.durability);
        }

        PrivateDamageEvent ev = new PrivateDamageEvent(b, t.id, attacker, tier.privateDamage);
        Bukkit.getPluginManager().callEvent(ev);
        if (ev.isCancelled()) return new Result(false, true, "cancelled", remaining(b, t), t.durability);
        int dmg = ev.getDamage();

        if (e == null) {
            e = new Entry();
            e.world = b.getWorld().getName();
            e.x = b.getX();
            e.y = b.getY();
            e.z = b.getZ();
            damage.put(k, e);
        }
        e.lastHit = now;
        plugin.stats().damage(attacker, Math.min(dmg, t.durability));

        if (t.instant || e.damage + dmg >= t.durability) {
            destroy(b, f, attacker);
            tell(att, plugin.messages().raw("private-destroyed", "type", Text.strip(t.name)));
            return new Result(true, false, "destroyed", 0, t.durability);
        }

        e.damage += dmg;
        int left = t.durability - e.damage;
        showCracks(b, t, e.damage);
        tell(att, plugin.messages().raw("private-damaged", "type", Text.strip(t.name), "left", left, "max", t.durability));
        notifyOwners(f, b, t, left, false);
        plugin.raidLog().log("Приват " + t.id + " (" + b.getWorld().getName() + " " + b.getX() + " " + b.getY() + " " + b.getZ()
                + ") получил урон " + dmg + ", осталось " + left + "/" + t.durability + ", Tier " + tier.id + ", игрок: " + name(attacker));
        return new Result(false, false, "damaged", left, t.durability);
    }

    private void destroy(Block b, Found f, UUID attacker) {
        PrivateType t = f.type;
        FileConfiguration cfg = plugin.getConfig();
        damage.remove(key(b));
        clearCracks(b);
        Location loc = b.getLocation().add(0.5, 0.5, 0.5);
        World w = b.getWorld();
        Set<UUID> owners = f.region == null ? new HashSet<>() : f.region.owners;
        String regionId = f.region == null ? null : f.region.id;

        Bukkit.getPluginManager().callEvent(new PrivateDestroyEvent(b, t.id, regionId, owners, attacker));

        Material mat = b.getType();
        b.setType(Material.AIR);
        if (cfg.getBoolean("privates.on-destroy.drop-block", false)) w.dropItemNaturally(loc, new ItemStack(mat));
        if (cfg.getBoolean("privates.on-destroy.remove-region", true) && f.region != null && plugin.worldGuard() != null) {
            plugin.worldGuard().removeRegion(w, f.region.id);
        }
        for (String cmd : cfg.getStringList("privates.on-destroy.commands")) {
            String c = Text.apply(cmd, "world", w.getName(), "x", b.getX(), "y", b.getY(), "z", b.getZ(),
                    "type", t.id, "owner", firstOwner(owners), "attacker", name(attacker));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c);
        }
        notifyOwners(f, b, t, 0, true);
        plugin.stats().destroyed(attacker);
        plugin.raidLog().log("ПРИВАТ УНИЧТОЖЕН: " + t.id + " (" + w.getName() + " " + b.getX() + " " + b.getY() + " " + b.getZ()
                + "), регион: " + regionId + ", владелец: " + firstOwner(owners) + ", игрок: " + name(attacker));
        Fx.particle(loc, "EXPLOSION_EMITTER", 1, 0, 0, 0, 0);
        Fx.sound(loc, "entity.wither.break_block", 1f, 1f);
    }

    // ───────────────────────── оповещения ─────────────────────────

    private void tell(Player p, String legacy) {
        if (p != null && legacy != null && !legacy.isEmpty()) p.sendActionBar(Text.color(legacy));
    }

    private void notifyOwners(Found f, Block b, PrivateType t, int left, boolean lost) {
        if (!plugin.getConfig().getBoolean("privates.notify-owners.enabled", true) || f.region == null) return;
        String sound = plugin.getConfig().getString("privates.notify-owners.sound", "");
        for (UUID u : f.region.owners) {
            Player p = Bukkit.getPlayer(u);
            if (p == null) continue;
            String msg = plugin.messages().raw(lost ? "private-lost" : "private-attacked",
                    "type", Text.strip(t.name), "x", b.getX(), "y", b.getY(), "z", b.getZ(),
                    "left", left, "max", t.durability);
            p.sendMessage(Text.color(msg));
            if (!sound.isEmpty()) p.playSound(p.getLocation(), sound, 1f, 1f);
        }
    }

    private static String firstOwner(Set<UUID> owners) {
        for (UUID u : owners) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(u);
            if (op.getName() != null) return op.getName();
        }
        return "-";
    }

    private static String name(UUID id) {
        if (id == null) return "-";
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        return op.getName() == null ? id.toString() : op.getName();
    }

    // ───────────────────────── трещины ─────────────────────────

    public void showCracks(Block b, PrivateType t, int dmg) {
        if (!plugin.getConfig().getBoolean("privates.cracks", true)) return;
        float progress = Math.min(0.95f, (float) dmg / t.durability);
        Location loc = b.getLocation();
        for (Player p : b.getWorld().getNearbyPlayers(loc, 64)) p.sendBlockDamage(loc, progress);
    }

    public void clearCracks(Block b) {
        Location loc = b.getLocation();
        for (Player p : b.getWorld().getNearbyPlayers(loc, 64)) p.sendBlockDamage(loc, 0f);
    }

    // ───────────────────────── обслуживание ─────────────────────────

    public void start() {
        maintenance = Bukkit.getScheduler().runTaskTimer(plugin, this::maintain, 100L, 100L);
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::save, 6000L, 6000L);
    }

    private void maintain() {
        FileConfiguration cfg = plugin.getConfig();
        boolean regen = cfg.getBoolean("privates.regen.enabled", true);
        long after = cfg.getLong("privates.regen.after-seconds", 1800) * 1000L;
        int amount = Math.max(1, cfg.getInt("privates.regen.amount", 1));
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Entry>> it = damage.entrySet().iterator();
        while (it.hasNext()) {
            Entry e = it.next().getValue();
            World w = Bukkit.getWorld(e.world);
            if (w == null || !w.isChunkLoaded(e.x >> 4, e.z >> 4)) continue;
            Block b = w.getBlockAt(e.x, e.y, e.z);
            PrivateType t = types.get(b.getType());
            if (t == null) {
                it.remove();
                continue;
            }
            if (regen && now - e.lastHit >= after) {
                e.damage -= amount;
                e.lastHit = now;
                if (e.damage <= 0) {
                    it.remove();
                    clearCracks(b);
                    continue;
                }
            }
            showCracks(b, t, e.damage);
        }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String s : y.getStringList("entries")) {
            String[] p = s.split(";");
            if (p.length < 6) continue;
            try {
                Entry e = new Entry();
                e.world = p[0];
                e.x = Integer.parseInt(p[1]);
                e.y = Integer.parseInt(p[2]);
                e.z = Integer.parseInt(p[3]);
                e.damage = Integer.parseInt(p[4]);
                e.lastHit = Long.parseLong(p[5]);
                damage.put(e.world + ";" + e.x + ";" + e.y + ";" + e.z, e);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    public void save() {
        List<String> out = new ArrayList<>();
        for (Entry e : damage.values()) {
            out.add(e.world + ";" + e.x + ";" + e.y + ";" + e.z + ";" + e.damage + ";" + e.lastHit);
        }
        YamlConfiguration y = new YamlConfiguration();
        y.set("entries", out);
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Не удалось сохранить damage.yml: " + ex.getMessage());
        }
    }

    public void shutdown() {
        if (maintenance != null) maintenance.cancel();
        save();
    }
}
