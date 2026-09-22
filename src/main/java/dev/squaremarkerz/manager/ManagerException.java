package dev.squaremarkerz.manager;

/**
 * Carries a {@code messages.yml} key (plus placeholder key/value pairs) instead of a hardcoded string,
 * so command handlers can render it through {@code Messages} the same way as any other reply.
 */
public final class ManagerException extends RuntimeException {

    private final String key;
    private final String[] placeholders;

    public ManagerException(final String key, final String... placeholders) {
        super(key);
        this.key = key;
        this.placeholders = placeholders;
    }

    public String key() {
        return this.key;
    }

    public String[] placeholders() {
        return this.placeholders;
    }

    /** Unwraps a {@link ManagerException} from a (possibly {@code CompletionException}-wrapped) cause chain. */
    public static ManagerException unwrap(final Throwable t) {
        Throwable cause = t;
        while (cause != null) {
            if (cause instanceof ManagerException managerException) {
                return managerException;
            }
            cause = cause.getCause();
        }
        return new ManagerException("icon-download-failed", "reason", t.getMessage() == null ? t.toString() : t.getMessage());
    }
}
