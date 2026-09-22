package digest.log;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Holds exactly one entry: what the most recent digest recommended, so the next prompt can ask
 * "you were told to do X - did it happen?" (AGENTS.md architectural decision #3). Not an
 * accumulating history - each write replaces the previous entry.
 */
public final class DigestLog {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DigestLog() {
    }

    /** date as a plain ISO string ("2026-09-21"), not LocalDate - avoids needing jackson-datatype-jsr310 for one flat field. Callers can LocalDate.parse(date()) if needed. */
    public record LogEntry(String date, String recommendation) {
    }

    /** Empty if the log doesn't exist yet (first-ever run). */
    public static Optional<LogEntry> read(Path path) throws IOException {
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        return Optional.of(MAPPER.readValue(path.toFile(), LogEntry.class));
    }

    public static void write(Path path, LogEntry entry) throws IOException {
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), entry);
    }
}
