package digest.llm;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Subprocess wrapper around headless Claude Code (`claude -p <prompt> --output-format text`
 * via ProcessBuilder). Deliberately not an HTTP client / API-key-based caller — authenticates
 * via the cached interactive login already on this machine.
 */
public class ClaudeCodeClient {

    private static final long TIMEOUT_SECONDS = 120;
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(60); // matches the error's own "retry in a minute"
    private static final String TRANSIENT_OAUTH_REFRESH_ERROR = "Failed to refresh OAuth token";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String executablePath;

    public ClaudeCodeClient(String executablePath) {
        this.executablePath = executablePath;
    }

    /**
     * Builds the command to invoke, as a list (never a shell string) so no quoting/injection
     * concerns arise from the prompt text.
     */
    List<String> buildCommand(String prompt) {
        return List.of(executablePath, "-p", "--output-format", "text", prompt);
    }

    /**
     * Runs the prompt through headless Claude Code and returns its exit code and output.
     * No retry/backoff - one attempt, one fixed timeout.
     */
    public ClaudeCodeResult run(String prompt) throws IOException, InterruptedException {
        return launch(buildCommand(prompt));
    }

    /**
     * Checks whether this machine's cached Claude Code login is still valid, via `claude auth
     * status --json` - a stable, purpose-built check (confirmed empirically: gives the same
     * loggedIn:false signature whether credentials are missing entirely or present but invalid,
     * so callers don't need to distinguish those cases). Cheaper and more robust than pattern
     * -matching on a real prompt call's error text, which could change wording across CLI versions.
     */
    public boolean isLoggedIn() throws IOException, InterruptedException {
        ClaudeCodeResult result = launch(buildAuthStatusCommand());
        return parseLoggedIn(result.output());
    }

    /** Exposed for the manual verification harness, same reason buildCommand(prompt) is - lets it reuse the exact real command against a deliberately broken environment, instead of re-deriving the flags by hand. */
    List<String> buildAuthStatusCommand() {
        return List.of(executablePath, "auth", "status", "--json");
    }

    static boolean parseLoggedIn(String statusJson) throws IOException {
        return MAPPER.readTree(statusJson).path("loggedIn").asBoolean(false);
    }

    /**
     * Retry wrapper around launchOnce(), specifically for the one known-transient failure this
     * project has actually observed in production (an OAuth refresh lock race under cron) - not a
     * general retry-on-any-failure policy. Every other failure (including a genuinely broken
     * login) returns on the first attempt, unchanged.
     */
    private ClaudeCodeResult launch(List<String> command) throws IOException, InterruptedException {
        ClaudeCodeResult result = launchOnce(command);
        for (int attempt = 2; attempt <= MAX_ATTEMPTS && isRetryableFailure(result); attempt++) {
            System.err.println("Claude Code hit a transient OAuth refresh error, retrying (attempt " + attempt + "/" + MAX_ATTEMPTS + ")...");
            Thread.sleep(RETRY_DELAY.toMillis());
            result = launchOnce(command);
        }
        return result;
    }

    /**
     * Pure and package-private specifically so this is unit-testable without a real process -
     * matches the exact error text captured from a real production failure, not a guess. Text-only
     * (not gated on exit code), since we've never actually confirmed what exit code this specific
     * error produces - gating on an unverified assumption risks a retry that silently never fires.
     */
    static boolean isRetryableFailure(ClaudeCodeResult result) {
        return result.output().contains(TRANSIENT_OAUTH_REFRESH_ERROR);
    }

    /**
     * Whether a result should be treated as a failed invocation overall - not just during the
     * retry loop. Exit code alone isn't trustworthy: confirmed empirically in production that
     * `claude -p` can exit 0 while its entire output is the OAuth-refresh error text (it slipped
     * past a plain exitCode() != 0 check and got posted/logged as a real digest). So this also
     * falls back to the same known-bad-text match retry already uses, rather than teaching callers
     * a new fact about the subprocess - they just ask "did this fail?"
     */
    static boolean isFailure(ClaudeCodeResult result) {
        return result.exitCode() != 0 || isRetryableFailure(result);
    }

    /**
     * One subprocess invocation. Output (stderr merged into stdout via
     * ProcessBuilder#redirectErrorStream) is redirected straight to a temp file instead of read
     * from a pipe - piped output has a bounded kernel buffer, so reading it can block until the
     * process writes/closes, which defeats waitFor's timeout entirely if the process hangs without
     * closing its output (confirmed as a real bug: the previous pipe-based version read to EOF
     * *before* waitFor(timeout) was ever reached, so a hung process never timed out). Redirecting
     * to a file removes that blocking read from the picture, so waitFor(timeout) genuinely bounds
     * the wait either way. stdin is redirected from /dev/null - without this, claude spends ~3s
     * waiting to see if piped input is coming and prints a warning into the very output we need to
     * parse (confirmed empirically).
     */
    private ClaudeCodeResult launchOnce(List<String> command) throws IOException, InterruptedException {
        File outputFile = File.createTempFile("claude-code-output-", ".txt");
        try {
            Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")))
                .redirectOutput(outputFile)
                .start();

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("Claude Code process timed out after " + TIMEOUT_SECONDS + "s");
            }

            return new ClaudeCodeResult(process.exitValue(), Files.readString(outputFile.toPath()));
        } finally {
            outputFile.delete();
        }
    }

    public record ClaudeCodeResult(int exitCode, String output) {
    }
}
