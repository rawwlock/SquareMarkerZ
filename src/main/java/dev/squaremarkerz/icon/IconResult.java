package dev.squaremarkerz.icon;

import xyz.jpenilla.squaremap.api.Key;

public record IconResult(boolean success, Key key, String reason) {

    public static IconResult success(final Key key) {
        return new IconResult(true, key, null);
    }

    public static IconResult failure(final String reason) {
        return new IconResult(false, null, reason);
    }
}
