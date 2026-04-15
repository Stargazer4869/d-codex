package org.dean.codex.runtime.springai.context;

import org.dean.codex.core.model.InputTextItem;
import org.dean.codex.core.model.ModelCompactRequest;
import org.dean.codex.core.model.ModelCompactResponse;
import org.dean.codex.core.model.ModelInputRole;
import org.dean.codex.core.model.ModelResponseMetadata;
import org.dean.codex.core.model.ResponsesCompactClient;
import org.dean.codex.protocol.conversation.MessageRole;
import org.dean.codex.protocol.conversation.ThreadId;
import org.dean.codex.protocol.conversation.TurnId;
import org.dean.codex.protocol.history.HistoryMessageItem;
import org.dean.codex.protocol.history.ThreadHistoryItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringAiThreadCompactionSummarizerTest {

    @Test
    void summarizeRoutesCompactionThroughDedicatedCompactClient() {
        AtomicReference<ModelCompactRequest> capturedRequest = new AtomicReference<>();
        ResponsesCompactClient compactClient = request -> {
            capturedRequest.set(request);
            return new ModelCompactResponse(
                    new ModelResponseMetadata("response-1", "session-1", "completed"),
                    "  compacted handoff  ");
        };
        SpringAiThreadCompactionSummarizer summarizer = new SpringAiThreadCompactionSummarizer(compactClient);
        ThreadId threadId = new ThreadId("thread-1");

        String summary = summarizer.summarize(
                threadId,
                List.of(new HistoryMessageItem(new TurnId("turn-1"), MessageRole.USER, "Inspect the repo", Instant.parse("2026-04-01T00:00:00Z"))),
                List.of(new HistoryMessageItem(new TurnId("turn-2"), MessageRole.ASSISTANT, "Tests still fail", Instant.parse("2026-04-01T00:01:00Z"))));

        assertEquals("compacted handoff", summary);
        ModelCompactRequest request = capturedRequest.get();
        assertEquals("thread-1", request.metadata().threadId());
        assertTrue(request.systemInstructions().contains("compact older Codex thread history"));
        InputTextItem inputItem = assertInstanceOf(InputTextItem.class, request.inputItems().get(0));
        assertEquals(ModelInputRole.USER, inputItem.role());
        assertTrue(inputItem.text().contains("Thread id: thread-1"));
        assertTrue(inputItem.text().contains("Older history to summarize:"));
        assertTrue(inputItem.text().contains("Retained recent history that will stay visible:"));
    }
}
