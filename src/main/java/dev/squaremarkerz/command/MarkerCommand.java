package dev.squaremarkerz.command;

import dev.squaremarkerz.config.Messages;
import dev.squaremarkerz.config.PluginSettings;
import dev.squaremarkerz.icon.IconService;
import dev.squaremarkerz.manager.ManagerException;
import dev.squaremarkerz.manager.MarkerManager;
import dev.squaremarkerz.manager.MarkerSetManager;
import dev.squaremarkerz.model.MarkerRecord;
import dev.squaremarkerz.squaremap.SquaremapIntegration;
import dev.squaremarkerz.util.IdValidator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Handles every {@code /marker} (alias {@code /squaremarker}) subcommand and its tab completion.
 * {@code /marker set ...} is delegated to {@link SetCommands}.
 */
public final class MarkerCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
        "add", "addat", "remove", "move", "moveset", "label", "icon", "desc",
        "list", "info", "tp", "reload", "set"
    );
    private static final List<String> SET_SUBCOMMANDS = List.of(
        "create", "delete", "label", "hide", "controls", "priority", "zindex", "icon", "list", "info"
    );
    private static final List<String> BOOLEANS = List.of("true", "false");
    private static final int PAGE_SIZE = 8;

    private final JavaPlugin plugin;
    private final Messages messages;
    private final PluginSettings settings;
    private final MarkerSetManager setManager;
    private final MarkerManager markerManager;
    private final SquaremapIntegration integration;
    private final SetCommands setCommands;
    private final Executor mainThread;

    public MarkerCommand(
        final JavaPlugin plugin,
        final Messages messages,
        final PluginSettings settings,
        final MarkerSetManager setManager,
        final MarkerManager markerManager,
        final IconService iconService,
        final SquaremapIntegration integration
    ) {
        this.plugin = plugin;
        this.messages = messages;
        this.settings = settings;
        this.setManager = setManager;
        this.markerManager = markerManager;
        this.integration = integration;
        this.mainThread = runnable -> plugin.getServer().getScheduler().runTask(plugin, runnable);
        this.setCommands = new SetCommands(messages, setManager, markerManager, iconService, new ConfirmationManager(), this.mainThread);
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (!this.hasAccess(sender)) {
            this.messages.send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            this.sendHelp(sender);
            return true;
        }
        final String sub = args[0].toLowerCase(Locale.ROOT);
        final String[] rest = Arrays.copyOfRange(args, 1, args.length);
        try {
            switch (sub) {
                case "add" -> this.add(sender, rest);
                case "addat" -> this.addAt(sender, rest);
                case "remove" -> this.remove(sender, rest);
                case "move" -> this.move(sender, rest);
                case "moveset" -> this.moveset(sender, rest);
                case "label" -> this.label(sender, rest);
                case "icon" -> this.icon(sender, rest);
                case "desc" -> this.desc(sender, rest);
                case "list" -> this.list(sender, rest);
                case "info" -> this.info(sender, rest);
                case "tp" -> this.tp(sender, rest);
                case "reload" -> this.reload(sender);
                case "set" -> this.setCommands.handle(sender, rest);
                default -> {
                    this.messages.send(sender, "unknown-subcommand");
                    this.sendHelp(sender);
                }
            }
        } catch (final ManagerException e) {
            this.messages.send(sender, e.key(), e.placeholders());
        } catch (final IllegalArgumentException e) {
            sender.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }
        return true;
    }

    // ----------------------------------------------------------------- marker subcommands

    private void add(final CommandSender sender, final String[] args) {
        if (!(sender instanceof final Player player)) {
            this.messages.send(sender, "player-only");
            return;
        }
        if (args.length < 1 || !IdValidator.isValid(args[0])) {
            this.usage(sender, "/marker add <id> [iconUrl] [label...] [--set <setId>]");
            return;
        }
        final List<String> rest = ArgUtils.mutableList(args, 1);
        final String setId = ArgUtils.extractFlag(rest, "--set");
        String iconUrl = null;
        if (!rest.isEmpty() && ArgUtils.looksLikeUrl(rest.get(0))) {
            iconUrl = rest.remove(0);
        }
        final String labelText = rest.isEmpty() ? null : ArgUtils.join(rest);
        final Location loc = player.getLocation();

        final CompletableFuture<MarkerRecord> future = this.markerManager.create(
            args[0], loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(),
            iconUrl, labelText, setId, player.getName()
        );
        this.completeCreate(sender, future);
    }

    private void addAt(final CommandSender sender, final String[] args) {
        if (args.length < 4 || !IdValidator.isValid(args[0])) {
            this.usage(sender, "/marker addat <id> <world> <x> <z> [iconUrl] [label...] [--set <setId>]");
            return;
        }
        final Double x = ArgUtils.parseDouble(args[2]);
        final Double z = ArgUtils.parseDouble(args[3]);
        if (x == null) {
            this.messages.send(sender, "invalid-number", "value", args[2]);
            return;
        }
        if (z == null) {
            this.messages.send(sender, "invalid-number", "value", args[3]);
            return;
        }
        final World world = Bukkit.getWorld(args[1]);
        if (world == null) {
            this.messages.send(sender, "world-not-found", "world", args[1]);
            return;
        }
        final List<String> rest = ArgUtils.mutableList(args, 4);
        final String setId = ArgUtils.extractFlag(rest, "--set");
        String iconUrl = null;
        if (!rest.isEmpty() && ArgUtils.looksLikeUrl(rest.get(0))) {
            iconUrl = rest.remove(0);
        }
        final String labelText = rest.isEmpty() ? null : ArgUtils.join(rest);
        final double y = world.getHighestBlockYAt(x.intValue(), z.intValue()) + 1;

        final CompletableFuture<MarkerRecord> future = this.markerManager.create(
            args[0], world.getName(), x, y, z, iconUrl, labelText, setId, sender.getName()
        );
        this.completeCreate(sender, future);
    }

    private void completeCreate(final CommandSender sender, final CompletableFuture<MarkerRecord> future) {
        future.whenCompleteAsync((record, throwable) -> {
            if (throwable != null) {
                final ManagerException e = ManagerException.unwrap(throwable);
                this.messages.send(sender, e.key(), e.placeholders());
            } else {
                this.messages.send(sender, "marker-created", "id", record.id(), "set", record.set());
            }
        }, this.mainThread);
    }

    private void remove(final CommandSender sender, final String[] args) {
        if (args.length < 1) {
            this.usage(sender, "/marker remove <id>");
            return;
        }
        final MarkerRecord record = this.markerManager.remove(args[0]);
        this.messages.send(sender, "marker-removed", "id", record.id());
    }

    private void move(final CommandSender sender, final String[] args) {
        if (!(sender instanceof final Player player)) {
            this.messages.send(sender, "player-only");
            return;
        }
        if (args.length < 1) {
            this.usage(sender, "/marker move <id>");
            return;
        }
        final Location loc = player.getLocation();
        final MarkerRecord record = this.markerManager.move(args[0], loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
        this.messages.send(sender, "marker-moved", "id", record.id());
    }

    private void moveset(final CommandSender sender, final String[] args) {
        if (args.length < 2) {
            this.usage(sender, "/marker moveset <id> <setId>");
            return;
        }
        final MarkerRecord record = this.markerManager.moveToSet(args[0], args[1]);
        this.messages.send(sender, "marker-moveset", "id", record.id(), "set", record.set());
    }

    private void label(final CommandSender sender, final String[] args) {
        if (args.length < 2) {
            this.usage(sender, "/marker label <id> <label...>");
            return;
        }
        final MarkerRecord record = this.markerManager.label(args[0], String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        this.messages.send(sender, "marker-label-set", "id", record.id());
    }

    private void icon(final CommandSender sender, final String[] args) {
        if (args.length < 2 || !ArgUtils.looksLikeUrl(args[1])) {
            this.usage(sender, "/marker icon <id> <iconUrl>");
            return;
        }
        final CompletableFuture<MarkerRecord> future = this.markerManager.icon(args[0], args[1]);
        future.whenCompleteAsync((record, throwable) -> {
            if (throwable != null) {
                final ManagerException e = ManagerException.unwrap(throwable);
                this.messages.send(sender, e.key(), e.placeholders());
            } else {
                this.messages.send(sender, "marker-icon-set", "id", record.id());
            }
        }, this.mainThread);
    }

    private void desc(final CommandSender sender, final String[] args) {
        if (args.length < 1) {
            this.usage(sender, "/marker desc <id> <text...>");
            return;
        }
        final String text = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "";
        final MarkerRecord record = this.markerManager.description(args[0], text);
        this.messages.send(sender, "marker-desc-set", "id", record.id());
    }

    private void list(final CommandSender sender, final String[] args) {
        final List<String> rest = ArgUtils.mutableList(args, 0);
        final String setFilter = ArgUtils.extractFlag(rest, "--set");
        if (setFilter != null) {
            this.setManager.require(setFilter);
        }
        String worldFilter = null;
        int page = 1;
        if (!rest.isEmpty()) {
            final Integer maybePage = ArgUtils.parseInt(rest.get(0));
            if (maybePage != null) {
                page = maybePage;
            } else {
                worldFilter = rest.remove(0);
                if (!rest.isEmpty()) {
                    final Integer p2 = ArgUtils.parseInt(rest.get(0));
                    if (p2 != null) {
                        page = p2;
                    }
                }
            }
        }

        final String normalizedSetFilter = setFilter == null ? null : IdValidator.normalize(setFilter);
        final String finalWorldFilter = worldFilter;
        final List<MarkerRecord> filtered = new ArrayList<>(this.markerManager.all());
        filtered.removeIf(r -> finalWorldFilter != null && !r.world().equalsIgnoreCase(finalWorldFilter));
        filtered.removeIf(r -> normalizedSetFilter != null && !r.set().equals(normalizedSetFilter));
        filtered.sort(Comparator.comparing(MarkerRecord::id));

        final int totalPages = Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        final int clampedPage = Math.min(Math.max(1, page), totalPages);
        final int from = (clampedPage - 1) * PAGE_SIZE;
        final int to = Math.min(filtered.size(), from + PAGE_SIZE);

        sender.sendMessage(Component.text(
            "Markers (page " + clampedPage + "/" + totalPages + ", " + filtered.size() + " total):", NamedTextColor.GOLD));
        for (final MarkerRecord record : filtered.subList(from, to)) {
            final Component line = Component.text(" - " + record.id() + " ", NamedTextColor.GRAY)
                .append(Component.text("[" + record.set() + "] ", NamedTextColor.DARK_AQUA))
                .append(Component.text(record.world() + " (" + fmt(record.x()) + ", " + fmt(record.y()) + ", " + fmt(record.z()) + ") ", NamedTextColor.GRAY))
                .append(Component.text("[tp]", NamedTextColor.GREEN)
                    .clickEvent(ClickEvent.runCommand("/marker tp " + record.id()))
                    .hoverEvent(HoverEvent.showText(Component.text("Teleport to " + record.id()))));
            sender.sendMessage(line);
        }
    }

    private void info(final CommandSender sender, final String[] args) {
        if (args.length < 1) {
            this.usage(sender, "/marker info <id>");
            return;
        }
        final MarkerRecord record = this.markerManager.require(args[0]);
        sender.sendMessage(Component.text("Marker '" + record.id() + "'", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  set: " + record.set(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  location: " + record.world() + " (" + fmt(record.x()) + ", " + fmt(record.y()) + ", " + fmt(record.z()) + ")", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  label: " + record.label(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  description: " + (record.description() == null || record.description().isEmpty() ? "(none)" : record.description()), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  icon: " + (record.iconUrl() == null ? "(none)" : record.iconUrl()), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  created by " + record.createdBy() + " at " + java.time.Instant.ofEpochMilli(record.createdAt()), NamedTextColor.GRAY));
    }

    private void tp(final CommandSender sender, final String[] args) {
        if (!(sender instanceof final Player player)) {
            this.messages.send(sender, "player-only");
            return;
        }
        if (args.length < 1) {
            this.usage(sender, "/marker tp <id>");
            return;
        }
        final MarkerRecord record = this.markerManager.require(args[0]);
        final World world = Bukkit.getWorld(record.world());
        if (world == null) {
            this.messages.send(sender, "world-not-found", "world", record.world());
            return;
        }
        player.teleport(new Location(world, record.x(), record.y(), record.z()));
        sender.sendMessage(Component.text("Teleported to '" + record.id() + "'.", NamedTextColor.GREEN));
    }

    private void reload(final CommandSender sender) {
        this.plugin.reloadConfig();
        this.settings.reload();
        this.messages.reload();
        this.setManager.reloadFromDisk();
        this.markerManager.reloadFromDisk();
        this.integration.rebuildAll();
        this.markerManager.resolveIconsAndPublish();
        this.messages.send(sender, "reload-complete",
            "sets", String.valueOf(this.setManager.all().size()),
            "markers", String.valueOf(this.markerManager.count()));
    }

    // ----------------------------------------------------------------- tab completion

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        if (!this.hasAccess(sender)) {
            return List.of();
        }
        if (args.length == 0) {
            return List.of();
        }
        if (args.length == 1) {
            return filterPrefix(SUBCOMMANDS, args[0]);
        }

        final int currentIndex = args.length - 1;
        final String current = args[currentIndex];
        final String previous = args[currentIndex - 1];
        if (previous.equalsIgnoreCase("--set") || previous.equalsIgnoreCase("--moveto")) {
            return this.setIds(current);
        }

        final String sub = args[0].toLowerCase(Locale.ROOT);
        final String[] rest = Arrays.copyOfRange(args, 1, args.length);
        return switch (sub) {
            case "set" -> this.setTabComplete(rest, current);
            case "addat" -> args.length == 3 ? this.worldNames(current) : filterPrefix(List.of("--set"), current);
            case "add" -> args.length == 2 ? List.of() : filterPrefix(List.of("--set"), current);
            case "remove", "move", "icon", "label", "desc", "info", "tp" -> args.length == 2 ? this.markerIds(current) : List.of();
            case "moveset" -> {
                if (args.length == 2) {
                    yield this.markerIds(current);
                }
                yield args.length == 3 ? this.setIds(current) : List.of();
            }
            case "list" -> {
                final List<String> options = new ArrayList<>(this.worldNames(current));
                options.addAll(filterPrefix(List.of("--set"), current));
                yield options;
            }
            default -> List.of();
        };
    }

    private List<String> setTabComplete(final String[] rest, final String current) {
        if (rest.length == 1) {
            return filterPrefix(SET_SUBCOMMANDS, current);
        }
        final String setSub = rest[0].toLowerCase(Locale.ROOT);
        return switch (setSub) {
            case "delete" -> rest.length == 2 ? this.setIds(current) : filterPrefix(List.of("confirm", "--moveto"), current);
            case "label", "priority", "zindex", "info" -> rest.length == 2 ? this.setIds(current) : List.of();
            case "hide", "controls" -> {
                if (rest.length == 2) {
                    yield this.setIds(current);
                }
                yield rest.length == 3 ? filterPrefix(BOOLEANS, current) : List.of();
            }
            case "icon" -> {
                if (rest.length == 2) {
                    yield this.setIds(current);
                }
                yield rest.length == 3 ? filterPrefix(List.of("none"), current) : List.of();
            }
            default -> List.of();
        };
    }

    private boolean hasAccess(final CommandSender sender) {
        if (!sender.hasPermission("squaremarkers.admin")) {
            return false;
        }
        return !(sender instanceof Player player) || player.isOp();
    }

    private void usage(final CommandSender sender, final String usage) {
        sender.sendMessage(Component.text("Usage: " + usage, NamedTextColor.RED));
    }

    private void sendHelp(final CommandSender sender) {
        sender.sendMessage(Component.text("--- SquareMarkerZ (/marker) ---", NamedTextColor.GOLD));
        for (final String line : new String[] {
            "add <id> [iconUrl] [label...] [--set <setId>]",
            "addat <id> <world> <x> <z> [iconUrl] [label...] [--set <setId>]",
            "remove <id>",
            "move <id>",
            "moveset <id> <setId>",
            "label <id> <label...>",
            "icon <id> <iconUrl>",
            "desc <id> <text...>",
            "list [world] [page] [--set <setId>]",
            "info <id>",
            "tp <id>",
            "reload",
            "set ... (run /marker set for its own help)"
        }) {
            sender.sendMessage(Component.text("/marker " + line, NamedTextColor.GRAY));
        }
    }

    private List<String> markerIds(final String prefix) {
        return filterPrefix(this.markerManager.all().stream().map(MarkerRecord::id).toList(), prefix);
    }

    private List<String> setIds(final String prefix) {
        return filterPrefix(this.setManager.all().stream().map(s -> s.id()).toList(), prefix);
    }

    private List<String> worldNames(final String prefix) {
        return filterPrefix(Bukkit.getWorlds().stream().map(World::getName).toList(), prefix);
    }

    private static List<String> filterPrefix(final List<String> options, final String prefix) {
        final String p = prefix.toLowerCase(Locale.ROOT);
        final List<String> result = new ArrayList<>();
        for (final String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(p)) {
                result.add(option);
            }
        }
        result.sort(String::compareTo);
        return result;
    }

    private static String fmt(final double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
