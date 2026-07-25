package net.guesswhoami.onlineplayers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;

/**
 * Recomputes network-wide and per-server player counts on every connect/disconnect and writes
 * them to {@code online-players.json}, so other processes can read current player counts without
 * an RCON round trip.
 *
 * <p>Counts are recomputed from Velocity's live state on every event rather than incremented/
 * decremented, so a missed or misordered event can never leave the file drifting from reality.
 */
final class PlayerCountService {

    // Files.createTempFile() defaults to owner-only permissions on POSIX (a deliberate JDK
    // security default for temp files); since online-players.json is produced by an atomic move
    // of that temp file, it would otherwise inherit those restrictive permissions and be
    // unreadable to anything but the proxy's own user - defeating the point of writing it for
    // external readers. Must be applied via setPosixFilePermissions() after creation, not
    // createTempFile()'s FileAttribute varargs - confirmed empirically that this JDK silently
    // ignores permissions requested that way and creates the file at the umask-restricted default
    // regardless.
    private static final Set<PosixFilePermission> WORLD_READABLE_PERMISSIONS =
            PosixFilePermissions.fromString("rw-rw-r--");

    // Cosmetic freshness only, not a liveness signal - keeps `updated` from going stale-looking
    // on the public status page during quiet periods with no joins/leaves.
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(45);

    private final OnlinePlayersPlugin plugin;
    private final ProxyServer server;
    private final Path dataDirectory;
    private final Logger logger;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path outputFile;
    private final AtomicReference<PlayerCountFile> lastWritten = new AtomicReference<>();

    PlayerCountService(
            final OnlinePlayersPlugin plugin,
            final ProxyServer server,
            final Path dataDirectory,
            final Logger logger) {
        this.plugin = plugin;
        this.server = server;
        this.dataDirectory = dataDirectory;
        this.logger = logger;
        this.outputFile = dataDirectory.resolve("online-players.json");
    }

    void start() {
        try {
            Files.createDirectories(dataDirectory);
        } catch (final IOException e) {
            logger.error("Could not create data directory {}: {}", dataDirectory, e.getMessage());
            return;
        }

        // Reflect current state immediately - don't wait for the next connect/disconnect.
        writeIfChanged();

        server.getScheduler()
                .buildTask(plugin, this::heartbeat)
                .delay(HEARTBEAT_INTERVAL)
                .repeat(HEARTBEAT_INTERVAL)
                .schedule();

        logger.info(
                "Writing {} on player connect/disconnect and every {}",
                outputFile,
                HEARTBEAT_INTERVAL);
    }

    @Subscribe
    public void onServerConnected(final ServerConnectedEvent event) {
        writeIfChanged();
    }

    @Subscribe
    public void onDisconnect(final DisconnectEvent event) {
        writeIfChanged();
    }

    private void writeIfChanged() {
        final Map<String, Integer> servers = currentServerCounts();
        final int total = server.getPlayerCount();

        final PlayerCountFile previous = lastWritten.get();
        if (previous != null && previous.sameCounts(total, servers)) {
            return;
        }

        logger.info("Refreshing {}: total={}, servers={}", outputFile, total, servers);
        write(total, servers);
    }

    // Rewrites unconditionally, even if the counts haven't changed, so `updated` keeps advancing
    // during quiet periods - see HEARTBEAT_INTERVAL.
    private void heartbeat() {
        write(server.getPlayerCount(), currentServerCounts());
    }

    private void write(final int total, final Map<String, Integer> servers) {
        final PlayerCountFile snapshot = PlayerCountFile.now(total, servers);
        writeAtomic(outputFile, gson.toJson(snapshot));
        lastWritten.set(snapshot);
    }

    private Map<String, Integer> currentServerCounts() {
        final Map<String, Integer> servers = new LinkedHashMap<>();
        for (final RegisteredServer registeredServer : server.getAllServers()) {
            servers.put(
                    registeredServer.getServerInfo().getName(),
                    registeredServer.getPlayersConnected().size());
        }
        return servers;
    }

    // Package-private (not private) so the permissions behavior is directly unit-testable.
    void writeAtomic(final Path target, final String content) {
        Path tmp = null;
        try {
            tmp = Files.createTempFile(dataDirectory, target.getFileName().toString(), ".tmp");
            Files.setPosixFilePermissions(tmp, WORLD_READABLE_PERMISSIONS);
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            tmp = null; // moved successfully - nothing left to clean up
        } catch (final IOException e) {
            logger.error("Failed to write {}: {}", target, e.getMessage());
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (final IOException ignored) {
                    // best effort - a leftover .tmp file isn't worth a second error log
                }
            }
        }
    }
}
