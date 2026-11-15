package org.github.sawervatoryy.mirageTNT.tnt;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.github.sawervatoryy.mirageTNT.MirageTNT;
import org.github.sawervatoryy.mirageTNT.hook.WorldGuardHook;
import org.github.sawervatoryy.mirageTNT.priv.PrivateManager;
import org.github.sawervatoryy.mirageTNT.util.Fx;
import org.github.sawervatoryy.mirageTNT.util.Text;
import org.github.sawervatoryy.mirageTNT.util.Watermark;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Движок взрывов MirageTNT. Ваниль не используется: список блоков считается самим плагином,
 * ломается «волнами» (blocks-per-tick), приваты получают урон по прочности.
 */
public final class BlastEngine {

    private enum Action { BREAK, DEGRADE, IGNITE, CANNON }

    private static final class Target {
        final Block block;
        final Material expected;
        final Action action;
        final Material next;
        final double dist;

        Target(Block block, Action action, Material next, double dist) {
            this.block = block;
            this.expected = block.getType();
            this.action = action;
            this.next = next;
            this.dist = dist;
        }
    }

    private static final class PrivTarget {
        final Block block;
        final PrivateManager.Found found;
        final double dist;

        PrivTarget(Block block, PrivateManager.Found found, double dist) {
            this.block = block;
            this.found = found;
            this.dist = dist;
        }
    }

    private final MirageTNT plugin;
    private final Random rnd = new Random();
    private boolean firing;

    private final Set<Material> neverBreak = EnumSet.noneOf(Material.class);
    private final Set<Material> hardBlocks = EnumSet.noneOf(Material.class);
    private final Map<Material, Material> chains = new EnumMap<>(Material.class);

    /** Временный лёд Ледяного динамита: блок -> исходные данные. */
    private final Map<Block, BlockData> frozen = new HashMap<>();

    public BlastEngine(MirageTNT plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration cfg = plugin.getConfig();
        fill(neverBreak, cfg.getStringList("protection.never-break"));
        fill(hardBlocks, cfg.getStringList("protection.hard-blocks"));
        chains.clear();
        var sec = cfg.getConfigurationSection("protection.chains");
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                Material from = Material.matchMaterial(k);
                Material to = Material.matchMaterial(sec.getString(k, ""));
                if (from != null && to != null) chains.put(from, to);
            }
        }
    }

    private static void fill(Set<Material> set, List<String> names) {
        set.clear();
        for (String n : names) {
            Material m = Material.matchMaterial(n);
            if (m != null) set.add(m);
        }
    }

    public boolean isFiring() {
        return firing;
    }

    public boolean isTempIce(Block b) {
        return frozen.containsKey(b);
    }

    /** При выключении плагина возвращаем воду на место. */
    public void shutdown() {
        for (Map.Entry<Block, BlockData> e : frozen.entrySet()) {
            if (e.getKey().getType() == Material.ICE) e.getKey().setBlockData(e.getValue());
        }
        frozen.clear();
    }

    // ───────────────────────── защита ─────────────────────────

    /** Блок защищён регионом / запрещённой зоной. */
    public boolean isProtected(Block b) {
        WorldGuardHook wg = plugin.worldGuard();
        if (wg == null) return false;
        FileConfiguration cfg = plugin.getConfig();
        List<String> blocked = cfg.getStringList("general.blocked-regions");
        List<String> ignored = cfg.getStringList("protection.ignored-regions");
        boolean all = cfg.getBoolean("protection.regions", true);
        for (WorldGuardHook.Region r : wg.regionsAt(b.getLocation())) {
            if (blocked.contains(r.id)) return true;
            if (r.global || ignored.contains(r.id)) continue;
            if (all || r.flagDenied) return true;
        }
        return false;
    }

    /** Только blocked-regions и явные запреты флагами (для кражи спавнеров). */
    private boolean isHardProtected(Block b) {
        WorldGuardHook wg = plugin.worldGuard();
        if (wg == null) return false;
        List<String> blocked = plugin.getConfig().getStringList("general.blocked-regions");
        for (WorldGuardHook.Region r : wg.regionsAt(b.getLocation())) {
            if (blocked.contains(r.id) || r.flagDenied) return true;
        }
        return false;
    }

    private static boolean isSubmerged(Block b) {
        Material m = b.getType();
        if (m == Material.WATER || m == Material.BUBBLE_COLUMN) return true;
        BlockData d = b.getBlockData();
        if (d instanceof Waterlogged w && w.isWaterlogged()) return true;
        for (BlockFace f : new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Material n = b.getRelative(f).getType();
            if (n == Material.WATER || n == Material.BUBBLE_COLUMN) return true;
        }
        return false;
    }

    // ───────────────────────── главный метод ─────────────────────────

    public void detonate(Location center, Tier tier, UUID igniter, Entity source) {
        World w = center.getWorld();
        if (w == null) return;
        if (plugin.getConfig().getStringList("general.blocked-worlds").contains(w.getName())) return;

        plugin.debug("Взрыв Tier " + tier.id + " в " + w.getName() + " " + center.getBlockX() + " " + center.getBlockY() + " " + center.getBlockZ()
                + ", игрок: " + igniter);
        plugin.stats().blast(igniter);
        playFx(center, tier);

        if (tier.type == Tier.Type.FROST) freeze(center, tier);
        if (tier.type == Tier.Type.THIEF) steal(center, tier, igniter);

        List<Target> targets = new ArrayList<>();
        List<PrivTarget> privs = new ArrayList<>();
        collect(center, tier, targets, privs);

        // совместимость: даём другим плагинам защиты возможность вычеркнуть блоки
        if (plugin.getConfig().getBoolean("general.fire-explode-event", true) && source != null && !targets.isEmpty()
                && !plugin.privates().isShieldy()) {
            List<Block> list = new ArrayList<>(targets.size());
            for (Target t : targets) list.add(t.block);
            EntityExplodeEvent ev = new EntityExplodeEvent(source, center, list, 0f, org.bukkit.ExplosionResult.DESTROY);
            firing = true;
            try {
                Bukkit.getPluginManager().callEvent(ev);
            } finally {
                firing = false;
            }
            if (ev.isCancelled()) {
                targets.clear();
            } else {
                Set<Block> allowed = new HashSet<>(ev.blockList());
                targets.removeIf(t -> !allowed.contains(t.block));
            }
        }

        // приваты получают урон мгновенно
        plugin.debug("Найдено приватов в радиусе: " + privs.size() + ", обычных блоков: " + targets.size());
        privs.sort(Comparator.comparingDouble(p -> p.dist));
        for (PrivTarget p : privs) {
            plugin.privates().hit(p.block, p.found, tier, igniter, source);
        }

        damageEntities(center, tier, source);
        if (tier.lightning) w.strikeLightningEffect(center);

        targets.sort(Comparator.comparingDouble(t -> t.dist));
        if (!targets.isEmpty()) scheduleBreak(targets, tier, igniter);
    }

    /**
     * Имитирует «настоящий» взрыв нашего TNT у приват-блока, чтобы ShieldyBlocks (слушает EntityExplodeEvent
     * от PRIMED_TNT) снял 1 прочность, обновил голограмму и при нуле удалил регион.
     * Событие идёт от центра привата: ShieldyBlocks ищет приваты в радиусе 8 от точки взрыва,
     * а соседний приват быть так близко не может (регионы не пересекаются).
     */
    public void fireImpact(Entity source, Block privateBlock) {
        Location loc = privateBlock.getLocation().add(0.5, 0.5, 0.5);
        List<Block> list = new ArrayList<>();
        list.add(privateBlock);
        EntityExplodeEvent ev = new EntityExplodeEvent(source, loc, list, 0f, org.bukkit.ExplosionResult.DESTROY);
        boolean prev = firing;
        firing = true;
        try {
            Bukkit.getPluginManager().callEvent(ev);
        } finally {
            firing = prev;
        }
    }

    private void playFx(Location c, Tier tier) {
        Fx.sound(c, tier.sound, 4f, tier.soundPitch);
        Fx.particles(c, tier.particles, 3, 0.5, 0.05);
        if (tier.shockwave) Fx.shockwave(plugin, c, Math.min(tier.radius * 1.6, 24), tier.shockwaveParticle);
    }

    // ───────────────────────── сбор блоков ─────────────────────────

    private void collect(Location c, Tier tier, List<Target> targets, List<PrivTarget> privs) {
        World w = c.getWorld();
        int r = (int) Math.ceil(tier.radius * (1 + tier.irregularity)) + 1;
        int cx = c.getBlockX(), cy = c.getBlockY(), cz = c.getBlockZ();
        int minY = w.getMinHeight(), maxY = w.getMaxHeight() - 1;
        List<Target> raw = new ArrayList<>();

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                int by = cy + y;
                if (by < minY || by > maxY) continue;
                for (int z = -r; z <= r; z++) {
                    double dx = cx + x + 0.5 - c.getX();
                    double dy = by + 0.5 - c.getY();
                    double dz = cz + z + 0.5 - c.getZ();
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    double limit = tier.radius * (1 + (rnd.nextDouble() - 0.5) * 2 * tier.irregularity);
                    if (dist > limit) continue;
                    Block b = w.getBlockAt(cx + x, by, cz + z);
                    process(b, tier, dist, raw, privs);
                }
            }
        }
        // ограничение по числу блоков: оставляем ближайшие
        raw.sort(Comparator.comparingDouble(t -> t.dist));
        int limit = Math.min(raw.size(), tier.maxBlocks);
        for (int i = 0; i < limit; i++) targets.add(raw.get(i));
    }

    private void process(Block b, Tier tier, double dist, List<Target> out, List<PrivTarget> privs) {
        Material m = b.getType();
        if (m.isAir() || m == Material.WATER || m == Material.LAVA) return;

        if (plugin.privates().typeOf(m) != null) {
            PrivateManager.Found f = plugin.privates().detect(b);
            if (f != null) {
                privs.add(new PrivTarget(b, f, dist));
                return;
            }
        }
        if (neverBreak.contains(m)) return;
        if (isProtected(b)) return;

        if (m == Material.TNT) {
            out.add(new Target(b, Action.IGNITE, null, dist));
            return;
        }
        if (m == Material.DISPENSER && plugin.cannons().isCannon(b)) {
            out.add(new Target(b, Action.CANNON, null, dist));
            return;
        }
        if (!tier.breakUnderwater && isSubmerged(b)) return;

        if (hardBlocks.contains(m)) {
            if (!tier.breakHard) return;
            Material next = chains.get(m);
            if (next != null) out.add(new Target(b, Action.DEGRADE, next, dist));
            else out.add(new Target(b, Action.BREAK, null, dist));
            return;
        }
        out.add(new Target(b, Action.BREAK, null, dist));
    }

    // ───────────────────────── поэтапное разрушение ─────────────────────────

    private void scheduleBreak(List<Target> targets, Tier tier, UUID igniter) {
        new BukkitRunnable() {
            int idx = 0;

            @Override
            public void run() {
                int n = 0;
                while (idx < targets.size() && n < tier.blocksPerTick) {
                    apply(targets.get(idx), tier, igniter, idx);
                    idx++;
                    n++;
                }
                if (idx >= targets.size()) cancel();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void apply(Target t, Tier tier, UUID igniter, int index) {
        Block b = t.block;
        if (b.getType() != t.expected) return;
        Location loc = b.getLocation().add(0.5, 0.5, 0.5);
        switch (t.action) {
            case DEGRADE -> {
                b.setType(t.next);
                Fx.particle(loc, "ENCHANT", 8, 0.4, 0.4, 0.4, 0.4);
                Fx.sound(loc, "block.respawn_anchor.deplete", 1f, 0.8f);
            }
            case IGNITE -> igniteTnt(b, igniter);
            case CANNON -> plugin.cannons().dismantle(b, true);
            default -> {
                if (rnd.nextDouble() < tier.dropChance) b.breakNaturally();
                else b.setType(Material.AIR);
                if (index % 8 == 0) Fx.particle(loc, "POOF", 2, 0.3, 0.3, 0.3, 0.02);
            }
        }
    }

    private void igniteTnt(Block b, UUID igniter) {
        Integer id = plugin.store().take(b);
        Tier t = id == null ? null : plugin.tiers().get(id);
        b.setType(Material.AIR);
        World w = b.getWorld();
        Location l = b.getLocation().add(0.5, 0, 0.5);
        int fuse = 10 + rnd.nextInt(20);
        w.spawn(l, TNTPrimed.class, tnt -> {
            tnt.setFuseTicks(fuse);
            tnt.setVelocity(new Vector(0, 0.2, 0));
            if (t != null && t.enabled) plugin.tntListener().prepare(tnt, t, igniter, fuse);
        });
    }

    // ───────────────────────── Ледяной ─────────────────────────

    private void freeze(Location c, Tier tier) {
        World w = c.getWorld();
        int r = (int) Math.ceil(tier.freezeRadius);
        int cx = c.getBlockX(), cy = c.getBlockY(), cz = c.getBlockZ();
        List<Block> changed = new ArrayList<>();
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    if (Math.sqrt(x * x + y * y + z * z) > tier.freezeRadius) continue;
                    Block b = w.getBlockAt(cx + x, cy + y, cz + z);
                    Material m = b.getType();
                    boolean water = m == Material.WATER || m == Material.BUBBLE_COLUMN || m == Material.KELP
                            || m == Material.KELP_PLANT || m == Material.SEAGRASS || m == Material.TALL_SEAGRASS;
                    if (!water || frozen.containsKey(b) || isProtected(b)) continue;
                    frozen.put(b, b.getBlockData().clone());
                    b.setType(Material.ICE, false);
                    changed.add(b);
                }
            }
        }
        if (!changed.isEmpty()) {
            Fx.sound(c, "block.glass.break", 2f, 0.5f);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Block b : changed) {
                    BlockData old = frozen.remove(b);
                    if (old != null && b.getType() == Material.ICE) b.setBlockData(old);
                }
            }, tier.freezeSeconds * 20L);
        }
        // эффекты на существ
        PotionEffectType slow = PotionEffectType.getByKey(NamespacedKey.minecraft("slowness"));
        for (Entity e : w.getNearbyEntities(c, tier.freezeRadius, tier.freezeRadius, tier.freezeRadius)) {
            if (!(e instanceof LivingEntity le) || e instanceof ArmorStand) continue;
            if (le instanceof Player p && (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR)) continue;
            le.setFreezeTicks(le.getMaxFreezeTicks() + tier.entityFreezeSeconds * 20);
            if (slow != null) le.addPotionEffect(new PotionEffect(slow, tier.slownessSeconds * 20, tier.slownessAmplifier));
        }
    }

    // ───────────────────────── Расхититель ─────────────────────────

    private void steal(Location c, Tier tier, UUID igniter) {
        World w = c.getWorld();
        int r = tier.stealRadius;
        int cx = c.getBlockX(), cy = c.getBlockY(), cz = c.getBlockZ();
        List<Block> found = new ArrayList<>();
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    Block b = w.getBlockAt(cx + x, cy + y, cz + z);
                    if (b.getType() != Material.SPAWNER) continue;
                    boolean prot = tier.stealIgnoreRegions ? isHardProtected(b) : isProtected(b);
                    if (!prot) found.add(b);
                }
            }
        }
        found.sort(Comparator.comparingDouble(b -> b.getLocation().add(0.5, 0.5, 0.5).distanceSquared(c)));
        int n = 0;
        for (Block b : found) {
            if (n++ >= tier.maxSpawners) break;
            stealSpawner(b, tier, igniter);
        }
    }

    private void stealSpawner(Block b, Tier tier, UUID igniter) {
        if (!(b.getState() instanceof CreatureSpawner cs)) return;
        EntityType type = cs.getSpawnedType();
        boolean keep = type != null && rnd.nextDouble() < tier.stealChance;
        EntityType finalType = keep ? type : EntityType.PIG;

        ItemStack item = new ItemStack(Material.SPAWNER);
        BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();
        CreatureSpawner is = (CreatureSpawner) meta.getBlockState();
        is.setSpawnedType(finalType);
        meta.setBlockState(is);
        String mob = finalType.name();
        meta.displayName(Text.item("&fРассадник &#EF00FF" + mob));
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(" &f- Спавнер получен с помощью");
        lore.add("   &fуникального динамита &#EF00FFРасхититель");
        lore.add("");
        lore.add(Watermark.LORE_LINE);
        meta.lore(Text.lore(lore));
        item.setItemMeta(meta);

        Location loc = b.getLocation().add(0.5, 0.5, 0.5);
        b.getWorld().dropItemNaturally(loc, item);
        b.setType(Material.AIR);
        Fx.sound(loc, keep ? "entity.zombie.death" : "entity.pig.death", 1f, 1f);
        Fx.particle(loc, "PORTAL", 40, 0.5, 0.5, 0.5, 0.5);
        plugin.stats().stolen(igniter);
        Player p = igniter == null ? null : Bukkit.getPlayer(igniter);
        if (p != null) p.sendActionBar(Text.color(plugin.messages().raw("spawner-stolen", "mob", mob)));
        plugin.raidLog().log("Украден спавнер " + mob + " (" + b.getWorld().getName() + " " + b.getX() + " " + b.getY() + " " + b.getZ()
                + "), сохранён моб: " + keep + ", игрок: " + (p == null ? "-" : p.getName()));
    }

    // ───────────────────────── урон по существам ─────────────────────────

    private void damageEntities(Location c, Tier tier, Entity source) {
        if (!tier.damageEnabled || tier.damageRadius <= 0) return;
        World w = c.getWorld();
        double dr = tier.damageRadius;
        for (Entity e : w.getNearbyEntities(c, dr, dr, dr)) {
            if (!(e instanceof LivingEntity le) || e instanceof ArmorStand) continue;
            if (e instanceof Player p) {
                if (!tier.damagePlayers) continue;
                if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) continue;
            }
            double dist = e.getLocation().distance(c);
            if (dist > dr) continue;
            double base = tier.maxDamage - dist * tier.dropPerBlock;
            if (base <= 0) continue;
            int cover = coverBlocks(c, le.getEyeLocation());
            double dmg = base * Math.max(0, 1.0 - cover * tier.coverReduction);
            if (dmg > 0) le.damage(dmg);
            if (tier.knockback > 0) {
                Vector v = e.getLocation().toVector().subtract(c.toVector());
                if (v.lengthSquared() > 0.0001) {
                    v.normalize().multiply(tier.knockback * Math.max(0.1, 1 - dist / dr)).setY(0.35);
                    le.setVelocity(le.getVelocity().add(v));
                }
            }
        }
    }

    private int coverBlocks(Location from, Location to) {
        Vector dir = to.toVector().subtract(from.toVector());
        double distance = dir.length();
        if (distance < 0.5) return 0;
        dir.normalize();
        int count = 0;
        for (double i = 0.5; i < distance; i += 0.5) {
            Location p = from.clone().add(dir.clone().multiply(i));
            if (p.getBlock().getType().isSolid()) {
                count++;
                i += 0.5;
            }
        }
        return count;
    }

    // ───────────────────────── утилиты ─────────────────────────

    public static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
