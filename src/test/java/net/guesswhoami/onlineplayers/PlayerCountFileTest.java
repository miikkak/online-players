package net.guesswhoami.onlineplayers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlayerCountFileTest {

    private final Gson gson = new Gson();

    @Test
    void roundTripsThroughJsonWithServers() {
        final PlayerCountFile snapshot = PlayerCountFile.now(8, Map.of("lobby", 5, "survival", 3));

        final PlayerCountFile parsed = gson.fromJson(gson.toJson(snapshot), PlayerCountFile.class);

        assertEquals(snapshot.total(), parsed.total());
        assertEquals(snapshot.servers(), parsed.servers());
        assertTrue(parsed.updated().length() > 0);
    }

    @Test
    void sameCountsIgnoresTimestamp() {
        final PlayerCountFile snapshot = PlayerCountFile.now(8, Map.of("lobby", 5, "survival", 3));

        assertTrue(snapshot.sameCounts(8, Map.of("lobby", 5, "survival", 3)));
    }

    @Test
    void sameCountsDetectsTotalChange() {
        final PlayerCountFile snapshot = PlayerCountFile.now(8, Map.of("lobby", 5, "survival", 3));

        assertFalse(snapshot.sameCounts(9, Map.of("lobby", 5, "survival", 3)));
    }

    @Test
    void sameCountsDetectsPerServerChange() {
        final PlayerCountFile snapshot = PlayerCountFile.now(8, Map.of("lobby", 5, "survival", 3));

        assertFalse(snapshot.sameCounts(8, Map.of("lobby", 4, "survival", 4)));
    }
}
