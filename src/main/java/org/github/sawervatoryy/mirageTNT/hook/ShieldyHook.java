package org.github.sawervatoryy.mirageTNT.hook;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * Интеграция с ShieldyBlocks (imApeki) через рефлексию — без compile-зависимости.
 * Читает записи регионов из его базы; урон наносится через событие взрыва (см. BlastEngine#fireImpact).
 */
public final class ShieldyHook {

    public static final class Rec {
        public final String blockId;
        public final int durability;
        public final String owner;
        public final Set<String> participants;
        public final String regionName;

        Rec(String blockId, int durability, String owner, Set<String> participants, String regionName) {
            this.blockId = blockId;
            this.durability = durability;
            this.owner = owner;
            this.participants = participants;
            this.regionName = regionName;
        }
    }

    private final Plugin plugin;
    private final Method getRepo;
    private final Method find;
    private Method mBlockId, mDur, mOwner, mParts, mName;

    public static ShieldyHook create() {
        Plugin p = Bukkit.getPluginManager().getPlugin("ShieldyBlocks");
        if (p == null || !p.isEnabled()) return null;
        try {
            return new ShieldyHook(p);
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[MirageTNT] Не удалось подключить ShieldyBlocks: " + t);
            return null;
        }
    }

    private ShieldyHook(Plugin p) throws Exception {
        this.plugin = p;
        this.getRepo = p.getClass().getMethod("currentRegionRepository");
        Object repo = getRepo.invoke(p);
        this.find = repo.getClass().getMethod("findByLocation", String.class, int.class, int.class, int.class);
    }

    public File dataFolder() {
        return plugin.getDataFolder();
    }

    @SuppressWarnings("unchecked")
    public Rec find(Block b) {
        try {
            Object repo = getRepo.invoke(plugin);
            Object rec = find.invoke(repo, b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
            if (rec == null) return null;
            if (mBlockId == null) {
                Class<?> c = rec.getClass();
                mBlockId = c.getMethod("getBlockId");
                mDur = c.getMethod("getCurrentDurability");
                mOwner = c.getMethod("getOwnerName");
                mParts = c.getMethod("getParticipantNames");
                mName = c.getMethod("getRegionName");
            }
            Set<String> parts = new HashSet<>((java.util.Collection<String>) mParts.invoke(rec));
            return new Rec((String) mBlockId.invoke(rec), ((Number) mDur.invoke(rec)).intValue(),
                    (String) mOwner.invoke(rec), parts, (String) mName.invoke(rec));
        } catch (Exception e) {
            return null;
        }
    }
}
