package org.github.sawervatoryy.mirageTNT.tnt;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.github.sawervatoryy.mirageTNT.MirageTNT;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Поставленные блоки кастомного динамита. Хранятся в PDC чанка, поэтому переживают
 * рестарт сервера (в wHolyTNT это были metadata и терялись).
 */
public final class PlacedTntStore {

    private final NamespacedKey key;

    public PlacedTntStore(MirageTNT plugin) {
        this.key = new NamespacedKey(plugin, "placed_tnt");
    }

    private static String k(Block b) {
        return b.getX() + "," + b.getY() + "," + b.getZ();
    }

    private Map<String, Integer> read(Chunk c) {
        Map<String, Integer> map = new LinkedHashMap<>();
        String raw = c.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) return map;
        for (String part : raw.split(";")) {
            int eq = part.indexOf('=');
            if (eq < 0) continue;
            try {
                map.put(part.substring(0, eq), Integer.parseInt(part.substring(eq + 1)));
            } catch (NumberFormatException ignored) {
            }
        }
        return map;
    }

    private void write(Chunk c, Map<String, Integer> map) {
        PersistentDataContainer pdc = c.getPersistentDataContainer();
        if (map.isEmpty()) {
            pdc.remove(key);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            if (sb.length() > 0) sb.append(';');
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        pdc.set(key, PersistentDataType.STRING, sb.toString());
    }

    public void put(Block b, int tier) {
        Chunk c = b.getChunk();
        Map<String, Integer> map = read(c);
        map.put(k(b), tier);
        write(c, map);
    }

    /** Тир блока или null (если запись устарела — удаляется). */
    public Integer peek(Block b) {
        Chunk c = b.getChunk();
        Map<String, Integer> map = read(c);
        Integer t = map.get(k(b));
        if (t != null && b.getType() != Material.TNT) {
            map.remove(k(b));
            write(c, map);
            return null;
        }
        return t;
    }

    /** Забрать и удалить запись. */
    public Integer take(Block b) {
        Chunk c = b.getChunk();
        Map<String, Integer> map = read(c);
        Integer t = map.remove(k(b));
        if (t != null) write(c, map);
        return t;
    }

    /** Забрать запись без проверки материала (блок уже заменён воздухом). */
    public Integer takeRaw(Block b) {
        return take(b);
    }
}
