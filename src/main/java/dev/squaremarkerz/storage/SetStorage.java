package dev.squaremarkerz.storage;

import dev.squaremarkerz.model.MarkerSet;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class SetStorage {

    private final JavaPlugin plugin;
    private final File file;

    public SetStorage(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "sets.yml");
    }

    public List<MarkerSet> load() {
        final List<MarkerSet> result = new ArrayList<>();
        if (!this.file.exists()) {
            return result;
        }
        final YamlConfiguration config = YamlConfiguration.loadConfiguration(this.file);
        final ConfigurationSection sets = config.getConfigurationSection("sets");
        if (sets == null) {
            return result;
        }
        for (final String id : sets.getKeys(false)) {
            final ConfigurationSection s = sets.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            final String defaultIconUrl = s.getString("default-icon-url", null);
            result.add(new MarkerSet(
                id,
                s.getString("label", id),
                s.getInt("priority", 5),
                s.getInt("z-index", 5),
                s.getBoolean("hidden-by-default", false),
                s.getBoolean("show-controls", true),
                (defaultIconUrl == null || defaultIconUrl.isBlank()) ? null : defaultIconUrl
            ));
        }
        return result;
    }

    public void saveAsync(final Collection<MarkerSet> sets) {
        final YamlConfiguration config = new YamlConfiguration();
        final Map<String, Object> root = new LinkedHashMap<>();
        for (final MarkerSet set : sets) {
            final Map<String, Object> data = new LinkedHashMap<>();
            data.put("label", set.label());
            data.put("priority", set.priority());
            data.put("z-index", set.zIndex());
            data.put("hidden-by-default", set.hiddenByDefault());
            data.put("show-controls", set.showControls());
            data.put("default-icon-url", set.defaultIconUrl());
            root.put(set.id(), data);
        }
        config.createSection("sets", root);
        AtomicYamlWriter.saveAsync(this.plugin, config, this.file);
    }
}
