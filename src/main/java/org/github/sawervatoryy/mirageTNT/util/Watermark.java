package org.github.sawervatoryy.mirageTNT.util;

import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.logging.Logger;

/** Водяной знак MirageStudio. Не отключается конфигом. */
public final class Watermark {

    public static final String STUDIO = "MirageStudio";
    public static final String AUTHOR = "sawervatoryy";
    public static final String DISCORD = "https://discord.gg/mQbZT3x3Q";

    /** Подпись в конце лора каждого предмета плагина. */
    public static final String LORE_LINE = "&8" + STUDIO + " &7• &8" + AUTHOR;

    private Watermark() {}

    public static void banner(Plugin plugin) {
        Logger log = plugin.getLogger();
        log.info("╔══════════════════════════════════════════╗");
        log.info("║  MirageTNT v" + plugin.getPluginMeta().getVersion());
        log.info("║  " + STUDIO + " · Автор: " + AUTHOR);
        log.info("║  Discord: " + DISCORD);
        log.info("╚══════════════════════════════════════════╝");
    }

    public static List<String> about(String version) {
        return List.of(
                "&#00ACFF&lMirageTNT &8v" + version,
                "&7Динамиты 5 тиров, прочность приватов, двойная защита обсидиана.",
                "&8• &7Студия: &#00ACFF" + STUDIO,
                "&8• &7Автор: &#00ACFF" + AUTHOR,
                "&8• &7Discord: &#00ACFF" + DISCORD
        );
    }
}
