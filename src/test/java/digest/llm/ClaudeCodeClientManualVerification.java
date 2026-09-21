package digest.llm;

import io.github.cdimascio.dotenv.Dotenv;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NOT an automated test - deliberately not named "*Test" so Surefire skips it. Run manually
 * (needs dotenv-java on the classpath now, for reading CLAUDE_EXECUTABLE_PATH):
 *
 *   mvn -q dependency:build-classpath -Dmdep.outputFile=/tmp/mb-cp.txt
 *   mvn -q test-compile
 *   java -cp "target/classes:target/test-classes:$(cat /tmp/mb-cp.txt)" digest.llm.ClaudeCodeClientManualVerification
 *
 * Makes two real Claude Code calls against the machine's cached login (billed/metered like any
 * other Claude Code usage). Exists to answer one question, per AGENTS.md's architectural
 * decision #1: can a cron-triggered subprocess (no TTY, minimal environment) still read the
 * cached auth? Run A is the happy-path baseline; Run B simulates what real cron actually gives
 * a job - a stripped PATH, no TTY, only HOME preserved.
 *
 * The claude executable path comes from .env's CLAUDE_EXECUTABLE_PATH - this dev machine and the
 * homelab machine that actually runs the cron job are different installs, so the path must never
 * be hardcoded here. Both runs use the same resolved path, so the only variable between them is
 * the environment (full vs. cron-stripped), not also how the path was found.
 */
public class ClaudeCodeClientManualVerification {

    private static final String PROMPT = "Reply with exactly: PONG";
    private static final String CRON_DEFAULT_PATH = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin";

    public static void main(String[] args) throws Exception {
        String claudePath = Dotenv.configure().load().get("CLAUDE_EXECUTABLE_PATH");
        if (claudePath == null || claudePath.isBlank()) {
            System.err.println("CLAUDE_EXECUTABLE_PATH is not set in .env - see AGENTS.md Secrets section.");
            System.exit(1);
        }

        System.out.println("=== Run A: baseline (inherited environment, TTY-less by default via ProcessBuilder) ===");
        runAndPrint(new ClaudeCodeClient(claudePath));

        System.out.println();
        System.out.println("=== Run B: cron simulation (minimal PATH, no TTY) ===");
        runCronSimulationAndPrint(claudePath);
    }

    private static void runAndPrint(ClaudeCodeClient client) throws Exception {
        ClaudeCodeClient.ClaudeCodeResult result = client.run(PROMPT);
        System.out.println("exit code: " + result.exitCode());
        System.out.println("output:\n" + result.output());
    }

    private static void runCronSimulationAndPrint(String claudePath) throws Exception {
        // Reuse ClaudeCodeClient's own command-building rather than re-deriving the flags here -
        // ClaudeCodeClient.run() doesn't expose custom environment control, which is the only
        // reason this method falls back to raw ProcessBuilder instead of just calling run().
        List<String> command = new ClaudeCodeClient(claudePath).buildCommand(PROMPT);
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        pb.redirectInput(ProcessBuilder.Redirect.from(new java.io.File("/dev/null")));

        Map<String, String> env = new HashMap<>();
        env.put("PATH", CRON_DEFAULT_PATH);
        env.put("HOME", System.getenv("HOME"));
        pb.environment().clear();
        pb.environment().putAll(env);

        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        boolean finished = process.waitFor(120, java.util.concurrent.TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            System.out.println("TIMED OUT after 120s");
            return;
        }
        System.out.println("exit code: " + process.exitValue());
        System.out.println("output:\n" + output);
    }
}
