package digest.notion;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import digest.notion.NotionModels.LongTermPage;
import digest.notion.NotionModels.MinutesPage;
import digest.notion.NotionModels.NotionListResponse;
import digest.notion.NotionModels.TaskPage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches raw data from Notion (Long Term, Tasks, Minutes databases) via the Notion API, and
 * parses it into NotionModels types. Fetching (fetchX) and parsing (parseX) are deliberately
 * separate: parseX is pure and is what NotionExtractorTest exercises against the real captured
 * fixtures - no live Notion call is ever made in the automated suite. fetchX contains the real
 * HTTP call, written against documented Notion API conventions but not yet empirically verified
 * against live Notion (that verification needs your explicit go-ahead per AGENTS.md's Behavior
 * rules, and is a separate, later roadmap item).
 */
public class NotionExtractor {

    private static final String NOTION_VERSION = "2022-06-28";
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final String notionToken;
    private final HttpClient httpClient;

    public NotionExtractor(String notionToken) {
        this(notionToken, HttpClient.newHttpClient());
    }

    NotionExtractor(String notionToken, HttpClient httpClient) {
        this.notionToken = notionToken;
        this.httpClient = httpClient;
    }

    public List<LongTermPage> fetchLongTerm(String databaseId) throws IOException, InterruptedException {
        return fetchAllPages(databaseId, new TypeReference<NotionListResponse<LongTermPage>>() {
        });
    }

    public List<TaskPage> fetchTasks(String databaseId) throws IOException, InterruptedException {
        return fetchAllPages(databaseId, new TypeReference<NotionListResponse<TaskPage>>() {
        });
    }

    public List<MinutesPage> fetchMinutes(String databaseId) throws IOException, InterruptedException {
        return fetchAllPages(databaseId, new TypeReference<NotionListResponse<MinutesPage>>() {
        });
    }

    public static List<LongTermPage> parseLongTerm(String json) throws IOException {
        return parseResults(json, new TypeReference<NotionListResponse<LongTermPage>>() {
        });
    }

    public static List<TaskPage> parseTasks(String json) throws IOException {
        return parseResults(json, new TypeReference<NotionListResponse<TaskPage>>() {
        });
    }

    public static List<MinutesPage> parseMinutes(String json) throws IOException {
        return parseResults(json, new TypeReference<NotionListResponse<MinutesPage>>() {
        });
    }

    private static <T> List<T> parseResults(String json, TypeReference<NotionListResponse<T>> type) throws IOException {
        return MAPPER.readValue(json, type).results();
    }

    /**
     * Queries a database, following next_cursor/has_more until exhausted - Notion caps a single
     * response at 100 rows regardless of database size, so a single un-paginated call would
     * silently truncate any database larger than that.
     */
    private <T> List<T> fetchAllPages(String databaseId, TypeReference<NotionListResponse<T>> type)
        throws IOException, InterruptedException {
        List<T> all = new ArrayList<>();
        String cursor = null;
        boolean hasMore = true;

        while (hasMore) {
            String body = cursor == null ? "{}" : "{\"start_cursor\":\"" + cursor + "\"}";
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.notion.com/v1/databases/" + databaseId + "/query"))
                .header("Authorization", "Bearer " + notionToken)
                .header("Notion-Version", NOTION_VERSION)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("Notion API request failed: HTTP " + response.statusCode() + " - " + response.body());
            }

            NotionListResponse<T> page = MAPPER.readValue(response.body(), type);
            all.addAll(page.results());
            hasMore = page.hasMore();
            cursor = page.nextCursor();
        }

        return all;
    }
}
