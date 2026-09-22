package dev.squaremarkerz.config;

import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Loads the {@code messages} section of config.yml and renders/sends messages, with prefix and
 * {@code %placeholder%} substitution. Supports either MiniMessage or legacy '&' color codes, chosen via
 * {@code messages.minimessage} in config.yml.
 */
public final class Messages {

    private final JavaPlugin plugin;
    private final Map<String, String> raw = new java.util.HashMap<>();
    private String prefix = "";
    private boolean miniMessage;

    public Messages(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.reload();
    }

    public void reload() {
        this.raw.clear();
        final ConfigurationSection section = this.plugin.getConfig().getConfigurationSection("messages");
        this.miniMessage = this.plugin.getConfig().getBoolean("messages.minimessage", false);
        if (section == null) {
            return;
        }
        for (final String key : section.getKeys(false)) {
            if ("minimessage".equals(key) || "prefix".equals(key)) {
                continue;
            }
            this.raw.put(key, section.getString(key, ""));
        }
        this.prefix = section.getString("prefix", "");
    }

    private Component render(final String text) {
        if (this.miniMessage) {
            return MiniMessage.miniMessage().deserialize(text);
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    private String apply(final String key, final String... placeholders) {
        String text = this.raw.getOrDefault(key, key);
        if (placeholders.length % 2 != 0) {
            throw new IllegalArgumentException("Placeholders must be key/value pairs");
        }
        for (int i = 0; i < placeholders.length; i += 2) {
            text = text.replace("%" + placeholders[i] + "%", placeholders[i + 1]);
        }
        return text;
    }

    public Component build(final String key, final String... placeholders) {
        return this.render(this.prefix + this.apply(key, placeholders));
    }

    public Component buildRaw(final String key, final String... placeholders) {
        return this.render(this.apply(key, placeholders));
    }

    public void send(final CommandSender sender, final String key, final String... placeholders) {
        sender.sendMessage(this.build(key, placeholders));
    }

    public void sendRaw(final CommandSender sender, final String key, final String... placeholders) {
        sender.sendMessage(this.buildRaw(key, placeholders));
    }
}
