package org.dean.codex.runtime.springai.appserver;

import org.dean.codex.core.agent.TurnControl;
import org.dean.codex.core.agent.TurnExecutor;
import org.dean.codex.core.appserver.CodexAppServer;
import org.dean.codex.core.appserver.CodexAppServerSession;
import org.dean.codex.core.context.ContextManager;
import org.dean.codex.core.context.ThreadContextReconstructionService;
import org.dean.codex.core.conversation.ConversationStore;
import org.dean.codex.core.conversation.InMemoryConversationStore;
import org.dean.codex.core.exec.ExecSessionId;
import org.dean.codex.core.exec.ExecSessionManager;
import org.dean.codex.core.exec.ExecSessionSummary;
import org.dean.codex.core.runtime.CodexRuntimeGateway;
import org.dean.codex.core.skill.ResolvedSkill;
import org.dean.codex.core.skill.SkillService;
import org.dean.codex.protocol.appserver.AppServerCapabilities;
import org.dean.codex.protocol.appserver.AppServerClientInfo;
import org.dean.codex.protocol.appserver.AppServerNotification;
import org.dean.codex.protocol.appserver.CommandExecParams;
import org.dean.codex.protocol.appserver.CommandExecResizeParams;
import org.dean.codex.protocol.appserver.CommandExecTerminateParams;
import org.dean.codex.protocol.appserver.CommandExecWriteParams;
import org.dean.codex.protocol.appserver.AgentCloseParams;
import org.dean.codex.protocol.appserver.AgentCloseResponse;
import org.dean.codex.protocol.appserver.AgentAssignTaskParams;
import org.dean.codex.protocol.appserver.AgentAssignTaskResponse;
import org.dean.codex.protocol.appserver.AgentListParams;
import org.dean.codex.protocol.appserver.AgentListResponse;
import org.dean.codex.protocol.appserver.AgentMailboxUpdatedNotification;
import org.dean.codex.protocol.appserver.AgentResumeParams;
import org.dean.codex.protocol.appserver.AgentResumeResponse;
import org.dean.codex.protocol.appserver.AgentSendMessageParams;
import org.dean.codex.protocol.appserver.AgentSendMessageResponse;
import org.dean.codex.protocol.appserver.AgentSpawnParams;
import org.dean.codex.protocol.appserver.AgentSpawnResponse;
import org.dean.codex.protocol.appserver.InitializeParams;
import org.dean.codex.protocol.appserver.InitializeResponse;
import org.dean.codex.protocol.appserver.InitializedNotification;
import org.dean.codex.protocol.appserver.SkillsListParams;
import org.dean.codex.protocol.appserver.CommandExecutionCompletedNotification;
import org.dean.codex.protocol.appserver.CommandExecutionOutputDeltaNotification;
import org.dean.codex.protocol.appserver.CommandExecutionTerminalInteractionNotification;
import org.dean.codex.protocol.appserver.ThreadArchiveParams;
import org.dean.codex.protocol.appserver.ThreadCompaction;
import org.dean.codex.protocol.appserver.ThreadCompactStartParams;
import org.dean.codex.protocol.appserver.ThreadCompactionStartedNotification;
import org.dean.codex.protocol.appserver.ThreadCompactedNotification;
import org.dean.codex.protocol.appserver.ThreadBackgroundTerminalsCleanParams;
import org.dean.codex.protocol.appserver.ThreadBackgroundTerminalsCleanResponse;
import org.dean.codex.protocol.appserver.ThreadClosedNotification;
import org.dean.codex.protocol.appserver.ThreadForkParams;
import org.dean.codex.protocol.appserver.ThreadListParams;
import org.dean.codex.protocol.appserver.ThreadLoadedListParams;
import org.dean.codex.protocol.appserver.ThreadMetadataUpdateParams;
import org.dean.codex.protocol.appserver.ThreadMetadataUpdatedNotification;
import org.dean.codex.protocol.appserver.ThreadNameSetParams;
import org.dean.codex.protocol.appserver.ThreadNameUpdatedNotification;
import org.dean.codex.protocol.appserver.ThreadReadResponse;
import org.dean.codex.protocol.appserver.ThreadReadParams;
import org.dean.codex.protocol.appserver.ThreadRollbackParams;
import org.dean.codex.protocol.appserver.ThreadResumeParams;
import org.dean.codex.protocol.appserver.ThreadSortKey;
import org.dean.codex.protocol.appserver.ThreadStartParams;
import org.dean.codex.protocol.appserver.ThreadStartResponse;
import org.dean.codex.protocol.appserver.ThreadStartedNotification;
import org.dean.codex.protocol.appserver.ThreadStatusChangedNotification;
import org.dean.codex.protocol.appserver.ThreadShellCommandParams;
import org.dean.codex.protocol.appserver.ThreadShellCommandResponse;
import org.dean.codex.protocol.appserver.ThreadSourceKind;
import org.dean.codex.protocol.appserver.ThreadUnarchiveParams;
import org.dean.codex.protocol.appserver.ThreadUnsubscribeParams;
import org.dean.codex.protocol.appserver.ThreadUnsubscribeResponse;
import org.dean.codex.protocol.appserver.TurnCompletedNotification;
import org.dean.codex.protocol.appserver.TurnInterruptParams;
import org.dean.codex.protocol.appserver.TurnItemNotification;
import org.dean.codex.protocol.appserver.TurnStartParams;
import org.dean.codex.protocol.appserver.TurnStartedNotification;
import org.dean.codex.protocol.appserver.TurnSteerParams;
import org.dean.codex.protocol.agent.AgentSpawnRequest;
import org.dean.codex.protocol.agent.AgentSummary;
import org.dean.codex.protocol.conversation.ConversationTurn;
import org.dean.codex.protocol.conversation.ItemId;
import org.dean.codex.protocol.conversation.ThreadId;
import org.dean.codex.protocol.conversation.ThreadSource;
import org.dean.codex.protocol.conversation.ThreadStatus;
import org.dean.codex.protocol.conversation.ThreadSummary;
import org.dean.codex.protocol.conversation.TurnId;
import org.dean.codex.protocol.conversation.TurnStatus;
import org.dean.codex.protocol.context.ReconstructedThreadContext;
import org.dean.codex.protocol.context.ReconstructedReplayItem;
import org.dean.codex.protocol.context.ThreadMemory;
import org.dean.codex.protocol.event.CodexTurnResult;
import org.dean.codex.protocol.item.TurnItem;
import org.dean.codex.protocol.item.UserMessageItem;
import org.dean.codex.protocol.item.CollabDeliveryState;
import org.dean.codex.protocol.item.RawModelOutputItem;
import org.dean.codex.protocol.item.AgentMessageItem;
import org.dean.codex.protocol.skill.SkillMetadata;
import org.dean.codex.protocol.skill.SkillScope;
import org.dean.codex.protocol.tool.CommandApprovalDecision;
import org.dean.codex.protocol.tool.ShellCommandResult;
import org.dean.codex.runtime.springai.model.ThreadModelSessionSnapshot;
import org.dean.codex.runtime.springai.model.ThreadModelSessionStateStore;
import org.dean.codex.runtime.springai.runtime.DefaultCodexRuntimeGateway;
import org.dean.codex.runtime.springai.prompt.ThreadPromptSnapshot;
import org.dean.codex.runtime.springai.prompt.ThreadPromptStateStore;
import org.dean.codex.runtime.springai.thread.DefaultThreadCatalogService;
import org.dean.codex.core.tool.local.ShellCommandTool;
import org.dean.codex.tools.local.PatternCommandApprovalPolicy;
import org.dean.codex.tools.local.ShellCommandToolImpl;
import org.dean.codex.tools.local.exec.InMemoryExecSessionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InProcessCodexAppServerTest {

    @TempDir
    Path workspaceRoot;

    @Test
    void threadStartPublishesThreadStartedNotification() throws Exception {
        CodexAppServer appServer = appServer(new NoOpTurnExecutor());
        BlockingQueue<AppServerNotification> notifications = new LinkedBlockingQueue<>();

        try (CodexAppServerSession session = initializedSession(appServer);
             AutoCloseable ignored = session.subscribe(notifications::add)) {
            ThreadId threadId = session.threadStart(new ThreadStartParams("App thread")).thread().threadId();
            AppServerNotification notification = notifications.poll(2, TimeUnit.SECONDS);
            assertNotNull(notification);
            assertTrue(notification instanceof ThreadStartedNotification);
            assertEquals(threadId, ((ThreadStartedNotification) notification).thread().threadId());
        }
    }

    @Test
    void threadStartAssignsCliSourceForCliSessions() throws Exception {
        CodexAppServer appServer = appServer(new NoOpTurnExecutor());

        try (CodexAppServerSession session = appServer.connect()) {
            session.initialize(new InitializeParams(
                    new AppServerClientInfo("codex-java-cli", "Codex Java CLI", "1.0-SNAPSHOT"),
                    new AppServerCapabilities(false, List.of())));
            session.initialized(new InitializedNotification());

            ThreadSummary thread = session.threadStart(new ThreadStartParams("CLI thread")).thread();
            assertEquals(ThreadSource.CLI, thread.source());
        }
    }

    @Test
    void threadResumeBackfillsUnknownSourceForCliSessions() throws Exception {
        ConversationStore store = new InMemoryConversationStore();
        ThreadId threadId = store.createThread("Legacy thread");
        CodexAppServer appServer = appServer(store, new NoOpTurnExecutor());

        try (CodexAppServerSession session = appServer.connect()) {
            session.initialize(new InitializeParams(
                    new AppServerClientInfo("codex-java-cli", "Codex Java CLI", "1.0-SNAPSHOT"),
                    new AppServerCapabilities(false, List.of())));
            session.initialized(new InitializedNotification());

            ThreadSummary resumed = session.threadResume(new ThreadResumeParams(threadId)).thread();
            assertEquals(ThreadSource.CLI, resumed.source());
        }
    }

    @Test
    void threadForkPublishesStartedNotificationAndDivergesFromParent() throws Exception {
        CodexAppServer appServer = appServer(new NoOpTurnExecutor());
        BlockingQueue<AppServerNotification> notifications = new LinkedBlockingQueue<>();

        try (CodexAppServerSession session = initializedSession(appServer);
             AutoCloseable ignored = session.subscribe(notifications::add)) {
            ThreadId parentThreadId = session.threadStart(new ThreadStartParams("Parent thread")).thread().threadId();
            session.turnStart(new TurnStartParams(parentThreadId, "Inspect repo"));

            var forked = session.threadFork(new ThreadForkParams(
                    parentThreadId,
                    "Forked thread",
                    Boolean.TRUE,
                    "/workspace/forked",
                    "openai",
                    "gpt-5.4",
                    ThreadSource.APP_SERVER,
                    "worker-1",
                    "worker",
                    "root/worker-1")).thread();

            assertEquals("Forked thread", forked.title());
            assertEquals("/workspace/forked", forked.cwd());
            assertTrue(forked.ephemeral());
            assertNotNull(forked.promptState());
            assertEquals(parentThreadId, forked.promptState().inheritedFromThreadId());
            assertNotNull(forked.modelSessionState());
            assertEquals(parentThreadId, forked.modelSessionState().inheritedFromThreadId());
            assertEquals(parentThreadId, forked.modelSessionState().rootThreadId());

            var forkedRead = session.threadRead(new ThreadReadParams(forked.threadId(), true));
            assertEquals(1, forkedRead.turns().size());
            assertNotNull(forkedRead.thread().promptState());
            assertEquals(parentThreadId, forkedRead.thread().promptState().inheritedFromThreadId());
            assertNotNull(forkedRead.thread().modelSessionState());
            assertEquals(parentThreadId, forkedRead.thread().modelSessionState().inheritedFromThreadId());
            assertTrue(session.threadLoadedList(new ThreadLoadedListParams()).data().contains(forked.threadId()));

            session.turnStart(new TurnStartParams(forked.threadId(), "Write follow-up"));

            assertEquals(1, session.threadRead(new ThreadReadParams(parentThreadId, true)).turns().size());
            assertEquals(2, session.threadRead(new ThreadReadParams(forked.threadId(), true)).turns().size());
        }
    }

    @Test
    void turnStartAndSteerFlowThroughAppServerContract() throws Exception {
        CodexAppServer appServer = appServer(new SteeringTurnExecutor());
        BlockingQueue<AppServerNotification> notifications = new LinkedBlockingQueue<>();

        try (CodexAppServerSession session = initializedSession(appServer);
             AutoCloseable ignored = session.subscribe(notifications::add)) {
            ThreadId threadId = session.threadStart(new ThreadStartParams("App thread")).thread().threadId();
            TurnId turnId = session.turnStart(new TurnStartParams(threadId, "Inspect repo")).turn().turnId();
            assertTrue(session.turnSteer(new TurnSteerParams(threadId, turnId, "Please focus on tests")).accepted());

            List<AppServerNotification> observed = awaitNotifications(notifications);
            AppServerNotification first = observed.get(0);
            assertTrue(first instanceof ThreadStartedNotification || first instanceof TurnStartedNotification);
            assertTrue(observed.stream().anyMatch(TurnStartedNotification.class::isInstance));
            assertTrue(observed.stream().anyMatch(notification ->
                    notification instanceof TurnItemNotification item
                            && item.item() instanceof UserMessageItem userMessageItem
                            && userMessageItem.text().contains("focus on tests")));
            assertTrue(observed.stream().anyMatch(TurnCompletedNotification.class::isInstance));
        }
    }

    @Test
    void turnStartCanPassthroughRawModelOutputItemsOverAppServerNotifications() throws Exception {
        CodexAppServer appServer = appServer(new RawItemTurnExecutor());
        BlockingQueue<AppServerNotification> notifications = new LinkedBlockingQueue<>();

        try (CodexAppServerSession session = initializedSession(appServer);
             AutoCloseable ignored = session.subscribe(notifications::add)) {
            ThreadId threadId = session.threadStart(new ThreadStartParams("Raw item thread")).thread().threadId();
            session.turnStart(new TurnStartParams(threadId, "Inspect repo"));

            List<AppServerNotification> observed = awaitNotifications(notifications, 5);
            RawModelOutputItem rawItem = observed.stream()
                    .filter(TurnItemNotification.class::isInstance)
                    .map(TurnItemNotification.class::cast)
                    .map(TurnItemNotification::item)
                    .filter(RawModelOutputItem.class::isInstance)
                    .map(RawModelOutputItem.class::cast)
                    .findFirst()
                    .orElseThrow();

            assertEquals("reasoning", rawItem.modelItemType());
            assertEquals("resp-item-1", rawItem.modelItemId());
            assertEquals(threadId.value(), rawItem.threadId());
            assertEquals(1, rawItem.streamSequence());
            assertTrue(rawItem.payloadJson().contains("Need to inspect README"));
        }
    }

    @Test
    void skillsListDelegatesToRuntimeSkills() throws Exception {
        CodexAppServer appServer = appServer(new NoOpTurnExecutor());

        try (CodexAppServerSession session = initializedSession(appServer)) {
            var response = session.skillsList(new SkillsListParams(false));

            assertEquals(1, response.skills().size());
            assertEquals("reviewer", response.skills().get(0).name());
        }
    }

    @Test
    void threadCompactStartPublishesThreadCompactedNotification() throws Exception {
        CodexAppServer appServer = appServer(new NoOpTurnExecutor());
        BlockingQueue<AppServerNotification> notifications = new LinkedBlockingQueue<>();

        try (CodexAppServerSession session = initializedSession(appServer);
             AutoCloseable ignored = session.subscribe(notifications::add)) {
            ThreadId threadId = session.threadStart(new ThreadStartParams("App thread")).thread().threadId();
            var response = session.threadCompactStart(new ThreadCompactStartParams(threadId));

            List<AppServerNotification> observed = awaitNotifications(notifications, 3);
            assertTrue(observed.stream().anyMatch(ThreadCompactionStartedNotification.class::isInstance));
            assertTrue(observed.stream().anyMatch(ThreadCompactedNotification.class::isInstance));

            ThreadCompactionStartedNotification started = (ThreadCompactionStartedNotification) observed.stream()
                    .filter(ThreadCompactionStartedNotification.class::isInstance)
                    .findFirst()
                    .orElseThrow();
            ThreadCompactedNotification completed = (ThreadCompactedNotification) observed.stream()
                    .filter(ThreadCompactedNotification.class::isInstance)
                    .findFirst()
                    .orElseThrow();

            assertEquals(threadId, started.compaction().threadId());
            assertEquals(threadId, completed.compaction().threadId());
            assertEquals(response.compaction().compactionId(), completed.compaction().compactionId());
            assertNotNull(response.threadMemory());
            assertTrue(response.compaction().completed());
        }
    }

    @Test
    void threadListFiltersAndReadIncludeTurnsFlowThroughAppServerContract() throws Exception {
        CodexAppServer appServer = appServer(new NoOpTurnExecutor());

        try (CodexAppServerSession session = initializedSession(appServer)) {
            ThreadId threadId = session.threadStart(new ThreadStartParams("Alpha thread", "workspace-write", "review-sensitive")).thread().threadId();
            session.turnStart(new TurnStartParams(threadId, "Inspect transport"));
            session.threadStart(new ThreadStartParams("Beta thread", "read-only", "auto"));

            var filtered = session.threadList(new ThreadListParams(
                    null,
                    null,
                    null,
                    null,
                    null,
                    Boolean.FALSE,
                    null,
                    "inspect",
                    List.of("workspace-write"),
                    List.of("review-sensitive")));
            assertEquals(1, filtered.threads().size());
            assertEquals(threadId, filtered.threads().get(0).threadId());
            assertNull(filtered.nextCursor());

            var metadataOnly = session.threadRead(new ThreadReadParams(threadId, false));
            assertTrue(metadataOnly.turns().isEmpty());
            assertNull(metadataOnly.threadMemory());
            assertNull(metadataOnly.reconstructedContext());
            assertEquals(threadId, metadataOnly.treeRootThreadId());
            assertTrue(metadataOnly.relatedThreads().isEmpty());

            var withTurns = session.threadRead(new ThreadReadParams(threadId, true));
            assertEquals(1, withTurns.turns().size());
            assertNotNull(withTurns.threadMemory());
            assertNotNull(withTurns.reconstructedContext());
            assertEquals(threadId, withTurns.treeRootThreadId());
            assertTrue(withTurns.relatedThreads().isEmpty());

            var loaded = session.threadLoadedList(new ThreadLoadedListParams());
            assertTrue(loaded.data().contains(threadId));
        }
    }

    @Test
    void threadListSupportsStatusesAndParentThreadFilterThroughAppServerContract() throws Exception {
        Instant base = Instant.parse("2026-04-01T00:00:00Z");
        ThreadId parentThreadId = new ThreadId("thread-parent");
        ThreadId childThreadId = new ThreadId("thread-child");
        ThreadId grandchildThreadId = new ThreadId("thread-grandchild");
        ThreadSummary parent = new ThreadSummary(
                parentThreadId,
                "Parent thread",
                base,
                base.plusSeconds(5),
                1,
                "Parent preview",
                "Parent preview",
                null,
                null,
                false,
                "openai",
                "gpt-5.4",
                ThreadStatus.ACTIVE,
                List.<org.dean.codex.protocol.conversation.ThreadActiveFlag>of(),
                "/tmp/threads/thread-parent",
                "/workspace/parent",
                org.dean.codex.protocol.conversation.ThreadSource.CLI,
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        ThreadSummary child = new ThreadSummary(
                childThreadId,
                "Child thread",
                base.plusSeconds(1),
                base.plusSeconds(6),
                1,
                "Child preview",
                "Child preview",
                null,
                null,
                false,
                "openai",
                "gpt-5.4",
                ThreadStatus.IDLE,
                List.<org.dean.codex.protocol.conversation.ThreadActiveFlag>of(),
                "/tmp/threads/thread-child",
                "/workspace/child",
                org.dean.codex.protocol.conversation.ThreadSource.SUB_AGENT,
                true,
                null,
                null,
                null,
                null,
                parentThreadId,
                1,
                org.dean.codex.protocol.agent.AgentStatus.IDLE,
                null);
        ThreadSummary grandchild = new ThreadSummary(
                grandchildThreadId,
                "Grandchild thread",
                base.plusSeconds(2),
                base.plusSeconds(7),
                1,
                "Grandchild preview",
                "Grandchild preview",
                null,
                null,
                false,
                "openai",
                "gpt-5.4",
                ThreadStatus.IDLE,
                List.<org.dean.codex.protocol.conversation.ThreadActiveFlag>of(),
                "/tmp/threads/thread-grandchild",
                "/workspace/grandchild",
                org.dean.codex.protocol.conversation.ThreadSource.SUB_AGENT,
                true,
                null,
                null,
                null,
                null,
                childThreadId,
                2,
                org.dean.codex.protocol.agent.AgentStatus.IDLE,
                null);
        CodexAppServer appServer = appServer(new StubThreadListRuntimeGateway(List.of(parent, child, grandchild)));

        try (CodexAppServerSession session = initializedSession(appServer)) {
            var filtered = session.threadList(new ThreadListParams(
                    null,
                    null,
                    ThreadSortKey.UPDATED_AT,
                    null,
                    null,
                    Boolean.FALSE,
                    null,
                    null,
                    null,
                    null,
                    List.of(ThreadStatus.IDLE),
                    parentThreadId));

            assertEquals(1, filtered.threads().size());
            assertEquals(childThreadId, filtered.threads().get(0).threadId());
            assertEquals(parentThreadId, filtered.threads().get(0).parentThreadId());
        }
    }

    @Test
    void threadReadExposesTopLevelReplaySummaryFromReconstruction() throws Exception {
        CodexAppServer appServer = new InProcessCodexAppServer(new DefaultCodexRuntimeGateway(
                new InMemoryConversationStore(),
                new NoOpTurnExecutor(),
                new NoOpContextManager(),
                new ReplaySummaryThreadContextReconstructionService(),
                new NoOpSkillService()));

        try (CodexAppServerSession session = initializedSession(appServer)) {
            ThreadId threadId = session.threadStart(new ThreadStartParams("Replay thread")).thread().threadId();

            ThreadReadResponse read = session.threadRead(new ThreadReadParams(threadId, true));

            assertEquals(1, read.replaySummary().size());
            assertEquals("collaboration", read.replaySummary().get(0).kind());
            assertEquals(CollabDeliveryState.DISPATCHED, read.replaySummary().get(0).deliveryState());
            assertNotNull(read.reconstructedContext());
            assertEquals(read.replaySummary(), read.reconstructedContext().replaySummary());
        }
    }

    @Test
    void threadResumeBackfillsSubAgentSubscriptionsAndThreadReadReturnsTreeNavigation() throws Exception {
        ConversationStore conversationStore = new InMemoryConversationStore();
        DefaultCodexRuntimeGateway runtimeGateway = new DefaultCodexRuntimeGateway(
                conversationStore,
                new NoOpTurnExecutor(),
                new NoOpContextManager(),
                new NoOpThreadContextReconstructionService(),
                new NoOpSkillService());
        ThreadId parentThreadId = runtimeGateway.threadStart("Parent thread").threadId();
        ThreadId childThreadId = runtimeGateway.spawnAgent(new AgentSpawnRequest(
                parentThreadId,
                "root/worker-1",
                "Investigate the failing tests",
                "worker-1",
                "worker",
                null,
                null,
                null,
                null)).threadId();

        CodexAppServer appServer = new InProcessCodexAppServer(runtimeGateway);
        BlockingQueue<AppServerNotification> notifications = new LinkedBlockingQueue<>();

        try (CodexAppServerSession session = initializedSession(appServer);
             AutoCloseable ignored = session.subscribe(notifications::add)) {
            session.threadResume(new ThreadResumeParams(parentThreadId));

            var parentRead = session.threadRead(new ThreadReadParams(parentThreadId, false));
            assertEquals(parentThreadId, parentRead.treeRootThreadId());
            assertTrue(parentRead.relatedThreads().stream().anyMatch(summary -> summary.threadId().equals(childThreadId)));

            var childRead = session.threadRead(new ThreadReadParams(childThreadId, false));
            assertEquals(parentThreadId, childRead.treeRootThreadId());
            assertTrue(childRead.relatedThreads().stream().anyMatch(summary -> summary.threadId().equals(parentThreadId)));

            assertTrue(session.threadLoadedList(new ThreadLoadedListParams()).data().contains(childThreadId));

            runtimeGateway.turnStart(childThreadId, "Follow up on the investigation");

            List<AppServerNotification> observed = awaitNotifications(notifications, 3);
            assertTrue(observed.stream().anyMatch(notification ->
                    notification instanceof TurnStartedNotification started
                            && started.turn().threadId().equals(childThreadId)));
            assertTrue(observed.stream().anyMatch(notification ->
                    notification instanceof TurnCompletedNotification completed
                            && completed.turn().threadId().equals(childThreadId)));
        }
    }

    @Test
    void threadArchiveUnarchiveAndRollbackFlowThroughAppServerContract() throws Exception {
        CodexAppServer appServer = appServer(new NoOpTurnExecutor());
        BlockingQueue<AppServerNotification> notifications = new LinkedBlockingQueue<>();

        try (CodexAppServerSession session = initializedSession(appServer);
             AutoCloseable ignored = session.subscribe(notifications::add)) {
            ThreadId threadId = session.threadStart(new ThreadStartParams("Lifecycle thread")).thread().threadId();
            session.turnStart(new TurnStartParams(threadId, "Inspect repo"));
            session.turnStart(new TurnStartParams(threadId, "Run tests"));
            awaitTurnCompletions(notifications, 2);

            var archived = session.threadArchive(new ThreadArchiveParams(threadId)).thread();
            assertTrue(archived.archived());
            assertFalse(session.threadLoadedList(new ThreadLoadedListParams()).data().contains(threadId));
            assertTrue(session.threadList(new ThreadListParams(null, null, null, null, null, null, null, null)).threads().isEmpty());
            assertEquals(List.of(threadId),
                    session.threadList(new ThreadListParams(null, null, null, null, null, Boolean.TRUE, null, null)).threads()
                            .stream()
                            .map(ThreadSummary::threadId)
                            .toList());

            var unarchived = session.threadUnarchive(new ThreadUnarchiveParams(threadId)).thread();
            assertFalse(unarchived.archived());

            var rollback = session.threadRollback(new ThreadRollbackParams(threadId, 1));
            assertEquals(1, rollback.thread().turnCount());
            assertEquals(1, rollback.turns().size());
            assertEquals("Inspect repo", rollback.turns().get(0).userInput());
            assertTrue(rollback.replaySummary().isEmpty());
        }
    }

    @Test
    void threadRollbackExposesTopLevelReplaySummaryFromReconstruction() throws Exception {
        CodexAppServer appServer = new InProcessCodexAppServer(new DefaultCodexRuntimeGateway(
                new InMemoryConversationStore(),
                new NoOpTurnExecutor(),
                new NoOpContextManager(),
                new ReplaySummaryThreadContextReconstructionService(),
                new NoOpSkillService()));
        BlockingQueue<AppServerNotification> notifications = new LinkedBlockingQueue<>();

        try (CodexAppServerSession session = initializedSession(appServer);
             AutoCloseable ignored = session.subscribe(notifications::add)) {
            ThreadId threadId = session.threadStart(new ThreadStartParams("Rollback replay thread")).thread().threadId();
            session.turnStart(new TurnStartParams(threadId, "Inspect repo"));
            awaitTurnCompletions(notifications, 1);

            var rolledBack = session.threadRollback(new ThreadRollbackParams(threadId, 1));

            assertEquals(1, rolledBack.replaySummary().size());
            assertEquals("collaboration", rolledBack.replaySummary().get(0).kind());
            assertEquals(CollabDeliveryState.DISPATCHED, rolledBack.replaySummary().get(0).deliveryState());
        }
    }

    @Test
    void threadUnsubscribeNameSetAndMetadataUpdateFlowThroughAppServerContract() throws Exception {
        CodexAppServer appServer = appServer(new NoOpTurnExecutor());
        BlockingQueue<AppServerNotification> notifications = new LinkedBlockingQueue<>();

        try (CodexAppServerSession session = initializedSession(appServer);
             AutoCloseable ignored = session.subscribe(notifications::add)) {
            ThreadStartResponse started = session.threadStart(new ThreadStartParams("Original thread", "workspace-write", "review-sensitive"));
            ThreadId threadId = started.thread().threadId();
            assertEquals("workspace-write", started.thread().sandboxMode());
            assertEquals("review-sensitive", started.thread().approvalMode());
            assertEquals("1.0-SNAPSHOT", started.thread().cliVersion());

            session.threadNameSet(new ThreadNameSetParams(threadId, "Renamed thread"));
            assertEquals("Renamed thread", session.threadRead(new ThreadReadParams(threadId, false)).thread().title());

            var metadataUpdated = session.threadMetadataUpdate(new ThreadMetadataUpdateParams(
                    threadId,
                    "/workspace/app",
                    "openai",
                    "gpt-5.4",
                    "read-only",
                    "auto",
                    "1234567890abcdef",
                    "main",
                    "git@github.com:org/repo.git",
                    "1.1.0"));
            assertEquals("/workspace/app", metadataUpdated.thread().cwd());
            assertEquals("openai", metadataUpdated.thread().modelProvider());
            assertEquals("gpt-5.4", metadataUpdated.thread().model());
            assertEquals("read-only", metadataUpdated.thread().sandboxMode());
            assertEquals("auto", metadataUpdated.thread().approvalMode());
            assertEquals("1234567890abcdef", metadataUpdated.thread().gitSha());
            assertEquals("main", metadataUpdated.thread().gitBranch());
            assertEquals("git@github.com:org/repo.git", metadataUpdated.thread().gitOriginUrl());
            assertEquals("1.1.0", metadataUpdated.thread().cliVersion());

            var unsubscribed = session.threadUnsubscribe(new ThreadUnsubscribeParams(threadId));
            assertEquals("unsubscribed", unsubscribed.status());
            assertFalse(session.threadLoadedList(new ThreadLoadedListParams()).data().contains(threadId));

            List<AppServerNotification> observed = awaitNotifications(notifications, 5);
            assertTrue(observed.stream().anyMatch(ThreadNameUpdatedNotification.class::isInstance));
            assertTrue(observed.stream().anyMatch(ThreadMetadataUpdatedNotification.class::isInstance));
            assertTrue(observed.stream().anyMatch(ThreadStatusChangedNotification.class::isInstance));
            assertTrue(observed.stream().anyMatch(ThreadClosedNotification.class::isInstance));
        }
    }

    @Test
    void threadUnsubscribeIsConnectionScopedAndOnlyLastSubscriberUnloads() throws Exception {
        CodexAppServer appServer = appServer(new NoOpTurnExecutor());
        BlockingQueue<AppServerNotification> firstSessionNotifications = new LinkedBlockingQueue<>();
        BlockingQueue<AppServerNotification> secondSessionNotifications = new LinkedBlockingQueue<>();

        try (CodexAppServerSession firstSession = initializedSession(appServer);
             AutoCloseable firstSubscription = firstSession.subscribe(firstSessionNotifications::add);
             CodexAppServerSession secondSession = initializedSession(appServer);
             AutoCloseable secondSubscription = secondSession.subscribe(secondSessionNotifications::add);
             CodexAppServerSession thirdSession = initializedSession(appServer)) {
            ThreadId threadId = firstSession.threadStart(new ThreadStartParams("Shared thread")).thread().threadId();
            secondSession.threadResume(new ThreadResumeParams(threadId));

            assertEquals("notSubscribed", thirdSession.threadUnsubscribe(new ThreadUnsubscribeParams(threadId)).status());

            assertEquals("unsubscribed", firstSession.threadUnsubscribe(new ThreadUnsubscribeParams(threadId)).status());
            assertTrue(firstSession.threadLoadedList(new ThreadLoadedListParams()).data().contains(threadId));
            assertTrue(secondSession.threadLoadedList(new ThreadLoadedListParams()).data().contains(threadId));

            assertEquals("unsubscribed", secondSession.threadUnsubscribe(new ThreadUnsubscribeParams(threadId)).status());
            assertFalse(firstSession.threadLoadedList(new ThreadLoadedListParams()).data().contains(threadId));
            assertFalse(secondSession.threadLoadedList(new ThreadLoadedListParams()).data().contains(threadId));

            assertEquals("notLoaded", firstSession.threadUnsubscribe(new ThreadUnsubscribeParams(threadId)).status());

            List<AppServerNotification> firstObserved = awaitNotifications(firstSessionNotifications, 5);
            List<AppServerNotification> secondObserved = awaitNotifications(secondSessionNotifications, 5);
            assertFalse(firstObserved.stream().anyMatch(ThreadClosedNotification.class::isInstance));
            assertFalse(firstObserved.stream().anyMatch(ThreadStatusChangedNotification.class::isInstance));
            assertTrue(secondObserved.stream().anyMatch(ThreadStatusChangedNotification.class::isInstance));
            assertTrue(secondObserved.stream().anyMatch(ThreadClosedNotification.class::isInstance));
        }
    }

    @Test
    void threadResumeOfPersistedThreadLoadsAndPublishesStatusChanged() throws Exception {
        CodexAppServer appServer = appServer(new NoOpTurnExecutor());
        BlockingQueue<AppServerNotification> notifications = new LinkedBlockingQueue<>();

        try (CodexAppServerSession session = initializedSession(appServer);
             AutoCloseable ignored = session.subscribe(notifications::add)) {
            ThreadId threadId = session.threadStart(new ThreadStartParams("Resume thread")).thread().threadId();

            assertEquals("unsubscribed", session.threadUnsubscribe(new ThreadUnsubscribeParams(threadId)).status());
            assertFalse(session.threadLoadedList(new ThreadLoadedListParams()).data().contains(threadId));

            var resumed = session.threadResume(new ThreadResumeParams(threadId));
            assertEquals(threadId, resumed.thread().threadId());
            assertTrue(session.threadLoadedList(new ThreadLoadedListParams()).data().contains(threadId));

            List<AppServerNotification> observed = awaitNotifications(notifications, 5);
            assertTrue(observed.stream().anyMatch(ThreadStatusChangedNotification.class::isInstance));
            assertTrue(observed.stream().anyMatch(ThreadClosedNotification.class::isInstance));
        }
    }

    @Test
    void threadResumeExposesTopLevelReplaySummaryFromReconstruction() throws Exception {
        CodexAppServer appServer = new InProcessCodexAppServer(new DefaultCodexRuntimeGateway(
                new InMemoryConversationStore(),
                new NoOpTurnExecutor(),
                new NoOpContextManager(),
                new ReplaySummaryThreadContextReconstructionService(),
                new NoOpSkillService()));

        try (CodexAppServerSession session = initializedSession(appServer)) {
            ThreadId threadId = session.threadStart(new ThreadStartParams("Resume replay thread")).thread().threadId();

            var resumed = session.threadResume(new ThreadResumeParams(threadId));

            assertEquals(1, resumed.replaySummary().size());
            assertEquals("collaboration", resumed.replaySummary().get(0).kind());
            assertEquals(CollabDeliveryState.DISPATCHED, resumed.replaySummary().get(0).deliveryState());
        }
    }

    @Test
    void threadShellCommandRequiresLoadedThreadAndReturnsThreadScopedResult() throws Exception {
        RecordingShellCommandTool shellCommandTool = new RecordingShellCommandTool();
        CodexAppServer appServer = appServer(new NoOpTurnExecutor(), shellCommandTool);

        try (CodexAppServerSession session = initializedSession(appServer)) {
            ThreadId threadId = session.threadStart(new ThreadStartParams("Shell thread")).thread().threadId();

            ThreadShellCommandResponse response = session.threadShellCommand(
                    new ThreadShellCommandParams(threadId, "printf 'hello from thread'"));

            assertEquals("printf 'hello from thread'", shellCommandTool.lastCommand);
            assertEquals("hello from thread", response.result().stdout());
            assertTrue(response.result().executed());

            session.threadUnsubscribe(new ThreadUnsubscribeParams(threadId));
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> session.threadShellCommand(new ThreadShellCommandParams(threadId, "printf 'again'")));
            assertTrue(exception.getMessage().contains("not loaded"));
        }
    }

    @Test
    void threadShellCommandPublishesCommandExecutionNotifications() throws Exception {
        ExecSessionManager execSessionManager = new InMemoryExecSessionManager();
        ShellCommandTool shellCommandTool = new ShellCommandToolImpl(
                workspaceRoot,
                new PatternCommandApprovalPolicy(PatternCommandApprovalPolicy.Mode.REVIEW_SENSITIVE),
                Duration.ofSeconds(2),
                execSessionManager);
        CodexAppServer appServer = appServer(new InMemoryConversationStore(), new NoOpTurnExecutor(), shellCommandTool, execSessionManager);
        BlockingQueue<AppServerNotification> notifications = new LinkedBlockingQueue<>();

        try (CodexAppServerSession session = initializedSession(appServer);
             AutoCloseable ignored = session.subscribe(notifications::add)) {
            ThreadId threadId = session.threadStart(new ThreadStartParams("Streaming shell thread")).thread().threadId();

            ThreadShellCommandResponse response = session.threadShellCommand(
                    new ThreadShellCommandParams(threadId, "printf 'one\\n'; sleep 0.2; printf 'two\\n'"));

            assertTrue(response.result().success());
            assertTrue(response.result().stdout().contains("one"));
            assertTrue(response.result().stdout().contains("two"));

            List<AppServerNotification> observed = awaitNotifications(notifications, 12);
            String stdout = observed.stream()
                    .filter(CommandExecutionOutputDeltaNotification.class::isInstance)
                    .map(CommandExecutionOutputDeltaNotification.class::cast)
                    .filter(notification -> threadId.equals(notification.commandExecution().threadId()))
                    .map(CommandExecutionOutputDeltaNotification::stdout)
                    .reduce("", String::concat);
            assertTrue(stdout.contains("one"));
            assertTrue(stdout.contains("two"));
            assertTrue(observed.stream().anyMatch(notification ->
                    notification instanceof CommandExecutionCompletedNotification completed
                            && threadId.equals(completed.commandExecution().threadId())
                            && "COMPLETED".equals(completed.commandExecution().status())));
        }
    }

    @Test
    void commandExecRpcStartsWritesAndTerminatesExecSessions() throws Exception {
        ExecSessionManager execSessionManager = new InMemoryExecSessionManager();
        ShellCommandTool shellCommandTool = new ShellCommandToolImpl(
                workspaceRoot,
                new PatternCommandApprovalPolicy(PatternCommandApprovalPolicy.Mode.REVIEW_SENSITIVE),
                Duration.ofSeconds(2),
                execSessionManager);
        CodexAppServer appServer = appServer(new InMemoryConversationStore(), new NoOpTurnExecutor(), shellCommandTool, execSessionManager);

        try (CodexAppServerSession session = initializedSession(appServer)) {
            ThreadId threadId = session.threadStart(new ThreadStartParams("Exec rpc thread")).thread().threadId();

            var started = session.commandExec(new CommandExecParams(
                    threadId,
                    "printf 'one\\n'; sleep 1; printf 'two\\n'",
                    workspaceRoot.toString(),
                    250L,
                    5_000L,
                    Boolean.FALSE));

            assertEquals(threadId, started.commandExecution().threadId());
            assertEquals("RUNNING", started.commandExecution().status());

            var current = started;
            StringBuilder combinedStdout = new StringBuilder(started.stdout());
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while ("RUNNING".equals(current.commandExecution().status()) && System.nanoTime() < deadline) {
                current = session.commandExecWrite(new CommandExecWriteParams(
                        threadId,
                        started.commandExecution().sessionId(),
                        "",
                        250L));
                combinedStdout.append(current.stdout());
            }

            assertTrue(combinedStdout.toString().contains("one"));
            assertTrue(combinedStdout.toString().contains("two"));

            assertEquals("COMPLETED", current.commandExecution().status());

            var resize = session.commandExecResize(new CommandExecResizeParams(
                    threadId,
                    started.commandExecution().sessionId(),
                    120,
                    40));
            assertFalse(resize.applied());
            assertEquals(started.commandExecution().sessionId(), resize.commandExecution().sessionId());

            var terminate = session.commandExecTerminate(new CommandExecTerminateParams(
                    threadId,
                    started.commandExecution().sessionId()));
            assertTrue(terminate.terminated());
            assertEquals(started.commandExecution().sessionId(), terminate.commandExecution().sessionId());
        }
    }

    @Test
    void commandExecRpcSupportsPtyResizeAndTerminalInteractionNotifications() throws Exception {
        ExecSessionManager execSessionManager = new InMemoryExecSessionManager();
        ShellCommandTool shellCommandTool = new ShellCommandToolImpl(
                workspaceRoot,
                new PatternCommandApprovalPolicy(PatternCommandApprovalPolicy.Mode.REVIEW_SENSITIVE),
                Duration.ofSeconds(5),
                execSessionManager);
        CodexAppServer appServer = appServer(new InMemoryConversationStore(), new NoOpTurnExecutor(), shellCommandTool, execSessionManager);
        BlockingQueue<AppServerNotification> notifications = new LinkedBlockingQueue<>();

        try (CodexAppServerSession session = initializedSession(appServer);
             AutoCloseable ignored = session.subscribe(notifications::add)) {
            ThreadId threadId = session.threadStart(new ThreadStartParams("Exec pty thread")).thread().threadId();

            var started = session.commandExec(new CommandExecParams(
                    threadId,
                    "stty size; stty -echo; read value; stty echo; stty size; printf 'got:%s\\n' \"$value\"",
                    workspaceRoot.toString(),
                    250L,
                    5_000L,
                    Boolean.TRUE));

            assertEquals(threadId, started.commandExecution().threadId());
            assertEquals("RUNNING", started.commandExecution().status());

            var resize = session.commandExecResize(new CommandExecResizeParams(
                    threadId,
                    started.commandExecution().sessionId(),
                    120,
                    40));
            assertTrue(resize.applied());

            var written = session.commandExecWrite(new CommandExecWriteParams(
                    threadId,
                    started.commandExecution().sessionId(),
                    "hello\n",
                    1_500L));
            StringBuilder combinedOutput = new StringBuilder(normalizeOutput(started.stdout() + written.stdout()));
            var current = written;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while ("RUNNING".equals(current.commandExecution().status()) && System.nanoTime() < deadline) {
                current = session.commandExecWrite(new CommandExecWriteParams(
                        threadId,
                        started.commandExecution().sessionId(),
                        "",
                        250L));
                combinedOutput.append(normalizeOutput(current.stdout()));
            }
            String fullOutput = combinedOutput.toString();
            assertTrue(fullOutput.contains("24 80"));
            assertTrue(fullOutput.contains("40 120"));
            assertTrue(fullOutput.contains("got:hello"));
            assertEquals("COMPLETED", current.commandExecution().status());

            List<AppServerNotification> observed = awaitNotifications(notifications, 16);
            assertTrue(observed.stream().anyMatch(notification ->
                    notification instanceof CommandExecutionTerminalInteractionNotification interaction
                            && threadId.equals(interaction.commandExecution().threadId())
                            && "resize".equals(interaction.kind())
                            && Integer.valueOf(120).equals(interaction.columns())
                            && Integer.valueOf(40).equals(interaction.rows())));
            assertTrue(observed.stream().anyMatch(notification ->
                    notification instanceof CommandExecutionTerminalInteractionNotification interaction
                            && threadId.equals(interaction.commandExecution().threadId())
                            && "stdin".equals(interaction.kind())
                            && Integer.valueOf(6).equals(interaction.inputLength())));
        }
    }

    @Test
    void threadBackgroundTerminalsCleanRemovesThreadOwnedBackgroundProcessState() throws Exception {
        ExecSessionManager execSessionManager = new InMemoryExecSessionManager();
        CodexAppServer appServer = appServer(new InMemoryConversationStore(), new NoOpTurnExecutor(), new NoOpShellCommandTool(), execSessionManager);

        try (CodexAppServerSession session = initializedSession(appServer)) {
            ThreadId threadId = session.threadStart(new ThreadStartParams("Background thread")).thread().threadId();
            var launched = session.threadShellCommand(new ThreadShellCommandParams(threadId, "sleep 60 &"));

            assertTrue(launched.result().success());
            assertTrue(launched.result().stdout().contains("Background terminal started"));
            assertNotNull(launched.backgroundTerminal());
            assertNotNull(launched.backgroundTerminal().terminalId());
            assertTrue(launched.backgroundTerminal().pid() > 0);
            ExecSessionSummary execSession = execSessionManager.session(new ExecSessionId(launched.backgroundTerminal().terminalId())).orElseThrow();
            assertEquals(threadId, execSession.threadId());
            assertTrue(execSession.running());

            ThreadReadResponse read = session.threadRead(new ThreadReadParams(threadId, false));
            assertEquals(1, read.backgroundTerminals().size());
            assertEquals(launched.backgroundTerminal(), read.backgroundTerminals().get(0));

            var resumed = session.threadResume(new ThreadResumeParams(threadId));
            assertEquals(1, resumed.backgroundTerminals().size());
            assertEquals(launched.backgroundTerminal(), resumed.backgroundTerminals().get(0));

            ThreadBackgroundTerminalsCleanResponse cleaned =
                    session.threadBackgroundTerminalsClean(new ThreadBackgroundTerminalsCleanParams(threadId));
            assertEquals(threadId, cleaned.threadId());
            assertEquals(1, cleaned.cleanedCount());
            ExecSessionSummary terminatedSession = awaitExecSessionNotRunning(execSessionManager, new ExecSessionId(launched.backgroundTerminal().terminalId()));
            assertFalse(terminatedSession.running());

            ThreadReadResponse cleanedRead = session.threadRead(new ThreadReadParams(threadId, false));
            assertTrue(cleanedRead.backgroundTerminals().isEmpty());

            ThreadBackgroundTerminalsCleanResponse secondClean =
                    session.threadBackgroundTerminalsClean(new ThreadBackgroundTerminalsCleanParams(threadId));
            assertEquals(0, secondClean.cleanedCount());
        }
    }

    @Test
    void agentControlMethodsFlowThroughAppServerContract() throws Exception {
        CodexAppServer appServer = appServer(new NoOpTurnExecutor());
        BlockingQueue<AppServerNotification> notifications = new LinkedBlockingQueue<>();

        try (CodexAppServerSession session = initializedSession(appServer);
             AutoCloseable ignored = session.subscribe(notifications::add)) {
            ThreadId rootThreadId = session.threadStart(new ThreadStartParams("Agent parent")).thread().threadId();

            AgentSpawnResponse spawned = session.agentSpawn(new AgentSpawnParams(new AgentSpawnRequest(
                    rootThreadId,
                    "Investigate a task",
                    "Please inspect the workspace",
                    "worker-1",
                    "worker",
                    null,
                    null,
                    null,
                    null)));

            assertEquals(rootThreadId, spawned.agent().parentThreadId());
            assertTrue(session.agentList(new AgentListParams(rootThreadId, false)).agents().stream()
                    .map(AgentSummary::threadId)
                    .anyMatch(spawned.agent().threadId()::equals));

            awaitNotifications(notifications, 10);

            AgentSendMessageResponse sentMessage = session.agentSendMessage(new AgentSendMessageParams(
                    spawned.agent().threadId(),
                    new org.dean.codex.protocol.agent.AgentMessage(rootThreadId, spawned.agent().threadId(), "message only", Instant.now())));
            assertEquals(spawned.agent().threadId(), sentMessage.agent().threadId());

            List<AppServerNotification> mailboxObserved = awaitNotifications(notifications, 10);
            assertTrue(mailboxObserved.stream().anyMatch(AgentMailboxUpdatedNotification.class::isInstance));

            int turnsAfterMessage = awaitTurnCount(session, spawned.agent().threadId(), 1);
            assertEquals(1, turnsAfterMessage);
        }
    }

    @Test
    void agentAssignTaskThroughAppServerContractStartsAQueuedTurn() throws Exception {
        CodexAppServer appServer = appServer(new NoOpTurnExecutor());
        BlockingQueue<AppServerNotification> notifications = new LinkedBlockingQueue<>();

        try (CodexAppServerSession session = initializedSession(appServer);
             AutoCloseable ignored = session.subscribe(notifications::add)) {
            ThreadId rootThreadId = session.threadStart(new ThreadStartParams("Agent parent")).thread().threadId();
            AgentSpawnResponse spawned = session.agentSpawn(new AgentSpawnParams(new AgentSpawnRequest(
                    rootThreadId,
                    "Investigate a task",
                    "Please inspect the workspace",
                    "worker-1",
                    "worker",
                    null,
                    null,
                    null,
                    null)));

            AgentAssignTaskResponse assigned = session.agentAssignTask(new AgentAssignTaskParams(
                    spawned.agent().threadId(),
                    new org.dean.codex.protocol.agent.AgentMessage(rootThreadId, spawned.agent().threadId(), "continue", Instant.now()),
                    false));
            assertEquals(spawned.agent().threadId(), assigned.agent().threadId());
            assertTrue(session.threadRead(new ThreadReadParams(spawned.agent().threadId(), true)).turns().size() >= 1);

            AgentResumeResponse resumed = session.agentResume(new AgentResumeParams(spawned.agent().threadId()));
            assertEquals(spawned.agent().threadId(), resumed.agent().threadId());

            AgentCloseResponse closed = session.agentClose(new AgentCloseParams(spawned.agent().threadId()));
            assertEquals(spawned.agent().threadId(), closed.agent().threadId());
            assertTrue(closed.agent().closed());
        }
    }

    @Test
    void turnInterruptDoesNotCleanBackgroundTerminals() throws Exception {
        InterruptibleTurnExecutor turnExecutor = new InterruptibleTurnExecutor();
        ConversationStore conversationStore = new InMemoryConversationStore();
        CodexRuntimeGateway runtimeGateway = new DefaultCodexRuntimeGateway(
                conversationStore,
                turnExecutor,
                new NoOpContextManager(),
                new NoOpThreadContextReconstructionService(),
                new NoOpSkillService());
        CodexAppServer appServer = new InProcessCodexAppServer(runtimeGateway, new NoOpShellCommandTool(), new InMemoryExecSessionManager());

        try (CodexAppServerSession session = initializedSession(appServer)) {
            ThreadId threadId = session.threadStart(new ThreadStartParams("Interrupt thread")).thread().threadId();
            runtimeGateway.turnStart(threadId, "Wait for interrupt");

            assertTrue(turnExecutor.awaitStarted());
            session.threadShellCommand(new ThreadShellCommandParams(threadId, "sleep 60 &"));

            TurnId runningTurnId = turnExecutor.runningTurnId();
            assertNotNull(runningTurnId);
            assertTrue(session.turnInterrupt(new TurnInterruptParams(threadId, runningTurnId)).accepted());
            assertTrue(turnExecutor.awaitFinished());

            ThreadBackgroundTerminalsCleanResponse cleaned =
                    session.threadBackgroundTerminalsClean(new ThreadBackgroundTerminalsCleanParams(threadId));
            assertEquals(1, cleaned.cleanedCount());
        }
    }

    @Test
    void threadListSupportsFilteringAndPaginationCursor() throws Exception {
        Instant base = Instant.parse("2026-04-01T00:00:00Z");
        ThreadSummary alpha = new ThreadSummary(
                new ThreadId("thread-alpha"),
                "Alpha thread",
                base.plusSeconds(10),
                base.plusSeconds(30),
                1,
                "Alpha preview",
                false,
                "openai",
                "gpt-5.4",
                ThreadStatus.NOT_LOADED,
                List.of(),
                "/tmp/threads/thread-alpha",
                "/workspace/a",
                ThreadSource.CLI,
                true,
                null,
                null,
                null,
                null);
        ThreadSummary beta = new ThreadSummary(
                new ThreadId("thread-beta"),
                "Beta thread",
                base.plusSeconds(20),
                base.plusSeconds(40),
                2,
                "Beta preview",
                false,
                "openai",
                "gpt-5.4",
                ThreadStatus.NOT_LOADED,
                List.of(),
                "/tmp/threads/thread-beta",
                "/workspace/a",
                ThreadSource.CLI,
                true,
                null,
                null,
                null,
                null);
        ThreadSummary archived = new ThreadSummary(
                new ThreadId("thread-archived"),
                "Archived thread",
                base.plusSeconds(30),
                base.plusSeconds(50),
                3,
                "Archived alpha preview",
                false,
                "anthropic",
                "claude-sonnet",
                ThreadStatus.NOT_LOADED,
                List.of(),
                "/tmp/threads/thread-archived",
                "/workspace/b",
                ThreadSource.SUB_AGENT,
                true,
                base.plusSeconds(60),
                null,
                null,
                null);
        CodexRuntimeGateway gateway = new StubThreadListRuntimeGateway(List.of(alpha, beta, archived));
        CodexAppServer appServer = appServer(gateway);

        try (CodexAppServerSession session = initializedSession(appServer)) {
            var filtered = session.threadList(new ThreadListParams(
                    null,
                    10,
                    ThreadSortKey.CREATED_AT,
                    List.of("openai"),
                    List.of(ThreadSourceKind.CLI),
                    Boolean.FALSE,
                    "/workspace/a",
                    "alpha"));
            assertEquals(1, filtered.threads().size());
            assertEquals(alpha.threadId(), filtered.threads().get(0).threadId());
            assertNull(filtered.nextCursor());

            var firstPage = session.threadList(new ThreadListParams(
                    null,
                    1,
                    ThreadSortKey.CREATED_AT,
                    null,
                    List.of(ThreadSourceKind.CLI),
                    Boolean.FALSE,
                    null,
                    null));
            assertEquals(1, firstPage.threads().size());
            assertEquals(beta.threadId(), firstPage.threads().get(0).threadId());
            assertNotNull(firstPage.nextCursor());
            assertNotEquals("1", firstPage.nextCursor());

            var secondPage = session.threadList(new ThreadListParams(
                    firstPage.nextCursor(),
                    1,
                    ThreadSortKey.CREATED_AT,
                    null,
                    List.of(ThreadSourceKind.CLI),
                    Boolean.FALSE,
                    null,
                    null));
            assertEquals(1, secondPage.threads().size());
            assertEquals(alpha.threadId(), secondPage.threads().get(0).threadId());
            assertNull(secondPage.nextCursor());
        }
    }

    @Test
    void threadReadDoesNotLoadPersistedThreadButThreadResumeDoes() throws Exception {
        ConversationStore store = new InMemoryConversationStore();
        ThreadId threadId = store.createThread("Persisted thread");
        CodexAppServer appServer = appServer(store, new NoOpTurnExecutor());

        try (CodexAppServerSession session = initializedSession(appServer)) {
            var metadataOnly = session.threadRead(new ThreadReadParams(threadId, false));
            assertEquals(threadId, metadataOnly.thread().threadId());

            var loadedBeforeResume = session.threadLoadedList(new ThreadLoadedListParams());
            assertFalse(loadedBeforeResume.data().contains(threadId));

            var resumed = session.threadResume(new ThreadResumeParams(threadId));
            assertEquals(threadId, resumed.thread().threadId());

            var loadedAfterResume = session.threadLoadedList(new ThreadLoadedListParams());
            assertTrue(loadedAfterResume.data().contains(threadId));
        }
    }

    @Test
    void sessionRejectsOperationalCallsBeforeInitialization() throws Exception {
        CodexAppServer appServer = appServer(new NoOpTurnExecutor());

        try (CodexAppServerSession session = appServer.connect()) {
            IllegalStateException exception = assertThrows(IllegalStateException.class, session::threadList);
            assertEquals("Not initialized", exception.getMessage());
        }
    }

    @Test
    void sessionRejectsRepeatedInitialize() throws Exception {
        CodexAppServer appServer = appServer(new NoOpTurnExecutor());

        try (CodexAppServerSession session = appServer.connect()) {
            session.initialize(defaultInitializeParams(List.of()));
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> session.initialize(defaultInitializeParams(List.of())));
            assertEquals("Already initialized", exception.getMessage());
        }
    }

    @Test
    void initializeReportsResolvedCodexHome() throws Exception {
        Path codexHome = workspaceRoot.resolve(".d-codex-custom");
        CodexAppServer appServer = appServer(
                new InMemoryConversationStore(),
                new NoOpTurnExecutor(),
                new NoOpShellCommandTool(),
                new InMemoryExecSessionManager(),
                codexHome);

        try (CodexAppServerSession session = appServer.connect()) {
            InitializeResponse response = session.initialize(defaultInitializeParams(List.of()));

            assertEquals(codexHome.toAbsolutePath().normalize().toString(), response.codexHome());
        }
    }

    @Test
    void notificationOptOutSuppressesExactMethodMatchesOnly() throws Exception {
        CodexAppServer appServer = appServer(new NoOpTurnExecutor());
        BlockingQueue<AppServerNotification> notifications = new LinkedBlockingQueue<>();

        try (CodexAppServerSession session = initializedSession(appServer, List.of("thread/started"));
             AutoCloseable ignored = session.subscribe(notifications::add)) {
            ThreadId threadId = session.threadStart(new ThreadStartParams("App thread")).thread().threadId();
            session.threadCompactStart(new ThreadCompactStartParams(threadId));

            List<AppServerNotification> observed = awaitNotifications(notifications, 2);
            assertEquals(2, observed.size());
            assertTrue(observed.stream().anyMatch(ThreadCompactionStartedNotification.class::isInstance));
            assertTrue(observed.stream().anyMatch(ThreadCompactedNotification.class::isInstance));
            assertFalse(observed.stream().anyMatch(ThreadStartedNotification.class::isInstance));
        }
    }

    private CodexAppServer appServer(TurnExecutor turnExecutor) {
        return appServer(new InMemoryConversationStore(), turnExecutor);
    }

    private CodexAppServer appServer(TurnExecutor turnExecutor, ShellCommandTool shellCommandTool) {
        return appServer(new InMemoryConversationStore(), turnExecutor, shellCommandTool);
    }

    private CodexAppServer appServer(CodexRuntimeGateway runtimeGateway) {
        return new InProcessCodexAppServer(runtimeGateway);
    }

    private CodexAppServer appServer(ConversationStore store, TurnExecutor turnExecutor) {
        return appServer(store, turnExecutor, new NoOpShellCommandTool());
    }

    private CodexAppServer appServer(ConversationStore store, TurnExecutor turnExecutor, ShellCommandTool shellCommandTool) {
        return appServer(store, turnExecutor, shellCommandTool, new InMemoryExecSessionManager());
    }

    private CodexAppServer appServer(ConversationStore store,
                                     TurnExecutor turnExecutor,
                                     ShellCommandTool shellCommandTool,
                                     ExecSessionManager execSessionManager) {
        return appServer(store, turnExecutor, shellCommandTool, execSessionManager, null);
    }

    private CodexAppServer appServer(ConversationStore store,
                                     TurnExecutor turnExecutor,
                                     ShellCommandTool shellCommandTool,
                                     ExecSessionManager execSessionManager,
                                     Path codexHome) {
        SkillService skillService = new SkillService() {
            @Override
            public List<SkillMetadata> listSkills(boolean forceReload) {
                return List.of(new SkillMetadata("reviewer", "Review code", "Review code", "/tmp/reviewer/SKILL.md", SkillScope.USER, true));
            }

            @Override
            public List<ResolvedSkill> resolveSkills(String input, boolean forceReload) {
                return List.of();
            }
        };
        ContextManager contextManager = new ContextManager() {
            @Override
            public Optional<ThreadMemory> latestThreadMemory(ThreadId threadId) {
                return Optional.of(new ThreadMemory("memory-1", threadId, "Compacted earlier thread context.", List.of(), 0, Instant.now()));
            }

            @Override
            public ThreadMemory compactThread(ThreadId threadId) {
                return latestThreadMemory(threadId).orElseThrow();
            }
        };
        ThreadContextReconstructionService reconstructionService = threadId -> new ReconstructedThreadContext(
                threadId,
                contextManager.latestThreadMemory(threadId).orElse(null),
                List.of(),
                List.of(),
                List.of(),
                Instant.now());
        InMemoryThreadPromptStateStore promptStateStore = new InMemoryThreadPromptStateStore();
        InMemoryThreadModelSessionStateStore modelSessionStateStore = new InMemoryThreadModelSessionStateStore();
        return new InProcessCodexAppServer(
                new DefaultCodexRuntimeGateway(
                        store,
                        turnExecutor,
                        contextManager,
                        reconstructionService,
                        null,
                        skillService,
                        promptStateStore,
                        () -> new ThreadPromptSnapshot(
                                "Base instructions",
                                List.of("Project instructions:\nStay focused."),
                                Instant.parse("2026-04-09T00:00:00Z")),
                        modelSessionStateStore,
                        4),
                shellCommandTool,
                execSessionManager,
                new DefaultThreadCatalogService(),
                codexHome);
    }

    private static final class StubThreadListRuntimeGateway implements CodexRuntimeGateway {

        private final List<ThreadSummary> threads;

        private StubThreadListRuntimeGateway(List<ThreadSummary> threads) {
            this.threads = List.copyOf(threads);
        }

        @Override
        public ThreadSummary threadStart(String title) {
            throw new UnsupportedOperationException("Not used in this test");
        }

        @Override
        public ThreadSummary threadResume(ThreadId threadId) {
            throw new UnsupportedOperationException("Not used in this test");
        }

        @Override
        public List<ThreadSummary> listThreads() {
            return threads;
        }

        @Override
        public List<ConversationTurn> turns(ThreadId threadId) {
            throw new UnsupportedOperationException("Not used in this test");
        }

        @Override
        public ConversationTurn turn(ThreadId threadId, TurnId turnId) {
            throw new UnsupportedOperationException("Not used in this test");
        }

        @Override
        public ReconstructedThreadContext reconstructThreadContext(ThreadId threadId) {
            throw new UnsupportedOperationException("Not used in this test");
        }

        @Override
        public Optional<ThreadMemory> latestThreadMemory(ThreadId threadId) {
            return Optional.empty();
        }

        @Override
        public ThreadMemory compactThread(ThreadId threadId) {
            throw new UnsupportedOperationException("Not used in this test");
        }

        @Override
        public List<SkillMetadata> listSkills(boolean forceReload) {
            return List.of();
        }

        @Override
        public org.dean.codex.protocol.runtime.RuntimeTurn turnStart(ThreadId threadId, String input) {
            throw new UnsupportedOperationException("Not used in this test");
        }

        @Override
        public org.dean.codex.protocol.runtime.RuntimeTurn turnResume(ThreadId threadId, TurnId turnId) {
            throw new UnsupportedOperationException("Not used in this test");
        }

        @Override
        public boolean turnSteer(ThreadId threadId, TurnId turnId, String input) {
            throw new UnsupportedOperationException("Not used in this test");
        }

        @Override
        public boolean turnInterrupt(ThreadId threadId, TurnId turnId) {
            throw new UnsupportedOperationException("Not used in this test");
        }

        @Override
        public AutoCloseable subscribe(ThreadId threadId, Consumer<org.dean.codex.protocol.runtime.RuntimeNotification> listener) {
            throw new UnsupportedOperationException("Not used in this test");
        }
    }

    private static final class InMemoryThreadPromptStateStore implements ThreadPromptStateStore {

        private final java.util.Map<ThreadId, ThreadPromptSnapshot> snapshots = new java.util.HashMap<>();

        @Override
        public Optional<ThreadPromptSnapshot> read(ThreadId threadId) {
            return Optional.ofNullable(snapshots.get(threadId));
        }

        @Override
        public ThreadPromptSnapshot write(ThreadId threadId, ThreadPromptSnapshot snapshot) {
            snapshots.put(threadId, snapshot);
            return snapshot;
        }
    }

    private static final class InMemoryThreadModelSessionStateStore implements ThreadModelSessionStateStore {

        private final java.util.Map<ThreadId, ThreadModelSessionSnapshot> snapshots = new java.util.HashMap<>();

        @Override
        public Optional<ThreadModelSessionSnapshot> read(ThreadId threadId) {
            return Optional.ofNullable(snapshots.get(threadId));
        }

        @Override
        public ThreadModelSessionSnapshot write(ThreadId threadId, ThreadModelSessionSnapshot snapshot) {
            snapshots.put(threadId, snapshot);
            return snapshot;
        }
    }

    private CodexAppServerSession initializedSession(CodexAppServer appServer) {
        return initializedSession(appServer, List.of());
    }

    private CodexAppServerSession initializedSession(CodexAppServer appServer, List<String> optOutMethods) {
        CodexAppServerSession session = appServer.connect();
        session.initialize(defaultInitializeParams(optOutMethods));
        session.initialized(new InitializedNotification());
        return session;
    }

    private InitializeParams defaultInitializeParams(List<String> optOutMethods) {
        return new InitializeParams(
                new AppServerClientInfo("codex-java-test", "Codex Java Test", "1.0-SNAPSHOT"),
                new AppServerCapabilities(false, optOutMethods));
    }

    private List<AppServerNotification> awaitNotifications(BlockingQueue<AppServerNotification> notifications) throws InterruptedException {
        return awaitNotifications(notifications, 10);
    }

    private List<AppServerNotification> awaitNotifications(BlockingQueue<AppServerNotification> notifications, int maxNotifications) throws InterruptedException {
        List<AppServerNotification> observed = new ArrayList<>();
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadlineNanos && observed.size() < maxNotifications) {
            AppServerNotification notification = notifications.poll(100, TimeUnit.MILLISECONDS);
            if (notification == null) {
                continue;
            }
            observed.add(notification);
            if (notification instanceof TurnCompletedNotification) {
                break;
            }
        }
        return observed;
    }

    private List<AppServerNotification> awaitTurnCompletions(BlockingQueue<AppServerNotification> notifications, int expectedCompletions) throws InterruptedException {
        List<AppServerNotification> observed = new ArrayList<>();
        int completions = 0;
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadlineNanos && completions < expectedCompletions) {
            AppServerNotification notification = notifications.poll(100, TimeUnit.MILLISECONDS);
            if (notification == null) {
                continue;
            }
            observed.add(notification);
            if (notification instanceof TurnCompletedNotification) {
                completions++;
            }
        }
        assertEquals(expectedCompletions, completions, "Expected turn completion notifications before continuing");
        return observed;
    }

    private static final class NoOpTurnExecutor implements TurnExecutor {
        @Override
        public CodexTurnResult executeTurn(ThreadId threadId, String input) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CodexTurnResult executeTurn(ThreadId threadId, TurnId turnId, String input, Consumer<TurnItem> itemConsumer, TurnControl turnControl) {
            return new CodexTurnResult(threadId, turnId, TurnStatus.COMPLETED, List.of(), "done");
        }

        @Override
        public CodexTurnResult resumeTurn(ThreadId threadId, TurnId turnId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RawItemTurnExecutor implements TurnExecutor {
        @Override
        public CodexTurnResult executeTurn(ThreadId threadId, String input) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CodexTurnResult executeTurn(ThreadId threadId, TurnId turnId, String input, Consumer<TurnItem> itemConsumer, TurnControl turnControl) {
            RawModelOutputItem rawItem = new RawModelOutputItem(
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
                    Instant.parse("2026-04-13T00:00:00Z"));
            AgentMessageItem assistant = new AgentMessageItem(
                    new ItemId("assistant-1"),
                    "done",
                    Instant.parse("2026-04-13T00:00:01Z"));
            itemConsumer.accept(rawItem);
            itemConsumer.accept(assistant);
            return new CodexTurnResult(threadId, turnId, TurnStatus.COMPLETED, List.of(rawItem, assistant), "done");
        }

        @Override
        public CodexTurnResult resumeTurn(ThreadId threadId, TurnId turnId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class NoOpSkillService implements SkillService {
        @Override
        public List<SkillMetadata> listSkills(boolean forceReload) {
            return List.of();
        }

        @Override
        public List<ResolvedSkill> resolveSkills(String input, boolean forceReload) {
            return List.of();
        }
    }

    private static final class NoOpShellCommandTool implements ShellCommandTool {
        @Override
        public ShellCommandResult runCommand(String command) {
            return new ShellCommandResult(
                    true,
                    command,
                    0,
                    "",
                    "",
                    false,
                    "/tmp/workspace",
                    true,
                    CommandApprovalDecision.ALLOW,
                    "allowed",
                    "");
        }

        @Override
        public ShellCommandResult runApprovedCommand(String command) {
            return runCommand(command);
        }
    }

    private static final class InterruptibleTurnExecutor implements TurnExecutor {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch finished = new CountDownLatch(1);
        private final AtomicReference<TurnId> runningTurnId = new AtomicReference<>();

        @Override
        public CodexTurnResult executeTurn(ThreadId threadId, String input) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CodexTurnResult executeTurn(ThreadId threadId, TurnId turnId, String input, Consumer<TurnItem> itemConsumer, TurnControl turnControl) {
            runningTurnId.set(turnId);
            started.countDown();
            while (!turnControl.interruptionRequested()) {
                try {
                    Thread.sleep(10);
                }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            finished.countDown();
            return new CodexTurnResult(threadId, turnId, TurnStatus.INTERRUPTED, List.of(), "Interrupted");
        }

        @Override
        public CodexTurnResult resumeTurn(ThreadId threadId, TurnId turnId) {
            throw new UnsupportedOperationException();
        }

        boolean awaitStarted() throws InterruptedException {
            return started.await(2, TimeUnit.SECONDS);
        }

        boolean awaitFinished() throws InterruptedException {
            return finished.await(2, TimeUnit.SECONDS);
        }

        TurnId runningTurnId() {
            return runningTurnId.get();
        }
    }

    private static final class RecordingShellCommandTool implements ShellCommandTool {
        private String lastCommand;

        @Override
        public ShellCommandResult runCommand(String command) {
            this.lastCommand = command;
            return new ShellCommandResult(
                    true,
                    command,
                    0,
                    "hello from thread",
                    "",
                    false,
                    "/tmp/workspace",
                    true,
                    CommandApprovalDecision.ALLOW,
                    "allowed",
                    "");
        }

        @Override
        public ShellCommandResult runApprovedCommand(String command) {
            return runCommand(command);
        }
    }

    private static final class NoOpContextManager implements ContextManager {
        @Override
        public Optional<ThreadMemory> latestThreadMemory(ThreadId threadId) {
            return Optional.empty();
        }

        @Override
        public ThreadMemory compactThread(ThreadId threadId) {
            return new ThreadMemory("memory-0", threadId, "summary", List.of(), 0, Instant.now());
        }
    }

    private int awaitTurnCount(CodexAppServerSession session, ThreadId threadId, int expectedCount) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadlineNanos) {
            int count = session.threadRead(new ThreadReadParams(threadId, true)).turns().size();
            if (count >= expectedCount) {
                return count;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Timed out waiting for turn count " + expectedCount + " for " + threadId.value());
    }

    private ExecSessionSummary awaitExecSessionNotRunning(ExecSessionManager execSessionManager, ExecSessionId sessionId) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadlineNanos) {
            ExecSessionSummary summary = execSessionManager.session(sessionId).orElse(null);
            if (summary != null && !summary.running()) {
                return summary;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Timed out waiting for exec session to stop: " + sessionId.value());
    }

    private String normalizeOutput(String value) {
        return value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static final class NoOpThreadContextReconstructionService implements ThreadContextReconstructionService {
        @Override
        public ReconstructedThreadContext reconstruct(ThreadId threadId) {
            return new ReconstructedThreadContext(threadId, null, List.of(), List.of(), List.of(), Instant.now());
        }
    }

    private static final class ReplaySummaryThreadContextReconstructionService implements ThreadContextReconstructionService {
        @Override
        public ReconstructedThreadContext reconstruct(ThreadId threadId) {
            ReconstructedReplayItem replayItem = new ReconstructedReplayItem(
                    new TurnId("turn-replay"),
                    "collaboration",
                    "collabToolCall: spawn_agent completed",
                    CollabDeliveryState.DISPATCHED,
                    "agent-1 pending=0 seq=2",
                    "mailbox_updated",
                    Instant.now());
            return new ReconstructedThreadContext(
                    threadId,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(replayItem),
                    Instant.now());
        }
    }

    private static final class SteeringTurnExecutor implements TurnExecutor {
        @Override
        public CodexTurnResult executeTurn(ThreadId threadId, String input) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CodexTurnResult executeTurn(ThreadId threadId, TurnId turnId, String input, Consumer<TurnItem> itemConsumer, TurnControl turnControl) {
            long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while (System.nanoTime() < deadlineNanos) {
                List<String> steeringInputs = turnControl.drainSteeringInputs();
                if (!steeringInputs.isEmpty()) {
                    UserMessageItem steeringItem = new UserMessageItem(new ItemId("steer-1"), steeringInputs.get(0), Instant.now());
                    itemConsumer.accept(steeringItem);
                    return new CodexTurnResult(threadId, turnId, TurnStatus.COMPLETED, List.of(steeringItem), "done");
                }
                try {
                    Thread.sleep(10);
                }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            return new CodexTurnResult(threadId, turnId, TurnStatus.COMPLETED, List.of(), "done");
        }

        @Override
        public CodexTurnResult resumeTurn(ThreadId threadId, TurnId turnId) {
            throw new UnsupportedOperationException();
        }
    }
}
