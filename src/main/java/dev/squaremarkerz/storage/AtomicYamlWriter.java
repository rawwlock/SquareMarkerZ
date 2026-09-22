package dev.squaremarkerz.storage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Saves a {@link YamlConfiguration} to disk asynchronously by writing to a temp file and renaming it
 * into place, so a crash or concurrent read never observes a partially written file.
 */
public final class AtomicYamlWriter {

    private AtomicYamlWriter() {
    }

    public static void saveAsync(final JavaPlugin plugin, final YamlConfiguration config, final File target) {
        // The YamlConfiguration passed in must already be a private snapshot (not mutated further by the
        // caller after this call), since serialization happens off the main thread.
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            final File parent = target.getParentFile();
            final File tmp = new File(parent, target.getName() + ".tmp");
            try {
                if (!parent.exists() && !parent.mkdirs() && !parent.exists()) {
                    throw new IOException("Could not create directory " + parent);
                }
                config.save(tmp);
                try {
                    Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (final java.nio.file.AtomicMoveNotSupportedException ex) {
                    Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (final IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save " + target.getName(), e);
            }
        });
    }
}
