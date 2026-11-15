package org.github.sawervatoryy.mirageTNT.event;

import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Динамит собирается нанести урон приват-блоку. Можно отменить или изменить урон. */
public class PrivateDamageEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Block block;
    private final String privateType;
    private final UUID attacker;
    private int damage;
    private boolean cancelled;

    public PrivateDamageEvent(Block block, String privateType, UUID attacker, int damage) {
        this.block = block;
        this.privateType = privateType;
        this.attacker = attacker;
        this.damage = damage;
    }

    public Block getBlock() { return block; }
    public String getPrivateType() { return privateType; }
    /** Игрок, поджёгший динамит (может быть null). */
    public UUID getAttacker() { return attacker; }
    public int getDamage() { return damage; }
    public void setDamage(int damage) { this.damage = Math.max(0, damage); }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
