package digest;

import io.github.cdimascio.dotenv.Dotenv;

import java.util.function.Function;

public class Main {

    private static final String[] REQUIRED_ENV_KEYS = {
        "NOTION_TOKEN",
        "NOTION_LONG_TERM_DB_ID",
        "NOTION_TASKS_DB_ID",
        "NOTION_MINUTES_DB_ID",
        "DISCORD_WEBHOOK_URL",
        "CLAUDE_EXECUTABLE_PATH"
    };

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().load();

        if (!hasRequiredEnv(dotenv::get)) {
            System.err.println("Missing one or more required .env keys - see AGENTS.md Secrets section.");
            System.exit(1);
        }

        System.out.println("Environment OK: all required keys present.");
        // TODO: NotionExtractor -> SnapshotBuilder -> PromptBuilder/ClaudeCodeClient -> DiscordNotifier -> DigestLog
    }

    /**
     * Checks presence of all required keys without ever reading/printing their values.
     * Takes a lookup function (rather than a concrete Dotenv) so this is unit-testable
     * without touching the filesystem or real secrets.
     */
    static boolean hasRequiredEnv(Function<String, String> lookup) {
        for (String key : REQUIRED_ENV_KEYS) {
            String value = lookup.apply(key);
            if (value == null || value.isBlank()) {
                return false;
            }
        }
        return true;
    }
}
