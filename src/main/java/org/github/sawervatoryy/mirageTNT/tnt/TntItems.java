package org.github.sawervatoryy.mirageTNT.tnt;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.github.sawervatoryy.mirageTNT.MirageTNT;
import org.github.sawervatoryy.mirageTNT.util.Text;
import org.github.sawervatoryy.mirageTNT.util.Watermark;

import java.util.ArrayList;
import java.util.List;

/** Создание и распознавание предметов плагина (данные хранятся в PDC предмета). */
public final class TntItems {

    public final NamespacedKey keyTier;
    public final NamespacedKey keyCannon;
    public final NamespacedKey keyLock;
    public final NamespacedKey keyIgniter;

    private final MirageTNT plugin;

    public TntItems(MirageTNT plugin) {
        this.plugin = plugin;
        this.keyTier = new NamespacedKey(plugin, "tier");
        this.keyCannon = new NamespacedKey(plugin, "cannon");
        this.keyLock = new NamespacedKey(plugin, "lock");
        this.keyIgniter = new NamespacedKey(plugin, "igniter");
    }

    public ItemStack create(Tier tier, int amount) {
        ItemStack item = new ItemStack(Material.TNT, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.item(tier.displayName));
        List<String> lore = new ArrayList<>();
        for (String line : tier.lore) lore.add(fill(line, tier));
        lore.add(Watermark.LORE_LINE);
        meta.lore(Text.lore(lore));
        if (tier.glow) meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(keyTier, PersistentDataType.INTEGER, tier.id);
        item.setItemMeta(meta);
        return item;
    }

    private static String fill(String line, Tier t) {
        return Text.apply(line,
                "radius", trim(t.radius),
                "pierce", t.pierce,
                "private-damage", t.privateDamage,
                "fuse", trim(t.fuseSeconds),
                "freeze-radius", trim(t.freezeRadius),
                "freeze-seconds", t.freezeSeconds,
                "steal-chance", (int) Math.round(t.stealChance * 100));
    }

    private static String trim(double d) {
        return d == Math.floor(d) ? String.valueOf((int) d) : String.valueOf(d);
    }

    /** Номер тира у предмета или null. */
    public Integer tierOf(ItemStack item) {
        if (item == null || item.getType() != Material.TNT || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(keyTier, PersistentDataType.INTEGER);
    }

    public ItemStack cannon(int amount) {
        ItemStack item = new ItemStack(Material.DISPENSER, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.item(plugin.getConfig().getString("cannon.name", "&#00ACFFДинамитная пушка")));
        List<String> lore = new ArrayList<>(plugin.getConfig().getStringList("cannon.lore"));
        lore.add(Watermark.LORE_LINE);
        meta.lore(Text.lore(lore));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(keyCannon, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isCannon(ItemStack item) {
        if (item == null || item.getType() != Material.DISPENSER || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(keyCannon, PersistentDataType.BYTE);
    }
}
