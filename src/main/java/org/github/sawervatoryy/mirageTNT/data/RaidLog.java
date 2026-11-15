package org.github.sawervatoryy.mirageTNT.data;

import org.github.sawervatoryy.mirageTNT.MirageTNT;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Лог рейдов: raid.log и (по желанию) Discord webhook. */
public final class RaidLog {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final MirageTNT plugin;
    private final File file;
    private final HttpClient http = HttpClient.newHttpClient();

    public RaidLog(MirageTNT plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "raid.log");
    }

    public void log(String line) {
        final String text = "[" + LocalDateTime.now().format(FMT) + "] " + line;
        if (plugin.getConfig().getBoolean("raid-log.file", true)) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                synchronized (RaidLog.this) {
                    try (FileWriter w = new FileWriter(file, true)) {
                        w.write(text + System.lineSeparator());
                    } catch (IOException ignored) {
                    }
                }
            });
        }
        if (plugin.getConfig().getBoolean("raid-log.webhook.enabled", false)) {
            String url = plugin.getConfig().getString("raid-log.webhook.url", "");
            if (url == null || url.isBlank()) return;
            String user = plugin.getConfig().getString("raid-log.webhook.username", "MirageTNT");
            String json = "{\"username\":\"" + esc(user) + "\",\"content\":\"" + esc(line) + "\"}";
            try {
                http.sendAsync(HttpRequest.newBuilder(URI.create(url))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(json)).build(),
                        HttpResponse.BodyHandlers.discarding());
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("raid-log.webhook.url некорректен.");
            }
        }
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
