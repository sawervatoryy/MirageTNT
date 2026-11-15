package org.github.sawervatoryy.mirageTNT.tnt;

import org.bukkit.configuration.ConfigurationSection;
import org.github.sawervatoryy.mirageTNT.MirageTNT;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class TierRegistry {

    private final MirageTNT plugin;
    private final Map<Integer, Tier> tiers = new TreeMap<>();

    public TierRegistry(MirageTNT plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        tiers.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("tiers");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            int id;
            try {
                id = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Тир '" + key + "' пропущен: ключ должен быть числом.");
                continue;
            }
            ConfigurationSection s = sec.getConfigurationSection(key);
            if (s != null) tiers.put(id, new Tier(id, s));
        }
    }

    public Tier get(int id) {
        return tiers.get(id);
    }

    public Collection<Tier> all() {
        return tiers.values();
    }

    public int count() {
        return tiers.size();
    }

    /** Поиск по номеру, алиасу или типу: "3", "frost", "titan". */
    public Tier find(String token) {
        if (token == null) return null;
        String t = token.toLowerCase(Locale.ROOT);
        try {
            return tiers.get(Integer.parseInt(t));
        } catch (NumberFormatException ignored) {
        }
        for (Tier tier : tiers.values()) if (tier.aliases.contains(t)) return tier;
        return null;
    }

    /** Минимальный включённый тир, способный повредить приват такой прочности; -1 если нет. */
    public int neededTier(int durability) {
        for (Tier t : tiers.values()) if (t.enabled && t.pierce >= durability) return t.id;
        return -1;
    }

    public java.util.List<String> tokens() {
        java.util.List<String> out = new ArrayList<>();
        for (Tier t : tiers.values()) {
            out.add(String.valueOf(t.id));
            out.addAll(t.aliases);
        }
        return out;
    }
}
