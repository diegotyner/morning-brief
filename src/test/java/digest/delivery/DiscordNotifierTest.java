package digest.delivery;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordNotifierTest {

    @Test
    void truncateLeavesShortContentUnchanged() {
        assertEquals("Work on the CI task.", DiscordNotifier.truncate("Work on the CI task."));
    }

    @Test
    void truncateLeavesContentAtExactlyTheLimitUnchanged() {
        String content = "a".repeat(2000);
        assertEquals(content, DiscordNotifier.truncate(content));
    }

    @Test
    void truncateShortensContentOverTheLimit() {
        String content = "a".repeat(2500);

        String truncated = DiscordNotifier.truncate(content);

        assertEquals(2000, truncated.length());
        assertTrue(truncated.endsWith("…"));
    }

    @Test
    void buildPayloadProducesExpectedJson() throws IOException {
        String payload = DiscordNotifier.buildPayload("Work on the CI task.");

        assertEquals("{\"content\":\"Work on the CI task.\"}", payload);
    }

    @Test
    void buildPayloadEscapesQuotesAndNewlinesCorrectly() throws IOException {
        String payload = DiscordNotifier.buildPayload("He said \"hi\"\nnew line");

        // if this were hand-built via string concatenation instead of Jackson, this would produce invalid JSON
        assertEquals("{\"content\":\"He said \\\"hi\\\"\\nnew line\"}", payload);
    }
}
