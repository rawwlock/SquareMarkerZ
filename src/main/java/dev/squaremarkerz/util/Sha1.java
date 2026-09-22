package dev.squaremarkerz.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Sha1 {

    private Sha1() {
    }

    public static String hex(final String input) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-1");
            final byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            final StringBuilder sb = new StringBuilder(hash.length * 2);
            for (final byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (final NoSuchAlgorithmException e) {
            // SHA-1 is guaranteed to be available on every JDK implementation.
            throw new IllegalStateException(e);
        }
    }
}
