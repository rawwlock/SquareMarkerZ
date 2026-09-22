package dev.squaremarkerz.squaremap;

import dev.squaremarkerz.config.PluginSettings;
import dev.squaremarkerz.manager.MarkerSetManager;
import dev.squaremarkerz.model.MarkerRecord;
import dev.squaremarkerz.model.MarkerSet;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xyz.jpenilla.squaremap.api.BukkitAdapter;
import xyz.jpenilla.squaremap.api.Key;
import xyz.jpenilla.squaremap.api.MapWorld;
import xyz.jpenilla.squaremap.api.Point;
import xyz.jpenilla.squaremap.api.SimpleLayerProvider;
import xyz.jpenilla.squaremap.api.Squaremap;
import xyz.jpenilla.squaremap.api.WorldIdentifier;
import xyz.jpenilla.squaremap.api.marker.Icon;
import xyz.jpenilla.squaremap.api.marker.Marker;
import xyz.jpenilla.squaremap.api.marker.MarkerOptions;

/**
 * Owns every squaremap-facing object: one {@link SimpleLayerProvider} per marker set per world, and the
 * {@link Icon} markers inside them. Nothing here is persisted; on every reload/startup it is rebuilt from
 * {@code MarkerSetManager}/{@code MarkerSource} (the source of truth).
 *
 * <p>squaremap "enables" worlds asynchronously and may pick up newly loaded worlds after this plugin has
 * already started, so in addition to listening for {@link WorldLoadEvent} this polls periodically until a
 * world shows up via {@link Squaremap#getWorldIfEnabled(WorldIdentifier)}.</p>
 */
public final class SquaremapIntegration implements Listener {

    private final JavaPlugin plugin;
    private final Squaremap squaremap;
    private final MarkerSetManager setManager;
    private final MarkerSource markerSource;
    private final PluginSettings settings;
    private final Map<WorldIdentifier, Map<String, SimpleLayerProvider>> layersByWorld = new ConcurrentHashMap<>();
    private BukkitTask watchTask;

    public SquaremapIntegration(
        final JavaPlugin plugin,
        final Squaremap squaremap,
        final MarkerSetManager setManager,
        final MarkerSource markerSource,
        final PluginSettings settings
    ) {
        this.plugin = plugin;
        this.squaremap = squaremap;
        this.setManager = setManager;
        this.markerSource = markerSource;
        this.settings = settings;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, this.plugin);
        for (final World world : Bukkit.getWorlds()) {
            this.registerWorldIfNeeded(world);
        }
        // squaremap may finish enabling a world (or a newly loaded one) slightly after WorldLoadEvent fires;
        // this catches those cases without depending on any squaremap-internal signal.
        this.watchTask = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
            for (final World world : Bukkit.getWorlds()) {
                this.registerWorldIfNeeded(world);
            }
        }, 100L, 100L);
    }

    public void shutdown() {
        if (this.watchTask != null) {
            this.watchTask.cancel();
        }
        this.unregisterAll();
        HandlerList.unregisterAll(this);
    }

    /** Fully rebuilds every layer/marker from the current set/marker data. Used by {@code /marker reload}. */
    public void rebuildAll() {
        this.unregisterAll();
        for (final World world : Bukkit.getWorlds()) {
            this.registerWorldIfNeeded(world);
        }
    }

    public void onSetCreated(final MarkerSet set) {
        for (final Map.Entry<WorldIdentifier, Map<String, SimpleLayerProvider>> entry : this.layersByWorld.entrySet()) {
            this.squaremap.getWorldIfEnabled(entry.getKey()).ifPresent(mapWorld -> {
                final World world = BukkitAdapter.bukkitWorld(mapWorld);
                final SimpleLayerProvider provider = this.buildProvider(set, world.getName());
                mapWorld.layerRegistry().register(layerKey(set.id()), provider);
                entry.getValue().put(set.id(), provider);
            });
        }
    }

    public void onSetDeleted(final String setId) {
        final Key key = layerKey(setId);
        for (final Map.Entry<WorldIdentifier, Map<String, SimpleLayerProvider>> entry : this.layersByWorld.entrySet()) {
            this.squaremap.getWorldIfEnabled(entry.getKey()).ifPresent(mapWorld -> {
                if (mapWorld.layerRegistry().hasEntry(key)) {
                    mapWorld.layerRegistry().unregister(key);
                }
            });
            entry.getValue().remove(setId);
        }
    }

    /** Rebuilds the layer for a set whose label/priority/zIndex/visibility changed, on every world. */
    public void onSetUpdated(final MarkerSet set) {
        final Key key = layerKey(set.id());
        for (final Map.Entry<WorldIdentifier, Map<String, SimpleLayerProvider>> entry : this.layersByWorld.entrySet()) {
            this.squaremap.getWorldIfEnabled(entry.getKey()).ifPresent(mapWorld -> {
                final World world = BukkitAdapter.bukkitWorld(mapWorld);
                if (mapWorld.layerRegistry().hasEntry(key)) {
                    mapWorld.layerRegistry().unregister(key);
                }
                final SimpleLayerProvider provider = this.buildProvider(set, world.getName());
                mapWorld.layerRegistry().register(key, provider);
                entry.getValue().put(set.id(), provider);
            });
        }
    }

    /** Upserts a marker's icon marker into the layer for its current world/set. */
    public void refreshMarker(final MarkerRecord record) {
        this.removeMarkerFromAllLayers(record.id());
        final WorldIdentifier worldId = this.worldIdentifierByName(record.world());
        if (worldId == null) {
            return;
        }
        final Map<String, SimpleLayerProvider> perSet = this.layersByWorld.get(worldId);
        if (perSet == null) {
            return;
        }
        final SimpleLayerProvider provider = perSet.get(record.set());
        if (provider == null) {
            return;
        }
        final Icon icon = this.buildIcon(record);
        if (icon != null) {
            provider.addMarker(Key.of(record.id()), icon);
        }
    }

    public void removeMarker(final String markerId) {
        this.removeMarkerFromAllLayers(markerId);
    }

    @EventHandler
    public void onWorldLoad(final WorldLoadEvent event) {
        this.registerWorldIfNeeded(event.getWorld());
    }

    @EventHandler
    public void onWorldUnload(final WorldUnloadEvent event) {
        this.layersByWorld.remove(BukkitAdapter.worldIdentifier(event.getWorld()));
    }

    private void registerWorldIfNeeded(final World world) {
        final WorldIdentifier id = BukkitAdapter.worldIdentifier(world);
        if (this.layersByWorld.containsKey(id)) {
            return;
        }
        final Optional<MapWorld> mapWorld = this.squaremap.getWorldIfEnabled(id);
        if (mapWorld.isEmpty()) {
            return;
        }
        final Map<String, SimpleLayerProvider> perSet = new ConcurrentHashMap<>();
        for (final MarkerSet set : this.setManager.all()) {
            final SimpleLayerProvider provider = this.buildProvider(set, world.getName());
            mapWorld.get().layerRegistry().register(layerKey(set.id()), provider);
            perSet.put(set.id(), provider);
        }
        this.layersByWorld.put(id, perSet);
    }

    private SimpleLayerProvider buildProvider(final MarkerSet set, final String worldName) {
        final SimpleLayerProvider provider = SimpleLayerProvider.builder(set.label())
            .defaultHidden(set.hiddenByDefault())
            .showControls(set.showControls())
            .layerPriority(set.priority())
            .zIndex(set.zIndex())
            .build();
        for (final MarkerRecord record : this.markerSource.markersInWorldAndSet(worldName, set.id())) {
            final Icon icon = this.buildIcon(record);
            if (icon != null) {
                provider.addMarker(Key.of(record.id()), icon);
            }
        }
        return provider;
    }

    private Icon buildIcon(final MarkerRecord record) {
        final Key iconKey = this.markerSource.iconKeyFor(record.id());
        if (iconKey == null) {
            // Icon still resolving (or failed); refreshMarker will be called again once it's ready.
            return null;
        }
        final Icon icon = Marker.icon(Point.of(record.x(), record.z()), iconKey, this.settings.iconSize());
        final String description = record.description();
        icon.markerOptions(MarkerOptions.builder()
            .hoverTooltip(record.label())
            .clickTooltip((description == null || description.isEmpty()) ? null : description)
            .build());
        return icon;
    }

    private void removeMarkerFromAllLayers(final String markerId) {
        final Key key = Key.of(markerId);
        for (final Map<String, SimpleLayerProvider> perSet : this.layersByWorld.values()) {
            for (final SimpleLayerProvider provider : perSet.values()) {
                provider.removeMarker(key);
            }
        }
    }

    private void unregisterAll() {
        for (final Map.Entry<WorldIdentifier, Map<String, SimpleLayerProvider>> entry : this.layersByWorld.entrySet()) {
            this.squaremap.getWorldIfEnabled(entry.getKey()).ifPresent(mapWorld -> {
                for (final String setId : entry.getValue().keySet()) {
                    final Key key = layerKey(setId);
                    if (mapWorld.layerRegistry().hasEntry(key)) {
                        mapWorld.layerRegistry().unregister(key);
                    }
                }
            });
        }
        this.layersByWorld.clear();
    }

    private WorldIdentifier worldIdentifierByName(final String name) {
        final World world = Bukkit.getWorld(name);
        return world == null ? null : BukkitAdapter.worldIdentifier(world);
    }

    private static Key layerKey(final String setId) {
        return Key.of("smz_set_" + setId);
    }
}
