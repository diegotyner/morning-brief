package digest.notion;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Jackson models for the three Notion databases (Long Term, Tasks, Minutes), matching the
 * captured fixtures in src/test/resources/notion-sample-*.json exactly - never guessed.
 *
 * Notion wraps every property value in a verbose, type-tagged envelope (e.g.
 * {"type":"status","status":{"name":"Blocked",...}}). Rather than modeling that envelope
 * 1:1, a handful of small deserializers below each unwrap one Notion property *shape* directly
 * into a plain Java value, reused across all three page schemas wherever that shape appears.
 */
public final class NotionModels {

    private NotionModels() {
    }

    // ---- Envelope ----

    /** The {"object":"list","results":[...],"next_cursor":...,"has_more":...} wrapper Notion's query endpoint returns. */
    record NotionListResponse<T>(
        List<T> results,
        @JsonProperty("next_cursor") String nextCursor,
        @JsonProperty("has_more") boolean hasMore
    ) {
    }

    // ---- Pages ----

    public record LongTermPage(String id, LongTermProperties properties) {
    }

    public record TaskPage(String id, TaskProperties properties) {
    }

    public record MinutesPage(String id, MinutesProperties properties) {
    }

    // ---- Properties (one record per database, fields matching AGENTS.md's documented schema) ----

    public record LongTermProperties(
        @JsonProperty("Name") @JsonDeserialize(using = TextDeserializer.class) String name,
        @JsonProperty("Description") @JsonDeserialize(using = TextDeserializer.class) String description,
        @JsonProperty("Status") @JsonDeserialize(using = NameDeserializer.class) String status,
        @JsonProperty("Total Minutes") @JsonDeserialize(using = NumberDeserializer.class) Double totalMinutes,
        @JsonProperty("Target Date") @JsonDeserialize(using = DateDeserializer.class) LocalDate targetDate,
        @JsonProperty("Child Tasks") @JsonDeserialize(using = RelationDeserializer.class) List<String> childTaskIds
    ) {
    }

    public record TaskProperties(
        @JsonProperty("Name") @JsonDeserialize(using = TextDeserializer.class) String name,
        @JsonProperty("Created") @JsonDeserialize(using = InstantDeserializer.class) Instant created,
        @JsonProperty("Parent") @JsonDeserialize(using = RelationDeserializer.class) List<String> parentIds,
        @JsonProperty("Status") @JsonDeserialize(using = NameDeserializer.class) String status,
        @JsonProperty("Priority") @JsonDeserialize(using = NameDeserializer.class) String priority,
        @JsonProperty("Next Action") @JsonDeserialize(using = TextDeserializer.class) String nextAction,
        @JsonProperty("Estimated Minutes") @JsonDeserialize(using = NumberDeserializer.class) Double estimatedMinutes,
        @JsonProperty("Minutes Frac") @JsonDeserialize(using = NumberDeserializer.class) Double minutesFrac,
        @JsonProperty("Blockers") @JsonDeserialize(using = TextDeserializer.class) String blockers,
        @JsonProperty("Log Minutes") @JsonDeserialize(using = RelationDeserializer.class) List<String> logMinutesIds,
        @JsonProperty("Total Mins") @JsonDeserialize(using = NumberDeserializer.class) Double totalMins,
        @JsonProperty("Sessions") @JsonDeserialize(using = NumberDeserializer.class) Double sessions,
        @JsonProperty("Edited") @JsonDeserialize(using = InstantDeserializer.class) Instant edited
    ) {
    }

    public record MinutesProperties(
        @JsonProperty("Name") @JsonDeserialize(using = TextDeserializer.class) String name,
        @JsonProperty("Date") @JsonDeserialize(using = DateDeserializer.class) LocalDate date,
        @JsonProperty("Minutes") @JsonDeserialize(using = NumberDeserializer.class) Double minutes,
        @JsonProperty("Notes") @JsonDeserialize(using = TextDeserializer.class) String notes,
        @JsonProperty("Task") @JsonDeserialize(using = RelationDeserializer.class) List<String> taskIds,
        @JsonProperty("Last edited time") @JsonDeserialize(using = InstantDeserializer.class) Instant lastEditedTime
    ) {
    }

    // ---- Deserializers, one per Notion property "shape", reused across all three schemas ----

    /** title/rich_text -> concatenated plain text. Empty array becomes "" - never null, since an empty text field genuinely means "nothing entered", not "unknown". */
    static final class TextDeserializer extends JsonDeserializer<String> {
        @Override
        public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            String arrayKey = node.get("type").asText();
            StringBuilder text = new StringBuilder();
            for (JsonNode item : node.path(arrayKey)) {
                text.append(item.path("plain_text").asText(""));
            }
            return text.toString();
        }
    }

    /** status/select -> the chosen option's name. Null if genuinely unset - unlike text, "nothing selected" is a real, distinct state here. */
    static final class NameDeserializer extends JsonDeserializer<String> {
        @Override
        public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            String key = node.get("type").asText();
            JsonNode value = node.path(key);
            return value.isMissingNode() || value.isNull() ? null : value.path("name").asText(null);
        }
    }

    /** number / rollup-of-number / formula-of-number -> the numeric value. One deserializer for all three shapes, since they only differ in nesting depth. Null if unset. */
    static final class NumberDeserializer extends JsonDeserializer<Double> {
        @Override
        public Double deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            String type = node.get("type").asText();
            JsonNode numberNode = switch (type) {
                case "number" -> node.path("number");
                case "rollup" -> node.path("rollup").path("number");
                case "formula" -> node.path("formula").path("number");
                default -> null;
            };
            return numberNode == null || numberNode.isMissingNode() || numberNode.isNull()
                ? null : numberNode.asDouble();
        }
    }

    /** relation -> related page ids. Empty array becomes an empty list - never null, since "related to nothing" is a real, common state, not an error. */
    static final class RelationDeserializer extends JsonDeserializer<List<String>> {
        @Override
        public List<String> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            List<String> ids = new ArrayList<>();
            for (JsonNode item : node.path("relation")) {
                ids.add(item.path("id").asText());
            }
            return List.copyOf(ids);
        }
    }

    /** date -> date.start as a LocalDate. Null if unset. */
    static final class DateDeserializer extends JsonDeserializer<LocalDate> {
        @Override
        public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            JsonNode start = node.path("date").path("start");
            return start.isMissingNode() || start.isNull() ? null : LocalDate.parse(start.asText());
        }
    }

    /** created_time/last_edited_time -> Instant. Generic across both: reads "type", then parses the value stored at that same key. */
    static final class InstantDeserializer extends JsonDeserializer<Instant> {
        @Override
        public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            String key = node.get("type").asText();
            JsonNode value = node.path(key);
            return value.isMissingNode() || value.isNull() ? null : Instant.parse(value.asText());
        }
    }
}
