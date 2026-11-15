package org.github.sawervatoryy.mirageTNT.priv;

import org.bukkit.Material;

public final class PrivateType {
    public final String id;
    public final Material material;
    public final int durability;
    public final boolean instant;
    public final String name;

    public PrivateType(String id, Material material, int durability, boolean instant, String name) {
        this.id = id;
        this.material = material;
        this.durability = Math.max(1, durability);
        this.instant = instant;
        this.name = name;
    }
}
