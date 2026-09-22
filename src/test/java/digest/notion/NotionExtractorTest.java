package digest.notion;

import digest.notion.NotionModels.LongTermPage;
import digest.notion.NotionModels.MinutesPage;
import digest.notion.NotionModels.TaskPage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises NotionExtractor.parseX against the real captured (sanitized) fixtures - no live
 * Notion call is made here or anywhere in the automated suite.
 */
class NotionExtractorTest {

    @Test
    void parsesLongTermFixture() throws IOException {
        List<LongTermPage> pages = NotionExtractor.parseLongTerm(readFixture("notion-sample-long-term.json"));

        assertEquals(7, pages.size());

        LongTermPage ciCd = pages.get(0);
        assertEquals("00000000-0000-4000-8000-000000000001", ciCd.id());
        assertEquals("Placeholder Name 1", ciCd.properties().name());
        assertEquals("Placeholder Description 1", ciCd.properties().description());
        assertEquals("Not started", ciCd.properties().status());
        assertEquals(0.0, ciCd.properties().totalMinutes());
        assertNull(ciCd.properties().targetDate());
        assertEquals(List.of("00000000-0000-4000-8000-000000000008"), ciCd.properties().childTaskIds());
    }

    @Test
    void parsesTasksFixture() throws IOException {
        List<TaskPage> pages = NotionExtractor.parseTasks(readFixture("notion-sample-tasks.json"));

        assertEquals(7, pages.size());

        TaskPage blockedTask = pages.get(0);
        assertEquals("Placeholder Name 8", blockedTask.properties().name());
        assertEquals("Blocked", blockedTask.properties().status());
        assertEquals("High", blockedTask.properties().priority());
        assertEquals("Placeholder Next Action 1", blockedTask.properties().nextAction());
        assertEquals(180.0, blockedTask.properties().estimatedMinutes());
        assertEquals("", blockedTask.properties().blockers());
        assertEquals(
            List.of("00000000-0000-4000-8000-000000000021", "00000000-0000-4000-8000-000000000001"),
            blockedTask.properties().parentIds()
        );
        assertTrue(blockedTask.properties().logMinutesIds().isEmpty());
    }

    @Test
    void parsesMinutesFixture() throws IOException {
        List<MinutesPage> pages = NotionExtractor.parseMinutes(readFixture("notion-sample-minutes.json"));

        assertEquals(1, pages.size());

        MinutesPage session = pages.get(0);
        assertEquals("Placeholder Name 15", session.properties().name());
        assertEquals(LocalDate.of(2024, 1, 21), session.properties().date());
        assertEquals(90.0, session.properties().minutes());
        assertEquals("Placeholder Notes 1", session.properties().notes());
        assertEquals(List.of("00000000-0000-4000-8000-000000000054"), session.properties().taskIds());
    }

    private static String readFixture(String name) throws IOException {
        try (InputStream in = NotionExtractorTest.class.getResourceAsStream("/" + name)) {
            if (in == null) {
                throw new IOException("Fixture not found on classpath: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
