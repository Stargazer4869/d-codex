package org.dean.codex.runtime.springai.appserver;

import org.dean.codex.core.agent.AgentControl;
import org.dean.codex.core.appserver.CodexAppServer;
import org.dean.codex.core.appserver.CodexAppServerSession;
import org.dean.codex.core.runtime.CodexRuntimeGateway;
import org.dean.codex.core.tool.local.ShellCommandTool;
import org.dean.codex.protocol.agent.AgentMessage;
import org.dean.codex.protocol.agent.AgentMailboxState;
import org.dean.codex.protocol.agent.AgentSpawnRequest;
import org.dean.codex.protocol.agent.AgentSummary;
import org.dean.codex.protocol.agent.AgentWaitResult;
import org.dean.codex.protocol.appserver.AgentCloseParams;
import org.dean.codex.protocol.appserver.AgentCloseResponse;
import org.dean.codex.protocol.appserver.AgentAssignTaskParams;
import org.dean.codex.protocol.appserver.AgentAssignTaskResponse;
import org.dean.codex.protocol.appserver.AgentListParams;
import org.dean.codex.protocol.appserver.AgentListResponse;
import org.dean.codex.protocol.appserver.AgentMailboxUpdatedNotification;
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
import org.dean.codex.protocol.appserver.AppServerNotification;
import org.dean.codex.protocol.appserver.BackgroundTerminalSummary;
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
import org.dean.codex.protocol.appserver.ThreadClosedNotification;
import org.dean.codex.protocol.appserver.ThreadListParams;
import org.dean.codex.protocol.appserver.ThreadListResponse;
import org.dean.codex.protocol.appserver.ThreadLoadedListParams;
import org.dean.codex.protocol.appserver.ThreadLoadedListResponse;
import org.dean.codex.protocol.appserver.ThreadReadParams;
import org.dean.codex.protocol.appserver.ThreadReadResponse;
import org.dean.codex.protocol.appserver.ThreadMetadataUpdateParams;
import org.dean.codex.protocol.appserver.ThreadMetadataUpdateResponse;
import org.dean.codex.protocol.appserver.ThreadMetadataUpdatedNotification;
import org.dean.codex.protocol.appserver.ThreadNameSetParams;
import org.dean.codex.protocol.appserver.ThreadNameSetResponse;
import org.dean.codex.protocol.appserver.ThreadNameUpdatedNotification;
import org.dean.codex.protocol.appserver.ThreadStatusChangedNotification;
import org.dean.codex.protocol.appserver.ThreadShellCommandParams;
import org.dean.codex.protocol.appserver.ThreadShellCommandResponse;
import org.dean.codex.protocol.appserver.ThreadRollbackParams;
import org.dean.codex.protocol.appserver.ThreadRollbackResponse;
import org.dean.codex.protocol.appserver.ThreadResumeParams;
import org.dean.codex.protocol.appserver.ThreadResumeResponse;
import org.dean.codex.protocol.appserver.ThreadSortKey;
import org.dean.codex.protocol.appserver.ThreadStartParams;
import org.dean.codex.protocol.appserver.ThreadStartResponse;
import org.dean.codex.protocol.appserver.ThreadStartedNotification;
import org.dean.codex.protocol.appserver.ThreadSourceKind;
import org.dean.codex.protocol.appserver.ThreadUnarchiveParams;
import org.dean.codex.protocol.appserver.ThreadUnarchiveResponse;
import org.dean.codex.protocol.appserver.ThreadUnsubscribeParams;
import org.dean.codex.protocol.appserver.ThreadUnsubscribeResponse;
import org.dean.codex.protocol.appserver.TurnCompletedNotification;
import org.dean.codex.protocol.appserver.TurnInterruptParams;
import org.dean.codex.protocol.appserver.TurnInterruptResponse;
import org.dean.codex.protocol.appserver.TurnItemNotification;
import org.dean.codex.protocol.appserver.TurnResumeParams;
import org.dean.codex.protocol.appserver.TurnResumeResponse;
import org.dean.codex.protocol.appserver.TurnStartParams;
import org.dean.codex.protocol.appserver.TurnStartResponse;
import org.dean.codex.protocol.appserver.TurnStartedNotification;
import org.dean.codex.protocol.appserver.TurnSteerParams;
import org.dean.codex.protocol.appserver.TurnSteerResponse;
import org.dean.codex.protocol.tool.ShellCommandResult;
import org.dean.codex.protocol.conversation.ConversationTurn;
import org.dean.codex.protocol.conversation.ThreadId;
import org.dean.codex.protocol.conversation.ThreadSummary;
import org.dean.codex.protocol.runtime.RuntimeNotification;
import org.dean.codex.protocol.runtime.RuntimeNotificationType;
import org.dean.codex.protocol.context.ReconstructedThreadContext;
import org.dean.codex.runtime.springai.thread.DefaultThreadCatalogService;
import org.dean.codex.runtime.springai.thread.ThreadCatalogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Component
public class InProcessCodexAppServer implements CodexAppServer {

    private final CodexRuntimeGateway runtimeGateway;
    private final ShellCommandTool shellCommandTool;
    private final ThreadCatalogService threadCatalogService;
    private final Map<ThreadId, List<BackgroundTerminalHandle>> backgroundTerminals = new ConcurrentHashMap<>();

    @Autowired
    public InProcessCodexAppServer(CodexRuntimeGateway runtimeGateway,
                                   ShellCommandTool shellCommandTool,
                                   ThreadCatalogService threadCatalogService) {
        this.runtimeGateway = runtimeGateway;
        this.shellCommandTool = shellCommandTool;
        this.threadCatalogService = threadCatalogService == null ? new DefaultThreadCatalogService() : threadCatalogService;
    }

    public InProcessCodexAppServer(CodexRuntimeGateway runtimeGateway) {
        this(runtimeGateway, null, new DefaultThreadCatalogService());
    }

    public InProcessCodexAppServer(CodexRuntimeGateway runtimeGateway, ShellCommandTool shellCommandTool) {
        this(runtimeGateway, shellCommandTool, new DefaultThreadCatalogService());
    }

    @Override
    public CodexAppServerSession connect() {
        return new Session();
    }

    private final class Session implements CodexAppServerSession {

        private final CopyOnWriteArrayList<Consumer<AppServerNotification>> subscribers = new CopyOnWriteArrayList<>();
        private final Map<ThreadId, AutoCloseable> runtimeSubscriptions = new ConcurrentHashMap<>();
        private final Set<String> optOutNotificationMethods = new HashSet<>();
        private String clientVersion;
        private boolean initializeCalled;
        private boolean initializedAcknowledged;

        @Override
        public synchronized InitializeResponse initialize(InitializeParams params) {
            if (initializeCalled) {
                throw new IllegalStateException("Already initialized");
            }
            initializeCalled = true;
            optOutNotificationMethods.clear();
            clientVersion = params == null || params.clientInfo() == null ? null : normalizeClientVersion(params.clientInfo().version());
            if (params != null && params.capabilities() != null) {
                optOutNotificationMethods.addAll(params.capabilities().optOutNotificationMethods());
            }
            return new InitializeResponse(
                    buildUserAgent(params),
                    Path.of(System.getProperty("user.home"), ".codex-java").toAbsolutePath().normalize().toString(),
                    "desktop",
                    System.getProperty("os.name", "unknown"));
        }

        @Override
        public synchronized void initialized(InitializedNotification notification) {
            if (!initializeCalled) {
                throw new IllegalStateException("Not initialized");
            }
            initializedAcknowledged = true;
        }

        @Override
        public ThreadStartResponse threadStart(ThreadStartParams params) {
            ensureReady();
            ThreadSummary thread = runtimeGateway.threadStart(params == null ? "" : params.title());
            String cliVersion = currentClientVersion();
            if (params != null && (params.sandboxMode() != null || params.approvalMode() != null || cliVersion != null)) {
                thread = applyMetadataUpdate(
                        thread,
                        thread.threadId(),
                        null,
                        null,
                        null,
                        params.sandboxMode(),
                        params.approvalMode(),
                        null,
                        null,
                        null,
                        cliVersion);
            }
            ensureRuntimeSubscriptions(List.of(thread.threadId()));
            publish(new ThreadStartedNotification(thread));
            return new ThreadStartResponse(thread);
        }

        @Override
        public ThreadResumeResponse threadResume(ThreadResumeParams params) {
            ensureReady();
            ThreadId threadId = requireThreadId(params);
            boolean alreadyLoaded = runtimeGateway.loadedThreads().contains(threadId);
            ThreadSummary thread = runtimeGateway.threadResume(threadId);
            String cliVersion = currentClientVersion();
            if (cliVersion != null) {
                thread = applyMetadataUpdate(thread, thread.threadId(), null, null, null, null, null, null, null, null, cliVersion);
            }
            ReconstructedThreadContext reconstructedContext = runtimeGateway.reconstructThreadContext(threadId);
            ensureRuntimeSubscriptions(loadedRelatedThreadIds(thread.threadId()));
            if (!alreadyLoaded) {
                publish(new ThreadStatusChangedNotification(thread));
            }
            return new ThreadResumeResponse(thread, reconstructedContext.replaySummary(), activeBackgroundTerminals(threadId));
        }

        @Override
        public ThreadListResponse threadList(ThreadListParams params) {
            ensureReady();
            return threadCatalogService.listThreads(runtimeGateway.listThreads(), params);
        }

        @Override
        public ThreadLoadedListResponse threadLoadedList(ThreadLoadedListParams params) {
            ensureReady();
            List<ThreadId> loadedThreadIds = runtimeGateway.loadedThreads();
            int offset = decodeCursor(params == null ? null : params.cursor());
            int limit = normalizeLimit(params == null ? null : params.limit(), loadedThreadIds.size());
            int endExclusive = Math.min(offset + limit, loadedThreadIds.size());
            List<ThreadId> page = loadedThreadIds.subList(Math.min(offset, loadedThreadIds.size()), endExclusive);
            String nextCursor = endExclusive < loadedThreadIds.size() ? Integer.toString(endExclusive) : null;
            return new ThreadLoadedListResponse(page, nextCursor);
        }

        @Override
        public ThreadReadResponse threadRead(ThreadReadParams params) {
            ensureReady();
            ThreadId threadId = requireThreadId(params);
            ThreadSummary thread = runtimeGateway.listThreads().stream()
                    .filter(summary -> summary.threadId().equals(threadId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown thread id: " + threadId.value()));
            List<ThreadSummary> threadTree = runtimeGateway.relatedThreads(threadId);
            ensureRuntimeSubscriptions(threadTree.stream()
                    .filter(ThreadSummary::loaded)
                    .map(ThreadSummary::threadId)
                    .toList());
            boolean includeTurns = params != null && params.includeTurns();
            ReconstructedThreadContext reconstructedContext = includeTurns
                    ? runtimeGateway.reconstructThreadContext(threadId)
                    : null;
            return new ThreadReadResponse(
                    thread,
                    includeTurns ? runtimeGateway.turns(threadId) : List.of(),
                    includeTurns ? runtimeGateway.latestThreadMemory(threadId).orElse(null) : null,
                    reconstructedContext,
                    reconstructedContext == null ? List.of() : reconstructedContext.replaySummary(),
                    activeBackgroundTerminals(threadId),
                    runtimeGateway.threadTreeRoot(threadId),
                    threadTree.stream()
                            .filter(summary -> !summary.threadId().equals(threadId))
                            .toList());
        }

        @Override
        public ThreadForkResponse threadFork(ThreadForkParams params) {
            ensureReady();
            if (params == null || params.threadId() == null) {
                throw new IllegalArgumentException("threadId is required");
            }
            ThreadSummary thread = runtimeGateway.threadFork(params);
            String cliVersion = currentClientVersion();
            if (cliVersion != null) {
                thread = applyMetadataUpdate(thread, thread.threadId(), null, null, null, null, null, null, null, null, cliVersion);
            }
            ensureRuntimeSubscriptions(List.of(thread.threadId()));
            publish(new ThreadStartedNotification(thread));
            return new ThreadForkResponse(thread);
        }

        @Override
        public ThreadArchiveResponse threadArchive(ThreadArchiveParams params) {
            ensureReady();
            return new ThreadArchiveResponse(runtimeGateway.threadArchive(requireThreadId(params)));
        }

        @Override
        public ThreadUnarchiveResponse threadUnarchive(ThreadUnarchiveParams params) {
            ensureReady();
            return new ThreadUnarchiveResponse(runtimeGateway.threadUnarchive(requireThreadId(params)));
        }

        @Override
        public ThreadRollbackResponse threadRollback(ThreadRollbackParams params) {
            ensureReady();
            ThreadId threadId = requireThreadId(params);
            if (params.numTurns() < 1) {
                throw new IllegalArgumentException("numTurns must be >= 1");
            }
            ThreadSummary thread = runtimeGateway.threadRollback(threadId, params.numTurns());
            ReconstructedThreadContext reconstructedContext = runtimeGateway.reconstructThreadContext(threadId);
            return new ThreadRollbackResponse(thread, runtimeGateway.turns(threadId), reconstructedContext.replaySummary());
        }

        @Override
        public ThreadUnsubscribeResponse threadUnsubscribe(ThreadUnsubscribeParams params) {
            ensureReady();
            ThreadId threadId = requireThreadId(params);
            boolean loaded = runtimeGateway.loadedThreads().contains(threadId);
            AutoCloseable runtimeSubscription = runtimeSubscriptions.remove(threadId);
            if (runtimeSubscription == null) {
                return new ThreadUnsubscribeResponse(loaded ? "notSubscribed" : "notLoaded");
            }
            ThreadSummary closedThread = runtimeGateway.listThreads().stream()
                    .filter(thread -> thread.threadId().equals(threadId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown thread id: " + threadId.value()));
            try {
                runtimeSubscription.close();
            }
            catch (Exception exception) {
                throw new IllegalStateException("Unable to unsubscribe thread " + threadId.value(), exception);
            }
            if (!loaded) {
                return new ThreadUnsubscribeResponse("notLoaded");
            }
            if (runtimeGateway.threadSubscriptionCount(threadId) == 0) {
                runtimeGateway.unloadThread(threadId);
                ThreadSummary unloadedThread = runtimeGateway.listThreads().stream()
                        .filter(thread -> thread.threadId().equals(threadId))
                        .findFirst()
                        .orElse(closedThread);
                publish(new ThreadStatusChangedNotification(unloadedThread));
                publish(new ThreadClosedNotification(unloadedThread));
            }
            return new ThreadUnsubscribeResponse("unsubscribed");
        }

        @Override
        public ThreadNameSetResponse threadNameSet(ThreadNameSetParams params) {
            ensureReady();
            ThreadId threadId = requireThreadId(params);
            ThreadSummary updated = runtimeGateway.renameThread(threadId, params.title());
            publish(new ThreadNameUpdatedNotification(updated));
            return new ThreadNameSetResponse();
        }

        @Override
        public ThreadMetadataUpdateResponse threadMetadataUpdate(ThreadMetadataUpdateParams params) {
            ensureReady();
            ThreadId threadId = requireThreadId(params);
            String cliVersion = params.cliVersion() == null ? currentClientVersion() : params.cliVersion();
            ThreadSummary current = runtimeGateway.listThreads().stream()
                    .filter(thread -> thread.threadId().equals(threadId))
                    .findFirst()
                    .orElse(null);
            ThreadSummary updated = applyMetadataUpdate(
                    current,
                    threadId,
                    params.cwd(),
                    params.modelProvider(),
                    params.model(),
                    params.sandboxMode(),
                    params.approvalMode(),
                    params.gitSha(),
                    params.gitBranch(),
                    params.gitOriginUrl(),
                    cliVersion);
            publish(new ThreadMetadataUpdatedNotification(updated));
            return new ThreadMetadataUpdateResponse(updated);
        }

        private ThreadSummary applyMetadataUpdate(ThreadSummary current,
                                                 ThreadId threadId,
                                                 String cwd,
                                                 String modelProvider,
                                                 String model,
                                                 String sandboxMode,
                                                 String approvalMode,
                                                 String gitSha,
                                                 String gitBranch,
                                                 String gitOriginUrl,
                                                 String cliVersion) {
            ThreadSummary updated = runtimeGateway.updateThreadMetadata(
                    threadId,
                    cwd,
                    modelProvider,
                    model,
                    sandboxMode,
                    approvalMode,
                    gitSha,
                    gitBranch,
                    gitOriginUrl,
                    cliVersion);
            if (updated.promptState() == null && current != null && current.promptState() != null) {
                return updated.withPromptState(current.promptState());
            }
            return updated;
        }

        @Override
        public ThreadShellCommandResponse threadShellCommand(ThreadShellCommandParams params) {
            ensureReady();
            ThreadId threadId = requireThreadId(params);
            requireLoadedThread(threadId);
            String command = params == null ? null : params.command();
            if (isBackgroundCommand(command)) {
                BackgroundTerminalHandle handle = launchBackgroundTerminal(threadId, command);
                return new ThreadShellCommandResponse(handle.result(), handle.summary());
            }
            if (shellCommandTool == null) {
                throw new IllegalStateException("Thread shell command service is not configured");
            }
            ShellCommandResult result = shellCommandTool.runCommand(command);
            return new ThreadShellCommandResponse(result);
        }

        @Override
        public ThreadBackgroundTerminalsCleanResponse threadBackgroundTerminalsClean(ThreadBackgroundTerminalsCleanParams params) {
            ensureReady();
            ThreadId threadId = requireThreadId(params);
            List<BackgroundTerminalHandle> handles = backgroundTerminals.remove(threadId);
            if (handles == null || handles.isEmpty()) {
                return new ThreadBackgroundTerminalsCleanResponse(threadId, 0);
            }
            int cleanedCount = 0;
            for (BackgroundTerminalHandle handle : handles) {
                if (terminateBackgroundTerminal(handle)) {
                    cleanedCount++;
                }
            }
            return new ThreadBackgroundTerminalsCleanResponse(threadId, cleanedCount);
        }

        @Override
        public AgentSpawnResponse agentSpawn(AgentSpawnParams params) {
            ensureReady();
            AgentSpawnRequest request = params == null ? null : params.request();
            AgentSummary summary = agentControl().spawnAgent(request);
            ensureRuntimeSubscriptions(List.of(summary.threadId()));
            ThreadSummary spawnedThread = runtimeGateway.listThreads().stream()
                    .filter(thread -> thread.threadId().equals(summary.threadId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Unable to load spawned agent thread: " + summary.threadId().value()));
            publish(new ThreadStartedNotification(spawnedThread));
            publishMailboxUpdate(summary.threadId());
            return new AgentSpawnResponse(summary);
        }

        @Override
        public AgentSendInputResponse agentSendInput(AgentSendInputParams params) {
            return new AgentSendInputResponse(agentAssignTask(new AgentAssignTaskParams(
                    params == null ? null : params.agentThreadId(),
                    params == null ? null : params.message(),
                    params != null && params.interrupt())).agent());
        }

        @Override
        public AgentSendMessageResponse agentSendMessage(AgentSendMessageParams params) {
            ensureReady();
            ThreadId agentThreadId = params == null ? null : params.agentThreadId();
            ensureRuntimeSubscriptions(List.of(agentThreadId));
            AgentMessage message = params == null ? null : params.message();
            AgentSummary summary = agentControl().sendMessage(agentThreadId, message);
            publishMailboxUpdate(agentThreadId);
            return new AgentSendMessageResponse(summary);
        }

        @Override
        public AgentAssignTaskResponse agentAssignTask(AgentAssignTaskParams params) {
            ensureReady();
            ThreadId agentThreadId = params == null ? null : params.agentThreadId();
            ensureRuntimeSubscriptions(List.of(agentThreadId));
            AgentMessage message = params == null ? null : params.message();
            boolean interrupt = params != null && params.interrupt();
            AgentSummary summary = agentControl().assignTask(agentThreadId, message, interrupt);
            publishMailboxUpdate(agentThreadId);
            return new AgentAssignTaskResponse(summary);
        }

        @Override
        public AgentWaitResponse agentWait(AgentWaitParams params) {
            ensureReady();
            List<ThreadId> threadIds = params == null ? List.of() : params.agentThreadIds();
            long timeoutMillis = params == null ? 1000L : params.timeoutMillis();
            AgentWaitResult result = agentControl().waitAgent(threadIds, timeoutMillis);
            return new AgentWaitResponse(result);
        }

        @Override
        public AgentResumeResponse agentResume(AgentResumeParams params) {
            ensureReady();
            ThreadId agentThreadId = params == null ? null : params.agentThreadId();
            AgentSummary summary = agentControl().resumeAgent(agentThreadId);
            ensureRuntimeSubscriptions(List.of(summary.threadId()));
            publishMailboxUpdate(summary.threadId());
            return new AgentResumeResponse(summary);
        }

        @Override
        public AgentCloseResponse agentClose(AgentCloseParams params) {
            ensureReady();
            ThreadId agentThreadId = params == null ? null : params.agentThreadId();
            AgentSummary summary = agentControl().closeAgent(agentThreadId);
            return new AgentCloseResponse(summary);
        }

        @Override
        public AgentListResponse agentList(AgentListParams params) {
            ensureReady();
            ThreadId parentThreadId = params == null ? null : params.parentThreadId();
            boolean recursive = params != null && params.recursive();
            return new AgentListResponse(agentControl().listAgents(parentThreadId, recursive));
        }

        @Override
        public ThreadCompactStartResponse threadCompactStart(ThreadCompactStartParams params) {
            ensureReady();
            ThreadId threadId = requireThreadId(params);
            String compactionId = UUID.randomUUID().toString();
            Instant startedAt = Instant.now();
            ThreadCompaction startedCompaction = new ThreadCompaction(
                    compactionId,
                    threadId,
                    List.of(),
                    0,
                    "",
                    startedAt,
                    null);
            publish(new ThreadCompactionStartedNotification(startedCompaction));

            var threadMemory = runtimeGateway.compactThread(threadId);
            ThreadCompaction completedCompaction = new ThreadCompaction(
                    compactionId,
                    threadId,
                    threadMemory.sourceTurnIds(),
                    threadMemory.compactedTurnCount(),
                    threadMemory.summary(),
                    startedAt,
                    threadMemory.createdAt());
            publish(new ThreadCompactedNotification(completedCompaction));
            return new ThreadCompactStartResponse(completedCompaction, threadMemory);
        }

        @Override
        public TurnStartResponse turnStart(TurnStartParams params) {
            ensureReady();
            ThreadId threadId = requireThreadId(params);
            ensureRuntimeSubscriptions(List.of(threadId));
            return new TurnStartResponse(runtimeGateway.turnStart(threadId, params.input()));
        }

        @Override
        public TurnResumeResponse turnResume(TurnResumeParams params) {
            ensureReady();
            ThreadId threadId = requireThreadId(params);
            ensureRuntimeSubscriptions(List.of(threadId));
            return new TurnResumeResponse(runtimeGateway.turnResume(threadId, params.turnId()));
        }

        @Override
        public TurnInterruptResponse turnInterrupt(TurnInterruptParams params) {
            ensureReady();
            boolean accepted = runtimeGateway.turnInterrupt(requireThreadId(params), params.turnId());
            return new TurnInterruptResponse(params.turnId(), accepted);
        }

        @Override
        public TurnSteerResponse turnSteer(TurnSteerParams params) {
            ensureReady();
            boolean accepted = runtimeGateway.turnSteer(requireThreadId(params), params.turnId(), params.input());
            return new TurnSteerResponse(params.turnId(), accepted);
        }

        @Override
        public SkillsListResponse skillsList(SkillsListParams params) {
            ensureReady();
            return new SkillsListResponse(runtimeGateway.listSkills(params != null && params.forceReload()));
        }

        @Override
        public AutoCloseable subscribe(Consumer<AppServerNotification> listener) {
            ensureReady();
            subscribers.add(listener);
            return () -> subscribers.remove(listener);
        }

        @Override
        public void close() throws Exception {
            for (AutoCloseable runtimeSubscription : runtimeSubscriptions.values()) {
                runtimeSubscription.close();
            }
            runtimeSubscriptions.clear();
            subscribers.clear();
        }

        private void ensureReady() {
            if (!initializeCalled || !initializedAcknowledged) {
                throw new IllegalStateException("Not initialized");
            }
        }

        private void ensureRuntimeSubscriptions(List<ThreadId> threadIds) {
            if (threadIds == null || threadIds.isEmpty()) {
                return;
            }
            for (ThreadId threadId : threadIds) {
                if (threadId == null) {
                    continue;
                }
                runtimeSubscriptions.computeIfAbsent(threadId, ignored -> {
                    try {
                        return runtimeGateway.subscribe(threadId, this::publishRuntimeNotification);
                    }
                    catch (Exception exception) {
                        throw new IllegalStateException("Unable to subscribe to runtime notifications for thread " + threadId.value(), exception);
                    }
                });
            }
        }

        private List<ThreadId> loadedRelatedThreadIds(ThreadId threadId) {
            return runtimeGateway.relatedThreads(threadId).stream()
                    .filter(ThreadSummary::loaded)
                    .map(ThreadSummary::threadId)
                    .toList();
        }

        private boolean isBackgroundCommand(String command) {
            if (command == null) {
                return false;
            }
            String trimmed = command.trim();
            return trimmed.endsWith("&") && !trimmed.endsWith("&&");
        }

        private BackgroundTerminalHandle launchBackgroundTerminal(ThreadId threadId, String command) {
            String actualCommand = normalizeBackgroundCommand(command);
            if (actualCommand.isBlank()) {
                return new BackgroundTerminalHandle(null, 0L, command == null ? "" : command, threadWorkspace(threadId), Instant.now(), new ShellCommandResult(
                        false,
                        command == null ? "" : command,
                        -1,
                        "",
                        "",
                        false,
                        threadWorkspace(threadId),
                        false,
                        org.dean.codex.protocol.tool.CommandApprovalDecision.BLOCK,
                        "Command must not be blank.",
                        "Command must not be blank."));
            }

            Process process;
            try {
                process = new ProcessBuilder("zsh", "-lc", actualCommand + " & echo $!")
                        .directory(Path.of(threadWorkspace(threadId)).toFile())
                        .start();
            }
            catch (Exception exception) {
                return new BackgroundTerminalHandle(null, 0L, command, threadWorkspace(threadId), Instant.now(), new ShellCommandResult(
                        false,
                        command,
                        -1,
                        "",
                        "",
                        false,
                        threadWorkspace(threadId),
                        false,
                        org.dean.codex.protocol.tool.CommandApprovalDecision.ALLOW,
                        "Background terminal launch requested.",
                        exception.getMessage()));
            }

            String stdout;
            String stderr;
            try {
                boolean finished = process.waitFor(5, TimeUnit.SECONDS);
                stdout = finished ? readProcessOutput(process.getInputStream()) : "";
                stderr = finished ? readProcessOutput(process.getErrorStream()) : "";
                if (!finished) {
                    process.destroyForcibly();
                    return new BackgroundTerminalHandle(null, 0L, command, threadWorkspace(threadId), Instant.now(), new ShellCommandResult(
                            false,
                            command,
                            -1,
                            "",
                            "",
                            false,
                            threadWorkspace(threadId),
                            false,
                            org.dean.codex.protocol.tool.CommandApprovalDecision.ALLOW,
                            "Background terminal launch requested.",
                            "Background terminal launcher did not exit cleanly."));
                }
            }
            catch (Exception exception) {
                process.destroyForcibly();
                return new BackgroundTerminalHandle(null, 0L, command, threadWorkspace(threadId), Instant.now(), new ShellCommandResult(
                        false,
                        command,
                        -1,
                        "",
                        "",
                        false,
                        threadWorkspace(threadId),
                        false,
                        org.dean.codex.protocol.tool.CommandApprovalDecision.ALLOW,
                        "Background terminal launch requested.",
                        exception.getMessage()));
            }

            long pid = parseBackgroundPid(stdout);
            if (pid < 1) {
                return new BackgroundTerminalHandle(null, 0L, command, threadWorkspace(threadId), Instant.now(), new ShellCommandResult(
                        false,
                        command,
                        -1,
                        stdout,
                        stderr,
                        false,
                        threadWorkspace(threadId),
                        false,
                        org.dean.codex.protocol.tool.CommandApprovalDecision.ALLOW,
                        "Background terminal launch requested.",
                        "Unable to determine background process id."));
            }

            BackgroundTerminalHandle handle = new BackgroundTerminalHandle(
                    UUID.randomUUID().toString(),
                    pid,
                    command,
                    threadWorkspace(threadId),
                    Instant.now(),
                    new ShellCommandResult(
                    true,
                    command,
                    0,
                    "Background terminal started (pid=%d).".formatted(pid),
                    stderr,
                    false,
                    threadWorkspace(threadId),
                    true,
                    org.dean.codex.protocol.tool.CommandApprovalDecision.ALLOW,
                    "Background terminal launch requested.",
                    ""));
            backgroundTerminals.computeIfAbsent(threadId, ignored -> new CopyOnWriteArrayList<>()).add(handle);
            return handle;
        }

        private boolean terminateBackgroundTerminal(BackgroundTerminalHandle handle) {
            if (handle == null) {
                return false;
            }
            ProcessHandle.of(handle.pid()).ifPresent(processHandle -> {
                if (processHandle.isAlive()) {
                    processHandle.destroy();
                    try {
                        processHandle.onExit().get(1, TimeUnit.SECONDS);
                    }
                    catch (Exception ignored) {
                        processHandle.destroyForcibly();
                    }
                }
            });
            return true;
        }

        private List<BackgroundTerminalSummary> activeBackgroundTerminals(ThreadId threadId) {
            if (threadId == null) {
                return List.of();
            }
            List<BackgroundTerminalHandle> handles = backgroundTerminals.get(threadId);
            if (handles == null || handles.isEmpty()) {
                return List.of();
            }
            List<BackgroundTerminalHandle> activeHandles = handles.stream()
                    .filter(this::isBackgroundTerminalAlive)
                    .toList();
            if (activeHandles.size() != handles.size()) {
                if (activeHandles.isEmpty()) {
                    backgroundTerminals.remove(threadId, handles);
                }
                else {
                    backgroundTerminals.put(threadId, new CopyOnWriteArrayList<>(activeHandles));
                }
            }
            return activeHandles.stream()
                    .map(BackgroundTerminalHandle::summary)
                    .toList();
        }

        private boolean isBackgroundTerminalAlive(BackgroundTerminalHandle handle) {
            return handle != null
                    && handle.pid() > 0
                    && ProcessHandle.of(handle.pid()).map(ProcessHandle::isAlive).orElse(false);
        }

        private String threadWorkspace(ThreadId threadId) {
            return runtimeGateway.listThreads().stream()
                    .filter(thread -> thread.threadId().equals(threadId))
                    .map(ThreadSummary::cwd)
                    .filter(cwd -> cwd != null && !cwd.isBlank())
                    .findFirst()
                    .orElseGet(() -> Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().toString());
        }

        private String normalizeBackgroundCommand(String command) {
            if (command == null) {
                return "";
            }
            String trimmed = command.trim();
            if (!trimmed.endsWith("&") || trimmed.endsWith("&&")) {
                return trimmed;
            }
            return trimmed.substring(0, trimmed.length() - 1).trim();
        }

        private long parseBackgroundPid(String stdout) {
            if (stdout == null || stdout.isBlank()) {
                return -1;
            }
            try {
                return Long.parseLong(stdout.trim().split("\\s+")[0]);
            }
            catch (Exception exception) {
                return -1;
            }
        }

        private String readProcessOutput(java.io.InputStream stream) throws java.io.IOException {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(stream))) {
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!output.isEmpty()) {
                        output.append(System.lineSeparator());
                    }
                    output.append(line);
                }
                return output.toString();
            }
        }

        private void publishRuntimeNotification(RuntimeNotification notification) {
            if (notification == null || notification.turn() == null) {
                return;
            }
            AppServerNotification mapped = switch (notification.type()) {
                case TURN_STARTED -> new TurnStartedNotification(notification.turn());
                case TURN_ITEM -> new TurnItemNotification(notification.turn(), notification.item());
                case TURN_COMPLETED -> new TurnCompletedNotification(notification.turn(), notification.finalAnswer());
                case THREAD_STARTED, THREAD_RESUMED -> null;
            };
            if (mapped != null) {
                publish(mapped);
            }
        }

        private void publish(AppServerNotification notification) {
            if (notification == null || optOutNotificationMethods.contains(notification.method())) {
                return;
            }
            for (Consumer<AppServerNotification> subscriber : subscribers) {
                try {
                    subscriber.accept(notification);
                }
                catch (Exception ignored) {
                    // App-server subscribers should not break the runtime.
                }
            }
        }

        private String buildUserAgent(InitializeParams params) {
            if (params == null || params.clientInfo() == null || params.clientInfo().name() == null || params.clientInfo().name().isBlank()) {
                return "codex-java-app-server";
            }
            return params.clientInfo().name().trim();
        }

        private String currentClientVersion() {
            return clientVersion;
        }

        private String normalizeClientVersion(String version) {
            if (version == null) {
                return null;
            }
            String normalized = version.trim();
            return normalized.isEmpty() ? null : normalized;
        }

        private int decodeCursor(String cursor) {
            if (cursor == null || cursor.isBlank()) {
                return 0;
            }
            try {
                return Math.max(0, Integer.parseInt(cursor.trim()));
            }
            catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid cursor: " + cursor);
            }
        }

        private int normalizeLimit(Integer limit, int defaultValue) {
            if (limit == null) {
                return Math.max(0, defaultValue);
            }
            if (limit < 0) {
                throw new IllegalArgumentException("limit must be >= 0");
            }
            return limit;
        }

        private AgentControl agentControl() {
            if (runtimeGateway instanceof AgentControl agentControl) {
                return agentControl;
            }
            throw new IllegalStateException("Agent control is not available");
        }

        private void publishMailboxUpdate(ThreadId threadId) {
            AgentMailboxState mailbox = agentControl().mailboxState(threadId);
            if (mailbox != null) {
                publish(new AgentMailboxUpdatedNotification(mailbox));
            }
        }
    }

        private record BackgroundTerminalHandle(String terminalId,
                                            long pid,
                                            String command,
                                            String workingDirectory,
                                            Instant startedAt,
                                            ShellCommandResult result) {

        private BackgroundTerminalSummary summary() {
            if (terminalId == null || pid < 1) {
                return null;
            }
            return new BackgroundTerminalSummary(terminalId, pid, command, workingDirectory, startedAt);
        }
    }

    private ThreadId requireThreadId(ThreadResumeParams params) {
        if (params == null || params.threadId() == null) {
            throw new IllegalArgumentException("threadId is required");
        }
        return params.threadId();
    }

    private ThreadId requireThreadId(ThreadReadParams params) {
        if (params == null || params.threadId() == null) {
            throw new IllegalArgumentException("threadId is required");
        }
        return params.threadId();
    }

    private ThreadId requireThreadId(ThreadUnsubscribeParams params) {
        if (params == null || params.threadId() == null) {
            throw new IllegalArgumentException("threadId is required");
        }
        return params.threadId();
    }

    private ThreadId requireThreadId(ThreadNameSetParams params) {
        if (params == null || params.threadId() == null) {
            throw new IllegalArgumentException("threadId is required");
        }
        return params.threadId();
    }

    private ThreadId requireThreadId(ThreadMetadataUpdateParams params) {
        if (params == null || params.threadId() == null) {
            throw new IllegalArgumentException("threadId is required");
        }
        return params.threadId();
    }

    private ThreadId requireThreadId(ThreadShellCommandParams params) {
        if (params == null || params.threadId() == null) {
            throw new IllegalArgumentException("threadId is required");
        }
        return params.threadId();
    }

    private ThreadId requireThreadId(ThreadCompactStartParams params) {
        if (params == null || params.threadId() == null) {
            throw new IllegalArgumentException("threadId is required");
        }
        return params.threadId();
    }

    private ThreadId requireThreadId(ThreadArchiveParams params) {
        if (params == null || params.threadId() == null) {
            throw new IllegalArgumentException("threadId is required");
        }
        return params.threadId();
    }

    private ThreadId requireThreadId(ThreadBackgroundTerminalsCleanParams params) {
        if (params == null || params.threadId() == null) {
            throw new IllegalArgumentException("threadId is required");
        }
        return params.threadId();
    }

    private ThreadId requireThreadId(ThreadUnarchiveParams params) {
        if (params == null || params.threadId() == null) {
            throw new IllegalArgumentException("threadId is required");
        }
        return params.threadId();
    }

    private ThreadId requireThreadId(ThreadRollbackParams params) {
        if (params == null || params.threadId() == null) {
            throw new IllegalArgumentException("threadId is required");
        }
        return params.threadId();
    }

    private ThreadId requireThreadId(TurnStartParams params) {
        if (params == null || params.threadId() == null) {
            throw new IllegalArgumentException("threadId is required");
        }
        return params.threadId();
    }

    private ThreadId requireThreadId(TurnInterruptParams params) {
        if (params == null || params.threadId() == null) {
            throw new IllegalArgumentException("threadId is required");
        }
        return params.threadId();
    }

    private ThreadId requireThreadId(TurnSteerParams params) {
        if (params == null || params.threadId() == null) {
            throw new IllegalArgumentException("threadId is required");
        }
        return params.threadId();
    }

    private ThreadId requireThreadId(TurnResumeParams params) {
        if (params == null || params.threadId() == null) {
            throw new IllegalArgumentException("threadId is required");
        }
        return params.threadId();
    }

    private void requireLoadedThread(ThreadId threadId) {
        if (!runtimeGateway.loadedThreads().contains(threadId)) {
            throw new IllegalStateException("Thread is not loaded: " + threadId.value());
        }
    }
}
