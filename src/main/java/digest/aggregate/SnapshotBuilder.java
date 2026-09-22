package digest.aggregate;

import digest.notion.NotionModels.LongTermPage;
import digest.notion.NotionModels.MinutesPage;
import digest.notion.NotionModels.TaskPage;
import digest.notion.NotionModels.TaskProperties;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Joins the three already-fetched Notion sources into one aggregated snapshot: resolves each
 * task's parent project(s) and its latest logged session, in plain Java rather than a fragile
 * Notion formula chain (see AGENTS.md architectural decision #2). No I/O, no filtering/ranking -
 * that's the LLM step's job at PromptBuilder; this layer's only responsibility is the join.
 */
public final class SnapshotBuilder {

    private SnapshotBuilder() {
    }

    public record LatestSession(LocalDate date, Double minutes, String notes) {
    }

    public record TaskSnapshot(
        String id,
        String name,
        String status,
        String priority,
        String nextAction,
        String blockers,
        Double estimatedMinutes,
        Double minutesFrac,
        Double totalMins,
        Double sessions,
        LatestSession latestSession
    ) {
    }

    public record ProjectSnapshot(
        String id,
        String name,
        String description,
        String status,
        Double totalMinutes,
        LocalDate targetDate,
        List<TaskSnapshot> tasks
    ) {
    }

    public record DigestSnapshot(List<ProjectSnapshot> projects, List<TaskSnapshot> unassignedTasks) {
    }

    public static DigestSnapshot build(List<LongTermPage> projects, List<TaskPage> tasks, List<MinutesPage> minutes) {
        Map<String, List<MinutesPage>> minutesByTaskId = groupMinutesByTaskId(minutes);
        Set<String> knownProjectIds = new HashSet<>();
        for (LongTermPage project : projects) {
            knownProjectIds.add(project.id());
        }

        Map<String, List<TaskSnapshot>> tasksByProjectId = new HashMap<>();
        List<TaskSnapshot> unassignedTasks = new ArrayList<>();

        for (TaskPage task : tasks) {
            TaskSnapshot snapshot = toTaskSnapshot(task, minutesByTaskId);
            List<String> validParentIds = new ArrayList<>();
            for (String parentId : task.properties().parentIds()) {
                if (knownProjectIds.contains(parentId)) {
                    validParentIds.add(parentId);
                }
            }
            if (validParentIds.isEmpty()) {
                unassignedTasks.add(snapshot);
            } else {
                for (String parentId : validParentIds) {
                    tasksByProjectId.computeIfAbsent(parentId, id -> new ArrayList<>()).add(snapshot);
                }
            }
        }

        List<ProjectSnapshot> projectSnapshots = new ArrayList<>();
        for (LongTermPage project : projects) {
            projectSnapshots.add(toProjectSnapshot(project, tasksByProjectId.getOrDefault(project.id(), List.of())));
        }

        return new DigestSnapshot(List.copyOf(projectSnapshots), List.copyOf(unassignedTasks));
    }

    private static Map<String, List<MinutesPage>> groupMinutesByTaskId(List<MinutesPage> minutes) {
        Map<String, List<MinutesPage>> byTaskId = new HashMap<>();
        for (MinutesPage entry : minutes) {
            for (String taskId : entry.properties().taskIds()) {
                byTaskId.computeIfAbsent(taskId, id -> new ArrayList<>()).add(entry);
            }
        }
        return byTaskId;
    }

    /** Most recent dated entry for a task; entries with no date can't be ordered, so they're skipped. Null if the task has no dated sessions at all. */
    private static LatestSession resolveLatestSession(List<MinutesPage> sessions) {
        if (sessions == null) {
            return null;
        }
        MinutesPage latest = null;
        for (MinutesPage session : sessions) {
            LocalDate date = session.properties().date();
            if (date == null) {
                continue;
            }
            if (latest == null || date.isAfter(latest.properties().date())) {
                latest = session;
            }
        }
        return latest == null ? null : new LatestSession(
            latest.properties().date(),
            latest.properties().minutes(),
            latest.properties().notes()
        );
    }

    private static TaskSnapshot toTaskSnapshot(TaskPage task, Map<String, List<MinutesPage>> minutesByTaskId) {
        TaskProperties p = task.properties();
        return new TaskSnapshot(
            task.id(),
            p.name(),
            p.status(),
            p.priority(),
            p.nextAction(),
            p.blockers(),
            p.estimatedMinutes(),
            p.minutesFrac(),
            p.totalMins(),
            p.sessions(),
            resolveLatestSession(minutesByTaskId.get(task.id()))
        );
    }

    private static ProjectSnapshot toProjectSnapshot(LongTermPage project, List<TaskSnapshot> tasks) {
        return new ProjectSnapshot(
            project.id(),
            project.properties().name(),
            project.properties().description(),
            project.properties().status(),
            project.properties().totalMinutes(),
            project.properties().targetDate(),
            tasks
        );
    }
}
