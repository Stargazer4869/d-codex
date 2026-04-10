package org.dean.codex.runtime.springai.prompt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultToolObservationReducerTest {

    private final DefaultToolObservationReducer reducer = new DefaultToolObservationReducer();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void reducesReadFileObservationsToAnExcerpt() throws Exception {
        String observation = """
                {
                  "success": true,
                  "path": "README.md",
                  "content": "%s",
                  "truncated": false,
                  "totalCharacters": 2000,
                  "error": ""
                }
                """.formatted("x".repeat(2000));

        JsonNode reduced = objectMapper.readTree(reducer.reduce("READ_FILE", observation));

        assertEquals("README.md", reduced.path("path").asText());
        assertEquals(2000, reduced.path("totalCharacters").asInt());
        assertTrue(reduced.path("contentExcerpt").asText().length() <= 512);
        assertTrue(reduced.path("contentExcerptTruncated").asBoolean());
        assertFalse(reduced.has("content"));
    }

    @Test
    void reducesSearchAndWebResultsToBoundedHits() throws Exception {
        String searchObservation = """
                {
                  "success": true,
                  "query": "codex",
                  "scope": "src",
                  "matches": [
                    {"path": "a.java", "lineNumber": 1, "lineContent": "%s"},
                    {"path": "b.java", "lineNumber": 2, "lineContent": "short"}
                  ],
                  "totalMatches": 2,
                  "truncated": false,
                  "error": ""
                }
                """.formatted("y".repeat(500));
        String webObservation = """
                {
                  "success": true,
                  "query": "codex",
                  "backend": "ddg",
                  "hits": [
                    {"rank": 1, "title": "T", "url": "https://example.com", "snippet": "%s"},
                    {"rank": 2, "title": "U", "url": "https://example.org", "snippet": "ok"}
                  ],
                  "totalHits": 2,
                  "truncated": false,
                  "error": ""
                }
                """.formatted("z".repeat(500));

        JsonNode reducedSearch = objectMapper.readTree(reducer.reduce("SEARCH_FILES", searchObservation));
        JsonNode reducedWeb = objectMapper.readTree(reducer.reduce("WEB_SEARCH", webObservation));

        assertEquals(2, reducedSearch.path("matches").size());
        assertTrue(reducedSearch.path("matches").get(0).path("lineContentPreview").asText().length() <= 160);
        assertTrue(reducedSearch.path("matches").get(0).path("lineContentPreviewTruncated").asBoolean());
        assertFalse(reducedSearch.path("matches").get(0).has("lineContent"));
        assertEquals(2, reducedWeb.path("hits").size());
        assertTrue(reducedWeb.path("hits").get(0).path("snippetPreview").asText().length() <= 160);
        assertTrue(reducedWeb.path("hits").get(0).path("snippetPreviewTruncated").asBoolean());
        assertFalse(reducedWeb.path("hits").get(0).has("snippet"));
    }

    @Test
    void reducesCommandOutputsToStatusAndPreviews() throws Exception {
        String observation = """
                {
                  "success": true,
                  "command": "npm test",
                  "sessionId": "session-1",
                  "processId": 12345,
                  "status": "RUNNING",
                  "exitCode": null,
                  "pty": true,
                  "workingDirectory": "/tmp/workspace",
                  "executed": true,
                  "approvalDecision": "ALLOW",
                  "approvalReason": "Allowed",
                  "stdout": "%s",
                  "stderr": "%s",
                  "error": ""
                }
                """.formatted("out".repeat(500), "err".repeat(500));

        JsonNode reduced = objectMapper.readTree(reducer.reduce("EXEC_COMMAND", observation));

        assertEquals("session-1", reduced.path("sessionId").asText());
        assertEquals("RUNNING", reduced.path("status").asText());
        assertTrue(reduced.path("stdoutPreview").asText().length() <= 512);
        assertTrue(reduced.path("stderrPreview").asText().length() <= 512);
        assertTrue(reduced.path("stdoutPreviewTruncated").asBoolean());
        assertTrue(reduced.path("stderrPreviewTruncated").asBoolean());
        assertFalse(reduced.has("stdout"));
        assertFalse(reduced.has("stderr"));
    }
}
