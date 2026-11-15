package org.github.sawervatoryy.mirageTNT.tnt;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Один тир динамита (секция tiers.N из config.yml). */
public final class Tier {

    public enum Type { ASSAULT, ARMOR_PIERCING, FROST, THIEF, TITAN }

    public final int id;
    public final Type type;
    public final boolean enabled;
    public final List<String> aliases = new ArrayList<>();
    public final String displayName;
    public final List<String> lore;
    public final boolean glow;

    public final double fuseSeconds;
    public final double radius;
    public final int pierce;
    public final int privateDamage;
    public final boolean breakHard;
    public final boolean breakUnderwater;
    public final double irregularity;
    public final double dropChance;
    public final int blocksPerTick;
    public final int maxBlocks;

    // урон по существам
    public final boolean damageEnabled;
    public final boolean damagePlayers;
    public final double maxDamage;
    public final double dropPerBlock;
    public final double damageRadius;
    public final double coverReduction;
    public final double knockback;

    // FROST
    public final double freezeRadius;
    public final int freezeSeconds;
    public final int entityFreezeSeconds;
    public final int slownessSeconds;
    public final int slownessAmplifier;

    // THIEF
    public final double stealChance;
    public final int stealRadius;
    public final int maxSpawners;
    public final boolean stealIgnoreRegions;

    // эффекты
    public final String sound;
    public final float soundPitch;
    public final List<String> particles;
    public final boolean shockwave;
    public final String shockwaveParticle;
    public final boolean lightning;

    public Tier(int id, ConfigurationSection s) {
        this.id = id;
        Type t;
        try {
            t = Type.valueOf(s.getString("type", "ASSAULT").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            t = Type.ASSAULT;
        }
        this.type = t;
        this.enabled = s.getBoolean("enabled", true);
        for (String a : s.getStringList("aliases")) aliases.add(a.toLowerCase(Locale.ROOT));
        aliases.add(type.name().toLowerCase(Locale.ROOT));
        this.displayName = s.getString("display-name", "&fДинамит Tier " + id);
        this.lore = s.getStringList("lore");
        this.glow = s.getBoolean("glow", false);

        this.fuseSeconds = s.getDouble("fuse-seconds", 4.0);
        this.radius = s.getDouble("radius", 4.0);
        this.pierce = s.getInt("pierce", id);
        this.privateDamage = Math.max(1, s.getInt("private-damage", 1));
        this.breakHard = s.getBoolean("break-hard", false);
        this.breakUnderwater = s.getBoolean("break-underwater", false);
        this.irregularity = s.getDouble("irregularity", 0.1);
        this.dropChance = s.getDouble("drop-chance", 0.3);
        this.blocksPerTick = Math.max(20, s.getInt("blocks-per-tick", 250));
        this.maxBlocks = Math.max(1, s.getInt("max-blocks", 2000));

        ConfigurationSection d = s.getConfigurationSection("damage");
        this.damageEnabled = d != null && d.getBoolean("enabled", true);
        this.damagePlayers = d == null || d.getBoolean("players", true);
        this.maxDamage = d == null ? 0 : d.getDouble("max", 20);
        this.dropPerBlock = d == null ? 0 : d.getDouble("per-block-drop", 3);
        this.damageRadius = d == null ? 0 : d.getDouble("radius", radius * 2);
        this.coverReduction = d == null ? 0 : d.getDouble("cover-reduction", 0.03);
        this.knockback = d == null ? 0 : d.getDouble("knockback", 0.5);

        ConfigurationSection f = s.getConfigurationSection("freeze");
        this.freezeRadius = f == null ? 0 : f.getDouble("radius", 4);
        this.freezeSeconds = f == null ? 0 : f.getInt("seconds", 8);
        this.entityFreezeSeconds = f == null ? 0 : f.getInt("entity-freeze-seconds", 6);
        this.slownessSeconds = f == null ? 0 : f.getInt("slowness-seconds", 5);
        this.slownessAmplifier = f == null ? 0 : f.getInt("slowness-amplifier", 2);

        ConfigurationSection st = s.getConfigurationSection("steal");
        this.stealChance = st == null ? 0 : st.getDouble("chance", 0.75);
        this.stealRadius = st == null ? 0 : st.getInt("radius", 4);
        this.maxSpawners = st == null ? 0 : st.getInt("max-spawners", 1);
        this.stealIgnoreRegions = st != null && st.getBoolean("ignore-regions", true);

        ConfigurationSection fx = s.getConfigurationSection("fx");
        this.sound = fx == null ? "entity.generic.explode" : fx.getString("sound", "entity.generic.explode");
        this.soundPitch = fx == null ? 1f : (float) fx.getDouble("pitch", 1.0);
        this.particles = fx == null ? new ArrayList<>() : fx.getStringList("particles");
        this.shockwave = fx == null || fx.getBoolean("shockwave", true);
        this.shockwaveParticle = fx == null ? "CLOUD" : fx.getString("shockwave-particle", "CLOUD");
        this.lightning = fx != null && fx.getBoolean("lightning", false);
    }

    public int fuseTicks() {
        return Math.max(1, (int) Math.round(fuseSeconds * 20));
    }

    public String plainName() {
        return org.github.sawervatoryy.mirageTNT.util.Text.strip(displayName);
    }
}
