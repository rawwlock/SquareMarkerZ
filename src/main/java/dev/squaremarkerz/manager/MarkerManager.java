package dev.squaremarkerz.manager;

import dev.squaremarkerz.icon.IconService;
import dev.squaremarkerz.model.MarkerRecord;
import dev.squaremarkerz.squaremap.MarkerSource;
import dev.squaremarkerz.squaremap.SquaremapIntegration;
import dev.squaremarkerz.storage.MarkerStorage;
import dev.squaremarkerz.util.IdValidator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.jpenilla.squaremap.api.Key;

/**
 * Owns markers: validation, CRUD, persistence, icon lifecycle and pushing live updates to squaremap.
 * Also implements {@link MarkerSource}, the read-only view {@code SquaremapIntegration} uses to (re)build
 * layers. All public methods are expected to be called from the main thread.
 */
public final class MarkerManager implements MarkerSource {

    private final JavaPlugin plugin;
    private final MarkerStorage storage;
    private final MarkerSetManager setManager;
    private final IconService iconService;
    private final Map<String, MarkerRecord> markers = new java.util.LinkedHashMap<>();
    private final Map<String, Key> iconKeys = new ConcurrentHashMap<>();
    private SquaremapIntegration integration;

    public MarkerManager(
        final JavaPlugin plugin,
        final MarkerStorage storage,
        final MarkerSetManager setManager,
        final IconService iconService
    ) {
        this.plugin = plugin;
        this.storage = storage;
        this.setManager = setManager;
        this.iconService = iconService;
        this.reloadFromDisk();
    }

    public void setIntegration(final SquaremapIntegration integration) {
        this.integration = integration;
    }

    /** Loads (or re-loads) marker data from disk. Does not touch squaremap; call {@link #resolveIconsAndPublish()} after. */
    public void reloadFromDisk() {
        for (final MarkerRecord old : this.markers.values()) {
            this.iconService.releaseForMarker(old.iconUrl());
        }
        this.markers.clear();
        this.iconKeys.clear();
        for (final MarkerRecord record : this.storage.load(this.setManager.defaultSetId())) {
            if (!this.setManager.exists(record.set())) {
                this.plugin.getLogger().warning("Marker '" + record.id() + "' referenced unknown set '"
                    + record.set() + "', moved to the default set.");
                record.set(this.setManager.defaultSetId());
            }
            this.markers.put(record.id(), record);
        }
    }

    /** Kicks off async icon resolution for every loaded marker, adding each to its layer once ready. */
    public void resolveIconsAndPublish() {
        for (final MarkerRecord record : this.markers.values()) {
            final String url = record.iconUrl();
            if (url == null) {
                continue;
            }
            this.iconService.resolveForMarker(url).thenAccept(result -> {
                if (result.success()) {
                    this.iconKeys.put(record.id(), result.key());
                    if (this.integration != null) {
                        this.integration.refreshMarker(record);
                    }
                } else {
                    this.plugin.getLogger().log(Level.WARNING,
                        "Could not load icon for marker '" + record.id() + "': " + result.reason());
                }
            });
        }
    }

    public MarkerRecord require(final String rawId) {
        final String id = (rawId == null) ? "" : IdValidator.normalize(rawId);
        final MarkerRecord record = this.markers.get(id);
        if (record == null) {
            throw new ManagerException("marker-not-found", "id", rawId == null ? "" : rawId);
        }
        return record;
    }

    public boolean exists(final String rawId) {
        return rawId != null && IdValidator.isValid(rawId) && this.markers.containsKey(IdValidator.normalize(rawId));
    }

    public Collection<MarkerRecord> all() {
        return this.markers.values();
    }

    public int count() {
        return this.markers.size();
    }

    /**
     * Creates a marker. Synchronous validation (id, set, url shape) throws {@link ManagerException}
     * immediately; the icon download/registration happens async and the future fails with a
     * {@link ManagerException} ({@code icon-download-failed}) on failure, in which case nothing is created.
     */
    public CompletableFuture<MarkerRecord> create(
        final String rawId,
        final String worldName,
        final double x,
        final double y,
        final double z,
        final String explicitIconUrl,
        final String rawLabel,
        final String rawSetId,
        final String createdBy
    ) {
        if (!IdValidator.isValid(rawId)) {
            throw new ManagerException("invalid-id", "id", rawId);
        }
        final String id = IdValidator.normalize(rawId);
        if (this.markers.containsKey(id)) {
            throw new ManagerException("marker-id-taken", "id", id);
        }
        final var set = (rawSetId == null) ? this.setManager.require(this.setManager.defaultSetId()) : this.setManager.require(rawSetId);
        final String effectiveUrl = (explicitIconUrl != null) ? explicitIconUrl : set.defaultIconUrl();
        if (effectiveUrl == null) {
            throw new ManagerException("icon-required");
        }
        if (!(effectiveUrl.startsWith("http://") || effectiveUrl.startsWith("https://"))) {
            throw new ManagerException("icon-invalid-url");
        }
        final String label = (rawLabel == null || rawLabel.isBlank()) ? id : rawLabel;

        return this.iconService.resolveForMarker(effectiveUrl).thenApply(result -> {
            if (!result.success()) {
                throw new ManagerException("icon-download-failed", "reason", result.reason());
            }
            final MarkerRecord record = new MarkerRecord(
                id, set.id(), worldName, x, y, z, label, "", effectiveUrl, createdBy, System.currentTimeMillis()
            );
            this.markers.put(id, record);
            this.iconKeys.put(id, result.key());
            this.persist();
            if (this.integration != null) {
                this.integration.refreshMarker(record);
            }
            return record;
        });
    }

    public MarkerRecord remove(final String rawId) {
        final MarkerRecord record = this.require(rawId);
        this.markers.remove(record.id());
        this.iconKeys.remove(record.id());
        this.iconService.releaseForMarker(record.iconUrl());
        this.persist();
        if (this.integration != null) {
            this.integration.removeMarker(record.id());
        }
        return record;
    }

    public MarkerRecord move(final String rawId, final String worldName, final double x, final double y, final double z) {
        final MarkerRecord record = this.require(rawId);
        record.position(worldName, x, y, z);
        this.persist();
        if (this.integration != null) {
            this.integration.refreshMarker(record);
        }
        return record;
    }

    public MarkerRecord moveToSet(final String rawId, final String rawSetId) {
        final MarkerRecord record = this.require(rawId);
        final var target = this.setManager.require(rawSetId);
        if (target.id().equals(record.set())) {
            throw new ManagerException("set-move-same");
        }
        record.set(target.id());
        this.persist();
        if (this.integration != null) {
            this.integration.refreshMarker(record);
        }
        return record;
    }

    public MarkerRecord label(final String rawId, final String label) {
        final MarkerRecord record = this.require(rawId);
        record.label((label == null || label.isBlank()) ? record.id() : label);
        this.persist();
        if (this.integration != null) {
            this.integration.refreshMarker(record);
        }
        return record;
    }

    public MarkerRecord description(final String rawId, final String text) {
        final MarkerRecord record = this.require(rawId);
        record.description(text == null ? "" : text);
        this.persist();
        if (this.integration != null) {
            this.integration.refreshMarker(record);
        }
        return record;
    }

    /** Changes a marker's icon. On failure the future completes exceptionally and the marker is unchanged. */
    public CompletableFuture<MarkerRecord> icon(final String rawId, final String newUrl) {
        final MarkerRecord record = this.require(rawId);
        if (!(newUrl.startsWith("http://") || newUrl.startsWith("https://"))) {
            throw new ManagerException("icon-invalid-url");
        }
        final String oldUrl = record.iconUrl();
        return this.iconService.resolveForMarker(newUrl).thenApply(result -> {
            if (!result.success()) {
                throw new ManagerException("icon-download-failed", "reason", result.reason());
            }
            this.iconService.releaseForMarker(oldUrl);
            record.iconUrl(newUrl);
            this.iconKeys.put(record.id(), result.key());
            this.persist();
            if (this.integration != null) {
                this.integration.refreshMarker(record);
            }
            return record;
        });
    }

    public List<MarkerRecord> markersInSet(final String setId) {
        final List<MarkerRecord> list = new ArrayList<>();
        for (final MarkerRecord record : this.markers.values()) {
            if (record.set().equals(setId)) {
                list.add(record);
            }
        }
        return list;
    }

    public void moveAllMarkersToSet(final String fromSetId, final String toSetId) {
        boolean changed = false;
        for (final MarkerRecord record : this.markers.values()) {
            if (record.set().equals(fromSetId)) {
                record.set(toSetId);
                changed = true;
                if (this.integration != null) {
                    this.integration.refreshMarker(record);
                }
            }
        }
        if (changed) {
            this.persist();
        }
    }

    public void removeAllMarkersInSet(final String setId) {
        final List<String> toRemove = new ArrayList<>();
        for (final MarkerRecord record : this.markers.values()) {
            if (record.set().equals(setId)) {
                toRemove.add(record.id());
            }
        }
        for (final String id : toRemove) {
            final MarkerRecord record = this.markers.remove(id);
            this.iconKeys.remove(id);
            this.iconService.releaseForMarker(record.iconUrl());
            if (this.integration != null) {
                this.integration.removeMarker(id);
            }
        }
        if (!toRemove.isEmpty()) {
            this.persist();
        }
    }

    @Override
    public Collection<MarkerRecord> markersInWorldAndSet(final String worldName, final String setId) {
        final List<MarkerRecord> list = new ArrayList<>();
        for (final MarkerRecord record : this.markers.values()) {
            if (record.world().equalsIgnoreCase(worldName) && record.set().equals(setId)) {
                list.add(record);
            }
        }
        return list;
    }

    @Override
    public Key iconKeyFor(final String markerId) {
        return this.iconKeys.get(markerId);
    }

    private void persist() {
        this.storage.saveAsync(this.markers.values());
    }
}
