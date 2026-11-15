package org.github.sawervatoryy.mirageTNT.listener;

import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Dispenser;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.github.sawervatoryy.mirageTNT.MirageTNT;
import org.github.sawervatoryy.mirageTNT.hook.WorldGuardHook;
import org.github.sawervatoryy.mirageTNT.tnt.Tier;
import org.github.sawervatoryy.mirageTNT.util.Fx;
import org.github.sawervatoryy.mirageTNT.util.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Динамитная пушка. Флаг пушки хранится в PDC раздатчика (переживает рестарт).
 * Центральный слот (4) — заряд, остальные закрыты стеклом на время открытого меню.
 */
public final class CannonListener implements Listener {

    private static final int SLOT = 4;

    private final MirageTNT plugin;
    private final Map<String, Long> cooldowns = new HashMap<>();

    public CannonListener(MirageTNT plugin) {
        this.plugin = plugin;
    }

    // ───────────────────────── определение ─────────────────────────

    public boolean isCannon(Block b) {
        if (b.getType() != Material.DISPENSER) return false;
        return b.getState() instanceof Dispenser d
                && d.getPersistentDataContainer().has(plugin.items().keyCannon, PersistentDataType.BYTE);
    }

    private boolean isCannonInv(Inventory inv) {
        return inv.getHolder() instanceof Dispenser d
                && d.getPersistentDataContainer().has(plugin.items().keyCannon, PersistentDataType.BYTE);
    }

    private boolean isLock(ItemStack it) {
        return it != null && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(plugin.items().keyLock, PersistentDataType.BYTE);
    }

    // ───────────────────────── установка / ломание ─────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!plugin.items().isCannon(e.getItemInHand())) return;
        if (e.getBlockPlaced().getState() instanceof Dispenser d) {
            d.getPersistentDataContainer().set(plugin.items().keyCannon, PersistentDataType.BYTE, (byte) 1);
            d.update();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!isCannon(b)) return;
        if (e.isCancelled()) {
            // пушку можно сломать даже в чужом привате
            if (!plugin.getConfig().getBoolean("cannon.breakable-in-foreign-region", true)) return;
            e.setCancelled(false);
        }
        e.setDropItems(false);
        dismantle(b, e.getPlayer().getGameMode() != GameMode.CREATIVE);
    }

    /** Разбирает пушку: чистит стёкла, возвращает заряд и предмет пушки. */
    public void dismantle(Block b, boolean drop) {
        if (!(b.getState() instanceof Dispenser d)) return;
        Inventory inv = d.getInventory();
        ItemStack charge = inv.getItem(SLOT);
        inv.clear();
        Location loc = b.getLocation().add(0.5, 0.5, 0.5);
        b.setType(Material.AIR);
        if (drop) {
            b.getWorld().dropItemNaturally(loc, plugin.items().cannon(1));
            if (charge != null && charge.getType() != Material.AIR && !isLock(charge)) {
                b.getWorld().dropItemNaturally(loc, charge);
            }
        }
    }

    // ───────────────────────── меню ─────────────────────────

    @EventHandler
    public void onOpen(InventoryOpenEvent e) {
        Inventory inv = e.getInventory();
        if (!isCannonInv(inv)) return;
        ItemStack glass = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta m = glass.getItemMeta();
        m.displayName(Text.item("&f"));
        m.getPersistentDataContainer().set(plugin.items().keyLock, PersistentDataType.BYTE, (byte) 1);
        glass.setItemMeta(m);
        for (int i = 0; i < 9; i++) if (i != SLOT) inv.setItem(i, glass);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        Inventory inv = e.getInventory();
        if (!isCannonInv(inv)) return;
        for (int i = 0; i < 9; i++) if (isLock(inv.getItem(i))) inv.setItem(i, null);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!isCannonInv(top)) return;
        Player p = e.getWhoClicked() instanceof Player pl ? pl : null;
        int raw = e.getRawSlot();

        if (raw >= 0 && raw < 9) {
            if (raw != SLOT) {
                e.setCancelled(true);
                return;
            }
            if (e.getAction() == InventoryAction.HOTBAR_SWAP || e.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD) {
                ItemStack hot = p == null || e.getHotbarButton() < 0 ? null : p.getInventory().getItem(e.getHotbarButton());
                if (hot != null && hot.getType() != Material.AIR && !allowed(hot)) {
                    e.setCancelled(true);
                    if (p != null) plugin.messages().send(p, "cannon-only-tnt");
                }
                return;
            }
            ItemStack cur = e.getCursor();
            if (cur != null && cur.getType() != Material.AIR && !allowed(cur)) {
                e.setCancelled(true);
                if (p != null) plugin.messages().send(p, "cannon-only-tnt");
            }
            return;
        }
        // shift-клик из инвентаря игрока: предмет мог бы попасть не в тот слот
        if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            e.setCancelled(true);
            if (p != null) plugin.messages().send(p, "cannon-center-only");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (!isCannonInv(e.getView().getTopInventory())) return;
        for (int raw : e.getRawSlots()) {
            if (raw >= 0 && raw < 9) {
                if (raw != SLOT || !allowed(e.getOldCursor())) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
    }

    private boolean allowed(ItemStack it) {
        if (it == null || it.getType() != Material.TNT) return false;
        if (plugin.items().tierOf(it) != null) return true;
        return plugin.getConfig().getBoolean("cannon.allow-vanilla-tnt", false);
    }

    // ───────────────────────── выстрел ─────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent e) {
        Block block = e.getBlock();
        if (block.getType() != Material.DISPENSER) return;
        boolean cannon = isCannon(block);

        // обычный раздатчик не должен выстреливать кастомным динамитом
        if (!cannon) {
            if (plugin.items().tierOf(e.getItem()) != null) e.setCancelled(true);
            return;
        }
        e.setCancelled(true);

        String key = block.getWorld().getName() + block.getX() + ";" + block.getY() + ";" + block.getZ();
        long now = System.currentTimeMillis();
        long cd = plugin.getConfig().getInt("cannon.cooldown-ticks", 20) * 50L;
        Long last = cooldowns.get(key);
        if (last != null && now - last < cd) return;
        cooldowns.put(key, now);

        Dispenser d = (Dispenser) block.getState();
        Inventory inv = d.getInventory();
        ItemStack charge = inv.getItem(SLOT);
        if (charge == null || charge.getType() != Material.TNT) return;
        Integer id = plugin.items().tierOf(charge);
        Tier tier = id == null ? null : plugin.tiers().get(id);
        if (id == null && !plugin.getConfig().getBoolean("cannon.allow-vanilla-tnt", false)) return;
        if (tier != null && !tier.enabled) return;

        launch(block, tier);
        if (charge.getAmount() > 1) charge.setAmount(charge.getAmount() - 1);
        else inv.setItem(SLOT, null);
    }

    private void launch(Block block, Tier tier) {
        if (!(block.getBlockData() instanceof Directional dir)) return;
        BlockFace facing = dir.getFacing();
        Vector direction = facing.getDirection().normalize();
        Location spawn = block.getLocation().add(0.5, 0.5, 0.5).add(direction.clone().multiply(1.2));
        Fx.sound(spawn, "entity.tnt.primed", 1f, 1f);
        Fx.sound(spawn, "entity.generic.explode", 0.6f, 1.6f);
        Fx.particle(spawn, "SMOKE", 12, 0.2, 0.2, 0.2, 0.03);

        final int maxFlight = plugin.getConfig().getInt("cannon.max-flight-ticks", 200);
        final double speed = Math.max(0.1, plugin.getConfig().getDouble("cannon.speed", 1.0));
        final TNTPrimed tnt = block.getWorld().spawn(spawn, TNTPrimed.class, t -> {
            t.setGravity(false);
            t.setFuseTicks(maxFlight + 40);
            if (tier != null) plugin.tntListener().prepare(t, tier, null, maxFlight + 40);
        });
        final String trail = tier != null && !tier.particles.isEmpty() ? tier.particles.get(0) : "SMOKE";

        new BukkitRunnable() {
            int ticks = 0;

            @Override public void run() {
                if (!tnt.isValid()) {
                    cancel();
                    return;
                }
                Location loc = tnt.getLocation();
                Vector step = direction.clone().multiply(speed);
                RayTraceResult hit = loc.getWorld().rayTraceBlocks(loc, direction, speed + 0.3, FluidCollisionMode.NEVER, true);
                boolean entityHit = false;
                for (Entity en : loc.getWorld().getNearbyEntities(loc, 0.7, 0.7, 0.7)) {
                    if (ticks > 3 && en != tnt && en instanceof LivingEntity) {
                        entityHit = true;
                        break;
                    }
                }
                if (hit != null || entityHit || ++ticks > maxFlight) {
                    if (hit != null && hit.getHitPosition() != null) {
                        Vector hp = hit.getHitPosition();
                        tnt.teleport(new Location(loc.getWorld(), hp.getX(), hp.getY(), hp.getZ()).subtract(direction.clone().multiply(0.25)));
                    }
                    tnt.setFuseTicks(1);
                    cancel();
                    return;
                }
                tnt.setVelocity(step);
                Fx.particle(loc, trail, 2, 0.05, 0.05, 0.05, 0.01);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}
