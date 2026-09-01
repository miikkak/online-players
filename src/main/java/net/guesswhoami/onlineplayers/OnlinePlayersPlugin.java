package net.guesswhoami.onlineplayers;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import java.nio.file.Path;
import org.slf4j.Logger;

@Plugin(
        id = "online-players",
        name = "online-players",
        version = BuildInfo.VERSION,
        description = "Records network-wide and per-server online player counts to a JSON file on change",
        authors = {"miikkak"})
public class OnlinePlayersPlugin {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    @Inject
    public OnlinePlayersPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        final PlayerCountService service = new PlayerCountService(this, server, dataDirectory, logger);
        if (service.start()) {
            server.getEventManager().register(this, service);
        }
    }
}
