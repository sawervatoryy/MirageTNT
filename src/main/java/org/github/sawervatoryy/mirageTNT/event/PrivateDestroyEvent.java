package org.github.sawervatoryy.mirageTNT.event;

import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;

/** Приват-блок уничтожен динамитом. Другие плагины могут чистить свои данные (голограммы, БД). */
public class PrivateDestroyEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Block block;
    private final String privateType;
    private final String regionId;
    private final Set<UUID> owners;
    private final UUID attacker;

    public PrivateDestroyEvent(Block block, String privateType, String regionId, Set<UUID> owners, UUID attacker) {
        this.block = block;
        this.privateType = privateType;
        this.regionId = regionId;
        this.owners = owners;
        this.attacker = attacker;
    }

    public Block getBlock() { return block; }
    public String getPrivateType() { return privateType; }
    /** ID региона WorldGuard или null. */
    public String getRegionId() { return regionId; }
    public Set<UUID> getOwners() { return owners; }
    public UUID getAttacker() { return attacker; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
