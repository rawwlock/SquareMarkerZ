package dev.squaremarkerz.command;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.command.CommandSender;

/**
 * Tracks short-lived "type it again to confirm" windows (e.g. {@code /marker set delete <id> confirm}),
 * scoped per sender and target so two admins confirming different sets can't cross-trigger each other.
 */
public final class ConfirmationManager {

    private static final long WINDOW_MILLIS = 30_000L;

    private final Map<String, Long> pending = new ConcurrentHashMap<>();

    public void request(final CommandSender sender, final String action, final String target) {
        this.pending.put(key(sender, action, target), System.currentTimeMillis() + WINDOW_MILLIS);
    }

    public boolean consumeIfValid(final CommandSender sender, final String action, final String target) {
        final Long expiry = this.pending.remove(key(sender, action, target));
        return expiry != null && expiry >= System.currentTimeMillis();
    }

    private static String key(final CommandSender sender, final String action, final String target) {
        return sender.getName() + ':' + action + ':' + target;
    }
}
