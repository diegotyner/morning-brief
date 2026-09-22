package digest.aggregate;

import digest.aggregate.SnapshotBuilder.DigestSnapshot;
import digest.aggregate.SnapshotBuilder.LatestSession;
import digest.notion.NotionModels.LongTermPage;
import digest.notion.NotionModels.LongTermProperties;
import digest.notion.NotionModels.MinutesPage;
import digest.notion.NotionModels.MinutesProperties;
import digest.notion.NotionModels.TaskPage;
import digest.notion.NotionModels.TaskProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure in-memory tests - no fixture files needed, since SnapshotBuilder does no JSON parsing,
 * only joins NotionModels objects that are simple to construct directly here.
 */
class SnapshotBuilderTest {

    private static TaskPage task(String id, String name, List<String> parentIds) {
        return new TaskPage(id, new TaskProperties(
            name, Instant.parse("2024-01-01T00:00:00Z"), parentIds, "Not started", "Medium",
            "Do the thing", 60.0, 0.0, "", List.of(), 0.0, 0.0, Instant.parse("2024-01-01T00:00:00Z")
        ));
    }

    private static LongTermPage project(String id, String name) {
        return new LongTermPage(id, new LongTermProperties(
            name, "Description of " + name, "Active", 0.0, null, List.of()
        ));
    }

    private static MinutesPage minutes(String id, LocalDate date, double mins, String notes, List<String> taskIds) {
        return new MinutesPage(id, new MinutesProperties(
            "Session", date, mins, notes, taskIds, Instant.parse("2024-01-01T00:00:00Z")
        ));
    }

    @Test
    void taskWithTwoParentsAppearsUnderBoth() {
        LongTermPage projectA = project("projA", "Project A");
        LongTermPage projectB = project("projB", "Project B");
        TaskPage sharedTask = task("task1", "Shared Task", List.of("projA", "projB"));

        DigestSnapshot snapshot = SnapshotBuilder.build(List.of(projectA, projectB), List.of(sharedTask), List.of());

        assertEquals(2, snapshot.projects().size());
        assertTrue(snapshot.projects().get(0).tasks().stream().anyMatch(t -> t.id().equals("task1")));
        assertTrue(snapshot.projects().get(1).tasks().stream().anyMatch(t -> t.id().equals("task1")));
        assertTrue(snapshot.unassignedTasks().isEmpty());
    }

    @Test
    void taskWithNoParentIsUnassigned() {
        TaskPage orphan = task("task1", "Orphan Task", List.of());

        DigestSnapshot snapshot = SnapshotBuilder.build(List.of(), List.of(orphan), List.of());

        assertEquals(1, snapshot.unassignedTasks().size());
        assertEquals("task1", snapshot.unassignedTasks().get(0).id());
    }

    @Test
    void taskWithDanglingParentIsUnassigned() {
        TaskPage danglingTask = task("task1", "Dangling Task", List.of("does-not-exist"));

        DigestSnapshot snapshot = SnapshotBuilder.build(List.of(), List.of(danglingTask), List.of());

        assertEquals(1, snapshot.unassignedTasks().size());
    }

    @Test
    void latestSessionResolvesToMostRecentDateNotFirstInList() {
        TaskPage t = task("task1", "Task", List.of());
        MinutesPage older = minutes("m1", LocalDate.of(2024, 1, 1), 30, "Older note", List.of("task1"));
        MinutesPage newer = minutes("m2", LocalDate.of(2024, 1, 15), 45, "Newer note", List.of("task1"));

        // deliberately listed with the newer one first, to prove resolution is by date, not list order
        DigestSnapshot snapshot = SnapshotBuilder.build(List.of(), List.of(t), List.of(newer, older));

        LatestSession latest = snapshot.unassignedTasks().get(0).latestSession();
        assertNotNull(latest);
        assertEquals(LocalDate.of(2024, 1, 15), latest.date());
        assertEquals("Newer note", latest.notes());
    }

    @Test
    void taskWithNoMinutesHasNullLatestSession() {
        TaskPage t = task("task1", "Task", List.of());

        DigestSnapshot snapshot = SnapshotBuilder.build(List.of(), List.of(t), List.of());

        assertNull(snapshot.unassignedTasks().get(0).latestSession());
    }

    @Test
    void minutesEntryWithNullDateIsSkippedForLatest() {
        TaskPage t = task("task1", "Task", List.of());
        MinutesPage dated = minutes("m1", LocalDate.of(2024, 1, 1), 30, "Dated note", List.of("task1"));
        MinutesPage undated = minutes("m2", null, 45, "Undated note", List.of("task1"));

        DigestSnapshot snapshot = SnapshotBuilder.build(List.of(), List.of(t), List.of(undated, dated));

        LatestSession latest = snapshot.unassignedTasks().get(0).latestSession();
        assertNotNull(latest);
        assertEquals("Dated note", latest.notes());
    }
}
