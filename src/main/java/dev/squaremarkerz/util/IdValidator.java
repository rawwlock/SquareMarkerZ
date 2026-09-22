package dev.squaremarkerz.util;

import java.util.regex.Pattern;

/**
 * Validates and normalizes marker and marker-set ids: {@code [a-z0-9_-]\{1,32\}}, case-insensitive.
 */
public final class IdValidator {

    private static final Pattern PATTERN = Pattern.compile("^[a-z0-9_-]{1,32}$");

    private IdValidator() {
    }

    public static boolean isValid(final String rawId) {
        if (rawId == null) {
            return false;
        }
        return PATTERN.matcher(rawId.toLowerCase(java.util.Locale.ROOT)).matches();
    }

    /**
     * Lower-cases an id for use as a storage/lookup key. Caller must validate first with {@link #isValid(String)}.
     */
    public static String normalize(final String rawId) {
        return rawId.toLowerCase(java.util.Locale.ROOT);
    }
}
