package digest.llm;

import digest.aggregate.SnapshotBuilder.DigestSnapshot;
import digest.aggregate.SnapshotBuilder.LatestSession;
import digest.aggregate.SnapshotBuilder.ProjectSnapshot;
import digest.aggregate.SnapshotBuilder.TaskSnapshot;
import digest.log.DigestLog.LogEntry;

import java.util.List;
import java.util.Optional;

/**
 * Builds the prompt text sent to the `claude -p` subprocess for the ranking step. Plain text,
 * no structured-output request - matches ClaudeCodeClient's --output-format text. First-draft
 * wording, expected to be tuned once we can see real output from an actual run.
 */
public final class PromptBuilder {

    private PromptBuilder() {
    }

    public static String build(DigestSnapshot snapshot, Optional<LogEntry> yesterday) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("""
            You are helping me decide what to work on today, based on my Notion task tracker.
            Below is my current state: long-term projects, their tasks, and any standalone tasks.

            """);

        yesterday.ifPresent(entry -> prompt
            .append("Yesterday (").append(entry.date()).append(") you recommended:\n")
            .append(entry.recommendation()).append("\n")
            .append("Take into account whether that likely got done, based on today's data below.\n\n"));

        for (ProjectSnapshot project : snapshot.projects()) {
            appendProject(prompt, project);
        }

        List<TaskSnapshot> unassigned = snapshot.unassignedTasks();
        if (!unassigned.isEmpty()) {
            prompt.append("## Standalone tasks (not tied to a project)\n");
            for (TaskSnapshot task : unassigned) {
                appendTask(prompt, task);
            }
            prompt.append("\n");
        }

        prompt.append("""
            Based on all of this, what's the single highest-value thing I should work on today?
            Give me a short, direct recommendation with your reasoning.
            """);

        return prompt.toString();
    }

    private static void appendProject(StringBuilder sb, ProjectSnapshot project) {
        sb.append("## Project: ").append(project.name()).append(" (").append(project.status()).append(")\n");
        if (isNonBlank(project.description())) {
            sb.append(project.description()).append("\n");
        }
        for (TaskSnapshot task : project.tasks()) {
            appendTask(sb, task);
        }
        sb.append("\n");
    }

    private static void appendTask(StringBuilder sb, TaskSnapshot task) {
        sb.append("- ").append(task.name()).append(" [").append(task.status()).append("]");
        if (task.priority() != null) {
            sb.append(", priority: ").append(task.priority());
        }
        sb.append("\n");

        if (isNonBlank(task.nextAction())) {
            sb.append("  Next action: ").append(task.nextAction()).append("\n");
        }
        if (isNonBlank(task.blockers())) {
            sb.append("  Blocked by: ").append(task.blockers()).append("\n");
        }

        LatestSession latest = task.latestSession();
        if (latest != null) {
            sb.append("  Last worked on ").append(latest.date())
                .append(" (").append(formatMinutes(latest.minutes())).append("): ")
                .append(latest.notes()).append("\n");
        }
    }

    private static boolean isNonBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String formatMinutes(Double minutes) {
        return minutes == null ? "?" : minutes.intValue() + " min";
    }
}
