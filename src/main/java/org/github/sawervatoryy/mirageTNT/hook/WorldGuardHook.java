package org.github.sawervatoryy.mirageTNT.hook;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Мягкая зависимость от WorldGuard. Класс загружается только если WG установлен,
 * наружу не отдаёт типы WG.
 */
public final class WorldGuardHook {

    public static final class Region {
        public final String id;
        public final Set<UUID> owners = new HashSet<>();
        public final Set<UUID> members = new HashSet<>();
        public final int minX, minY, minZ, maxX, maxY, maxZ;
        public final boolean global;
        public boolean flagDenied;

        Region(String id, boolean global, BlockVector3 min, BlockVector3 max) {
            this.id = id;
            this.global = global;
            this.minX = min.getBlockX();
            this.minY = min.getBlockY();
            this.minZ = min.getBlockZ();
            this.maxX = max.getBlockX();
            this.maxY = max.getBlockY();
            this.maxZ = max.getBlockZ();
        }

        /** Блок стоит в центре региона по X/Z с допуском. */
        public boolean isCenter(int x, int z, int tolerance) {
            double cx = (minX + maxX) / 2.0;
            double cz = (minZ + maxZ) / 2.0;
            return Math.abs(cx - x) <= tolerance && Math.abs(cz - z) <= tolerance;
        }
    }

    private RegionManager manager(World w) {
        return WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(w));
    }

    public List<Region> regionsAt(Location loc) {
        List<Region> out = new ArrayList<>();
        if (loc.getWorld() == null) return out;
        RegionManager rm = manager(loc.getWorld());
        if (rm == null) return out;
        ApplicableRegionSet set = rm.getApplicableRegions(BlockVector3.at(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
        for (ProtectedRegion pr : set) {
            Region r = new Region(pr.getId(), pr.getId().equals("__global__"), pr.getMinimumPoint(), pr.getMaximumPoint());
            r.owners.addAll(pr.getOwners().getUniqueIds());
            r.members.addAll(pr.getMembers().getUniqueIds());
            r.flagDenied = pr.getFlag(Flags.BLOCK_BREAK) == StateFlag.State.DENY
                    || pr.getFlag(Flags.TNT) == StateFlag.State.DENY;
            out.add(r);
        }
        return out;
    }

    public boolean removeRegion(World world, String id) {
        RegionManager rm = manager(world);
        if (rm == null || !rm.hasRegion(id)) return false;
        rm.removeRegion(id);
        try {
            rm.saveChanges();
        } catch (Exception ignored) {
        }
        return true;
    }
}
