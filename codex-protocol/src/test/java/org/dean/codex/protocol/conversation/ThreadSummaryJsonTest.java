package org.dean.codex.protocol.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dean.codex.protocol.agent.AgentStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreadSummaryJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void roundTripsExtendedThreadMetadata() throws Exception {
        ThreadSummary summary = new ThreadSummary(
                new ThreadId("thread-1"),
                "Demo thread",
                Instant.parse("2026-04-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:01:00Z"),
                2,
                "Inspect the transport layer changes",
                "Inspect the transport layer changes",
                "workspace-write",
                "review-sensitive",
                "1234567890abcdef",
                "main",
                "git@github.com:org/repo.git",
                "1.0-SNAPSHOT",
                false,
                "openai",
                "gpt-5.4",
                ThreadStatus.ACTIVE,
                List.of(ThreadActiveFlag.WAITING_ON_APPROVAL),
                "/tmp/.d-codex/threads/thread-1",
                "/Users/chenzhu/Git/d-codex",
                ThreadSource.SUB_AGENT,
                true,
                Instant.parse("2026-04-01T00:02:00Z"),
                "worker-1",
                "worker",
                "root/worker-1",
                new ThreadId("thread-parent"),
                2,
                AgentStatus.RUNNING,
                Instant.parse("2026-04-01T00:03:00Z"),
                new ThreadPromptStateSummary(
                        Instant.parse("2026-04-01T00:04:00Z"),
                        new ThreadId("thread-parent"),
                        2),
                new ThreadModelSessionSummary(
                        Instant.parse("2026-04-01T00:05:00Z"),
                        new ThreadId("thread-parent"),
                        new ThreadId("thread-root"),
                        new ThreadId("thread-parent"),
                        "root/worker-1",
                        2,
                        "response-123",
                        "session-456",
                        new TurnId("turn-7")));

        String json = objectMapper.writeValueAsString(summary);
        ThreadSummary restored = objectMapper.readValue(json, ThreadSummary.class);

        assertEquals(summary, restored);
        assertTrue(restored.loaded());
        assertTrue(restored.archived());
        assertEquals("Inspect the transport layer changes", restored.firstUserInput());
        assertEquals("workspace-write", restored.sandboxMode());
        assertEquals("review-sensitive", restored.approvalMode());
        assertEquals("1234567890abcdef", restored.gitSha());
        assertEquals("main", restored.gitBranch());
        assertEquals("git@github.com:org/repo.git", restored.gitOriginUrl());
        assertEquals("1.0-SNAPSHOT", restored.cliVersion());
        assertEquals(new ThreadId("thread-parent"), restored.parentThreadId());
        assertEquals(Integer.valueOf(2), restored.agentDepth());
        assertEquals(AgentStatus.RUNNING, restored.agentStatus());
        assertEquals(Instant.parse("2026-04-01T00:03:00Z"), restored.agentClosedAt());
        assertNotNull(restored.promptState());
        assertEquals(new ThreadId("thread-parent"), restored.promptState().inheritedFromThreadId());
        assertEquals(2, restored.promptState().userInstructionSectionCount());
        assertNotNull(restored.modelSessionState());
        assertEquals(new ThreadId("thread-root"), restored.modelSessionState().rootThreadId());
        assertEquals("response-123", restored.modelSessionState().responseId());
        assertEquals("session-456", restored.modelSessionState().sessionId());
    }

    @Test
    void legacyConstructorAndPayloadStillWork() throws Exception {
        ThreadSummary legacyConstructed = new ThreadSummary(
                new ThreadId("thread-legacy"),
                "Legacy thread",
                Instant.parse("2026-04-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:01:00Z"),
                1,
                "Legacy preview",
                null,
                null,
                null,
                false,
                "openai",
                "gpt-5.4",
                ThreadStatus.IDLE,
                List.of(),
                "/tmp/threads/thread-legacy",
                "/Users/chenzhu/Git/d-codex",
                ThreadSource.CLI,
                true,
                null,
                "worker-1",
                "worker",
                "root/worker-1");

        assertEquals(new ThreadId("thread-legacy"), legacyConstructed.threadId());
        assertNull(legacyConstructed.parentThreadId());
        assertNull(legacyConstructed.agentDepth());
        assertNull(legacyConstructed.agentStatus());
        assertNull(legacyConstructed.agentClosedAt());
        assertNull(legacyConstructed.promptState());
        assertNull(legacyConstructed.modelSessionState());
        assertNull(legacyConstructed.sandboxMode());
        assertNull(legacyConstructed.approvalMode());
        assertNull(legacyConstructed.gitSha());
        assertNull(legacyConstructed.gitBranch());
        assertNull(legacyConstructed.gitOriginUrl());
        assertNull(legacyConstructed.cliVersion());

        String legacyJson = """
                {
                  "threadId": { "value": "thread-legacy" },
                  "title": "Legacy thread",
                  "createdAt": "2026-04-01T00:00:00Z",
                  "updatedAt": "2026-04-01T00:01:00Z",
                  "turnCount": 1
                }
                """;

        ThreadSummary restored = objectMapper.readValue(legacyJson, ThreadSummary.class);

        assertEquals("Legacy thread", restored.preview());
        assertNull(restored.firstUserInput());
        assertEquals(ThreadStatus.NOT_LOADED, restored.status());
        assertEquals(ThreadSource.UNKNOWN, restored.source());
        assertTrue(restored.materialized());
        assertFalse(restored.loaded());
        assertFalse(restored.archived());
        assertNull(restored.parentThreadId());
        assertNull(restored.agentDepth());
        assertNull(restored.agentStatus());
        assertNull(restored.agentClosedAt());
        assertNull(restored.promptState());
        assertNull(restored.modelSessionState());
    }
}
