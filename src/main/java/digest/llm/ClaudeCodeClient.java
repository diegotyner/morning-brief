package digest.llm;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Subprocess wrapper around headless Claude Code (`claude -p <prompt> --output-format text`
 * via ProcessBuilder). Deliberately not an HTTP client / API-key-based caller — authenticates
 * via the cached interactive login already on this machine.
 */
public class ClaudeCodeClient {

    private static final long TIMEOUT_SECONDS = 120;
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
     * Shared subprocess-launching logic for run() and isLoggedIn(). Output (stderr merged into
     * stdout via ProcessBuilder#redirectErrorStream) is redirected straight to a temp file instead
     * of read from a pipe - piped output has a bounded kernel buffer, so reading it can block
     * until the process writes/closes, which defeats waitFor's timeout entirely if the process
     * hangs without closing its output (confirmed as a real bug: the previous pipe-based version
     * read to EOF *before* waitFor(timeout) was ever reached, so a hung process never timed out).
     * Redirecting to a file removes that blocking read from the picture, so waitFor(timeout)
     * genuinely bounds the wait either way. stdin is redirected from /dev/null - without this,
     * claude spends ~3s waiting to see if piped input is coming and prints a warning into the
     * very output we need to parse (confirmed empirically).
     */
    private ClaudeCodeResult launch(List<String> command) throws IOException, InterruptedException {
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
