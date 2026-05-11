package org.dean.codex.cli;

import org.dean.codex.core.approval.CommandApprovalService;
import org.dean.codex.core.appserver.CodexAppServer;
import org.dean.codex.core.appserver.CodexAppServerSession;
import org.dean.codex.core.conversation.ConversationStore;
import org.dean.codex.core.conversation.InMemoryConversationStore;
import org.dean.codex.protocol.appserver.AppServerNotification;
import org.dean.codex.protocol.appserver.CommandExecutionCompletedNotification;
import org.dean.codex.protocol.appserver.CommandExecutionEvent;
import org.dean.codex.protocol.appserver.CommandExecutionOutputDeltaNotification;
import org.dean.codex.protocol.appserver.CommandExecutionTerminalInteractionNotification;
import org.dean.codex.protocol.appserver.AgentCloseParams;
import org.dean.codex.protocol.appserver.AgentCloseResponse;
import org.dean.codex.protocol.appserver.AgentAssignTaskParams;
import org.dean.codex.protocol.appserver.AgentAssignTaskResponse;
import org.dean.codex.protocol.appserver.AgentListParams;
import org.dean.codex.protocol.appserver.AgentListResponse;
import org.dean.codex.protocol.appserver.AgentResumeParams;
import org.dean.codex.protocol.appserver.AgentResumeResponse;
import org.dean.codex.protocol.appserver.AgentSendInputParams;
import org.dean.codex.protocol.appserver.AgentSendInputResponse;
import org.dean.codex.protocol.appserver.AgentSendMessageParams;
import org.dean.codex.protocol.appserver.AgentSendMessageResponse;
import org.dean.codex.protocol.appserver.AgentSpawnParams;
import org.dean.codex.protocol.appserver.AgentSpawnResponse;
import org.dean.codex.protocol.appserver.AgentWaitParams;
import org.dean.codex.protocol.appserver.AgentWaitResponse;
import org.dean.codex.protocol.appserver.AgentMailboxUpdatedNotification;
import org.dean.codex.protocol.appserver.InitializeParams;
import org.dean.codex.protocol.appserver.InitializeResponse;
import org.dean.codex.protocol.appserver.InitializedNotification;
import org.dean.codex.protocol.appserver.SkillsListParams;
import org.dean.codex.protocol.appserver.SkillsListResponse;
import org.dean.codex.protocol.appserver.ThreadArchiveParams;
import org.dean.codex.protocol.appserver.ThreadArchiveResponse;
import org.dean.codex.protocol.appserver.ThreadBackgroundTerminalsCleanParams;
import org.dean.codex.protocol.appserver.ThreadBackgroundTerminalsCleanResponse;
import org.dean.codex.protocol.appserver.ThreadCompaction;
import org.dean.codex.protocol.appserver.ThreadCompactStartParams;
import org.dean.codex.protocol.appserver.ThreadCompactStartResponse;
import org.dean.codex.protocol.appserver.ThreadCompactionStartedNotification;
import org.dean.codex.protocol.appserver.ThreadCompactedNotification;
import org.dean.codex.protocol.appserver.ThreadForkParams;
import org.dean.codex.protocol.appserver.ThreadForkResponse;
import org.dean.codex.protocol.appserver.ThreadListParams;
import org.dean.codex.protocol.appserver.ThreadListResponse;
import org.dean.codex.protocol.appserver.ThreadLoadedListParams;
import org.dean.codex.protocol.appserver.ThreadLoadedListResponse;
import org.dean.codex.protocol.appserver.ThreadMetadataUpdateParams;
import org.dean.codex.protocol.appserver.ThreadMetadataUpdateResponse;
import org.dean.codex.protocol.appserver.ThreadNameSetParams;
import org.dean.codex.protocol.appserver.ThreadNameSetResponse;
import org.dean.codex.protocol.appserver.ThreadReadParams;
import org.dean.codex.protocol.appserver.ThreadReadResponse;
import org.dean.codex.protocol.appserver.ThreadRollbackParams;
import org.dean.codex.protocol.appserver.ThreadRollbackResponse;
import org.dean.codex.protocol.appserver.ThreadResumeParams;
import org.dean.codex.protocol.appserver.ThreadResumeResponse;
import org.dean.codex.protocol.appserver.ThreadStartParams;
import org.dean.codex.protocol.appserver.ThreadStartResponse;
import org.dean.codex.protocol.appserver.ThreadStartedNotification;
import org.dean.codex.protocol.appserver.ThreadShellCommandParams;
import org.dean.codex.protocol.appserver.ThreadShellCommandResponse;
import org.dean.codex.protocol.appserver.ThreadSourceKind;
import org.dean.codex.protocol.appserver.ThreadUnarchiveParams;
import org.dean.codex.protocol.appserver.ThreadUnarchiveResponse;
import org.dean.codex.protocol.appserver.ThreadUnsubscribeParams;
import org.dean.codex.protocol.appserver.ThreadUnsubscribeResponse;
import org.dean.codex.protocol.appserver.TurnCompletedNotification;
import org.dean.codex.protocol.appserver.TurnStartedNotification;
import org.dean.codex.protocol.appserver.TurnInterruptParams;
import org.dean.codex.protocol.appserver.TurnInterruptResponse;
import org.dean.codex.protocol.appserver.TurnResumeParams;
import org.dean.codex.protocol.appserver.TurnResumeResponse;
import org.dean.codex.protocol.appserver.TurnStartParams;
import org.dean.codex.protocol.appserver.TurnStartResponse;
import org.dean.codex.protocol.appserver.TurnSteerParams;
import org.dean.codex.protocol.appserver.TurnSteerResponse;
import org.dean.codex.protocol.conversation.ConversationTurn;
import org.dean.codex.protocol.conversation.ItemId;
import org.dean.codex.protocol.approval.ApprovalId;
import org.dean.codex.protocol.approval.ApprovalStatus;
import org.dean.codex.protocol.approval.CommandApprovalRequest;
import org.dean.codex.protocol.agent.AgentStatus;
import org.dean.codex.protocol.agent.AgentMailboxState;
import org.dean.codex.protocol.agent.AgentSummary;
import org.dean.codex.protocol.agent.AgentWaitResult;
import org.dean.codex.protocol.agent.AgentSpawnRequest;
import org.dean.codex.protocol.agent.AgentMessage;
import org.dean.codex.protocol.context.ReconstructedThreadContext;
import org.dean.codex.protocol.context.ReconstructedReplayItem;
import org.dean.codex.protocol.context.ThreadMemory;
import org.dean.codex.protocol.conversation.ThreadId;
import org.dean.codex.protocol.conversation.ThreadStatus;
import org.dean.codex.protocol.conversation.ThreadSummary;
import org.dean.codex.protocol.conversation.TurnId;
import org.dean.codex.protocol.conversation.TurnStatus;
import org.dean.codex.protocol.runtime.RuntimeTurn;
import org.dean.codex.protocol.skill.SkillMetadata;
import org.dean.codex.protocol.skill.SkillScope;
import org.dean.codex.protocol.item.ToolCallItem;
import org.dean.codex.protocol.item.ToolResultItem;
import org.dean.codex.protocol.item.CollabToolCallItem;
import org.dean.codex.protocol.item.CollabToolCallStatus;
import org.dean.codex.protocol.item.MailboxDeliveryKind;
import org.dean.codex.protocol.item.MailboxMessageItem;
import org.dean.codex.protocol.item.RawModelOutputItem;
import org.dean.codex.protocol.item.ReasoningItem;
import org.dean.codex.protocol.tool.CommandApprovalDecision;
import org.dean.codex.protocol.tool.ShellCommandResult;
import org.dean.codex.runtime.springai.thread.DefaultThreadCatalogService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodexConsoleRunnerTest {

    @Test
    void createsNewThreadFromConsoleCommand() throws Exception {
        StubAppServer runtime = new StubAppServer();
        CodexConsoleRunner runner = new CodexConsoleRunner(runtime, new StubApprovalService());
        ThreadId originalThread = runner.getActiveThreadIdForTest();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            assertTrue(runner.handleConsoleCommand("/new"));
        }
        finally {
            System.setOut(originalOut);
        }

        assertNotEquals(originalThread, runner.getActiveThreadIdForTest());
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Started new thread"));
    }

    @Test
    void constructorAndHelpDoNotInitializeInteractiveSession() throws Exception {
        StubAppServer runtime = new StubAppServer();
        CodexConsoleRunner runner = new CodexConsoleRunner(runtime, new StubApprovalService());

        assertEquals(0, runtime.connectCount());

        String console = captureConsole(() -> assertTrue(runner.handleConsoleCommand("/help")));

        assertEquals(0, runtime.connectCount());
        assertTrue(console.contains("Interactive commands use /command syntax"));
    }

    @Test
    void topLevelHelpUsesRootParserWithoutInitializingInteractiveSession() throws Exception {
        StubAppServer runtime = new StubAppServer();
        CodexConsoleRunner runner = new CodexConsoleRunner(runtime, new StubApprovalService());

        CapturedRun captured = captureRun(() -> runner.run("--help"), "");

        assertEquals(0, runtime.connectCount());
        assertTrue(captured.stdout().contains("Usage: codex"));
        assertFalse(captured.stdout().contains("Codex CLI. Active thread"));
    }

    @Test
    void listsThreadsFromConsoleCommand() throws Exception {
        StubAppServer runtime = new StubAppServer();
        runtime.setThreadGitMetadata(runtime.rootThreadId(), "1234567890abcdef", "main", "git@github.com:org/thread-one.git");
        runtime.setThreadCliVersion(runtime.rootThreadId(), "1.0-SNAPSHOT");
        CodexConsoleRunner runner = new CodexConsoleRunner(runtime, new StubApprovalService());

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            assertTrue(runner.handleConsoleCommand("/threads"));
        }
        finally {
            System.setOut(originalOut);
        }

        String console = output.toString(StandardCharsets.UTF_8);
        assertTrue(console.contains("Thread 1"));
        assertTrue(console.contains("turns=0"));
        assertTrue(console.contains("git: main"));
        assertTrue(console.contains("12345678"));
        assertTrue(console.contains("thread-one"));
        assertTrue(console.contains("cli=1.0-SNAPSHOT"));
    }

    @Test
    void threadsCommandSupportsSearchCwdStatusSourceAndParentFilters() throws Exception {
        StubAppServer runtime = new StubAppServer();
        CodexConsoleRunner runner = new CodexConsoleRunner(runtime, new StubApprovalService());
        String parentPrefix = shortId(runtime.rootThreadId());
        String cwd = Path.of("").toAbsolutePath().normalize().toString();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            assertTrue(runner.handleConsoleCommand("/threads --search Worker --cwd " + cwd + " --status idle --source unknown --parent " + parentPrefix));
        }
        finally {
            System.setOut(originalOut);
        }

        String console = output.toString(StandardCharsets.UTF_8);
        assertTrue(console.contains("Worker thread"));
        assertTrue(console.contains("sub-agent"));
        assertFalse(console.contains("Thread 1"));
        assertTrue(runtime.threadListCalls().stream().anyMatch(params ->
                params != null
                        && "Worker".equals(params.searchTerm())
                        && cwd.equals(params.cwd())
                        && params.statuses() != null
                        && params.statuses().contains(ThreadStatus.IDLE)
                        && params.sourceKinds() != null
                        && params.sourceKinds().contains(ThreadSourceKind.UNKNOWN)
                        && params.parentThreadId() != null
                        && params.parentThreadId().equals(runtime.rootThreadId())));
    }

    @Test
    void threadsCommandFailsClearlyWhenParentPrefixDoesNotResolve() throws Exception {
        StubAppServer runtime = new StubAppServer();
        CodexConsoleRunner runner = new CodexConsoleRunner(runtime, new StubApprovalService());

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            assertTrue(runner.handleConsoleCommand("/threads --parent no-such-thread"));
        }
        finally {
            System.setOut(originalOut);
        }

        String console = output.toString(StandardCharsets.UTF_8);
        assertTrue(console.contains("No thread matched: no-such-thread"));
    }

    @Test
    void ignoresNonCommandInput() {
        CodexConsoleRunner runner = new CodexConsoleRunner(new StubAppServer(), new StubApprovalService());

        assertFalse(runner.handleConsoleCommand("hello there"));
    }

    @Test
    void listsApprovalsFromConsoleCommand() throws Exception {
        CodexConsoleRunner runner = new CodexConsoleRunner(new StubAppServer(), new StubApprovalService());

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            assertTrue(runner.handleConsoleCommand("/approvals"));
        }
        finally {
            System.setOut(originalOut);
        }

        String console = output.toString(StandardCharsets.UTF_8);
        assertTrue(console.contains("pending command"));
        assertTrue(console.contains("PENDING"));
    }

    @Test
    void listsSkillsFromConsoleCommand() throws Exception {
        CodexConsoleRunner runner = new CodexConsoleRunner(new StubAppServer(), new StubApprovalService());

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            assertTrue(runner.handleConsoleCommand("/skills"));
        }
        finally {
            System.setOut(originalOut);
        }

        String console = output.toString(StandardCharsets.UTF_8);
        assertTrue(console.contains("reviewer"));
        assertTrue(console.contains("mention `$reviewer`"));
    }

    @Test
    void streamedToolActivityIsRenderedClearly() throws Exception {
        StubAppServer runtime = new StubAppServer(true, false, true);
        CodexConsoleRunner runner = new CodexConsoleRunner(runtime, new StubApprovalService());

        CapturedRun captured = captureRun(() -> runner.run(), "inspect repo\n");

        assertTrue(captured.stdout().contains("[collab:inProgress] spawn agent"));
        assertTrue(captured.stdout().contains("[collab:completed] spawn agent"));
        assertTrue(captured.stdout().contains("[collab:completed] wait agent"));
        assertTrue(captured.stdout().contains("wake=mailbox_updated"));
        assertTrue(captured.stdout().contains("mailbox["));
        assertFalse(captured.stdout().contains("[mailbox]"));
        assertTrue(captured.stdout().contains("[tool:start] run command -> ls -la"));
        assertTrue(captured.stdout().contains("[tool:done] run command -> success=true exitCode=0"));
    }

    @Test
    void streamedCommandExecutionNotificationsAreRenderedClearly() throws Exception {
        StubAppServer runtime = new StubAppServer(true, false, false, true, false, true, false);
        CodexConsoleRunner runner = new CodexConsoleRunner(runtime, new StubApprovalService());

        CapturedRun captured = captureRun(() -> runner.run(), "inspect repo\n");

        assertTrue(captured.stdout().contains("[command:stdout] one"));
        assertTrue(captured.stdout().contains("[command:stdout] two"));
        assertTrue(captured.stdout().contains("[command:resize] session=exec-ses size=120x40"));
        assertTrue(captured.stdout().contains("[command:stdin] session=exec-ses chars=6"));
        assertTrue(captured.stdout().contains("[command] completed session=exec-ses"));
        assertTrue(captured.stdout().contains("status=COMPLETED"));
        assertTrue(captured.stdout().contains("exitCode=0"));
    }

    @Test
    void streamedReasoningItemsAreRenderedClearly() throws Exception {
        StubAppServer runtime = new StubAppServer(true, false, false, false, false, true, false, false, true);
        CodexConsoleRunner runner = new CodexConsoleRunner(runtime, new StubApprovalService());

        CapturedRun captured = captureRun(() -> runner.run(), "inspect repo\n");

        assertTrue(captured.stdout().contains("[reasoning] Need to inspect README"));
        assertTrue(captured.stdout().contains("The request mentions setup issues."));
    }

    @Test
    void rawModelOutputItemsAreRenderedClearly() throws Exception {
        StubAppServer runtime = new StubAppServer();
        runtime.emitRawModelOutputActivity = true;
        CodexConsoleRunner runner = new CodexConsoleRunner(runtime, new StubApprovalService());

        CapturedRun captured = captureRun(() -> runner.run(), "inspect repo\n");

        assertTrue(captured.stdout().contains("[model:raw] reasoning id=resp-item-1"));
        assertTrue(captured.stdout().contains("stream=stream-1"));
        assertTrue(captured.stdout().contains("seq=1"));
        assertTrue(captured.stdout().contains("response=response-1"));
        assertTrue(captured.stdout().contains("Need to inspect README"));
    }

    @Test
    void streamedMailboxItemsAreRenderedClearly() throws Exception {
        StubAppServer runtime = new StubAppServer();
        runtime.emitMailboxActivity = true;
        CodexConsoleRunner runner = new CodexConsoleRunner(runtime, new StubApprovalService());

        CapturedRun captured = captureRun(() -> runner.run(), "inspect repo\n");

        assertTrue(captured.stdout().contains("[mailbox] child-completion"));
        assertTrue(captured.stdout().contains("thread-a ->"));
        assertTrue(captured.stdout().contains("README reviewed"));
    }

    @Test
    void historyCommandShowsReplayedCollaborationContextFromReconstruction() throws Exception {
        StubAppServer runtime = new StubAppServer();
        runtime.customThreadReadResponse = new ThreadReadResponse(
                runtime.runtimeSummary(runtime.rootThreadId()),
                List.of(),
                new ThreadMemory(
                        "memory-1",
                        runtime.rootThreadId(),
                        "Compacted earlier thread context.",
                        List.of(),
                        1,
                        Instant.parse("2026-03-31T00:00:00Z")),
                new ReconstructedThreadContext(
                        runtime.rootThreadId(),
                        new ThreadMemory(
                                "memory-1",
                                runtime.rootThreadId(),
                                "Compacted earlier thread context.",
                                List.of(),
                                1,
                                Instant.parse("2026-03-31T00:00:00Z")),
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
                                Instant.parse("2026-03-31T00:00:01Z"))),
                        Instant.parse("2026-03-31T00:00:02Z")),
                runtime.rootThreadId(),
                List.of());
        CodexConsoleRunner runner = new CodexConsoleRunner(runtime, new StubApprovalService());

        CapturedRun captured = captureRun(() -> runner.run(), "/history\n");

        assertTrue(captured.stdout().contains("[replay] reconstructed collaboration context:"));
        assertTrue(captured.stdout().contains("[collaboration] turn=turn-1 collabToolCall: spawn_agent completed"));
        assertTrue(captured.stdout().contains("delivery=dispatched"));
        assertTrue(captured.stdout().contains("mailbox[agent-1 pending=0 seq=2]"));
        assertTrue(captured.stdout().contains("wake=mailbox_updated"));
    }

    @Test
    void plainInputWhileTurnActiveSteersInsteadOfStartingNewTurn() throws Exception {
        StubAppServer runtime = new StubAppServer(true, false, true, false, true, true, false);
        CodexConsoleRunner runner = new CodexConsoleRunner(runtime, new StubApprovalService());

        CapturedRun captured = captureRun(() -> runner.run(), "inspect repo\nfollow up\n");

        assertEquals(1, runtime.turnStartCount());
        assertEquals(1, runtime.turnSteerCount());
        assertTrue(runtime.steeredInputs().contains("follow up"));
        assertTrue(captured.stdout().contains("handled: inspect repo"));
    }

    @Test
    void staleSteerStateFallsBackToStartingANewTurn() throws Exception {
        StubAppServer runtime = new StubAppServer(true, false, false, false, true, false, true);
        CodexConsoleRunner runner = new CodexConsoleRunner(runtime, new StubApprovalService());

        CapturedRun captured = captureRun(() -> runner.run(), "inspect repo\nfollow up\n");

        assertEquals(2, runtime.turnStartCount());
        assertEquals(1, runtime.turnSteerCount());
        assertTrue(captured.stdout().contains("handled: follow up"));
    }

    @Test
    void activeButUnsteerableTurnDoesNotSilentlyStartANewTurn() throws Exception {
        StubAppServer runtime = new StubAppServer(true, false, false, false, true, false, false);
        CodexConsoleRunner runner = new CodexConsoleRunner(runtime, new StubApprovalService());

        CapturedRun captured = captureRun(() -> runner.run(), "inspect repo\nfollow up\n");

        assertEquals(1, runtime.turnStartCount());
        assertEquals(1, runtime.turnSteerCount());
        assertTrue(captured.stdout().contains("Active turn is not steerable yet."));
    }

    @Test
    void helpOutputAdvertisesSlashCommandsAndLegacyAliases() throws Exception {
        CodexConsoleRunner runner = new CodexConsoleRunner(new StubAppServer(), new StubApprovalService());

        String console = captureConsole(() -> assertTrue(runner.handleConsoleCommand("/help")));
        assertTrue(console.contains("Interactive commands use /command syntax"));
        assertTrue(console.contains("/threads [all|loaded|archived]"));
        assertTrue(console.contains("Plain input steers an active regular turn"));
        assertFalse(console.contains("/steer"));
    }

    @Test
    void applicationConfigImportsSharedRuntimeDefaults() throws Exception {
        try (var inputStream = CodexConsoleRunnerTest.class.getResourceAsStream("/application.yml")) {
            assertNotNull(inputStream);
            String config = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(config.contains("import: optional:classpath:codex-runtime-defaults.yml"));
        assertTrue(config.contains("web-application-type: none"));
        assertTrue(config.contains("org.dean.codex: ${CODEX_LOG_LEVEL:WARN}"));
        assertTrue(config.contains("SimpleLoggerAdvisor: OFF"));
        assertTrue(config.contains("show-tool-activity: ${CODEX_SHOW_TOOL_ACTIVITY:true}"));
    }
    }

    @Test
    void approveCommandResumesTurn() throws Exception {
        CodexConsoleRunner runner = new CodexConsoleRunner(new StubAppServer(), new StubApprovalService());

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            assertTrue(runner.handleConsoleCommand("/approve approval-"));
        }
        finally {
            System.setOut(originalOut);
        }

        String console = output.toString(StandardCharsets.UTF_8);
        assertTrue(console.contains("Approved command"));
        assertTrue(console.contains("resumed turn"));
    }

    @Test
    void rejectCommandResumesTurn() throws Exception {
        StubAppServer runtime = new StubAppServer();
        CodexConsoleRunner runner = new CodexConsoleRunner(runtime, new StubApprovalService());

        String console = captureConsole(() -> assertTrue(runner.handleConsoleCommand("/reject approval- not-now")));

        assertTrue(console.contains("Rejected command"));
        assertTrue(console.contains("resumed turn"));
        assertEquals(1, runtime.turnResumeCount());
    }

    @Test
    void approveCommandDoesNotBlockOnTurnCompletion() throws Exception {
        StubAppServer runtime = new StubAppServer(true, false, false, false, false, true, false, true);
        CodexConsoleRunner runner = new CodexConsoleRunner(runtime, new StubApprovalService());

        assertTimeoutPreemptively(Duration.ofMillis(250), () -> {
            String console = captureConsole(() -> assertTrue(runner.handleConsoleCommand("/approve approval-")));
            assertTrue(console.contains("Approved command"));
        });

        assertEquals(1, runtime.turnResumeCount());
    }

    @Test
    void slashCommandsCanRunWhileTurnIsActiveAndInputStillSteers() throws Exception {
        StubAppServer runtime = new StubAppServer(true, false, false, false, true, true, false);
        CodexConsoleRunner runner = new CodexConsoleRunner(runtime, new StubApprovalService());

        CapturedRun captured = captureRun(() -> runner.run(), "inspect repo\n/history\nfollow up\n");

        assertEquals(1, runtime.turnStartCount());
        assertEquals(1, runtime.turnSteerCount());
        assertTrue(runtime.steeredInputs().contains("follow up"));
        assertTrue(captured.stdout().contains("[memory]"));
        assertTrue(captured.stdout().contains("USER: inspect repo"));
    }

    @Test
    void compactCommandPrintsCompactionLifecycleAndCompatibilitySnapshot() throws Exception {
        CodexConsoleRunner runner = new CodexConsoleRunner(new StubAppServer(), new StubApprovalService());

        String console = captureConsole(() -> assertTrue(runner.handleConsoleCommand("/compact")));
        assertTrue(console.contains("[compaction] started"));
        assertTrue(console.contains("[compaction] completed"));
        assertFalse(console.contains("[compaction] response"));
        assertTrue(console.contains("[memory] compatibility snapshot"));
    }

    @Test
    void resumeCommandSwitchesActiveThread() throws Exception {
        StubAppServer runtime = new StubAppServer();
        CodexConsoleRunner runner = new CodexConsoleRunner(runtime, new StubApprovalService());

        String console = captureConsole(() -> assertTrue(runner.handleConsoleCommand("/resume " + shortId(runtime.subagentThreadId()))));

        assertEquals(runtime.subagentThreadId(), runner.getActiveThreadIdForTest());
        assertTrue(console.contains("Switched to thread"));
    }

    @Test
    void forkCommandCreatesThreadAndSwitchesActiveThread() throws Exception {
        StubAppServer runtime = new StubAppServer();
        CodexConsoleRunner runner = new CodexConsoleRunner(runtime, new StubApprovalService());
        ThreadId originalThread = runner.getActiveThreadIdForTest();

        String console = captureConsole(() -> assertTrue(runner.handleConsoleCommand("/fork worker review")));

        assertNotEquals(originalThread, runner.getActiveThreadIdForTest());
        assertTrue(console.contains("Forked thread"));
        assertTrue(console.contains("Switched to thread"));
    }

    @Test
    void archiveCommandArchivesActiveThreadAndSwitchesContext() throws Exception {
        StubAppServer runtime = new StubAppServer();
        CodexConsoleRunner runner = new CodexConsoleRunner(runtime, new StubApprovalService());
        ThreadId originalThread = runner.getActiveThreadIdForTest();

        String console = captureConsole(() -> assertTrue(runner.handleConsoleCommand("/archive")));

        assertNotEquals(originalThread, runner.getActiveThreadIdForTest());
        assertTrue(console.contains("Archived thread"));
        assertTrue(console.contains("Switched to thread"));
    }

    @Test
    void rollbackCommandTrimsThreadHistory() throws Exception {
        StubAppServer runtime = new StubAppServer();
        CodexConsoleRunner runner = new CodexConsoleRunner(runtime, new StubApprovalService());
        runtime.addCompletedTurn(runner.getActiveThreadIdForTest(), "Inspect repo");

        String console = captureConsole(() -> assertTrue(runner.handleConsoleCommand("/rollback 1")));

        assertEquals(0, runtime.turnCount(runner.getActiveThreadIdForTest()));
        assertTrue(console.contains("Rolled back 1 turn(s)"));
        assertTrue(console.contains("Remaining turns=0"));
    }

    @Test
    void subagentsCommandPrintsThreadTree() throws Exception {
        StubAppServer runtime = new StubAppServer();
        CodexConsoleRunner runner = new CodexConsoleRunner(runtime, new StubApprovalService());

        String console = captureConsole(() -> assertTrue(runner.handleConsoleCommand("/subagents")));

        assertTrue(console.contains("Thread tree rooted at"));
        assertTrue(console.contains("Worker thread"));
        assertTrue(console.contains("role=explorer"));
    }

    @Test
    void agentUseCommandSwitchesToSubagent() throws Exception {
        StubAppServer runtime = new StubAppServer();
        CodexConsoleRunner runner = new CodexConsoleRunner(runtime, new StubApprovalService());

        String console = captureConsole(() -> assertTrue(runner.handleConsoleCommand("/agent use " + shortId(runtime.subagentThreadId()))));

        assertEquals(runtime.subagentThreadId(), runner.getActiveThreadIdForTest());
        assertTrue(console.contains("Switched to thread"));
    }

    @Test
    void bareAgentCommandPrintsPickerChoicesWithoutRichTerminal() throws Exception {
        StubAppServer runtime = new StubAppServer();
        CodexConsoleRunner runner = new CodexConsoleRunner(runtime, new StubApprovalService());

        String console = captureConsole(() -> assertTrue(runner.handleConsoleCommand("/agent")));

        assertTrue(console.contains("Agents in current thread tree:"));
        assertTrue(console.contains("Main [default]"));
        assertTrue(console.contains("worker [explorer]"));
        assertTrue(console.contains("Run /agent use <thread-id-prefix> to switch."));
    }

    @Test
    void topLevelResumeCommandSelectsRequestedThreadBeforeInteractiveLoop() throws Exception {
        StubAppServer runtime = new StubAppServer();
        CodexConsoleRunner runner = new CodexConsoleRunner(runtime, new StubApprovalService());

        CapturedRun captured = captureRun(
                () -> runner.run("resume", shortId(runtime.subagentThreadId())),
                "");

        assertEquals(runtime.subagentThreadId(), runner.getActiveThreadIdForTest());
        assertTrue(captured.stdout().contains("Codex CLI. Active thread"));
        assertTrue(captured.stdout().contains(shortId(runtime.subagentThreadId())));
    }

    @Test
    void resumedThreadStartsFreshTurnForFirstPlainPrompt() throws Exception {
        StubAppServer runtime = new StubAppServer();
        runtime.addRunningTurn(runtime.subagentThreadId(), "stale running turn");
        CodexConsoleRunner runner = new CodexConsoleRunner(runtime, new StubApprovalService());

        CapturedRun captured = captureRun(
                () -> runner.run("resume", shortId(runtime.subagentThreadId())),
                "Take a look around, what do you see\nexit\n");

        assertEquals(1, runtime.turnStartCount());
        assertEquals(0, runtime.turnSteerCount());
        assertTrue(captured.stdout().contains("handled: Take a look around, what do you see"));
    }

    @Test
    void topLevelForkCommandCreatesForkAndEntersInteractiveLoop() throws Exception {
        StubAppServer runtime = new StubAppServer();
        CodexConsoleRunner runner = new CodexConsoleRunner(runtime, new StubApprovalService());
        ThreadId originalThread = runtime.rootThreadId();

        CapturedRun captured = captureRun(
                () -> runner.run("fork", shortId(originalThread)),
                "");

        assertNotEquals(originalThread, runner.getActiveThreadIdForTest());
        assertTrue(captured.stdout().contains("Forked thread"));
        assertTrue(captured.stdout().contains("Codex CLI. Active thread"));
    }

    @Test
    void interactiveStartupResumesPersistedButUnloadedActiveThreadBeforeFirstTurn() throws Exception {
        StubAppServer runtime = new StubAppServer(false, false);
        CodexConsoleRunner runner = new CodexConsoleRunner(runtime, new StubApprovalService());

        CapturedRun captured = captureRun(() -> runner.run(), "hello\n");

        assertTrue(runner.getActiveThreadIdForTest().equals(runtime.rootThreadId())
                || runner.getActiveThreadIdForTest().equals(runtime.subagentThreadId()));
        assertTrue(runtime.resumeAttemptCount() >= 1);
        assertTrue(runtime.resumeCount() >= 1);
        assertTrue(captured.stdout().contains("handled: hello"));
    }

    @Test
    void interactiveStartupCreatesReplacementThreadWhenPersistedThreadsCannotBeResumed() throws Exception {
        StubAppServer runtime = new StubAppServer(false, true);
        CodexConsoleRunner runner = new CodexConsoleRunner(runtime, new StubApprovalService());

        CapturedRun captured = captureRun(() -> runner.run(), "hello\n");

        ThreadId active = runner.getActiveThreadIdForTest();
        assertNotEquals(runtime.rootThreadId(), active);
        assertNotEquals(runtime.subagentThreadId(), active);
        assertTrue(runtime.resumeAttemptCount() >= 2);
        assertTrue(captured.stdout().contains("handled: hello"));
    }

    @Test
    void sharedRootOverridesAreCarriedThroughTopLevelCompletion() throws Exception {
        StubAppServer runtime = new StubAppServer();
        CodexConsoleRunner runner = new CodexConsoleRunner(runtime, new StubApprovalService());

        captureRun(
                () -> runner.run("--model", "gpt-5.4", "--cd", "./workspace", "--sandbox", "workspace-write",
                        "--approval-mode", "review-sensitive", "completion", "--shell", "bash"),
                "");

        assertEquals("gpt-5.4", runner.getLaunchOverridesForTest().model());
        assertEquals("./workspace", runner.getLaunchOverridesForTest().cd());
        assertEquals(org.dean.codex.cli.config.CliSandboxMode.WORKSPACE_WRITE, runner.getLaunchOverridesForTest().sandbox());
        assertEquals(org.dean.codex.cli.config.CliApprovalMode.REVIEW_SENSITIVE, runner.getLaunchOverridesForTest().approvalMode());
    }

    @Test
    void topLevelForkAppliesSupportedLaunchOverridesToForkRequest() throws Exception {
        StubAppServer runtime = new StubAppServer();
        CodexConsoleRunner runner = new CodexConsoleRunner(runtime, new StubApprovalService());
        ThreadId originalThread = runtime.rootThreadId();

        captureRun(
                () -> runner.run("--model", "gpt-5.4", "--cd", "./workspace", "fork", shortId(originalThread)),
                "");

        assertEquals("./workspace", runtime.lastForkParams().cwd());
        assertEquals("gpt-5.4", runtime.lastForkParams().model());
    }

    @Test
    void topLevelCompletionCommandPrintsScriptWithoutStartingSession() throws Exception {
        StubAppServer runtime = new StubAppServer();
        CodexConsoleRunner runner = new CodexConsoleRunner(runtime, new StubApprovalService());

        CapturedRun captured = captureRun(
                () -> runner.run("completion", "--shell", "bash"),
                "");

        assertEquals(0, runtime.connectCount());
        assertFalse(captured.stdout().isBlank());
        assertFalse(captured.stdout().contains("Codex CLI. Active thread"));
    }

    @Test
    void unsupportedTopLevelNonInteractiveCommandFailsClearlyWithoutReplFallback() throws Exception {
        StubAppServer runtime = new StubAppServer();
        CodexConsoleRunner runner = new CodexConsoleRunner(runtime, new StubApprovalService());

        CapturedRun captured = captureRun(
                () -> runner.run("exec", "plan", "next", "step"),
                "");

        assertEquals(0, runtime.connectCount());
        assertTrue(captured.stderr().contains("exec"));
        assertTrue(captured.stderr().contains("not wired to runtime yet"));
        assertFalse(captured.stdout().contains("Codex CLI. Active thread"));
    }

    private static String captureConsole(ThrowingRunnable action) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            action.run();
        }
        finally {
            System.setOut(originalOut);
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private static CapturedRun captureRun(ThrowingRunnable action, String stdin) throws Exception {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        java.io.InputStream originalIn = System.in;
        try {
            System.setIn(new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)));
            System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
            action.run();
        }
        finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
        return new CapturedRun(stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
    }

    private static String shortId(ThreadId threadId) {
        String value = threadId.value();
        return value.length() <= 8 ? value : value.substring(0, 8);
    }

    private static final class StubAppServer implements CodexAppServer {

        private final ConversationStore store = new InMemoryConversationStore();
        private final Set<ThreadId> loadedThreadIds = new LinkedHashSet<>();
        private final Set<ThreadId> resumedThreadIds = new LinkedHashSet<>();
        private final ThreadId rootThreadId;
        private final ThreadId subagentThreadId;
        private final boolean failResumeForExistingThreads;
        private final boolean emitToolActivity;
        private final boolean emitCommandExecutionNotifications;
        private final boolean delayedTurnCompletion;
        private final boolean delayedTurnResumeCompletion;
        private final boolean steerAccepted;
        private final boolean completeOnSteerFailure;
        private final boolean emitReasoningActivity;
        private boolean emitRawModelOutputActivity;
        private boolean emitMailboxActivity;
        private final Set<TurnId> runningTurnIds = ConcurrentHashMap.newKeySet();
        private final CopyOnWriteArrayList<String> steeredInputs = new CopyOnWriteArrayList<>();
        private final CopyOnWriteArrayList<ThreadListParams> threadListCalls = new CopyOnWriteArrayList<>();
        private final AtomicInteger turnStartCount = new AtomicInteger();
        private final AtomicInteger turnSteerCount = new AtomicInteger();
        private final AtomicInteger turnResumeCount = new AtomicInteger();
        private ThreadReadResponse customThreadReadResponse;
        private int connectCount;
        private int resumeAttemptCount;
        private ThreadForkParams lastForkParams;

        private StubAppServer() {
            this(true, false, false, false, false, true, false);
        }

        private StubAppServer(boolean preloadThreadsAsLoaded, boolean failResumeForExistingThreads) {
            this(preloadThreadsAsLoaded, failResumeForExistingThreads, false, false, false, true, false);
        }

        private StubAppServer(boolean preloadThreadsAsLoaded,
                              boolean failResumeForExistingThreads,
                              boolean emitToolActivity) {
            this(preloadThreadsAsLoaded, failResumeForExistingThreads, emitToolActivity, false, false, true, false);
        }

        private StubAppServer(boolean preloadThreadsAsLoaded,
                              boolean failResumeForExistingThreads,
                              boolean emitToolActivity,
                              boolean emitCommandExecutionNotifications,
                              boolean delayedTurnCompletion,
                              boolean steerAccepted,
                              boolean completeOnSteerFailure) {
            this(preloadThreadsAsLoaded, failResumeForExistingThreads, emitToolActivity, emitCommandExecutionNotifications, delayedTurnCompletion,
                    steerAccepted, completeOnSteerFailure, false, false);
        }

        private StubAppServer(boolean preloadThreadsAsLoaded,
                              boolean failResumeForExistingThreads,
                              boolean emitToolActivity,
                              boolean emitCommandExecutionNotifications,
                              boolean delayedTurnCompletion,
                              boolean steerAccepted,
                              boolean completeOnSteerFailure,
                              boolean delayedTurnResumeCompletion) {
            this(preloadThreadsAsLoaded,
                    failResumeForExistingThreads,
                    emitToolActivity,
                    emitCommandExecutionNotifications,
                    delayedTurnCompletion,
                    steerAccepted,
                    completeOnSteerFailure,
                    delayedTurnResumeCompletion,
                    false);
        }

        private StubAppServer(boolean preloadThreadsAsLoaded,
                              boolean failResumeForExistingThreads,
                              boolean emitToolActivity,
                              boolean emitCommandExecutionNotifications,
                              boolean delayedTurnCompletion,
                              boolean steerAccepted,
                              boolean completeOnSteerFailure,
                              boolean delayedTurnResumeCompletion,
                              boolean emitReasoningActivity) {
            this.rootThreadId = store.createThread("Thread 1");
            this.subagentThreadId = store.createThread("Worker thread");
            this.failResumeForExistingThreads = failResumeForExistingThreads;
            this.emitToolActivity = emitToolActivity;
            this.emitCommandExecutionNotifications = emitCommandExecutionNotifications;
            this.delayedTurnCompletion = delayedTurnCompletion;
            this.delayedTurnResumeCompletion = delayedTurnResumeCompletion;
            this.steerAccepted = steerAccepted;
            this.completeOnSteerFailure = completeOnSteerFailure;
            this.emitReasoningActivity = emitReasoningActivity;
            if (preloadThreadsAsLoaded) {
                loadedThreadIds.add(rootThreadId);
                loadedThreadIds.add(subagentThreadId);
            }
            store.updateAgentThread(subagentThreadId, rootThreadId, 1, null, "worker", "explorer", "src/demo");
        }

        private ThreadId subagentThreadId() {
            return subagentThreadId;
        }

        private ThreadId rootThreadId() {
            return rootThreadId;
        }

        private void addCompletedTurn(ThreadId threadId, String input) {
            Instant now = Instant.now();
            TurnId turnId = store.startTurn(threadId, input, now);
            store.completeTurn(threadId, turnId, TurnStatus.COMPLETED, "handled: " + input, now.plusSeconds(1));
        }

        private TurnId addRunningTurn(ThreadId threadId, String input) {
            return store.startTurn(threadId, input, Instant.now());
        }

        private int turnCount(ThreadId threadId) {
            return store.turns(threadId).size();
        }

        private int turnStartCount() {
            return turnStartCount.get();
        }

        private int turnSteerCount() {
            return turnSteerCount.get();
        }

        private int turnResumeCount() {
            return turnResumeCount.get();
        }

        private List<String> steeredInputs() {
            return List.copyOf(steeredInputs);
        }

        private int connectCount() {
            return connectCount;
        }

        private List<ThreadListParams> threadListCalls() {
            return new ArrayList<>(threadListCalls);
        }

        private ThreadForkParams lastForkParams() {
            return lastForkParams;
        }

        private int resumeCount() {
            return resumedThreadIds.size();
        }

        private void setThreadGitMetadata(ThreadId threadId, String gitSha, String gitBranch, String gitOriginUrl) {
            store.updateThreadMetadata(threadId, null, null, null, null, null, gitSha, gitBranch, gitOriginUrl, null);
        }

        private void setThreadCliVersion(ThreadId threadId, String cliVersion) {
            store.updateThreadMetadata(threadId, null, null, null, null, null, null, null, null, cliVersion);
        }

        private int resumeAttemptCount() {
            return resumeAttemptCount;
        }

        private ThreadReadResponse customThreadReadResponse() {
            return customThreadReadResponse;
        }

        @Override
        public CodexAppServerSession connect() {
            connectCount++;
            return new StubSession();
        }

        private ThreadSummary requireThread(ThreadId threadId) {
            return store.listThreads().stream()
                    .filter(summary -> summary.threadId().equals(threadId))
                    .findFirst()
                    .orElseThrow();
        }

        private ThreadSummary runtimeSummary(ThreadId threadId) {
            return runtimeSummary(requireThread(threadId));
        }

        private ThreadSummary runtimeSummary(ThreadSummary thread) {
            boolean loaded = loadedThreadIds.contains(thread.threadId()) && !thread.archived();
            AgentStatus agentStatus = thread.parentThreadId() == null
                    ? null
                    : loaded ? AgentStatus.IDLE : thread.agentClosedAt() == null ? AgentStatus.PENDING_INIT : AgentStatus.SHUTDOWN;
            return thread.withRuntime(loaded ? ThreadStatus.IDLE : ThreadStatus.NOT_LOADED, List.of(), agentStatus);
        }

        private ThreadId treeRoot(ThreadId threadId) {
            ThreadSummary current = requireThread(threadId);
            while (current.parentThreadId() != null) {
                current = requireThread(current.parentThreadId());
            }
            return current.threadId();
        }

        private List<ThreadSummary> relatedThreads(ThreadId threadId) {
            ThreadId root = treeRoot(threadId);
            return store.listThreads().stream()
                    .map(this::runtimeSummary)
                    .filter(summary -> treeRoot(summary.threadId()).equals(root))
                    .filter(summary -> !summary.threadId().equals(threadId))
                    .toList();
        }

        private final class StubSession implements CodexAppServerSession {

            private final CopyOnWriteArrayList<Consumer<AppServerNotification>> listeners = new CopyOnWriteArrayList<>();
            private boolean initializeCalled;
            private boolean initializedAcknowledged;

            @Override
            public InitializeResponse initialize(InitializeParams params) {
                if (initializeCalled) {
                    throw new IllegalStateException("Already initialized");
                }
                initializeCalled = true;
                return new InitializeResponse(
                        params == null || params.clientInfo() == null ? "codex-java-test" : params.clientInfo().name(),
                        "/tmp/.d-codex",
                        "desktop",
                        "test");
            }

            @Override
            public void initialized(InitializedNotification notification) {
                if (!initializeCalled) {
                    throw new IllegalStateException("Not initialized");
                }
                initializedAcknowledged = true;
            }

            @Override
            public ThreadStartResponse threadStart(ThreadStartParams params) {
                ensureReady();
                String title = params == null ? "" : params.title();
                ThreadId threadId = store.createThread(title);
                loadedThreadIds.add(threadId);
                var thread = runtimeSummary(threadId);
                publish(threadId, new ThreadStartedNotification(thread));
                return new ThreadStartResponse(thread);
            }

            @Override
            public ThreadResumeResponse threadResume(ThreadResumeParams params) {
                ensureReady();
                ThreadId threadId = params.threadId();
                resumeAttemptCount++;
                if (requireThread(threadId).archived()) {
                    throw new IllegalArgumentException("Archived thread id: " + threadId.value());
                }
                if (failResumeForExistingThreads
                        && (threadId.equals(rootThreadId) || threadId.equals(subagentThreadId))) {
                    throw new IllegalStateException("Injected resume failure for startup thread: " + threadId.value());
                }
                loadedThreadIds.add(threadId);
                resumedThreadIds.add(threadId);
                return new ThreadResumeResponse(runtimeSummary(threadId));
            }

            @Override
            public ThreadListResponse threadList(ThreadListParams params) {
                ensureReady();
                threadListCalls.add(params);
                List<ThreadSummary> threads = store.listThreads().stream()
                        .map(StubAppServer.this::runtimeSummary)
                        .toList();
                return new DefaultThreadCatalogService().listThreads(threads, params);
            }

            @Override
            public ThreadLoadedListResponse threadLoadedList(ThreadLoadedListParams params) {
                ensureReady();
                return new ThreadLoadedListResponse(
                        store.listThreads().stream()
                                .map(StubAppServer.this::runtimeSummary)
                                .filter(ThreadSummary::loaded)
                                .map(ThreadSummary::threadId)
                                .toList(),
                        null);
            }

            @Override
            public ThreadReadResponse threadRead(ThreadReadParams params) {
                ensureReady();
                if (customThreadReadResponse != null) {
                    return customThreadReadResponse;
                }
                ThreadId threadId = params.threadId();
                List<ConversationTurn> turns = params.includeTurns() ? store.turns(threadId) : List.of();
                ThreadMemory threadMemory = params.includeTurns() ? latestThreadMemory(threadId) : null;
                ReconstructedThreadContext reconstructedContext = params.includeTurns()
                        ? new ReconstructedThreadContext(threadId, threadMemory, List.of(), turns, List.of(), List.of(), Instant.now())
                        : null;
                return new ThreadReadResponse(
                        runtimeSummary(threadId),
                        turns,
                        threadMemory,
                        reconstructedContext,
                        treeRoot(threadId),
                        relatedThreads(threadId));
            }

            @Override
            public ThreadForkResponse threadFork(ThreadForkParams params) {
                ensureReady();
                lastForkParams = params;
                ThreadId threadId = store.forkThread(params);
                loadedThreadIds.add(threadId);
                return new ThreadForkResponse(runtimeSummary(threadId));
            }

            @Override
            public ThreadArchiveResponse threadArchive(ThreadArchiveParams params) {
                ensureReady();
                loadedThreadIds.remove(params.threadId());
                return new ThreadArchiveResponse(runtimeSummary(store.archiveThread(params.threadId())));
            }

            @Override
            public ThreadUnarchiveResponse threadUnarchive(ThreadUnarchiveParams params) {
                ensureReady();
                return new ThreadUnarchiveResponse(runtimeSummary(store.unarchiveThread(params.threadId())));
            }

            @Override
            public ThreadUnsubscribeResponse threadUnsubscribe(ThreadUnsubscribeParams params) {
                ensureReady();
                ThreadId threadId = params.threadId();
                loadedThreadIds.remove(threadId);
                return new ThreadUnsubscribeResponse("unsubscribed");
            }

            @Override
            public ThreadNameSetResponse threadNameSet(ThreadNameSetParams params) {
                ensureReady();
                store.renameThread(params.threadId(), params.title());
                return new ThreadNameSetResponse();
            }

            @Override
            public ThreadMetadataUpdateResponse threadMetadataUpdate(ThreadMetadataUpdateParams params) {
                ensureReady();
                ThreadSummary updated = store.updateThreadMetadata(
                        params.threadId(),
                        params.cwd(),
                        params.modelProvider(),
                        params.model(),
                        params.sandboxMode(),
                        params.approvalMode(),
                        params.gitSha(),
                        params.gitBranch(),
                        params.gitOriginUrl(),
                        params.cliVersion());
                return new ThreadMetadataUpdateResponse(runtimeSummary(updated));
            }

            @Override
            public ThreadShellCommandResponse threadShellCommand(ThreadShellCommandParams params) {
                ensureReady();
                return new ThreadShellCommandResponse(new ShellCommandResult(
                        true,
                        params.command(),
                        0,
                        "shell output",
                        "",
                        false,
                        "/tmp/workspace",
                        true,
                        CommandApprovalDecision.ALLOW,
                        "allowed",
                        ""));
            }

            @Override
            public ThreadBackgroundTerminalsCleanResponse threadBackgroundTerminalsClean(ThreadBackgroundTerminalsCleanParams params) {
                ensureReady();
                return new ThreadBackgroundTerminalsCleanResponse(params.threadId(), 0);
            }

            @Override
            public AgentSpawnResponse agentSpawn(AgentSpawnParams params) {
                ensureReady();
                return new AgentSpawnResponse(new AgentSummary(
                        new ThreadId("agent-1"),
                        params == null || params.request() == null ? null : params.request().parentThreadId(),
                        "worker",
                        "explorer",
                        "src/demo",
                        1,
                        AgentStatus.IDLE,
                        Instant.now(),
                        Instant.now(),
                        null));
            }

            @Override
            public AgentSendInputResponse agentSendInput(AgentSendInputParams params) {
                return new AgentSendInputResponse(agentAssignTask(new AgentAssignTaskParams(
                        params.agentThreadId(),
                        params.message(),
                        params.interrupt())).agent());
            }

            @Override
            public AgentSendMessageResponse agentSendMessage(AgentSendMessageParams params) {
                ensureReady();
                return new AgentSendMessageResponse(new AgentSummary(
                        params.agentThreadId(),
                        new ThreadId("thread-1"),
                        "worker",
                        "explorer",
                        "src/demo",
                        1,
                        AgentStatus.IDLE,
                        Instant.now(),
                        Instant.now(),
                        null));
            }

            @Override
            public AgentAssignTaskResponse agentAssignTask(AgentAssignTaskParams params) {
                ensureReady();
                return new AgentAssignTaskResponse(new AgentSummary(
                        params.agentThreadId(),
                        new ThreadId("thread-1"),
                        "worker",
                        "explorer",
                        "src/demo",
                        1,
                        AgentStatus.RUNNING,
                        Instant.now(),
                        Instant.now(),
                        null));
            }

            @Override
            public AgentWaitResponse agentWait(AgentWaitParams params) {
                ensureReady();
                return new AgentWaitResponse(new AgentWaitResult(
                        params.agentThreadIds().isEmpty() ? null : params.agentThreadIds().get(0),
                        null,
                        AgentStatus.IDLE,
                        AgentStatus.IDLE,
                        false,
                        "Agent is idle.",
                        "",
                        new AgentMailboxState(params.agentThreadIds().isEmpty() ? null : params.agentThreadIds().get(0), 0L, 0, Instant.parse("2026-03-31T00:00:02Z")),
                        Instant.now()));
            }

            @Override
            public AgentResumeResponse agentResume(AgentResumeParams params) {
                ensureReady();
                return new AgentResumeResponse(new AgentSummary(
                        params.agentThreadId(),
                        new ThreadId("thread-1"),
                        "worker",
                        "explorer",
                        "src/demo",
                        1,
                        AgentStatus.IDLE,
                        Instant.now(),
                        Instant.now(),
                        null));
            }

            @Override
            public AgentCloseResponse agentClose(AgentCloseParams params) {
                ensureReady();
                return new AgentCloseResponse(new AgentSummary(
                        params.agentThreadId(),
                        new ThreadId("thread-1"),
                        "worker",
                        "explorer",
                        "src/demo",
                        1,
                        AgentStatus.SHUTDOWN,
                        Instant.now(),
                        Instant.now(),
                        Instant.now()));
            }

            @Override
            public AgentListResponse agentList(AgentListParams params) {
                ensureReady();
                return new AgentListResponse(List.of(new AgentSummary(
                        new ThreadId("agent-1"),
                        new ThreadId("thread-1"),
                        "worker",
                        "explorer",
                        "src/demo",
                        1,
                        AgentStatus.IDLE,
                        Instant.now(),
                        Instant.now(),
                        null)));
            }

            @Override
            public ThreadRollbackResponse threadRollback(ThreadRollbackParams params) {
                ensureReady();
                return new ThreadRollbackResponse(
                        runtimeSummary(store.rollbackThread(params.threadId(), params.numTurns())),
                        store.turns(params.threadId()));
            }

            @Override
            public ThreadCompactStartResponse threadCompactStart(ThreadCompactStartParams params) {
                ensureReady();
                ThreadMemory threadMemory = latestThreadMemory(params.threadId());
                ThreadCompaction started = new ThreadCompaction(
                        "comp-1",
                        params.threadId(),
                        List.of(),
                        0,
                        "",
                        Instant.parse("2026-03-31T00:00:00Z"),
                        null);
                ThreadCompaction completed = new ThreadCompaction(
                        "comp-1",
                        params.threadId(),
                        List.of(),
                        threadMemory.compactedTurnCount(),
                        threadMemory.summary(),
                        Instant.parse("2026-03-31T00:00:00Z"),
                        threadMemory.createdAt());
                Thread notificationThread = new Thread(() -> {
                    try {
                        Thread.sleep(10);
                    }
                    catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                    publish(params.threadId(), new ThreadCompactionStartedNotification(started));
                    publish(params.threadId(), new ThreadCompactedNotification(completed));
                }, "stub-compaction-notifications");
                notificationThread.setDaemon(true);
                notificationThread.start();
                return new ThreadCompactStartResponse(completed, threadMemory);
            }

            @Override
            public SkillsListResponse skillsList(SkillsListParams params) {
                ensureReady();
                return new SkillsListResponse(List.of(new SkillMetadata(
                        "reviewer",
                        "Review code for bugs and regressions.",
                        "Review code for bugs and regressions.",
                        "/tmp/skills/reviewer/SKILL.md",
                        SkillScope.USER,
                        true)));
            }

            @Override
            public TurnStartResponse turnStart(TurnStartParams params) {
                ensureReady();
                turnStartCount.incrementAndGet();
                ThreadId threadId = params.threadId();
                if (!loadedThreadIds.contains(threadId)) {
                    throw new IllegalStateException("Thread is not loaded: " + threadId.value());
                }
                String input = params.input();
                Instant now = Instant.now();
                TurnId turnId = store.startTurn(threadId, input, now);
                runningTurnIds.add(turnId);
                if (emitToolActivity) {
                    RuntimeTurn runningTurn = new RuntimeTurn(threadId, turnId, TurnStatus.RUNNING, now, null);
                    publish(threadId, new TurnStartedNotification(runningTurn));
                    publish(threadId, new org.dean.codex.protocol.appserver.TurnItemNotification(
                            runningTurn,
                            new CollabToolCallItem(
                                    new ItemId("collab-start-1"),
                                    "spawn_agent",
                                    CollabToolCallStatus.IN_PROGRESS,
                                    org.dean.codex.protocol.item.CollabDeliveryState.DISPATCHED,
                                    threadId,
                                    List.of(),
                                    null,
                                    "inspect repository",
                                    java.util.Map.of(),
                                    java.util.Map.of(),
                                    null,
                                    now.plusMillis(1))));
                    publish(threadId, new org.dean.codex.protocol.appserver.TurnItemNotification(
                            runningTurn,
                            new CollabToolCallItem(
                                    new ItemId("collab-end-1"),
                                    "spawn_agent",
                                    CollabToolCallStatus.COMPLETED,
                                    org.dean.codex.protocol.item.CollabDeliveryState.DISPATCHED,
                                    threadId,
                                    List.of(new ThreadId("agent-1")),
                                    new ThreadId("agent-1"),
                                    "inspect repository",
                                    java.util.Map.of("agent-1", AgentStatus.IDLE),
                                    java.util.Map.of("agent-1", new AgentMailboxState(new ThreadId("agent-1"), 1L, 1, now.plusMillis(1))),
                                    "mailbox_updated",
                                    now.plusMillis(1))));
                    publish(threadId, new org.dean.codex.protocol.appserver.TurnItemNotification(
                            runningTurn,
                            new CollabToolCallItem(
                                    new ItemId("collab-wait-1"),
                                    "wait_agent",
                                    CollabToolCallStatus.COMPLETED,
                                    org.dean.codex.protocol.item.CollabDeliveryState.WAKEUP,
                                    threadId,
                                    List.of(new ThreadId("agent-1")),
                                    null,
                                    "wait_agent",
                                    java.util.Map.of("agent-1", AgentStatus.IDLE),
                                    java.util.Map.of("agent-1", new AgentMailboxState(new ThreadId("agent-1"), 2L, 0, now.plusMillis(2))),
                                    "mailbox_updated",
                                    now.plusMillis(2))));
                    publish(threadId, new AgentMailboxUpdatedNotification(
                            new AgentMailboxState(threadId, 1L, 1, now.plusMillis(1))));
                    publish(threadId, new org.dean.codex.protocol.appserver.TurnItemNotification(
                            runningTurn,
                            new ToolCallItem(new ItemId("tool-call-1"), "RUN_COMMAND", "ls -la", now.plusMillis(1))));
                    publish(threadId, new org.dean.codex.protocol.appserver.TurnItemNotification(
                            runningTurn,
                            new ToolResultItem(new ItemId("tool-result-1"), "RUN_COMMAND", "success=true exitCode=0", now.plusMillis(2))));
                }
                if (emitReasoningActivity) {
                    RuntimeTurn runningTurn = new RuntimeTurn(threadId, turnId, TurnStatus.RUNNING, now, null);
                    publish(threadId, new TurnStartedNotification(runningTurn));
                    publish(threadId, new org.dean.codex.protocol.appserver.TurnItemNotification(
                            runningTurn,
                            new ReasoningItem(
                                    new ItemId("reasoning-1"),
                                    "Need to inspect README",
                                    "The request mentions setup issues.",
                                    now.plusMillis(1))));
                }
                if (emitRawModelOutputActivity) {
                    RuntimeTurn runningTurn = new RuntimeTurn(threadId, turnId, TurnStatus.RUNNING, now, null);
                    publish(threadId, new TurnStartedNotification(runningTurn));
                    publish(threadId, new org.dean.codex.protocol.appserver.TurnItemNotification(
                            runningTurn,
                            new RawModelOutputItem(
                                    new ItemId("raw-1"),
                                    "reasoning",
                                    "resp-item-1",
                                    "stream-1",
                                    1,
                                    threadId.value(),
                                    turnId.value(),
                                    1,
                                    "response-1",
                                    "session-1",
                                    "completed",
                                    "{\"id\":\"resp-item-1\",\"summary\":\"Need to inspect README\"}",
                                    now.plusMillis(1))));
                }
                if (emitMailboxActivity) {
                    RuntimeTurn runningTurn = new RuntimeTurn(threadId, turnId, TurnStatus.RUNNING, now, null);
                    publish(threadId, new TurnStartedNotification(runningTurn));
                    publish(threadId, new org.dean.codex.protocol.appserver.TurnItemNotification(
                            runningTurn,
                            new MailboxMessageItem(
                                    new ItemId("mailbox-1"),
                                    new ThreadId("thread-agent"),
                                    threadId,
                                    MailboxDeliveryKind.CHILD_COMPLETION,
                                    "Sub-agent worker-1 completed. Final answer:\nREADME reviewed",
                                    now.plusMillis(1))));
                }
                if (emitCommandExecutionNotifications) {
                    CommandExecutionEvent commandExecution = new CommandExecutionEvent(
                            "exec-session-1",
                            threadId,
                            "printf 'one\\n'; printf 'two\\n'",
                            "/tmp/workspace",
                            1234L,
                            "RUNNING",
                            now.plusMillis(1),
                            null,
                            null);
                    publish(threadId, new CommandExecutionOutputDeltaNotification(commandExecution, "one\n", ""));
                    publish(threadId, new CommandExecutionOutputDeltaNotification(commandExecution, "two\n", ""));
                    publish(threadId, new CommandExecutionTerminalInteractionNotification(
                            commandExecution,
                            "resize",
                            null,
                            120,
                            40));
                    publish(threadId, new CommandExecutionTerminalInteractionNotification(
                            commandExecution,
                            "stdin",
                            6,
                            null,
                            null));
                    publish(threadId, new CommandExecutionCompletedNotification(new CommandExecutionEvent(
                            "exec-session-1",
                            threadId,
                            "printf 'one\\n'; printf 'two\\n'",
                            "/tmp/workspace",
                            1234L,
                            "COMPLETED",
                            now.plusMillis(1),
                            now.plusMillis(3),
                            0)));
                }
                if (delayedTurnCompletion) {
                    Thread completionThread = new Thread(() -> completeTurnLater(threadId, turnId, now, input),
                            "stub-turn-completion-" + turnId.value());
                    completionThread.setDaemon(true);
                    completionThread.start();
                }
                else {
                    completeTurn(threadId, turnId, now, input);
                }
                return new TurnStartResponse(new RuntimeTurn(threadId, turnId, TurnStatus.RUNNING, now, null));
            }

            @Override
            public TurnResumeResponse turnResume(TurnResumeParams params) {
                ensureReady();
                turnResumeCount.incrementAndGet();
                ThreadId threadId = params.threadId();
                TurnId turnId = params.turnId();
                Instant now = Instant.now();
                if (!store.exists(threadId)) {
                    throw new IllegalArgumentException("Unknown thread id: " + threadId.value());
                }
                ConversationTurn existingTurn;
                try {
                    existingTurn = store.turn(threadId, turnId);
                }
                catch (IllegalArgumentException ignored) {
                    TurnId startedTurnId = store.startTurn(threadId, "resume request", now);
                    existingTurn = store.turn(threadId, startedTurnId);
                    turnId = startedTurnId;
                }
                store.completeTurn(threadId, turnId, TurnStatus.COMPLETED, "resumed turn", now.plusSeconds(1));
                Instant startedAt = existingTurn.startedAt();
                Instant completedAt = now.plusSeconds(1);
                final ThreadId resumedThreadId = threadId;
                final TurnId resumedTurnId = turnId;
                RuntimeTurn resumedTurn = new RuntimeTurn(threadId, turnId, TurnStatus.RUNNING, startedAt, null);
                publish(threadId, new TurnStartedNotification(resumedTurn));
                if (delayedTurnResumeCompletion) {
                    Thread completionThread = new Thread(() -> {
                        try {
                            Thread.sleep(150);
                        }
                        catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        publish(threadId, new TurnCompletedNotification(
                                new RuntimeTurn(resumedThreadId, resumedTurnId, TurnStatus.COMPLETED, startedAt, completedAt),
                                "resumed turn"));
                    }, "stub-turn-resume-completion-" + turnId.value());
                    completionThread.setDaemon(true);
                    completionThread.start();
                }
                else {
                    publish(threadId, new TurnCompletedNotification(
                            new RuntimeTurn(resumedThreadId, resumedTurnId, TurnStatus.COMPLETED, startedAt, completedAt),
                            "resumed turn"));
                }
                return new TurnResumeResponse(resumedTurn);
            }

            @Override
            public TurnInterruptResponse turnInterrupt(TurnInterruptParams params) {
                ensureReady();
                return new TurnInterruptResponse(params.turnId(), true);
            }

            @Override
            public TurnSteerResponse turnSteer(TurnSteerParams params) {
                ensureReady();
                turnSteerCount.incrementAndGet();
                steeredInputs.add(params.input());
                if (steerAccepted) {
                    return new TurnSteerResponse(params.turnId(), true);
                }
                if (completeOnSteerFailure && runningTurnIds.remove(params.turnId())) {
                    ThreadId threadId = params.threadId();
                    Instant now = Instant.now();
                    store.completeTurn(threadId, params.turnId(), TurnStatus.COMPLETED, "handled: " + params.input(), now.plusSeconds(1));
                    publish(threadId, new TurnCompletedNotification(
                            new RuntimeTurn(threadId, params.turnId(), TurnStatus.COMPLETED, now, now.plusSeconds(1)),
                            "handled: " + params.input()));
                }
                return new TurnSteerResponse(params.turnId(), false);
            }

            @Override
            public AutoCloseable subscribe(Consumer<AppServerNotification> listener) {
                ensureReady();
                listeners.add(listener);
                return () -> listeners.remove(listener);
            }

            @Override
            public void close() {
                listeners.clear();
            }

            private void ensureReady() {
                if (!initializeCalled || !initializedAcknowledged) {
                    throw new IllegalStateException("Not initialized");
                }
            }

            private void publish(ThreadId threadId, AppServerNotification notification) {
                for (Consumer<AppServerNotification> listener : listeners) {
                    listener.accept(notification);
                }
            }

            private void completeTurn(ThreadId threadId, TurnId turnId, Instant startedAt, String input) {
                if (!runningTurnIds.remove(turnId)) {
                    return;
                }
                Instant completedAt = startedAt.plusSeconds(1);
                store.completeTurn(threadId, turnId, TurnStatus.COMPLETED, "handled: " + input, completedAt);
                publish(threadId, new TurnCompletedNotification(
                        new RuntimeTurn(threadId, turnId, TurnStatus.COMPLETED, startedAt, completedAt),
                        "handled: " + input));
            }

            private void completeTurnLater(ThreadId threadId, TurnId turnId, Instant startedAt, String input) {
                try {
                    TimeUnit.MILLISECONDS.sleep(150);
                }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                }
                completeTurn(threadId, turnId, startedAt, input);
            }

            private ThreadMemory latestThreadMemory(ThreadId threadId) {
                return new ThreadMemory(
                        "memory-1",
                        threadId,
                        "Compacted earlier thread context:\n- USER: Inspect repo\n  ASSISTANT: handled: Inspect repo",
                        List.of(),
                        1,
                        Instant.now());
            }
        }
    }

    private record CapturedRun(String stdout, String stderr) {
    }

    private static final class StubApprovalService implements CommandApprovalService {

        @Override
        public CommandApprovalRequest requestApproval(ThreadId threadId, TurnId turnId, String command, String workingDirectory, String reason) {
            return sampleApproval(threadId);
        }

        @Override
        public List<CommandApprovalRequest> approvals(ThreadId threadId) {
            return List.of(sampleApproval(threadId));
        }

        @Override
        public CommandApprovalRequest approve(ThreadId threadId, String approvalIdPrefix) {
            return new CommandApprovalRequest(
                    new ApprovalId("approval-1234"),
                    threadId,
                    new TurnId("turn-1"),
                    "pending command",
                    "/tmp/workspace",
                    "Needs approval",
                    ApprovalStatus.APPROVED,
                    Instant.now(),
                    Instant.now(),
                    "Approved from CLI.",
                    new ShellCommandResult(true, "pending command", 0, "ok", "", false, "/tmp/workspace", true,
                            CommandApprovalDecision.ALLOW, "Approved", ""));
        }

        @Override
        public CommandApprovalRequest reject(ThreadId threadId, String approvalIdPrefix, String reason) {
            return new CommandApprovalRequest(
                    new ApprovalId("approval-1234"),
                    threadId,
                    new TurnId("turn-1"),
                    "pending command",
                    "/tmp/workspace",
                    "Needs approval",
                    ApprovalStatus.REJECTED,
                    Instant.now(),
                    Instant.now(),
                    reason,
                    null);
        }

        private CommandApprovalRequest sampleApproval(ThreadId threadId) {
            return new CommandApprovalRequest(
                    new ApprovalId("approval-1234"),
                    threadId,
                    new TurnId("turn-1"),
                    "pending command",
                    "/tmp/workspace",
                    "Needs approval",
                    ApprovalStatus.PENDING,
                    Instant.now(),
                    Instant.now(),
                    "",
                    null);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
