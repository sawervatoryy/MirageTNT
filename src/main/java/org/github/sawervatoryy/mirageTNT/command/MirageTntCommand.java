package org.github.sawervatoryy.mirageTNT.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.github.sawervatoryy.mirageTNT.MirageTNT;
import org.github.sawervatoryy.mirageTNT.data.StatsStore;
import org.github.sawervatoryy.mirageTNT.priv.PrivateManager;
import org.github.sawervatoryy.mirageTNT.tnt.Tier;
import org.github.sawervatoryy.mirageTNT.util.Text;
import org.github.sawervatoryy.mirageTNT.util.Watermark;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class MirageTntCommand implements CommandExecutor, TabCompleter {

    private final MirageTNT plugin;

    public MirageTntCommand(MirageTNT plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length == 0) {
            help(s);
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "give" -> give(s, a);
            case "list" -> list(s);
            case "check" -> check(s);
            case "repair" -> repair(s);
            case "stats" -> stats(s, a);
            case "top" -> top(s);
            case "reload" -> {
                if (!s.hasPermission("miragetnt.admin")) {
                    plugin.messages().send(s, "no-permission");
                    return true;
                }
                plugin.reloadAll();
                plugin.messages().send(s, "reload-ok", "tiers", plugin.tiers().count(), "privates", plugin.privates().typeCount());
            }
            case "about" -> {
                for (String l : Watermark.about(plugin.getPluginMeta().getVersion())) Text.send(s, l);
            }
            default -> help(s);
        }
        return true;
    }

    private void help(CommandSender s) {
        for (String l : plugin.messages().list("help")) Text.send(s, l);
        Text.send(s, Watermark.LORE_LINE);
    }

    private void give(CommandSender s, String[] a) {
        if (!s.hasPermission("miragetnt.admin")) {
            plugin.messages().send(s, "no-permission");
            return;
        }
        if (a.length < 2) {
            help(s);
            return;
        }
        String token = a[1].toLowerCase(Locale.ROOT);
        boolean cannon = token.equals("cannon") || token.equals("pushka");
        Tier tier = cannon ? null : plugin.tiers().find(token);
        if (!cannon && tier == null) {
            plugin.messages().send(s, "unknown-tier", "tier", a[1], "list", String.join(", ", plugin.tiers().tokens()) + ", cannon");
            return;
        }
        Player target;
        if (a.length >= 3) {
            target = Bukkit.getPlayerExact(a[2]);
            if (target == null) {
                plugin.messages().send(s, "unknown-player", "player", a[2]);
                return;
            }
        } else if (s instanceof Player p) {
            target = p;
        } else {
            plugin.messages().send(s, "player-only");
            return;
        }
        int amount = 1;
        if (a.length >= 4) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(a[3])));
            } catch (NumberFormatException ignored) {
            }
        }
        ItemStack item = cannon ? plugin.items().cannon(amount) : plugin.items().create(tier, amount);
        for (ItemStack left : target.getInventory().addItem(item).values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), left);
        }
        String name = cannon ? Text.strip(plugin.getConfig().getString("cannon.name", "Пушка")) : tier.plainName();
        plugin.messages().send(target, "give-received", "item", name, "amount", amount);
        if (s != target) plugin.messages().send(s, "give-success", "item", name, "amount", amount, "player", target.getName());
    }

    private void list(CommandSender s) {
        Text.send(s, plugin.messages().raw("list-header"));
        for (Tier t : plugin.tiers().all()) {
            Text.send(s, plugin.messages().raw("list-line", "id", t.id, "name", t.displayName, "radius", t.radius,
                    "pierce", t.pierce, "damage", t.privateDamage, "status", t.enabled ? "&aвкл" : "&cвыкл"));
        }
    }

    private Block target(Player p) {
        var r = p.rayTraceBlocks(8);
        return r == null ? null : r.getHitBlock();
    }

    private void check(CommandSender s) {
        if (!(s instanceof Player p)) {
            plugin.messages().send(s, "player-only");
            return;
        }
        if (!p.hasPermission("miragetnt.check")) {
            plugin.messages().send(s, "no-permission");
            return;
        }
        Block b = target(p);
        PrivateManager.Found f = b == null ? null : plugin.privates().detect(b);
        if (f == null) {
            plugin.messages().send(s, "check-none");
            return;
        }
        plugin.messages().send(s, "check-info", "type", Text.strip(f.type.name), "material", f.type.material.name(),
                "left", plugin.privates().remaining(b, f.type), "max", f.type.durability,
                "region", f.region == null ? "-" : f.region.id);
    }

    private void repair(CommandSender s) {
        if (!(s instanceof Player p)) {
            plugin.messages().send(s, "player-only");
            return;
        }
        if (!p.hasPermission("miragetnt.admin")) {
            plugin.messages().send(s, "no-permission");
            return;
        }
        Block b = target(p);
        PrivateManager.Found f = b == null ? null : plugin.privates().detect(b);
        if (f == null) {
            plugin.messages().send(s, "check-none");
            return;
        }
        if (plugin.privates().isShieldy()) {
            plugin.messages().send(s, "repair-shieldy");
            return;
        }
        plugin.privates().repair(b);
        plugin.messages().send(s, "repaired");
    }

    private void stats(CommandSender s, String[] a) {
        if (!s.hasPermission("miragetnt.stats")) {
            plugin.messages().send(s, "no-permission");
            return;
        }
        StatsStore.PlayerStats ps;
        boolean self = a.length < 2;
        if (self) {
            if (!(s instanceof Player p)) {
                plugin.messages().send(s, "player-only");
                return;
            }
            ps = plugin.stats().get(p.getUniqueId());
        } else {
            ps = plugin.stats().find(a[1]);
        }
        if (ps == null) ps = new StatsStore.PlayerStats();
        if (self) {
            plugin.messages().send(s, "stats-self", "blasts", ps.blasts, "destroyed", ps.destroyed, "damage", ps.damage, "stolen", ps.stolen);
        } else {
            plugin.messages().send(s, "stats-other", "player", a[1], "blasts", ps.blasts, "destroyed", ps.destroyed, "damage", ps.damage, "stolen", ps.stolen);
        }
    }

    private void top(CommandSender s) {
        if (!s.hasPermission("miragetnt.stats")) {
            plugin.messages().send(s, "no-permission");
            return;
        }
        List<StatsStore.PlayerStats> top = plugin.stats().top(10);
        Text.send(s, plugin.messages().raw("top-header"));
        if (top.isEmpty()) {
            Text.send(s, plugin.messages().raw("top-empty"));
            return;
        }
        int i = 1;
        for (StatsStore.PlayerStats p : top) {
            Text.send(s, plugin.messages().raw("top-line", "pos", i++, "player", p.name, "destroyed", p.destroyed, "blasts", p.blasts));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        String in = a[a.length - 1].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(List.of("give", "list", "check", "repair", "stats", "top", "reload", "about", "help"));
        } else if (a.length == 2 && a[0].equalsIgnoreCase("give")) {
            out.addAll(plugin.tiers().tokens());
            out.add("cannon");
        } else if (a.length == 3 && (a[0].equalsIgnoreCase("give") || a[0].equalsIgnoreCase("stats"))) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (a.length == 4 && a[0].equalsIgnoreCase("give")) {
            out.addAll(List.of("1", "8", "16", "64"));
        }
        return out.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(in)).distinct().collect(Collectors.toList());
    }
}
