package digest.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * Posts the finished digest to Discord via the webhook URL. `send` is real - not exercised by
 * the automated suite, since a live call uses the .env webhook URL directly and is covered by
 * AGENTS.md's "never make live calls to Discord... ask first" rule.
 */
public final class DiscordNotifier {

    private static final int MAX_CONTENT_LENGTH = 2000; // Discord's hard limit on webhook content
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DiscordNotifier() {
    }

    public static void send(String webhookUrl, String content) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(webhookUrl))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(buildPayload(content)))
            .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 && response.statusCode() != 204) {
            throw new IOException("Discord webhook request failed: HTTP " + response.statusCode() + " - " + response.body());
        }
    }

    static String truncate(String content) {
        return content.length() <= MAX_CONTENT_LENGTH
            ? content
            : content.substring(0, MAX_CONTENT_LENGTH - 1) + "…";
    }

    static String buildPayload(String content) throws IOException {
        return MAPPER.writeValueAsString(Map.of("content", truncate(content)));
    }
}
