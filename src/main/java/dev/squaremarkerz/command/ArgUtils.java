package dev.squaremarkerz.command;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared parsing helpers for {@code /marker} subcommands: pulling an anywhere-positioned
 * {@code --flag <value>} pair out of an argument list, URL sniffing, and joining trailing args into a label.
 */
public final class ArgUtils {

    private ArgUtils() {
    }

    public static List<String> mutableList(final String[] args, final int fromIndex) {
        final List<String> list = new ArrayList<>();
        for (int i = fromIndex; i < args.length; i++) {
            list.add(args[i]);
        }
        return list;
    }

    /**
     * Removes the first occurrence of {@code --flag <value>} (case-insensitive) from {@code args} and
     * returns the value, or {@code null} if the flag isn't present. Throws {@link IllegalArgumentException}
     * if the flag is present but has no following value.
     */
    public static String extractFlag(final List<String> args, final String flag) {
        for (int i = 0; i < args.size(); i++) {
            if (args.get(i).equalsIgnoreCase(flag)) {
                if (i + 1 >= args.size()) {
                    throw new IllegalArgumentException(flag + " requires a value");
                }
                final String value = args.get(i + 1);
                args.remove(i + 1);
                args.remove(i);
                return value;
            }
        }
        return null;
    }

    public static boolean looksLikeUrl(final String s) {
        return s != null && (s.startsWith("http://") || s.startsWith("https://"));
    }

    public static String join(final List<String> args) {
        return String.join(" ", args);
    }

    public static Double parseDouble(final String s) {
        try {
            return Double.parseDouble(s);
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    public static Integer parseInt(final String s) {
        try {
            return Integer.parseInt(s);
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    public static Boolean parseBoolean(final String s) {
        if ("true".equalsIgnoreCase(s)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(s)) {
            return Boolean.FALSE;
        }
        return null;
    }
}
