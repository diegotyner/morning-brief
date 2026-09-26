package digest.llm;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaudeCodeClientTest {

    @Test
    void buildCommandUsesPrintAndTextOutputFormat() {
        ClaudeCodeClient client = new ClaudeCodeClient("/usr/local/bin/claude");

        List<String> command = client.buildCommand("What should I work on today?");

        assertEquals(
            List.of("/usr/local/bin/claude", "-p", "--output-format", "text", "What should I work on today?"),
            command
        );
    }

    @Test
    void buildCommandKeepsPromptAsOneArgument() {
        ClaudeCodeClient client = new ClaudeCodeClient("claude");

        List<String> command = client.buildCommand("multi word prompt; with $shell chars");

        // The prompt must survive as a single list element - no shell is involved, so no
        // quoting/escaping is needed or should be applied.
        assertEquals("multi word prompt; with $shell chars", command.get(command.size() - 1));
        assertEquals(5, command.size());
    }

    // Both shapes below are the real `claude auth status --json` output, captured empirically
    // (logged-in state, and a fake HOME with no/corrupted credentials - both collapse to the
    // same loggedIn:false shape, so there's only one "not logged in" case to test).

    @Test
    void parseLoggedInDetectsLoggedInState() throws IOException {
        String json = """
            {
              "loggedIn": true,
              "authMethod": "claude.ai",
              "apiProvider": "firstParty"
            }
            """;

        assertTrue(ClaudeCodeClient.parseLoggedIn(json));
    }

    @Test
    void parseLoggedInDetectsNotLoggedInState() throws IOException {
        String json = """
            {
              "loggedIn": false,
              "authMethod": "none",
              "apiProvider": "firstParty"
            }
            """;

        assertFalse(ClaudeCodeClient.parseLoggedIn(json));
    }
}
