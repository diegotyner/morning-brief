package digest.llm;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Subprocess wrapper around headless Claude Code (`claude -p <prompt> --output-format text`
 * via ProcessBuilder). Deliberately not an HTTP client / API-key-based caller — authenticates
 * via the cached interactive login already on this machine.
 */
public class ClaudeCodeClient {

    private static final long TIMEOUT_SECONDS = 120;

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
     * stderr is merged into the same stream as stdout (ProcessBuilder#redirectErrorStream) so a
     * single blocking read can't deadlock against a full, unread second pipe. stdin is redirected
     * from /dev/null - without this, claude -p spends ~3s waiting to see if piped input is
     * coming and prints a warning into the very output we need to parse (confirmed empirically).
     * No retry/backoff - one attempt, one fixed timeout.
     */
    public ClaudeCodeResult run(String prompt) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(buildCommand(prompt))
            .redirectErrorStream(true)
            .redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")))
            .start();

        String output = readAll(process.getInputStream());

        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("Claude Code process timed out after " + TIMEOUT_SECONDS + "s");
        }

        return new ClaudeCodeResult(process.exitValue(), output);
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        in.transferTo(buffer);
        return buffer.toString();
    }

    public record ClaudeCodeResult(int exitCode, String output) {
    }
}
