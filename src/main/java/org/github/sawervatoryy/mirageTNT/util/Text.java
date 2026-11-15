package org.github.sawervatoryy.mirageTNT.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

/** Цвета: &c и HEX &#RRGGBB. */
public final class Text {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder().character('&').hexColors().build();

    private Text() {}

    public static Component color(String s) {
        return s == null ? Component.empty() : LEGACY.deserialize(s);
    }

    /** Для названий и лора предметов: без курсива. */
    public static Component item(String s) {
        return color(s).decoration(TextDecoration.ITALIC, false);
    }

    public static List<Component> lore(List<String> lines) {
        List<Component> out = new ArrayList<>();
        for (String l : lines) out.add(item(l));
        return out;
    }

    public static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    /** Убирает коды цвета (&c, &#RRGGBB) из строки. */
    public static String strip(String s) {
        if (s == null) return "";
        return s.replaceAll("&#[0-9a-fA-F]{6}", "").replaceAll("&[0-9a-fk-orA-FK-OR]", "");
    }

    /** Заменяет {ключ} значениями: apply(s, "player", "Steve", "n", 3). */
    public static String apply(String s, Object... kv) {
        if (s == null) return "";
        for (int i = 0; i + 1 < kv.length; i += 2) {
            s = s.replace("{" + kv[i] + "}", String.valueOf(kv[i + 1]));
        }
        return s;
    }

    public static void send(CommandSender to, String legacy) {
        if (to != null && legacy != null && !legacy.isEmpty()) to.sendMessage(color(legacy));
    }
}
