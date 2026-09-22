package dev.squaremarkerz.storage;

import dev.squaremarkerz.model.MarkerRecord;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class MarkerStorage {

    private final JavaPlugin plugin;
    private final File file;

    public MarkerStorage(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "markers.yml");
    }

    /**
     * Loads all markers. Any marker without a {@code set} field (from before marker sets existed) is
     * migrated into {@code defaultSetId}.
     */
    public List<MarkerRecord> load(final String defaultSetId) {
        final List<MarkerRecord> result = new ArrayList<>();
        if (!this.file.exists()) {
            return result;
        }
        final YamlConfiguration config = YamlConfiguration.loadConfiguration(this.file);
        final ConfigurationSection markers = config.getConfigurationSection("markers");
        if (markers == null) {
            return result;
        }
        for (final String id : markers.getKeys(false)) {
            final ConfigurationSection m = markers.getConfigurationSection(id);
            if (m == null) {
                continue;
            }
            final String set = m.getString("set");
            final String description = m.getString("description", "");
            final String iconUrl = m.getString("icon-url", null);
            result.add(new MarkerRecord(
                id,
                (set == null || set.isBlank()) ? defaultSetId : set,
                m.getString("world", "world"),
                m.getDouble("x", 0),
                m.getDouble("y", 64),
                m.getDouble("z", 0),
                m.getString("label", id),
                description == null ? "" : description,
                (iconUrl == null || iconUrl.isBlank()) ? null : iconUrl,
                m.getString("created-by", "unknown"),
                m.getLong("created-at", System.currentTimeMillis())
            ));
        }
        return result;
    }

    public void saveAsync(final Collection<MarkerRecord> markers) {
        final YamlConfiguration config = new YamlConfiguration();
        final Map<String, Object> root = new LinkedHashMap<>();
        for (final MarkerRecord marker : markers) {
            final Map<String, Object> data = new LinkedHashMap<>();
            data.put("set", marker.set());
            data.put("world", marker.world());
            data.put("x", marker.x());
            data.put("y", marker.y());
            data.put("z", marker.z());
            data.put("label", marker.label());
            data.put("description", marker.description());
            data.put("icon-url", marker.iconUrl());
            data.put("created-by", marker.createdBy());
            data.put("created-at", marker.createdAt());
            root.put(marker.id(), data);
        }
        config.createSection("markers", root);
        AtomicYamlWriter.saveAsync(this.plugin, config, this.file);
    }
}
