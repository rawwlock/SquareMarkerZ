package dev.squaremarkerz.icon;

/**
 * A user-facing reason an icon URL could not be downloaded, validated or decoded.
 */
public final class IconLoadException extends RuntimeException {

    public IconLoadException(final String message) {
        super(message);
    }
}
