package dev.squaremarkerz.model;

/**
 * A single marker. Mutable in place; all mutation happens on the main thread via {@code MarkerManager},
 * which is responsible for persisting and pushing changes to squaremap.
 */
public final class MarkerRecord {

    private final String id;
    private String set;
    private String world;
    private double x;
    private double y;
    private double z;
    private String label;
    private String description;
    private String iconUrl;
    private final String createdBy;
    private final long createdAt;

    public MarkerRecord(
        final String id,
        final String set,
        final String world,
        final double x,
        final double y,
        final double z,
        final String label,
        final String description,
        final String iconUrl,
        final String createdBy,
        final long createdAt
    ) {
        this.id = id;
        this.set = set;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.label = label;
        this.description = description;
        this.iconUrl = iconUrl;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public String id() {
        return this.id;
    }

    public String set() {
        return this.set;
    }

    public void set(final String set) {
        this.set = set;
    }

    public String world() {
        return this.world;
    }

    public double x() {
        return this.x;
    }

    public double y() {
        return this.y;
    }

    public double z() {
        return this.z;
    }

    public void position(final String world, final double x, final double y, final double z) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public String label() {
        return this.label;
    }

    public void label(final String label) {
        this.label = label;
    }

    public String description() {
        return this.description;
    }

    public void description(final String description) {
        this.description = description;
    }

    public String iconUrl() {
        return this.iconUrl;
    }

    public void iconUrl(final String iconUrl) {
        this.iconUrl = iconUrl;
    }

    public String createdBy() {
        return this.createdBy;
    }

    public long createdAt() {
        return this.createdAt;
    }
}
