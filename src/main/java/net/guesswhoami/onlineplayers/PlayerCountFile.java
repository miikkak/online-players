package net.guesswhoami.onlineplayers;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Snapshot written to {@code online-players.json} for external readers (scripts, website).
 *
 * <p>Deliberately a plain mutable class, not a record: Velocity 4.0.0 bundles Gson 2.8.0 (which
 * predates Gson's record support, added in 2.10).
 */
final class PlayerCountFile {

    private int total;
    private Map<String, Integer> servers;
    private String updated;

    PlayerCountFile(final int total, final Map<String, Integer> servers, final String updated) {
        this.total = total;
        this.servers = servers;
        this.updated = updated;
    }

    static PlayerCountFile now(final int total, final Map<String, Integer> servers) {
        return new PlayerCountFile(total, servers, Instant.now().toString());
    }

    int total() {
        return total;
    }

    Map<String, Integer> servers() {
        return servers;
    }

    String updated() {
        return updated;
    }

    // Deliberately ignores `updated` - used to decide whether the counts actually changed, so a
    // recompute that lands on the same numbers doesn't churn the file (and its mtime) needlessly.
    boolean sameCounts(final int otherTotal, final Map<String, Integer> otherServers) {
        return total == otherTotal && Objects.equals(servers, otherServers);
    }
}
