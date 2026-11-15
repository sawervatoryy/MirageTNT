package org.github.sawervatoryy.mirageTNT;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.github.sawervatoryy.mirageTNT.command.MirageTntCommand;
import org.github.sawervatoryy.mirageTNT.data.RaidLog;
import org.github.sawervatoryy.mirageTNT.data.StatsStore;
import org.github.sawervatoryy.mirageTNT.hook.WorldGuardHook;
import org.github.sawervatoryy.mirageTNT.listener.CannonListener;
import org.github.sawervatoryy.mirageTNT.listener.TntListener;
import org.github.sawervatoryy.mirageTNT.priv.PrivateManager;
import org.github.sawervatoryy.mirageTNT.tnt.BlastEngine;
import org.github.sawervatoryy.mirageTNT.tnt.PlacedTntStore;
import org.github.sawervatoryy.mirageTNT.tnt.TierRegistry;
import org.github.sawervatoryy.mirageTNT.tnt.TntItems;
import org.github.sawervatoryy.mirageTNT.util.Watermark;

/**
 * MirageTNT — MirageStudio · Автор: sawervatoryy · https://discord.gg/mQbZT3x3Q
 */
public final class MirageTNT extends JavaPlugin {

    private Messages messages;
    private TierRegistry tiers;
    private TntItems items;
    private PlacedTntStore store;
    private PrivateManager privates;
    private BlastEngine engine;
    private StatsStore stats;
    private RaidLog raidLog;
    private WorldGuardHook worldGuard;
    private TntListener tntListener;
    private CannonListener cannons;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(this);
        raidLog = new RaidLog(this);
        stats = new StatsStore(this);

        if (getServer().getPluginManager().isPluginEnabled("WorldGuard")) {
            try {
                worldGuard = new WorldGuardHook();
                getLogger().info("WorldGuard найден: защита регионов и определение приватов включены.");
            } catch (Throwable t) {
                worldGuard = null;
                getLogger().warning("Не удалось подключить WorldGuard: " + t.getMessage());
            }
        } else {
            getLogger().warning("WorldGuard не найден: приваты (WG_CENTER/WG_ANY) определяться не будут.");
        }

        tiers = new TierRegistry(this);
        items = new TntItems(this);
        store = new PlacedTntStore(this);
        privates = new PrivateManager(this);
        engine = new BlastEngine(this);
        tntListener = new TntListener(this);
        cannons = new CannonListener(this);

        getServer().getPluginManager().registerEvents(tntListener, this);
        getServer().getPluginManager().registerEvents(cannons, this);

        PluginCommand cmd = getCommand("miragetnt");
        if (cmd != null) {
            MirageTntCommand exec = new MirageTntCommand(this);
            cmd.setExecutor(exec);
            cmd.setTabCompleter(exec);
        }

        privates.start();
        stats.start();
        Watermark.banner(this);
        getLogger().info("Тиров: " + tiers.count() + ", типов приватов: " + privates.typeCount());
    }

    @Override
    public void onDisable() {
        if (engine != null) engine.shutdown();
        if (privates != null) privates.shutdown();
        if (stats != null) stats.shutdown();
    }

    public void debug(String msg) {
        if (getConfig().getBoolean("general.debug", false)) getLogger().info("[debug] " + msg);
    }

    public void reloadAll() {
        reloadConfig();
        messages.reload();
        tiers.reload();
        privates.reload();
        engine.reload();
    }

    public Messages messages() { return messages; }
    public TierRegistry tiers() { return tiers; }
    public TntItems items() { return items; }
    public PlacedTntStore store() { return store; }
    public PrivateManager privates() { return privates; }
    public BlastEngine engine() { return engine; }
    public StatsStore stats() { return stats; }
    public RaidLog raidLog() { return raidLog; }
    /** null, если WorldGuard не установлен. */
    public WorldGuardHook worldGuard() { return worldGuard; }
    public TntListener tntListener() { return tntListener; }
    public CannonListener cannons() { return cannons; }
}
