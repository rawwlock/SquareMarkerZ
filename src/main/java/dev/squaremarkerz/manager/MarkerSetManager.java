package dev.squaremarkerz.manager;

import dev.squaremarkerz.config.PluginSettings;
import dev.squaremarkerz.model.MarkerSet;
import dev.squaremarkerz.squaremap.SquaremapIntegration;
import dev.squaremarkerz.storage.SetStorage;
import dev.squaremarkerz.util.IdValidator;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Owns marker sets: validation, CRUD, persistence and pushing changes to squaremap. All methods are
 * expected to be called from the main thread.
 */
public final class MarkerSetManager {

    private final JavaPlugin plugin;
    private final SetStorage storage;
    private final PluginSettings settings;
    private final Map<String, MarkerSet> sets = new LinkedHashMap<>();
    private SquaremapIntegration integration;

    public MarkerSetManager(final JavaPlugin plugin, final SetStorage storage, final PluginSettings settings) {
        this.plugin = plugin;
        this.storage = storage;
        this.settings = settings;
        this.reloadFromDisk();
    }

    public void setIntegration(final SquaremapIntegration integration) {
        this.integration = integration;
    }

    public void reloadFromDisk() {
        this.sets.clear();
        for (final MarkerSet set : this.storage.load()) {
            this.sets.put(set.id(), set);
        }
        final String defaultId = this.settings.defaultSetId();
        if (!this.sets.containsKey(defaultId)) {
            this.sets.put(defaultId, new MarkerSet(
                defaultId,
                this.settings.defaultSetLabel(),
                this.settings.layerDefaultPriority(),
                this.settings.layerDefaultZIndex(),
                this.settings.layerDefaultHidden(),
                this.settings.layerDefaultShowControls(),
                null
            ));
            this.persist();
        }
    }

    public Collection<MarkerSet> all() {
        return this.sets.values();
    }

    public boolean exists(final String rawId) {
        return rawId != null && IdValidator.isValid(rawId) && this.sets.containsKey(IdValidator.normalize(rawId));
    }

    public boolean isDefault(final String id) {
        return this.settings.defaultSetId().equals(id);
    }

    public String defaultSetId() {
        return this.settings.defaultSetId();
    }

    /** Looks up a set, throwing {@link ManagerException} ({@code set-not-found}) if it doesn't exist. */
    public MarkerSet require(final String rawId) {
        final String id = (rawId == null) ? "" : IdValidator.normalize(rawId);
        final MarkerSet set = this.sets.get(id);
        if (set == null) {
            throw new ManagerException("set-not-found", "id", rawId == null ? "" : rawId);
        }
        return set;
    }

    public MarkerSet create(final String rawId, final String label) {
        if (!IdValidator.isValid(rawId)) {
            throw new ManagerException("invalid-id", "id", rawId);
        }
        final String id = IdValidator.normalize(rawId);
        if (this.sets.containsKey(id)) {
            throw new ManagerException("set-id-taken", "id", id);
        }
        final MarkerSet set = new MarkerSet(
            id,
            (label == null || label.isBlank()) ? id : label,
            this.settings.layerDefaultPriority(),
            this.settings.layerDefaultZIndex(),
            this.settings.layerDefaultHidden(),
            this.settings.layerDefaultShowControls(),
            null
        );
        this.sets.put(id, set);
        this.persist();
        if (this.integration != null) {
            this.integration.onSetCreated(set);
        }
        return set;
    }

    /**
     * Removes the set's bookkeeping and squaremap layer only. Callers must relocate or delete the set's
     * markers via {@code MarkerManager} first.
     */
    public void delete(final String rawId) {
        final MarkerSet set = this.require(rawId);
        if (this.isDefault(set.id())) {
            throw new ManagerException("set-default-protected");
        }
        this.sets.remove(set.id());
        this.persist();
        if (this.integration != null) {
            this.integration.onSetDeleted(set.id());
        }
    }

    public void label(final String rawId, final String label) {
        final MarkerSet set = this.require(rawId);
        set.label((label == null || label.isBlank()) ? set.id() : label);
        this.update(set);
    }

    public void hiddenByDefault(final String rawId, final boolean value) {
        final MarkerSet set = this.require(rawId);
        set.hiddenByDefault(value);
        this.update(set);
    }

    public void showControls(final String rawId, final boolean value) {
        final MarkerSet set = this.require(rawId);
        set.showControls(value);
        this.update(set);
    }

    public void priority(final String rawId, final int value) {
        final MarkerSet set = this.require(rawId);
        set.priority(value);
        this.update(set);
    }

    public void zIndex(final String rawId, final int value) {
        final MarkerSet set = this.require(rawId);
        set.zIndex(value);
        this.update(set);
    }

    public void defaultIconUrl(final String rawId, final String url) {
        final MarkerSet set = this.require(rawId);
        set.defaultIconUrl(url);
        this.update(set);
    }

    private void update(final MarkerSet set) {
        this.persist();
        if (this.integration != null) {
            this.integration.onSetUpdated(set);
        }
    }

    private void persist() {
        this.storage.saveAsync(this.sets.values());
    }
}
