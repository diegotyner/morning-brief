package digest;

import digest.aggregate.SnapshotBuilder;
import digest.aggregate.SnapshotBuilder.DigestSnapshot;
import digest.delivery.DiscordNotifier;
import digest.llm.ClaudeCodeClient;
import digest.llm.ClaudeCodeClient.ClaudeCodeResult;
import digest.llm.PromptBuilder;
import digest.log.DigestLog;
import digest.log.DigestLog.LogEntry;
import digest.notion.NotionExtractor;
import digest.notion.NotionModels.LongTermPage;
import digest.notion.NotionModels.MinutesPage;
import digest.notion.NotionModels.TaskPage;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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

    private static final Path DIGEST_LOG_PATH = Path.of("digest-log.json");

    public static void main(String[] args) {
        boolean dryRun = args.length > 0 && args[0].equals("--dry-run");

        Dotenv dotenv = Dotenv.configure().load();

        if (!hasRequiredEnv(dotenv::get)) {
            System.err.println("Missing one or more required .env keys - see AGENTS.md Secrets section.");
            System.exit(1);
        }

        try {
            run(dotenv, dryRun);
        } catch (IOException | InterruptedException e) {
            System.err.println("Digest run failed: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void run(Dotenv dotenv, boolean dryRun) throws IOException, InterruptedException {
        ClaudeCodeClient claude = new ClaudeCodeClient(dotenv.get("CLAUDE_EXECUTABLE_PATH"));

        if (!claude.isLoggedIn()) {
            String alert = "Claude Code is not logged in on this machine - no digest was generated. "
                + "Run `claude auth login` (or `/login`) to fix this.";
            deliver(dryRun, dotenv, alert);
            System.err.println(alert);
            System.exit(1);
            return;
        }

        Optional<LogEntry> yesterday = DigestLog.read(DIGEST_LOG_PATH);

        List<LongTermPage> longTerm;
        List<TaskPage> tasks;
        List<MinutesPage> minutes;

        if (dryRun) {
            longTerm = NotionExtractor.parseLongTerm(readFixture("notion-sample-long-term.json"));
            tasks = NotionExtractor.parseTasks(readFixture("notion-sample-tasks.json"));
            minutes = NotionExtractor.parseMinutes(readFixture("notion-sample-minutes.json"));
        } else {
            NotionExtractor extractor = new NotionExtractor(dotenv.get("NOTION_TOKEN"));
            longTerm = extractor.fetchLongTerm(dotenv.get("NOTION_LONG_TERM_DB_ID"));
            tasks = extractor.fetchTasks(dotenv.get("NOTION_TASKS_DB_ID"));
            minutes = extractor.fetchMinutes(dotenv.get("NOTION_MINUTES_DB_ID"));
        }

        DigestSnapshot snapshot = SnapshotBuilder.build(longTerm, tasks, minutes);
        String prompt = PromptBuilder.build(snapshot, yesterday);

        ClaudeCodeResult result = claude.run(prompt);
        String digest = result.output();

        deliver(dryRun, dotenv, digest);
        DigestLog.write(DIGEST_LOG_PATH, new LogEntry(LocalDate.now().toString(), digest));

        System.out.println(dryRun ? "Digest complete (dry run)." : "Digest complete and posted to Discord.");
    }

    /** dry-run prints instead of posting - used for both the normal digest and the auth-failure alert, so dry-run never touches the real webhook either way. */
    private static void deliver(boolean dryRun, Dotenv dotenv, String content) throws IOException, InterruptedException {
        if (dryRun) {
            System.out.println("=== DRY RUN: message that would be posted to Discord ===");
            System.out.println(content);
        } else {
            DiscordNotifier.send(dotenv.get("DISCORD_WEBHOOK_URL"), content);
        }
    }

    /** Dry-run only: reads the checked-in sanitized fixtures directly off the project's source tree. */
    private static String readFixture(String name) throws IOException {
        return Files.readString(Path.of("src/test/resources", name));
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
