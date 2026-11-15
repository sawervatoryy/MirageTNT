package org.github.sawervatoryy.mirageTNT.listener;

import com.destroystokyo.paper.event.block.TNTPrimeEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.github.sawervatoryy.mirageTNT.MirageTNT;
import org.github.sawervatoryy.mirageTNT.hook.WorldGuardHook;
import org.github.sawervatoryy.mirageTNT.tnt.Tier;
import org.github.sawervatoryy.mirageTNT.util.Text;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Random;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Постановка, поджиг, обратный отсчёт, обезвреживание и взрыв кастомного динамита. */
public final class TntListener implements Listener {

    private final MirageTNT plugin;
    private final Random rnd = new Random();
    /** Кто последним кликнул по блоку (кремень/огненный шар) — нужен для сообщений при отказе в поджиге. */
    private final Map<String, UUID> clickers = new ConcurrentHashMap<>();

    private static String blockKey(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(org.bukkit.event.player.PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.TNT) return;
        final String key = blockKey(b);
        clickers.put(key, e.getPlayer().getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> clickers.remove(key), 40L);
    }
    /** Кто поджёг (в PDC не кладём: ShieldyBlocks запоминает ВСЕ PDC-ключи TNT как «отпечаток»). */
    private final Map<UUID, UUID> igniters = new ConcurrentHashMap<>();

    public TntListener(MirageTNT plugin) {
        this.plugin = plugin;
    }

    // ───────────────────────── подготовка горящего динамита ─────────────────────────

    /** Помечает TNTPrimed тиром (PDC), задаёт фитиль и табличку с отсчётом. */
    public void prepare(TNTPrimed tnt, Tier tier, UUID igniter, int fuseTicks) {
        PersistentDataContainer pdc = tnt.getPersistentDataContainer();
        pdc.set(plugin.items().keyTier, PersistentDataType.INTEGER, tier.id);
        if (igniter != null) igniters.put(tnt.getUniqueId(), igniter);
        tnt.setFuseTicks(fuseTicks);

        if (!plugin.getConfig().getBoolean("fuse-display.enabled", true)) return;
        final String format = plugin.getConfig().getString("fuse-display.format", "{name} &8• &f{time}с");
        tnt.setCustomNameVisible(true);
        new BukkitRunnable() {
            @Override public void run() {
                if (!tnt.isValid()) {
                    cancel();
                    return;
                }
                double sec = tnt.getFuseTicks() / 20.0;
                String text = Text.apply(format, "name", tier.displayName, "time", String.format(java.util.Locale.ROOT, "%.1f", sec));
                tnt.customName(Text.color(text));
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    private Tier tierOf(Entity e) {
        Integer id = e.getPersistentDataContainer().get(plugin.items().keyTier, PersistentDataType.INTEGER);
        return id == null ? null : plugin.tiers().get(id);
    }

    private UUID igniterOf(Entity e) {
        return igniters.get(e.getUniqueId());
    }

    // ───────────────────────── постановка / ломание ─────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Integer id = plugin.items().tierOf(e.getItemInHand());
        if (id == null || e.getBlockPlaced().getType() != Material.TNT) return;
        plugin.store().put(e.getBlockPlaced(), id);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (b.getType() == Material.ICE && plugin.engine().isTempIce(b)) {
            e.setDropItems(false);
            return;
        }
        if (b.getType() != Material.TNT) return;
        Integer id = plugin.store().take(b);
        if (id == null) return;
        Tier t = plugin.tiers().get(id);
        if (t == null) return;
        e.setDropItems(false);
        if (e.getPlayer().getGameMode() != GameMode.CREATIVE) {
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), plugin.items().create(t, 1));
        }
    }

    /** Поршни не двигают кастомный динамит (данные о тире привязаны к координатам). */
    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        for (Block b : e.getBlocks()) {
            if (b.getType() == Material.TNT && plugin.store().peek(b) != null) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        for (Block b : e.getBlocks()) {
            if (b.getType() == Material.TNT && plugin.store().peek(b) != null) {
                e.setCancelled(true);
                return;
            }
        }
    }

    // ───────────────────────── поджиг ─────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrime(TNTPrimeEvent e) {
        Block b = e.getBlock();
        Integer id = plugin.store().peek(b);
        if (id == null) {
            plugin.debug("Поджиг TNT без метки MirageTNT (" + e.getReason() + ") в " + b.getX() + " " + b.getY() + " " + b.getZ()
                    + " — это не динамит плагина (выдавайте через /mtnt give).");
            return;
        }
        if (e.isCancelled()) {
            if (!plugin.getConfig().getBoolean("general.force-ignite", true)) {
                plugin.debug("Поджиг отменён другим плагином, force-ignite выключен.");
                return;
            }
            plugin.debug("Поджиг был отменён другим плагином — принудительно разрешаю (general.force-ignite).");
            e.setCancelled(false);
        }
        Tier tier = plugin.tiers().get(id);
        UUID clicker = clickers.get(blockKey(b));
        Player p = clicker == null ? null : Bukkit.getPlayer(clicker);

        if (tier == null || !tier.enabled) {
            e.setCancelled(true);
            if (p != null) plugin.messages().send(p, "tier-disabled");
            return;
        }
        List<String> reasons = plugin.getConfig().getStringList("general.ignite-reasons");
        if (!reasons.isEmpty() && !reasons.contains(e.getReason().name())) {
            e.setCancelled(true);
            return;
        }
        if (plugin.getConfig().getStringList("general.blocked-worlds").contains(b.getWorld().getName())) {
            e.setCancelled(true);
            if (p != null) plugin.messages().send(p, "world-blocked");
            return;
        }
        if (inBlockedRegion(b)) {
            e.setCancelled(true);
            if (p != null) plugin.messages().send(p, "region-blocked");
            return;
        }
        if (!raidOpen(p)) {
            e.setCancelled(true);
            if (p != null) {
                var c = plugin.getConfig();
                plugin.messages().send(p, "raid-closed", "start", c.getString("raid-hours.start"),
                        "end", c.getString("raid-hours.end"), "zone", c.getString("raid-hours.zone"));
            }
            return;
        }
        int limit = plugin.getConfig().getInt("general.max-active-per-player", 0);
        if (limit > 0 && p != null && countActive(p.getUniqueId(), b) >= limit) {
            e.setCancelled(true);
            plugin.messages().send(p, "limit-active", "limit", limit);
        }
    }

    private boolean inBlockedRegion(Block b) {
        WorldGuardHook wg = plugin.worldGuard();
        List<String> blocked = plugin.getConfig().getStringList("general.blocked-regions");
        if (wg == null || blocked.isEmpty()) return false;
        for (WorldGuardHook.Region r : wg.regionsAt(b.getLocation())) if (blocked.contains(r.id)) return true;
        return false;
    }

    private int countActive(UUID id, Block b) {
        int n = 0;
        for (TNTPrimed t : b.getWorld().getEntitiesByClass(TNTPrimed.class)) {
            if (id.equals(igniterOf(t))) n++;
        }
        return n;
    }

    /** Окно «времени рейдов». */
    private boolean raidOpen(Player p) {
        var c = plugin.getConfig();
        if (!c.getBoolean("raid-hours.enabled", false)) return true;
        if (p != null && p.hasPermission("miragetnt.bypass.raidhours")) return true;
        try {
            ZoneId zone = ZoneId.of(c.getString("raid-hours.zone", "Europe/Moscow"));
            LocalTime now = LocalTime.now(zone);
            LocalTime start = LocalTime.parse(c.getString("raid-hours.start", "18:00"));
            LocalTime end = LocalTime.parse(c.getString("raid-hours.end", "23:00"));
            if (start.equals(end)) return true;
            return start.isBefore(end)
                    ? !now.isBefore(start) && now.isBefore(end)
                    : !now.isBefore(start) || now.isBefore(end);
        } catch (java.time.DateTimeException ex) {
            return true;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawn(EntitySpawnEvent e) {
        if (!(e.getEntity() instanceof TNTPrimed tnt)) return;
        if (tnt.getPersistentDataContainer().has(plugin.items().keyTier, PersistentDataType.INTEGER)) return;
        Block b = tnt.getLocation().getBlock();
        Integer id = plugin.store().take(b);
        if (id == null) return;
        Tier tier = plugin.tiers().get(id);
        if (tier == null || !tier.enabled) return;

        UUID igniter = null;
        Entity src = tnt.getSource();
        if (src instanceof Player p) igniter = p.getUniqueId();
        else if (src instanceof Projectile pr && pr.getShooter() instanceof Player sh) igniter = sh.getUniqueId();
        prepare(tnt, tier, igniter, tier.fuseTicks());
    }

    // ───────────────────────── взрыв ─────────────────────────

    @EventHandler(priority = EventPriority.LOWEST)
    public void onExplode(EntityExplodeEvent e) {
        if (plugin.engine().isFiring()) return;
        if (!(e.getEntity() instanceof TNTPrimed tnt)) return;
        Tier tier = tierOf(tnt);
        if (tier == null) return;
        e.setCancelled(true);
        e.blockList().clear();
        Location loc = e.getLocation();
        UUID igniter = igniters.remove(tnt.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> plugin.engine().detonate(loc, tier, igniter, tnt));
    }

    // ───────────────────────── обезвреживание ─────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onDefuse(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof TNTPrimed tnt)) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        var c = plugin.getConfig();
        if (!c.getBoolean("defuse.enabled", true)) return;
        Tier tier = tierOf(tnt);
        if (tier == null) return;
        Player p = e.getPlayer();
        if (!p.hasPermission("miragetnt.defuse")) return;
        if (c.getBoolean("defuse.require-sneak", true) && !p.isSneaking()) return;
        e.setCancelled(true);

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType() != Material.SHEARS) {
            plugin.messages().send(p, "defuse-need-shears");
            return;
        }
        damageShears(p, hand, c.getInt("defuse.shears-damage", 1));

        if (rnd.nextInt(100) < c.getInt("defuse.chance", 60)) {
            Location l = tnt.getLocation();
            igniters.remove(tnt.getUniqueId());
            tnt.remove();
            l.getWorld().dropItemNaturally(l, plugin.items().create(tier, 1));
            plugin.messages().send(p, "defuse-success");
        } else {
            tnt.setFuseTicks(Math.min(tnt.getFuseTicks(), Math.max(1, c.getInt("defuse.fail-fuse-ticks", 20))));
            plugin.messages().send(p, "defuse-fail");
        }
    }

    private void damageShears(Player p, ItemStack item, int amount) {
        if (amount <= 0 || p.getGameMode() == GameMode.CREATIVE) return;
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable d) {
            d.setDamage(d.getDamage() + amount);
            item.setItemMeta(meta);
            if (d.getDamage() >= item.getType().getMaxDurability()) item.setAmount(0);
        }
    }
}
