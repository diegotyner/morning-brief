package digest.llm;

import digest.aggregate.SnapshotBuilder.DigestSnapshot;
import digest.aggregate.SnapshotBuilder.LatestSession;
import digest.aggregate.SnapshotBuilder.ProjectSnapshot;
import digest.aggregate.SnapshotBuilder.TaskSnapshot;
import digest.log.DigestLog.LogEntry;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptBuilderTest {

    private static TaskSnapshot task(String name, String status, String priority, String nextAction,
                                      String blockers, LatestSession latestSession) {
        return new TaskSnapshot(
            "id-" + name, name, status, priority, nextAction, blockers, 60.0, 0.0, 0.0, 0.0, latestSession
        );
    }

    private static ProjectSnapshot project(String name, String status, String description, List<TaskSnapshot> tasks) {
        return new ProjectSnapshot("id-" + name, name, description, status, 0.0, null, tasks);
    }

    @Test
    void includesProjectAndTaskDetails() {
        TaskSnapshot t = task("Add CI", "Blocked", "High", "Move to Docker containers", null, null);
        ProjectSnapshot p = project("CI/CD", "Active", "Moving to VM deployments", List.of(t));
        DigestSnapshot snapshot = new DigestSnapshot(List.of(p), List.of());

        String prompt = PromptBuilder.build(snapshot, Optional.empty());

        assertTrue(prompt.contains("CI/CD"));
        assertTrue(prompt.contains("Moving to VM deployments"));
        assertTrue(prompt.contains("Add CI"));
        assertTrue(prompt.contains("High"));
        assertTrue(prompt.contains("Move to Docker containers"));
    }

    @Test
    void includesLatestSessionWhenPresent() {
        LatestSession session = new LatestSession(LocalDate.of(2026, 9, 20), 45.0, "Worked on the test suite");
        TaskSnapshot t = task("Write tests", "Not started", null, null, null, session);
        DigestSnapshot snapshot = new DigestSnapshot(List.of(), List.of(t));

        String prompt = PromptBuilder.build(snapshot, Optional.empty());

        assertTrue(prompt.contains("2026-09-20"));
        assertTrue(prompt.contains("45 min"));
        assertTrue(prompt.contains("Worked on the test suite"));
    }

    @Test
    void includesYesterdaysRecommendationWhenPresent() {
        DigestSnapshot snapshot = new DigestSnapshot(List.of(), List.of());
        LogEntry yesterday = new LogEntry("2026-09-20", "Finish the CI pipeline docker migration.");

        String prompt = PromptBuilder.build(snapshot, Optional.of(yesterday));

        assertTrue(prompt.contains("2026-09-20"));
        assertTrue(prompt.contains("Finish the CI pipeline docker migration."));
    }

    @Test
    void omitsYesterdaySectionWhenAbsent() {
        DigestSnapshot snapshot = new DigestSnapshot(List.of(), List.of());

        String prompt = PromptBuilder.build(snapshot, Optional.empty());

        assertFalse(prompt.contains("Yesterday"));
    }

    @Test
    void putsOrphanTasksUnderStandaloneSection() {
        TaskSnapshot orphan = task("Bump email", "Not started", "High", null, null, null);
        DigestSnapshot snapshot = new DigestSnapshot(List.of(), List.of(orphan));

        String prompt = PromptBuilder.build(snapshot, Optional.empty());

        assertTrue(prompt.contains("Standalone tasks"));
        assertTrue(prompt.contains("Bump email"));
    }

    @Test
    void endsWithARecommendationRequest() {
        DigestSnapshot snapshot = new DigestSnapshot(List.of(), List.of());

        String prompt = PromptBuilder.build(snapshot, Optional.empty());

        assertTrue(prompt.contains("highest-value thing I should work on today"));
    }

    @Test
    void doesNotPrintLiteralNullForUnsetFields() {
        TaskSnapshot t = task("Bare task", "Not started", null, null, null, null);
        DigestSnapshot snapshot = new DigestSnapshot(List.of(), List.of(t));

        String prompt = PromptBuilder.build(snapshot, Optional.empty());

        assertFalse(prompt.contains("null"));
    }
}
