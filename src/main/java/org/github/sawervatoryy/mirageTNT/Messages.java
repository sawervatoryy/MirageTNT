package org.github.sawervatoryy.mirageTNT;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.github.sawervatoryy.mirageTNT.util.Text;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** messages.yml: тексты с {prefix} и плейсхолдерами. */
public final class Messages {

    private final MirageTNT plugin;
    private YamlConfiguration yaml;

    public Messages(MirageTNT plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "messages.yml");
        if (!f.exists()) plugin.saveResource("messages.yml", false);
        yaml = YamlConfiguration.loadConfiguration(f);
        try (InputStreamReader r = new InputStreamReader(plugin.getResource("messages.yml"), StandardCharsets.UTF_8)) {
            yaml.setDefaults(YamlConfiguration.loadConfiguration(r));
        } catch (Exception ignored) {
        }
    }

    public String raw(String key, Object... kv) {
        String s = yaml.getString(key, "");
        s = s.replace("{prefix}", yaml.getString("prefix", ""));
        return Text.apply(s, kv);
    }

    public List<String> list(String key) {
        return yaml.getStringList(key);
    }

    public void send(CommandSender to, String key, Object... kv) {
        Text.send(to, raw(key, kv));
    }
}
