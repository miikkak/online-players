package net.guesswhoami.onlineplayers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

class PlayerCountServiceTest {

    @Test
    void writeAtomicProducesGroupAndWorldReadableFile(@TempDir final Path dataDirectory) throws IOException {
        final PlayerCountService service =
                new PlayerCountService(new Object(), null, dataDirectory, NOPLogger.NOP_LOGGER);

        final Path target = dataDirectory.resolve("online-players.json");
        service.writeAtomic(target, "{}");

        final Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(target);
        assertTrue(permissions.contains(PosixFilePermission.GROUP_READ), "expected group-readable: " + permissions);
        assertTrue(permissions.contains(PosixFilePermission.OTHERS_READ), "expected world-readable: " + permissions);
        assertEquals("{}", Files.readString(target));
    }

    @Test
    void writeAtomicLeavesNoTempFileBehindOnSuccess(@TempDir final Path dataDirectory) throws IOException {
        final PlayerCountService service =
                new PlayerCountService(new Object(), null, dataDirectory, NOPLogger.NOP_LOGGER);

        service.writeAtomic(dataDirectory.resolve("online-players.json"), "{}");

        try (var files = Files.list(dataDirectory)) {
            final long tmpFiles = files.filter(p -> p.getFileName().toString().endsWith(".tmp")).count();
            assertEquals(0, tmpFiles, "expected no leftover .tmp files");
        }
    }
}
