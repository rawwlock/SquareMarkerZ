package dev.squaremarkerz.command;

import dev.squaremarkerz.config.Messages;
import dev.squaremarkerz.icon.IconService;
import dev.squaremarkerz.manager.ManagerException;
import dev.squaremarkerz.manager.MarkerManager;
import dev.squaremarkerz.manager.MarkerSetManager;
import dev.squaremarkerz.model.MarkerSet;
import dev.squaremarkerz.util.IdValidator;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

/**
 * Handles every {@code /marker set ...} subcommand.
 */
final class SetCommands {

    private final Messages messages;
    private final MarkerSetManager setManager;
    private final MarkerManager markerManager;
    private final IconService iconService;
    private final ConfirmationManager confirmations;
    private final Executor mainThread;

    SetCommands(
        final Messages messages,
        final MarkerSetManager setManager,
        final MarkerManager markerManager,
        final IconService iconService,
        final ConfirmationManager confirmations,
        final Executor mainThread
    ) {
        this.messages = messages;
        this.setManager = setManager;
        this.markerManager = markerManager;
        this.iconService = iconService;
        this.confirmations = confirmations;
        this.mainThread = mainThread;
    }

    void handle(final CommandSender sender, final String[] args) {
        if (args.length == 0) {
            this.sendHelp(sender);
            return;
        }
        final String sub = args[0].toLowerCase(java.util.Locale.ROOT);
        final String[] rest = Arrays.copyOfRange(args, 1, args.length);
        try {
            switch (sub) {
                case "create" -> this.create(sender, rest);
                case "delete" -> this.delete(sender, rest);
                case "label" -> this.label(sender, rest);
                case "hide" -> this.hide(sender, rest);
                case "controls" -> this.controls(sender, rest);
                case "priority" -> this.priority(sender, rest);
                case "zindex" -> this.zIndex(sender, rest);
                case "icon" -> this.icon(sender, rest);
                case "list" -> this.list(sender);
                case "info" -> this.info(sender, rest);
                default -> this.sendHelp(sender);
            }
        } catch (final ManagerException e) {
            this.messages.send(sender, e.key(), e.placeholders());
        }
    }

    private void create(final CommandSender sender, final String[] args) {
        if (args.length < 1) {
            this.usage(sender, "/marker set create <setId> [label...]");
            return;
        }
        final String label = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : null;
        final MarkerSet set = this.setManager.create(args[0], label);
        this.messages.send(sender, "set-created", "id", set.id());
    }

    private void delete(final CommandSender sender, final String[] args) {
        if (args.length < 1) {
            this.usage(sender, "/marker set delete <setId> [confirm] [--moveto <setId>]");
            return;
        }
        final List<String> rest = ArgUtils.mutableList(args, 0);
        final String moveTo = ArgUtils.extractFlag(rest, "--moveto");
        final String rawId = rest.remove(0);
        final boolean confirmFlag = !rest.isEmpty() && rest.get(0).equalsIgnoreCase("confirm");

        final MarkerSet set = this.setManager.require(rawId);
        if (this.setManager.isDefault(set.id())) {
            this.messages.send(sender, "set-default-protected");
            return;
        }

        if (moveTo != null) {
            final MarkerSet target = this.setManager.require(moveTo);
            if (target.id().equals(set.id())) {
                this.messages.send(sender, "set-move-same");
                return;
            }
            this.markerManager.moveAllMarkersToSet(set.id(), target.id());
            this.setManager.delete(set.id());
            this.messages.send(sender, "set-deleted-moved", "id", set.id(), "target", target.id());
            return;
        }

        if (confirmFlag) {
            if (!this.confirmations.consumeIfValid(sender, "set-delete", set.id())) {
                this.messages.send(sender, "set-delete-expired");
                return;
            }
            this.markerManager.removeAllMarkersInSet(set.id());
            this.setManager.delete(set.id());
            this.messages.send(sender, "set-deleted", "id", set.id());
            return;
        }

        this.confirmations.request(sender, "set-delete", set.id());
        final int count = this.markerManager.markersInSet(set.id()).size();
        this.messages.send(sender, "set-delete-confirm", "id", set.id(), "count", String.valueOf(count));
    }

    private void label(final CommandSender sender, final String[] args) {
        if (args.length < 2) {
            this.usage(sender, "/marker set label <setId> <label...>");
            return;
        }
        this.setManager.label(args[0], String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        this.messages.send(sender, "set-updated", "id", IdValidator.normalize(args[0]));
    }

    private void hide(final CommandSender sender, final String[] args) {
        if (args.length < 2 || ArgUtils.parseBoolean(args[1]) == null) {
            this.usage(sender, "/marker set hide <setId> <true|false>");
            return;
        }
        this.setManager.hiddenByDefault(args[0], ArgUtils.parseBoolean(args[1]));
        this.messages.send(sender, "set-updated", "id", IdValidator.normalize(args[0]));
    }

    private void controls(final CommandSender sender, final String[] args) {
        if (args.length < 2 || ArgUtils.parseBoolean(args[1]) == null) {
            this.usage(sender, "/marker set controls <setId> <true|false>");
            return;
        }
        this.setManager.showControls(args[0], ArgUtils.parseBoolean(args[1]));
        this.messages.send(sender, "set-updated", "id", IdValidator.normalize(args[0]));
    }

    private void priority(final CommandSender sender, final String[] args) {
        if (args.length < 2 || ArgUtils.parseInt(args[1]) == null) {
            this.usage(sender, "/marker set priority <setId> <number>");
            return;
        }
        this.setManager.priority(args[0], ArgUtils.parseInt(args[1]));
        this.messages.send(sender, "set-updated", "id", IdValidator.normalize(args[0]));
    }

    private void zIndex(final CommandSender sender, final String[] args) {
        if (args.length < 2 || ArgUtils.parseInt(args[1]) == null) {
            this.usage(sender, "/marker set zindex <setId> <number>");
            return;
        }
        this.setManager.zIndex(args[0], ArgUtils.parseInt(args[1]));
        this.messages.send(sender, "set-updated", "id", IdValidator.normalize(args[0]));
    }

    private void icon(final CommandSender sender, final String[] args) {
        if (args.length < 2) {
            this.usage(sender, "/marker set icon <setId> <iconUrl|none>");
            return;
        }
        final MarkerSet set = this.setManager.require(args[0]);
        final String urlArg = args[1];
        if (urlArg.equalsIgnoreCase("none")) {
            this.setManager.defaultIconUrl(set.id(), null);
            this.messages.send(sender, "set-updated", "id", set.id());
            return;
        }
        if (!ArgUtils.looksLikeUrl(urlArg)) {
            this.messages.send(sender, "icon-invalid-url");
            return;
        }
        this.iconService.validate(urlArg).whenCompleteAsync((result, throwable) -> {
            if (throwable != null || !result.success()) {
                final String reason = throwable != null ? throwable.getMessage() : result.reason();
                this.messages.send(sender, "icon-download-failed", "reason", reason);
                return;
            }
            this.setManager.defaultIconUrl(set.id(), urlArg);
            this.messages.send(sender, "set-updated", "id", set.id());
        }, this.mainThread);
    }

    private void list(final CommandSender sender) {
        final List<MarkerSet> sets = this.setManager.all().stream()
            .sorted((a, b) -> Integer.compare(a.priority(), b.priority()))
            .toList();
        sender.sendMessage(Component.text("Marker sets (" + sets.size() + "):", NamedTextColor.GOLD));
        for (final MarkerSet set : sets) {
            final int count = this.markerManager.markersInSet(set.id()).size();
            final String defaultTag = this.setManager.isDefault(set.id()) ? " (default)" : "";
            sender.sendMessage(Component.text(
                " - " + set.id() + defaultTag + ": \"" + set.label() + "\" [" + count + " marker(s)]",
                NamedTextColor.GRAY
            ));
        }
    }

    private void info(final CommandSender sender, final String[] args) {
        if (args.length < 1) {
            this.usage(sender, "/marker set info <setId>");
            return;
        }
        final MarkerSet set = this.setManager.require(args[0]);
        final int count = this.markerManager.markersInSet(set.id()).size();
        sender.sendMessage(Component.text("Set '" + set.id() + "'" + (this.setManager.isDefault(set.id()) ? " (default)" : ""), NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  label: " + set.label(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  priority: " + set.priority() + "  z-index: " + set.zIndex(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  hidden-by-default: " + set.hiddenByDefault() + "  show-controls: " + set.showControls(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  default-icon-url: " + (set.defaultIconUrl() == null ? "(none)" : set.defaultIconUrl()), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  markers: " + count, NamedTextColor.GRAY));
    }

    private void usage(final CommandSender sender, final String usage) {
        sender.sendMessage(Component.text("Usage: " + usage, NamedTextColor.RED));
    }

    private void sendHelp(final CommandSender sender) {
        sender.sendMessage(Component.text("--- /marker set ---", NamedTextColor.GOLD));
        for (final String line : new String[] {
            "create <setId> [label...]",
            "delete <setId> [confirm] [--moveto <setId>]",
            "label <setId> <label...>",
            "hide <setId> <true|false>",
            "controls <setId> <true|false>",
            "priority <setId> <number>",
            "zindex <setId> <number>",
            "icon <setId> <iconUrl|none>",
            "list",
            "info <setId>"
        }) {
            sender.sendMessage(Component.text("/marker set " + line, NamedTextColor.GRAY));
        }
    }
}
