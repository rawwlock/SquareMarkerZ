package dev.squaremarkerz.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Typed view over config.yml. Call {@link #reload()} after {@code plugin.reloadConfig()}.
 */
public final class PluginSettings {

    private final JavaPlugin plugin;

    private String defaultSetId;
    private String defaultSetLabel;
    private int iconSize;
    private long maxDownloadSizeBytes;
    private int connectTimeoutMillis;
    private int readTimeoutMillis;
    private boolean layerDefaultHidden;
    private boolean layerDefaultShowControls;
    private int layerDefaultPriority;
    private int layerDefaultZIndex;

    public PluginSettings(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.reload();
    }

    public void reload() {
        final FileConfiguration c = this.plugin.getConfig();
        this.defaultSetId = c.getString("default-set.id", "markers").toLowerCase(java.util.Locale.ROOT);
        this.defaultSetLabel = c.getString("default-set.label", "Markers");
        this.iconSize = Math.max(1, c.getInt("icons.size", 32));
        this.maxDownloadSizeBytes = Math.max(1, c.getInt("icons.max-download-size-kb", 512)) * 1024L;
        this.connectTimeoutMillis = Math.max(1, c.getInt("icons.connect-timeout-seconds", 5)) * 1000;
        this.readTimeoutMillis = Math.max(1, c.getInt("icons.read-timeout-seconds", 5)) * 1000;
        this.layerDefaultHidden = c.getBoolean("layer-defaults.hidden-by-default", false);
        this.layerDefaultShowControls = c.getBoolean("layer-defaults.show-controls", true);
        this.layerDefaultPriority = c.getInt("layer-defaults.priority", 5);
        this.layerDefaultZIndex = c.getInt("layer-defaults.z-index", 5);
    }

    public String defaultSetId() {
        return this.defaultSetId;
    }

    public String defaultSetLabel() {
        return this.defaultSetLabel;
    }

    public int iconSize() {
        return this.iconSize;
    }

    public long maxDownloadSizeBytes() {
        return this.maxDownloadSizeBytes;
    }

    public int connectTimeoutMillis() {
        return this.connectTimeoutMillis;
    }

    public int readTimeoutMillis() {
        return this.readTimeoutMillis;
    }

    public boolean layerDefaultHidden() {
        return this.layerDefaultHidden;
    }

    public boolean layerDefaultShowControls() {
        return this.layerDefaultShowControls;
    }

    public int layerDefaultPriority() {
        return this.layerDefaultPriority;
    }

    public int layerDefaultZIndex() {
        return this.layerDefaultZIndex;
    }
}
