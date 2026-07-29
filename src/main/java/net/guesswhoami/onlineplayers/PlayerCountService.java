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
            PosixFilePermissions.fromString("rw-r--r--");

    // Cosmetic freshness only, not a liveness signal - keeps `updated` from going stale-looking
    // on the public status page during quiet periods with no joins/leaves.
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(45);

    // Velocity fires ServerConnectedEvent/DisconnectEvent *before* it finishes mutating the
    // affected RegisteredServer's connected-player list (that happens in a continuation chained
    // after the event's future resolves) - see totalOf()'s javadoc. Recomputing counts
    // synchronously in the event handler can therefore observe stale per-server state; deferring
    // the recompute by one short tick lets that continuation run first.
    //
    // This leans on that ordering as an observed implementation detail, not a documented Velocity
    // guarantee - if a future Velocity version changes it, counts could go stale again with no
    // compile-time or test-time signal. If that's ever suspected, check this first.
    private static final Duration EVENT_SETTLE_DELAY = Duration.ofMillis(50);

    private final OnlinePlayersPlugin plugin;
    private final ProxyServer server;
    private final Path dataDirectory;
    private final Logger logger;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path outputFile;
    private final Object writeLock = new Object();
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
        scheduleWriteIfChanged();
    }

    @Subscribe
    public void onDisconnect(final DisconnectEvent event) {
        scheduleWriteIfChanged();
    }

    private void scheduleWriteIfChanged() {
        server.getScheduler().buildTask(plugin, this::writeIfChanged).delay(EVENT_SETTLE_DELAY).schedule();
    }

    // Velocity dispatches scheduled tasks onto the plugin's cached thread pool, not a single
    // event-loop thread, so a burst of connect/disconnect events can run their (delayed)
    // writeIfChanged() calls concurrently with no ordering guarantee between them. Since each
    // recomputes the snapshot from live state at execution time rather than carrying data
    // captured earlier, serializing the read-compare-write sequence under writeLock is enough to
    // guarantee the last write to land reflects the most recently observed state, instead of an
    // earlier-reading thread's write racing a later one and clobbering it with stale counts.
    private void writeIfChanged() {
        synchronized (writeLock) {
            final Map<String, Integer> servers = currentServerCounts();
            final int total = totalOf(servers);

            final PlayerCountFile previous = lastWritten.get();
            if (previous != null && previous.sameCounts(total, servers)) {
                return;
            }

            logger.info("Refreshing {}: total={}, servers={}", outputFile, total, servers);
            write(servers);
        }
    }

    // Rewrites unconditionally, even if the counts haven't changed, so `updated` keeps advancing
    // during quiet periods - see HEARTBEAT_INTERVAL.
    private void heartbeat() {
        synchronized (writeLock) {
            write(currentServerCounts());
        }
    }

    // Callers hold writeLock.
    private void write(final Map<String, Integer> servers) {
        final PlayerCountFile snapshot = PlayerCountFile.now(totalOf(servers), servers);
        // Only record the snapshot as delivered if the write actually landed - otherwise a
        // failed write would suppress the next identical-looking writeIfChanged() call via
        // sameCounts() above, leaving stale JSON on disk until the next heartbeat.
        if (writeAtomic(outputFile, gson.toJson(snapshot))) {
            lastWritten.set(snapshot);
        }
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

    // Derived from the same per-server snapshot rather than ProxyServer.getPlayerCount(), which
    // is backed by a separate proxy-wide registry populated earlier in the connection lifecycle
    // than RegisteredServer's own player list. Velocity fires ServerConnectedEvent *before*
    // VelocityRegisteredServer.addPlayer() runs (the latter happens in a continuation chained
    // after the event's future resolves), so at the moment our listener recomputes state,
    // getPlayerCount() can already count a just-connected player while their destination
    // server's connected-players list doesn't yet - deriving total from servers.values() instead
    // keeps the file's own `sum(servers) == total` invariant intact even when the underlying
    // Velocity state is momentarily mid-transition; the per-server figure catches up on the next
    // event or heartbeat tick.
    private static int totalOf(final Map<String, Integer> servers) {
        return servers.values().stream().mapToInt(Integer::intValue).sum();
    }

    // Package-private (not private) so the permissions behavior is directly unit-testable.
    // Returns whether the write landed, so callers can avoid recording a failed write as delivered.
    boolean writeAtomic(final Path target, final String content) {
        Path tmp = null;
        try {
            tmp = Files.createTempFile(dataDirectory, target.getFileName().toString(), ".tmp");
            Files.setPosixFilePermissions(tmp, WORLD_READABLE_PERMISSIONS);
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            tmp = null; // moved successfully - nothing left to clean up
            return true;
        } catch (final IOException e) {
            logger.error("Failed to write {}: {}", target, e.getMessage());
            return false;
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
