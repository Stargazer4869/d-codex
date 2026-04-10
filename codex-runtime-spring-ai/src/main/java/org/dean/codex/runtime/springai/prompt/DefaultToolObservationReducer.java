package org.dean.codex.runtime.springai.prompt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
public class DefaultToolObservationReducer implements ToolObservationReducer {

    private static final int CONTENT_PREVIEW_LIMIT = 512;
    private static final int MATCH_PREVIEW_LIMIT = 160;
    private static final int HITS_LIMIT = 5;
    private static final int ENTRIES_LIMIT = 20;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Override
    public String reduce(String actionName, String observation) {
        if (observation == null || observation.isBlank() || actionName == null || actionName.isBlank()) {
            return observation == null ? "" : observation;
        }

        try {
            JsonNode root = objectMapper.readTree(observation);
            if (!(root instanceof ObjectNode objectNode)) {
                return truncateText(observation, CONTENT_PREVIEW_LIMIT);
            }

            return switch (normalizeActionName(actionName)) {
                case "READ_FILE" -> reduceReadFile(objectNode);
                case "SEARCH_FILES" -> reduceSearchFiles(objectNode);
                case "LIST_DIR" -> reduceListDir(objectNode);
                case "WEB_SEARCH" -> reduceWebSearch(objectNode);
                case "RUN_COMMAND", "EXEC_COMMAND", "WRITE_STDIN" -> reduceCommand(objectNode);
                default -> observation;
            };
        }
        catch (Exception exception) {
            return truncateText(observation, CONTENT_PREVIEW_LIMIT);
        }
    }

    private String normalizeActionName(String actionName) {
        return actionName == null ? "" : actionName.trim().toUpperCase().replace('-', '_');
    }

    private String reduceReadFile(ObjectNode root) throws Exception {
        ObjectNode reduced = objectMapper.createObjectNode();
        copyIfPresent(root, reduced, "success");
        copyIfPresent(root, reduced, "path");
        copyIfPresent(root, reduced, "truncated");
        copyIfPresent(root, reduced, "totalCharacters");
        copyIfPresent(root, reduced, "error");
        String content = textValue(root, "content");
        reduced.put("contentExcerpt", truncateText(content, CONTENT_PREVIEW_LIMIT));
        reduced.put("contentExcerptCharacters", content.length());
        reduced.put("contentExcerptTruncated", content.length() > CONTENT_PREVIEW_LIMIT);
        return objectMapper.writeValueAsString(reduced);
    }

    private String reduceSearchFiles(ObjectNode root) throws Exception {
        ObjectNode reduced = objectMapper.createObjectNode();
        copyIfPresent(root, reduced, "success");
        copyIfPresent(root, reduced, "query");
        copyIfPresent(root, reduced, "scope");
        copyIfPresent(root, reduced, "totalMatches");
        copyIfPresent(root, reduced, "truncated");
        copyIfPresent(root, reduced, "error");
        ArrayNode matches = reduced.putArray("matches");
        JsonNode rawMatches = root.get("matches");
        if (rawMatches != null && rawMatches.isArray()) {
            int added = 0;
            for (JsonNode matchNode : rawMatches) {
                if (added >= HITS_LIMIT) {
                    break;
                }
                ObjectNode reducedMatch = objectMapper.createObjectNode();
                copyIfPresent(matchNode, reducedMatch, "path");
                copyIfPresent(matchNode, reducedMatch, "lineNumber");
                String lineContent = textValue(matchNode, "lineContent");
                reducedMatch.put("lineContentPreview", truncateText(lineContent, MATCH_PREVIEW_LIMIT));
                reducedMatch.put("lineContentPreviewTruncated", lineContent.length() > MATCH_PREVIEW_LIMIT);
                matches.add(reducedMatch);
                added++;
            }
        }
        return objectMapper.writeValueAsString(reduced);
    }

    private String reduceListDir(ObjectNode root) throws Exception {
        ObjectNode reduced = objectMapper.createObjectNode();
        copyIfPresent(root, reduced, "success");
        copyIfPresent(root, reduced, "path");
        copyIfPresent(root, reduced, "maxDepth");
        copyIfPresent(root, reduced, "totalEntries");
        copyIfPresent(root, reduced, "truncated");
        copyIfPresent(root, reduced, "error");
        ArrayNode entries = reduced.putArray("entries");
        JsonNode rawEntries = root.get("entries");
        if (rawEntries != null && rawEntries.isArray()) {
            int added = 0;
            for (JsonNode entryNode : rawEntries) {
                if (added >= ENTRIES_LIMIT) {
                    break;
                }
                ObjectNode reducedEntry = objectMapper.createObjectNode();
                copyIfPresent(entryNode, reducedEntry, "path");
                copyIfPresent(entryNode, reducedEntry, "directory");
                copyIfPresent(entryNode, reducedEntry, "depth");
                entries.add(reducedEntry);
                added++;
            }
        }
        return objectMapper.writeValueAsString(reduced);
    }

    private String reduceWebSearch(ObjectNode root) throws Exception {
        ObjectNode reduced = objectMapper.createObjectNode();
        copyIfPresent(root, reduced, "success");
        copyIfPresent(root, reduced, "query");
        copyIfPresent(root, reduced, "backend");
        copyIfPresent(root, reduced, "totalHits");
        copyIfPresent(root, reduced, "truncated");
        copyIfPresent(root, reduced, "error");
        ArrayNode hits = reduced.putArray("hits");
        JsonNode rawHits = root.get("hits");
        if (rawHits != null && rawHits.isArray()) {
            int added = 0;
            for (JsonNode hitNode : rawHits) {
                if (added >= HITS_LIMIT) {
                    break;
                }
                ObjectNode reducedHit = objectMapper.createObjectNode();
                copyIfPresent(hitNode, reducedHit, "rank");
                copyIfPresent(hitNode, reducedHit, "title");
                copyIfPresent(hitNode, reducedHit, "url");
                String snippet = textValue(hitNode, "snippet");
                reducedHit.put("snippetPreview", truncateText(snippet, MATCH_PREVIEW_LIMIT));
                reducedHit.put("snippetPreviewTruncated", snippet.length() > MATCH_PREVIEW_LIMIT);
                hits.add(reducedHit);
                added++;
            }
        }
        return objectMapper.writeValueAsString(reduced);
    }

    private String reduceCommand(ObjectNode root) throws Exception {
        ObjectNode reduced = objectMapper.createObjectNode();
        copyIfPresent(root, reduced, "success");
        copyIfPresent(root, reduced, "command");
        copyIfPresent(root, reduced, "sessionId");
        copyIfPresent(root, reduced, "processId");
        copyIfPresent(root, reduced, "status");
        copyIfPresent(root, reduced, "exitCode");
        copyIfPresent(root, reduced, "pty");
        copyIfPresent(root, reduced, "workingDirectory");
        copyIfPresent(root, reduced, "executed");
        copyIfPresent(root, reduced, "approvalDecision");
        copyIfPresent(root, reduced, "approvalReason");
        copyIfPresent(root, reduced, "timedOut");
        copyIfPresent(root, reduced, "error");
        String stdout = textValue(root, "stdout");
        String stderr = textValue(root, "stderr");
        reduced.put("stdoutPreview", truncateText(stdout, CONTENT_PREVIEW_LIMIT));
        reduced.put("stdoutCharacters", stdout.length());
        reduced.put("stdoutPreviewTruncated", stdout.length() > CONTENT_PREVIEW_LIMIT);
        reduced.put("stderrPreview", truncateText(stderr, CONTENT_PREVIEW_LIMIT));
        reduced.put("stderrCharacters", stderr.length());
        reduced.put("stderrPreviewTruncated", stderr.length() > CONTENT_PREVIEW_LIMIT);
        return objectMapper.writeValueAsString(reduced);
    }

    private void copyIfPresent(JsonNode source, ObjectNode target, String fieldName) {
        JsonNode node = source.get(fieldName);
        if (node != null && !node.isNull()) {
            target.set(fieldName, node);
        }
    }

    private String textValue(JsonNode source, String fieldName) {
        JsonNode node = source.get(fieldName);
        return node == null || node.isNull() ? "" : node.asText("");
    }

    private String truncateText(String value, int limit) {
        if (value == null || value.isBlank() || limit <= 0) {
            return value == null ? "" : value;
        }
        if (value.length() <= limit) {
            return value;
        }
        return value.substring(0, Math.max(0, limit - 3)) + "...";
    }
}
