package dev.squaremarkerz.icon;

import dev.squaremarkerz.config.PluginSettings;
import dev.squaremarkerz.util.Sha1;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.jpenilla.squaremap.api.Key;
import xyz.jpenilla.squaremap.api.Squaremap;

/**
 * Downloads, validates, resizes, caches and registers marker icons with squaremap's icon registry.
 *
 * <p>Icons are keyed and cached by the SHA-1 hash of their source URL, so several markers or sets sharing
 * the same URL share a single cached file and a single registry entry (reference-counted). All network
 * and disk I/O runs on a small dedicated executor; only the final squaremap registry read/write hops back
 * onto the main thread.</p>
 */
public final class IconService {

    private final JavaPlugin plugin;
    private final PluginSettings settings;
    private final Squaremap squaremap;
    private final File cacheDir;
    private final ExecutorService executor;
    private final Map<String, Registration> registrations = new ConcurrentHashMap<>();

    public IconService(final JavaPlugin plugin, final PluginSettings settings, final Squaremap squaremap) {
        this.plugin = plugin;
        this.settings = settings;
        this.squaremap = squaremap;
        this.cacheDir = new File(plugin.getDataFolder(), "icons");
        this.executor = Executors.newFixedThreadPool(2, runnable -> {
            final Thread thread = new Thread(runnable, "SquareMarkerZ-Icon-Download");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void shutdown() {
        this.executor.shutdownNow();
    }

    /**
     * Downloads/validates/caches the icon at {@code url} but does not register it or affect ref-counting.
     * Used to fail fast on bad URLs (e.g. {@code /marker set icon}) while still warming the disk cache.
     */
    public CompletableFuture<IconResult> validate(final String url) {
        return this.prepareImage(url)
            .thenApply(image -> IconResult.success(null))
            .exceptionally(ex -> IconResult.failure(rootMessage(ex)));
    }

    /**
     * Resolves {@code url} to a registered squaremap icon key, incrementing its reference count. Must be
     * released later with {@link #releaseForMarker(String)}. The resulting future always completes
     * normally, carrying either a successful {@link IconResult} or a failure reason.
     */
    public CompletableFuture<IconResult> resolveForMarker(final String url) {
        return this.prepareImage(url)
            .thenApplyAsync(image -> {
                final Registration reg = this.registrations.computeIfAbsent(url,
                    u -> new Registration(Key.of("smz_" + Sha1.hex(u))));
                if (reg.refCount.incrementAndGet() == 1) {
                    if (this.squaremap.iconRegistry().hasEntry(reg.key)) {
                        this.squaremap.iconRegistry().unregister(reg.key);
                    }
                    this.squaremap.iconRegistry().register(reg.key, image);
                }
                return IconResult.success(reg.key);
            }, this::runOnMainThread)
            .exceptionally(ex -> IconResult.failure(rootMessage(ex)));
    }

    /**
     * Releases a reference obtained via {@link #resolveForMarker(String)}. Must be called on the main
     * thread. Unregisters the icon from squaremap once nothing references it anymore (the disk cache file
     * is kept either way).
     */
    public void releaseForMarker(final String url) {
        if (url == null) {
            return;
        }
        final Registration reg = this.registrations.get(url);
        if (reg == null) {
            return;
        }
        if (reg.refCount.decrementAndGet() <= 0) {
            this.registrations.remove(url, reg);
            if (this.squaremap.iconRegistry().hasEntry(reg.key)) {
                this.squaremap.iconRegistry().unregister(reg.key);
            }
        }
    }

    private void runOnMainThread(final Runnable runnable) {
        try {
            this.plugin.getServer().getScheduler().runTask(this.plugin, runnable);
        } catch (final IllegalStateException | org.bukkit.plugin.IllegalPluginAccessException e) {
            // Plugin is disabling; nothing sensible left to do.
        }
    }

    private static String rootMessage(final Throwable t) {
        Throwable cause = t;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.toString();
    }

    private CompletableFuture<BufferedImage> prepareImage(final String url) {
        final CompletableFuture<BufferedImage> future = new CompletableFuture<>();
        this.executor.execute(() -> {
            try {
                future.complete(this.loadOrDownload(url));
            } catch (final Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private BufferedImage loadOrDownload(final String url) throws IOException {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            throw new IconLoadException("only http:// and https:// URLs are supported");
        }
        final String hash = Sha1.hex(url);
        final File cacheFile = new File(this.cacheDir, hash + ".png");
        byte[] pngBytes;
        if (cacheFile.exists()) {
            pngBytes = Files.readAllBytes(cacheFile.toPath());
        } else {
            final byte[] downloaded = this.download(url);
            final BufferedImage decoded = decodeStrict(downloaded);
            final BufferedImage resized = resize(decoded, this.settings.iconSize());
            pngBytes = encodePng(resized);
            writeCacheAtomic(cacheFile, pngBytes);
        }
        final BufferedImage finalImage = ImageIO.read(new ByteArrayInputStream(pngBytes));
        if (finalImage == null) {
            // Cache file was corrupt/truncated externally; drop it so the next attempt re-downloads.
            Files.deleteIfExists(cacheFile.toPath());
            throw new IconLoadException("cached icon was corrupt, please try again");
        }
        return finalImage;
    }

    private byte[] download(final String url) throws IOException {
        final HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(this.settings.connectTimeoutMillis());
        connection.setReadTimeout(this.settings.readTimeoutMillis());
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "SquareMarkerZ/1.0");
        try {
            final int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IconLoadException("server returned HTTP " + code);
            }
            final long declaredLength = connection.getContentLengthLong();
            final long maxBytes = this.settings.maxDownloadSizeBytes();
            if (declaredLength > 0 && declaredLength > maxBytes) {
                throw new IconLoadException("file is " + (declaredLength / 1024) + " KB, max is " + (maxBytes / 1024) + " KB");
            }
            final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (InputStream in = connection.getInputStream()) {
                final byte[] chunk = new byte[8192];
                long total = 0;
                int read;
                while ((read = in.read(chunk)) != -1) {
                    total += read;
                    if (total > maxBytes) {
                        throw new IconLoadException("file exceeds max size of " + (maxBytes / 1024) + " KB");
                    }
                    buffer.write(chunk, 0, read);
                }
            }
            return buffer.toByteArray();
        } finally {
            connection.disconnect();
        }
    }

    private static BufferedImage decodeStrict(final byte[] bytes) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (iis == null) {
                throw new IconLoadException("could not read image data");
            }
            final Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new IconLoadException("the URL did not return a valid PNG, JPG or GIF image");
            }
            final ImageReader reader = readers.next();
            final String format = reader.getFormatName().toLowerCase(Locale.ROOT);
            if (!(format.contains("png") || format.contains("jpeg") || format.contains("jpg") || format.contains("gif"))) {
                reader.dispose();
                throw new IconLoadException("unsupported image format '" + format + "', expected PNG, JPG or GIF");
            }
            reader.setInput(iis);
            final BufferedImage image = reader.read(0);
            reader.dispose();
            if (image == null) {
                throw new IconLoadException("failed to decode image");
            }
            return image;
        }
    }

    private static BufferedImage resize(final BufferedImage source, final int size) {
        final int sourceWidth = source.getWidth();
        final int sourceHeight = source.getHeight();
        final double scale = Math.min((double) size / sourceWidth, (double) size / sourceHeight);
        final int newWidth = Math.max(1, (int) Math.round(sourceWidth * scale));
        final int newHeight = Math.max(1, (int) Math.round(sourceHeight * scale));

        final BufferedImage canvas = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = canvas.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            final int x = (size - newWidth) / 2;
            final int y = (size - newHeight) / 2;
            g.drawImage(source, x, y, newWidth, newHeight, null);
        } finally {
            g.dispose();
        }
        return canvas;
    }

    private static byte[] encodePng(final BufferedImage image) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static void writeCacheAtomic(final File target, final byte[] bytes) throws IOException {
        final File parent = target.getParentFile();
        if (!parent.exists() && !parent.mkdirs() && !parent.exists()) {
            throw new IOException("Could not create directory " + parent);
        }
        final File tmp = new File(parent, target.getName() + ".tmp");
        Files.write(tmp.toPath(), bytes);
        try {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (final AtomicMoveNotSupportedException e) {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static final class Registration {
        private final Key key;
        private final AtomicInteger refCount = new AtomicInteger(0);

        private Registration(final Key key) {
            this.key = key;
        }
    }
}
