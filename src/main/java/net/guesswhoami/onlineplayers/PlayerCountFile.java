package net.guesswhoami.onlineplayers;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Snapshot written to {@code online-players.json} for external readers (scripts, website).
 *
 * <p>A plain mutable class, not a record — historically because the Gson version this project
 * appeared to target predated record support, but the actually-bundled version (2.14.0, see the
 * {@code gson} dependency comment in {@code build.gradle.kts}) does support records, so that
 * reasoning no longer applies. Left as a plain class for now as a style choice, not a technical
 * constraint; converting to a record would be a reasonable follow-up.
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
