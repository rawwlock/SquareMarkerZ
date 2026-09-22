package dev.squaremarkerz;

import dev.squaremarkerz.command.MarkerCommand;
import dev.squaremarkerz.config.Messages;
import dev.squaremarkerz.config.PluginSettings;
import dev.squaremarkerz.icon.IconService;
import dev.squaremarkerz.manager.MarkerManager;
import dev.squaremarkerz.manager.MarkerSetManager;
import dev.squaremarkerz.squaremap.SquaremapIntegration;
import dev.squaremarkerz.storage.MarkerStorage;
import dev.squaremarkerz.storage.SetStorage;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.jpenilla.squaremap.api.Squaremap;

public final class SquareMarkerZPlugin extends JavaPlugin {

    private IconService iconService;
    private SquaremapIntegration integration;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();

        final Squaremap squaremap = this.getServer().getServicesManager().load(Squaremap.class);
        if (squaremap == null) {
            this.getLogger().severe("Could not find the squaremap API. Is squaremap installed and enabled?");
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }

        final PluginSettings settings = new PluginSettings(this);
        final Messages messages = new Messages(this);

        final SetStorage setStorage = new SetStorage(this);
        final MarkerStorage markerStorage = new MarkerStorage(this);

        this.iconService = new IconService(this, settings, squaremap);

        final MarkerSetManager setManager = new MarkerSetManager(this, setStorage, settings);
        final MarkerManager markerManager = new MarkerManager(this, markerStorage, setManager, this.iconService);

        this.integration = new SquaremapIntegration(this, squaremap, setManager, markerManager, settings);
        setManager.setIntegration(this.integration);
        markerManager.setIntegration(this.integration);

        this.integration.start();
        markerManager.resolveIconsAndPublish();

        final MarkerCommand executor = new MarkerCommand(this, messages, settings, setManager, markerManager, this.iconService, this.integration);
        final var command = this.getCommand("marker");
        if (command == null) {
            this.getLogger().severe("Command 'marker' is missing from plugin.yml.");
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);

        this.getLogger().info("SquareMarkerZ enabled, hooked into squaremap.");
    }

    @Override
    public void onDisable() {
        if (this.integration != null) {
            this.integration.shutdown();
        }
        if (this.iconService != null) {
            this.iconService.shutdown();
        }
    }
}
