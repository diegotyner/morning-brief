package digest.log;

import digest.log.DigestLog.LogEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DigestLogTest {

    @TempDir
    Path tempDir;

    @Test
    void writeThenReadRoundTrips() throws IOException {
        Path logPath = tempDir.resolve("digest-log.json");
        LogEntry entry = new LogEntry("2026-09-21", "Work on the CD half of CI/CD.");

        DigestLog.write(logPath, entry);
        Optional<LogEntry> result = DigestLog.read(logPath);

        assertTrue(result.isPresent());
        assertEquals(entry, result.get());
    }

    @Test
    void readReturnsEmptyWhenLogDoesNotExistYet() throws IOException {
        Path logPath = tempDir.resolve("does-not-exist.json");

        assertTrue(DigestLog.read(logPath).isEmpty());
    }
}
