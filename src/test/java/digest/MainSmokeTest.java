package digest;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainSmokeTest {

    @Test
    void hasRequiredEnvAcceptsAllPresent() {
        Map<String, String> fake = Map.of(
            "NOTION_TOKEN", "x",
            "NOTION_LONG_TERM_DB_ID", "x",
            "NOTION_TASKS_DB_ID", "x",
            "NOTION_MINUTES_DB_ID", "x",
            "DISCORD_WEBHOOK_URL", "x",
            "CLAUDE_EXECUTABLE_PATH", "x"
        );
        assertTrue(Main.hasRequiredEnv(fake::get));
    }

    @Test
    void hasRequiredEnvDetectsMissingKey() {
        Map<String, String> fake = Map.of(
            "NOTION_TOKEN", "x",
            "NOTION_LONG_TERM_DB_ID", "x",
            "NOTION_TASKS_DB_ID", "x",
            "NOTION_MINUTES_DB_ID", "x",
            "CLAUDE_EXECUTABLE_PATH", "x"
            // DISCORD_WEBHOOK_URL intentionally missing
        );
        assertFalse(Main.hasRequiredEnv(fake::get));
    }
}
