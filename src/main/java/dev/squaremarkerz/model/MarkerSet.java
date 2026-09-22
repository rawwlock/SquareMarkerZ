package dev.squaremarkerz.model;

/**
 * A marker set (category / squaremap layer). Mutable in place; all mutation happens on the main thread
 * via the owning manager, which is responsible for persisting and pushing changes to squaremap.
 */
public final class MarkerSet {

    private final String id;
    private String label;
    private int priority;
    private int zIndex;
    private boolean hiddenByDefault;
    private boolean showControls;
    private String defaultIconUrl;

    public MarkerSet(
        final String id,
        final String label,
        final int priority,
        final int zIndex,
        final boolean hiddenByDefault,
        final boolean showControls,
        final String defaultIconUrl
    ) {
        this.id = id;
        this.label = label;
        this.priority = priority;
        this.zIndex = zIndex;
        this.hiddenByDefault = hiddenByDefault;
        this.showControls = showControls;
        this.defaultIconUrl = defaultIconUrl;
    }

    public String id() {
        return this.id;
    }

    public String label() {
        return this.label;
    }

    public void label(final String label) {
        this.label = label;
    }

    public int priority() {
        return this.priority;
    }

    public void priority(final int priority) {
        this.priority = priority;
    }

    public int zIndex() {
        return this.zIndex;
    }

    public void zIndex(final int zIndex) {
        this.zIndex = zIndex;
    }

    public boolean hiddenByDefault() {
        return this.hiddenByDefault;
    }

    public void hiddenByDefault(final boolean hiddenByDefault) {
        this.hiddenByDefault = hiddenByDefault;
    }

    public boolean showControls() {
        return this.showControls;
    }

    public void showControls(final boolean showControls) {
        this.showControls = showControls;
    }

    public String defaultIconUrl() {
        return this.defaultIconUrl;
    }

    public void defaultIconUrl(final String defaultIconUrl) {
        this.defaultIconUrl = defaultIconUrl;
    }
}
