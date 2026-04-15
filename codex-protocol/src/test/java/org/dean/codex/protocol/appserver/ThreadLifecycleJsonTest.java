package org.dean.codex.protocol.appserver;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dean.codex.protocol.context.ReconstructedThreadContext;
import org.dean.codex.protocol.context.ReconstructedReplayItem;
import org.dean.codex.protocol.context.ThreadMemory;
import org.dean.codex.protocol.conversation.ThreadSummary;
import org.dean.codex.protocol.conversation.ThreadId;
import org.dean.codex.protocol.conversation.ThreadStatus;
import org.dean.codex.protocol.conversation.TurnId;
import org.dean.codex.protocol.conversation.TurnStatus;
import org.dean.codex.protocol.conversation.ThreadSource;
import org.dean.codex.protocol.conversation.ItemId;
import org.dean.codex.protocol.item.ReasoningItem;
import org.dean.codex.protocol.item.RawModelOutputItem;
import org.dean.codex.protocol.runtime.RuntimeTurn;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ThreadLifecycleJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void roundTripsThreadLifecycleRequestsAndResponses() throws Exception {
        ThreadListParams listParams = new ThreadListParams(
                "cursor-1",
                25,
                ThreadSortKey.UPDATED_AT,
                List.of("openai"),
                List.of(ThreadSourceKind.CLI, ThreadSourceKind.SUB_AGENT),
                Boolean.TRUE,
                "/Users/chenzhu/Git/play-with-ai",
                "thread",
                List.of("workspace-write"),
                List.of("review-sensitive"),
                List.of(ThreadStatus.ACTIVE, ThreadStatus.IDLE),
                new ThreadId("thread-parent"));
        assertEquals(listParams, objectMapper.readValue(objectMapper.writeValueAsString(listParams), ThreadListParams.class));

        ThreadReadParams readParams = new ThreadReadParams(new ThreadId("thread-1"), true);
        assertEquals(readParams, objectMapper.readValue(objectMapper.writeValueAsString(readParams), ThreadReadParams.class));

        CommandExecParams commandExecParams = new CommandExecParams(
                new ThreadId("thread-1"),
                "printf 'hello'",
                "/Users/chenzhu/Git/play-with-ai",
                1_000L,
                10_000L,
                Boolean.TRUE);
        assertEquals(commandExecParams, objectMapper.readValue(objectMapper.writeValueAsString(commandExecParams), CommandExecParams.class));

        CommandExecWriteParams commandExecWriteParams = new CommandExecWriteParams(
                new ThreadId("thread-1"),
                "session-1",
                "\n",
                250L);
        assertEquals(commandExecWriteParams, objectMapper.readValue(objectMapper.writeValueAsString(commandExecWriteParams), CommandExecWriteParams.class));

        CommandExecResizeParams commandExecResizeParams = new CommandExecResizeParams(new ThreadId("thread-1"), "session-1", 120, 40);
        assertEquals(commandExecResizeParams, objectMapper.readValue(objectMapper.writeValueAsString(commandExecResizeParams), CommandExecResizeParams.class));

        CommandExecTerminateParams commandExecTerminateParams = new CommandExecTerminateParams(new ThreadId("thread-1"), "session-1");
        assertEquals(commandExecTerminateParams, objectMapper.readValue(objectMapper.writeValueAsString(commandExecTerminateParams), CommandExecTerminateParams.class));

        TurnStartParams multimodalTurnStartParams = new TurnStartParams(
                new ThreadId("thread-1"),
                "",
                List.of(
                        new TurnTextInputItem("Inspect this image"),
                        new TurnImageInputItem("file:///tmp/screenshot.png", "high")));
        assertEquals(multimodalTurnStartParams,
                objectMapper.readValue(objectMapper.writeValueAsString(multimodalTurnStartParams), TurnStartParams.class));
        assertEquals("[Image] file:///tmp/screenshot.png (detail=high)",
                new TurnStartParams(new ThreadId("thread-1"), "", List.of(new TurnImageInputItem("file:///tmp/screenshot.png", "high"))).inputSummary());

        ThreadStartParams startParams = new ThreadStartParams("App thread", "workspace-write", "review-sensitive");
        assertEquals(startParams, objectMapper.readValue(objectMapper.writeValueAsString(startParams), ThreadStartParams.class));

        ThreadForkParams forkParams = new ThreadForkParams(
                new ThreadId("thread-1"),
                "Forked thread",
                Boolean.FALSE,
                "/tmp/worktree",
                "openai",
                "gpt-5.4",
                "read-only",
                "auto",
                ThreadSource.APP_SERVER,
                "worker-1",
                "worker",
                "root/worker-1");
        assertEquals(forkParams, objectMapper.readValue(objectMapper.writeValueAsString(forkParams), ThreadForkParams.class));

        ThreadMetadataUpdateParams metadataUpdateParams = new ThreadMetadataUpdateParams(
                new ThreadId("thread-1"),
                "/workspace/app",
                "openai",
                "gpt-5.4",
                "workspace-write",
                "review-sensitive",
                "1234567890abcdef",
                "main",
                "git@github.com:org/repo.git",
                "1.0-SNAPSHOT");
        assertEquals(metadataUpdateParams, objectMapper.readValue(objectMapper.writeValueAsString(metadataUpdateParams), ThreadMetadataUpdateParams.class));

        ThreadArchiveParams archiveParams = new ThreadArchiveParams(new ThreadId("thread-1"));
        assertEquals(archiveParams, objectMapper.readValue(objectMapper.writeValueAsString(archiveParams), ThreadArchiveParams.class));

        ThreadUnarchiveParams unarchiveParams = new ThreadUnarchiveParams(new ThreadId("thread-1"));
        assertEquals(unarchiveParams, objectMapper.readValue(objectMapper.writeValueAsString(unarchiveParams), ThreadUnarchiveParams.class));

        ThreadRollbackParams rollbackParams = new ThreadRollbackParams(new ThreadId("thread-1"), 2);
        assertEquals(rollbackParams, objectMapper.readValue(objectMapper.writeValueAsString(rollbackParams), ThreadRollbackParams.class));

        ThreadRollbackResponse rollbackResponse = new ThreadRollbackResponse(
                new ThreadSummary(
                        new ThreadId("thread-1"),
                        "Demo thread",
                        Instant.parse("2026-04-01T00:00:00Z"),
                        Instant.parse("2026-04-01T00:00:05Z"),
                        2),
                List.of(),
                List.of(new ReconstructedReplayItem(
                        new TurnId("turn-1"),
                        "collaboration",
                        "collabToolCall: spawn_agent completed",
                        org.dean.codex.protocol.item.CollabDeliveryState.DISPATCHED,
                        "agent-1 pending=0 seq=2",
                        "mailbox_updated",
                        Instant.parse("2026-04-01T00:00:07Z"))));
        assertEquals(rollbackResponse, objectMapper.readValue(objectMapper.writeValueAsString(rollbackResponse), ThreadRollbackResponse.class));

        ThreadLoadedListParams loadedListParams = new ThreadLoadedListParams("cursor-2", 50);
        assertEquals(loadedListParams, objectMapper.readValue(objectMapper.writeValueAsString(loadedListParams), ThreadLoadedListParams.class));

        ThreadLoadedListResponse loadedListResponse = new ThreadLoadedListResponse(List.of(new ThreadId("thread-1")), "next-cursor");
        assertEquals(loadedListResponse, objectMapper.readValue(objectMapper.writeValueAsString(loadedListResponse), ThreadLoadedListResponse.class));

        ThreadResumeResponse resumeResponse = new ThreadResumeResponse(
                new ThreadSummary(
                        new ThreadId("thread-1"),
                        "Demo thread",
                        Instant.parse("2026-04-01T00:00:00Z"),
                        Instant.parse("2026-04-01T00:00:05Z"),
                        2),
                List.of(new ReconstructedReplayItem(
                        new TurnId("turn-1"),
                        "collaboration",
                        "collabToolCall: spawn_agent completed",
                        org.dean.codex.protocol.item.CollabDeliveryState.DISPATCHED,
                        "agent-1 pending=0 seq=2",
                        "mailbox_updated",
                        Instant.parse("2026-04-01T00:00:07Z"))),
                List.of(new BackgroundTerminalSummary(
                        "terminal-1",
                        12345L,
                        "sleep 60 &",
                        "/workspace/thread-1",
                        Instant.parse("2026-04-01T00:00:08Z"))));
        assertEquals(resumeResponse, objectMapper.readValue(objectMapper.writeValueAsString(resumeResponse), ThreadResumeResponse.class));

        CommandExecutionEvent commandExecutionEvent = new CommandExecutionEvent(
                "session-1",
                new ThreadId("thread-1"),
                "printf 'hello'",
                "/Users/chenzhu/Git/play-with-ai",
                12345L,
                "RUNNING",
                Instant.parse("2026-04-01T00:00:03Z"),
                null,
                null);
        CommandExecResponse commandExecResponse = new CommandExecResponse(commandExecutionEvent, "hello", "", "");
        assertEquals(commandExecResponse, objectMapper.readValue(objectMapper.writeValueAsString(commandExecResponse), CommandExecResponse.class));

        CommandExecResizeResponse commandExecResizeResponse = new CommandExecResizeResponse(commandExecutionEvent, false);
        assertEquals(commandExecResizeResponse, objectMapper.readValue(objectMapper.writeValueAsString(commandExecResizeResponse), CommandExecResizeResponse.class));

        CommandExecTerminateResponse commandExecTerminateResponse = new CommandExecTerminateResponse(commandExecutionEvent, true);
        assertEquals(commandExecTerminateResponse, objectMapper.readValue(objectMapper.writeValueAsString(commandExecTerminateResponse), CommandExecTerminateResponse.class));

        CommandExecutionTerminalInteractionNotification terminalInteractionNotification =
                new CommandExecutionTerminalInteractionNotification(
                        commandExecutionEvent,
                        "resize",
                        null,
                        120,
                        40);
        assertEquals(terminalInteractionNotification,
                objectMapper.readValue(
                        objectMapper.writeValueAsString(terminalInteractionNotification),
                        CommandExecutionTerminalInteractionNotification.class));

        TurnItemNotification turnItemNotification = new TurnItemNotification(
                new RuntimeTurn(
                        new ThreadId("thread-1"),
                        new TurnId("turn-1"),
                        TurnStatus.RUNNING,
                        Instant.parse("2026-04-01T00:00:03Z"),
                        null),
                new ReasoningItem(
                        new ItemId("reasoning-1"),
                        "Need to inspect README",
                        "The request mentions setup issues.",
                        Instant.parse("2026-04-01T00:00:04Z")));
        assertEquals(turnItemNotification,
                objectMapper.readValue(
                        objectMapper.writeValueAsString(turnItemNotification),
                        TurnItemNotification.class));

        TurnItemNotification rawTurnItemNotification = new TurnItemNotification(
                new RuntimeTurn(
                        new ThreadId("thread-1"),
                        new TurnId("turn-1"),
                        TurnStatus.RUNNING,
                        Instant.parse("2026-04-01T00:00:03Z"),
                        null),
                new RawModelOutputItem(
                        new ItemId("raw-1"),
                        "reasoning",
                        "resp-item-1",
                        "stream-1",
                        1,
                        "thread-1",
                        "turn-1",
                        2,
                        "response-1",
                        "session-1",
                        "completed",
                        "{\"id\":\"resp-item-1\",\"summary\":\"Need to inspect README\"}",
                        Instant.parse("2026-04-01T00:00:04Z")));
        assertEquals(rawTurnItemNotification,
                objectMapper.readValue(
                        objectMapper.writeValueAsString(rawTurnItemNotification),
                        TurnItemNotification.class));

        ThreadListResponse listResponse = new ThreadListResponse(
                List.of(new ThreadSummary(
                        new ThreadId("thread-1"),
                        "Demo thread",
                        Instant.parse("2026-04-01T00:00:00Z"),
                        Instant.parse("2026-04-01T00:00:05Z"),
                        2)),
                "cursor-3");
        assertEquals(listResponse, objectMapper.readValue(objectMapper.writeValueAsString(listResponse), ThreadListResponse.class));

        ThreadReadResponse readResponse = new ThreadReadResponse(
                new ThreadSummary(
                        new ThreadId("thread-1"),
                        "Demo thread",
                        Instant.parse("2026-04-01T00:00:00Z"),
                        Instant.parse("2026-04-01T00:00:05Z"),
                        2),
                List.of(),
                new ThreadMemory("memory-1", new ThreadId("thread-1"), "summary", List.of(), 0, Instant.parse("2026-04-01T00:00:06Z")),
                new ReconstructedThreadContext(
                        new ThreadId("thread-1"),
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(new ReconstructedReplayItem(
                                new TurnId("turn-1"),
                                "collaboration",
                                "collabToolCall: spawn_agent completed",
                                org.dean.codex.protocol.item.CollabDeliveryState.DISPATCHED,
                                "agent-1 pending=0 seq=2",
                                "mailbox_updated",
                                Instant.parse("2026-04-01T00:00:07Z"))),
                        Instant.parse("2026-04-01T00:00:07Z")),
                List.of(new ReconstructedReplayItem(
                        new TurnId("turn-1"),
                        "collaboration",
                        "collabToolCall: spawn_agent completed",
                        org.dean.codex.protocol.item.CollabDeliveryState.DISPATCHED,
                        "agent-1 pending=0 seq=2",
                        "mailbox_updated",
                        Instant.parse("2026-04-01T00:00:07Z"))),
                List.of(new BackgroundTerminalSummary(
                        "terminal-1",
                        12345L,
                        "sleep 60 &",
                        "/workspace/thread-1",
                        Instant.parse("2026-04-01T00:00:08Z"))),
                new ThreadId("thread-root"),
                List.of(new ThreadSummary(
                        new ThreadId("thread-child"),
                        "Child thread",
                        Instant.parse("2026-04-01T00:00:01Z"),
                        Instant.parse("2026-04-01T00:00:08Z"),
                        1)));
        assertEquals(readResponse, objectMapper.readValue(objectMapper.writeValueAsString(readResponse), ThreadReadResponse.class));
    }

    @Test
    void deserializesLegacyReadParamsWithDefaultIncludeTurns() throws Exception {
        ThreadReadParams restored = objectMapper.readValue("""
                {
                  "threadId": { "value": "thread-legacy" }
                }
                """, ThreadReadParams.class);

        assertEquals(new ThreadId("thread-legacy"), restored.threadId());
        assertFalse(restored.includeTurns());
    }
}
