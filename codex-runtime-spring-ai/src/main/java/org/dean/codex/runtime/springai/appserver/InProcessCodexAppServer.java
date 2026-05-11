package org.dean.codex.runtime.springai.appserver;

import org.dean.codex.core.agent.AgentControl;
import org.dean.codex.core.appserver.CodexAppServer;
import org.dean.codex.core.appserver.CodexAppServerSession;
import org.dean.codex.core.exec.ExecSessionEvent;
import org.dean.codex.core.exec.ExecSessionEventType;
import org.dean.codex.core.exec.ExecSessionId;
import org.dean.codex.core.exec.ExecSessionManager;
import org.dean.codex.core.exec.ExecSessionSummary;
import org.dean.codex.core.exec.ExecStartRequest;
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
import org.dean.codex.protocol.appserver.CommandExecParams;
import org.dean.codex.protocol.appserver.CommandExecResizeParams;
import org.dean.codex.protocol.appserver.CommandExecResizeResponse;
import org.dean.codex.protocol.appserver.CommandExecResponse;
import org.dean.codex.protocol.appserver.CommandExecTerminateParams;
import org.dean.codex.protocol.appserver.CommandExecTerminateResponse;
import org.dean.codex.protocol.appserver.CommandExecWriteParams;
import org.dean.codex.protocol.appserver.CommandExecutionCompletedNotification;
import org.dean.codex.protocol.appserver.CommandExecutionEvent;
import org.dean.codex.protocol.appserver.CommandExecutionOutputDeltaNotification;
import org.dean.codex.protocol.appserver.CommandExecutionTerminalInteractionNotification;
import org.dean.codex.protocol.appserver.ConfigGetParams;
import org.dean.codex.protocol.appserver.ConfigGetResponse;
import org.dean.codex.protocol.appserver.ConfigUpdateParams;
import org.dean.codex.protocol.appserver.ConfigUpdateResponse;
import org.dean.codex.protocol.appserver.InitializeParams;
import org.dean.codex.protocol.appserver.InitializeResponse;
import org.dean.codex.protocol.appserver.InitializedNotification;
import org.dean.codex.protocol.appserver.ModelListParams;
import org.dean.codex.protocol.appserver.ModelListResponse;
import org.dean.codex.protocol.appserver.ModelOption;
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
import org.dean.codex.protocol.conversation.ThreadSource;
import org.dean.codex.protocol.conversation.ThreadSummary;
import org.dean.codex.protocol.runtime.RuntimeNotification;
import org.dean.codex.protocol.runtime.RuntimeNotificationType;
import org.dean.codex.protocol.context.ReconstructedThreadContext;
import org.dean.codex.runtime.springai.config.CodexProperties;
import org.dean.codex.runtime.springai.thread.DefaultThreadCatalogService;
import org.dean.codex.runtime.springai.thread.ThreadCatalogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Component
public class InProcessCodexAppServer implements CodexAppServer {

    private static final String DEFAULT_CODEX_HOME_DIRECTORY_NAME = ".d-codex";

    private final CodexRuntimeGateway runtimeGateway;
    private final ShellCommandTool shellCommandTool;
    private final ExecSessionManager execSessionManager;
    private final ThreadCatalogService threadCatalogService;
    private final Path codexHome;
    private final CodexProperties codexProperties;
    private final Map<ThreadId, List<BackgroundTerminalRef>> backgroundTerminals = new ConcurrentHashMap<>();

    @Autowired
    public InProcessCodexAppServer(CodexRuntimeGateway runtimeGateway,
                                   ShellCommandTool shellCommandTool,
                                   ExecSessionManager execSessionManager,
                                   ThreadCatalogService threadCatalogService,
                                   @Qualifier("codexStorageRoot") Path codexHome,
                                   CodexProperties codexProperties) {
        this.runtimeGateway = runtimeGateway;
        this.shellCommandTool = shellCommandTool;
        this.execSessionManager = execSessionManager;
        this.threadCatalogService = threadCatalogService == null ? new DefaultThreadCatalogService() : threadCatalogService;
        this.codexHome = normalizeCodexHome(codexHome);
        this.codexProperties = codexProperties == null ? new CodexProperties() : codexProperties;
    }

    public InProcessCodexAppServer(CodexRuntimeGateway runtimeGateway,
                                   ShellCommandTool shellCommandTool,
                                   ExecSessionManager execSessionManager,
                                   ThreadCatalogService threadCatalogService,
                                   Path codexHome) {
        this(runtimeGateway, shellCommandTool, execSessionManager, threadCatalogService, codexHome, new CodexProperties());
    }

    public InProcessCodexAppServer(CodexRuntimeGateway runtimeGateway) {
        this(runtimeGateway, null, null, new DefaultThreadCatalogService(), defaultCodexHome());
    }

    public InProcessCodexAppServer(CodexRuntimeGateway runtimeGateway, ShellCommandTool shellCommandTool) {
        this(runtimeGateway, shellCommandTool, null, new DefaultThreadCatalogService(), defaultCodexHome());
    }

    public InProcessCodexAppServer(CodexRuntimeGateway runtimeGateway,
                                   ShellCommandTool shellCommandTool,
                                   ExecSessionManager execSessionManager) {
        this(runtimeGateway, shellCommandTool, execSessionManager, new DefaultThreadCatalogService(), defaultCodexHome());
    }

    public InProcessCodexAppServer(CodexRuntimeGateway runtimeGateway,
                                   ShellCommandTool shellCommandTool,
                                   ExecSessionManager execSessionManager,
                                   ThreadCatalogService threadCatalogService) {
        this(runtimeGateway, shellCommandTool, execSessionManager, threadCatalogService, defaultCodexHome());
    }

    @Override
    public CodexAppServerSession connect() {
        return new Session();
    }

    private final class Session implements CodexAppServerSession {

        private static final Duration DEFAULT_EXEC_YIELD = Duration.ofSeconds(1);
        private static final Duration DEFAULT_EXEC_POLL_YIELD = Duration.ofMillis(250);

        private final CopyOnWriteArrayList<Consumer<AppServerNotification>> subscribers = new CopyOnWriteArrayList<>();
        private final Map<ThreadId, AutoCloseable> runtimeSubscriptions = new ConcurrentHashMap<>();
        private final Set<String> optOutNotificationMethods = new HashSet<>();
        private final AutoCloseable execSubscription;
        private String clientName;
        private String clientVersion;
        private boolean initializeCalled;
        private boolean initializedAcknowledged;

        private Session() {
            this.execSubscription = subscribeExecNotifications();
        }

        @Override
        public synchronized InitializeResponse initialize(InitializeParams params) {
            if (initializeCalled) {
                throw new IllegalStateException("Already initialized");
            }
            initializeCalled = true;
            optOutNotificationMethods.clear();
            clientName = params == null || params.clientInfo() == null ? null : normalizeClientName(params.clientInfo().name());
            clientVersion = params == null || params.clientInfo() == null ? null : normalizeClientVersion(params.clientInfo().version());
            if (params != null && params.capabilities() != null) {
                optOutNotificationMethods.addAll(params.capabilities().optOutNotificationMethods());
            }
            return new InitializeResponse(
                    buildUserAgent(params),
                    codexHome.toString(),
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
            thread = applyMetadataUpdate(
                    thread,
                    thread.threadId(),
                    null,
                    null,
                    null,
                    params == null ? null : params.sandboxMode(),
                    params == null ? null : params.approvalMode(),
                    null,
                    null,
                    null,
                    cliVersion,
                    currentThreadSource());
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
            ThreadSource source = thread.source() == ThreadSource.UNKNOWN ? currentThreadSource() : null;
            if (cliVersion != null || source != null) {
                thread = applyMetadataUpdate(thread, thread.threadId(), null, null, null, null, null, null, null, null, cliVersion, source);
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
                thread = applyMetadataUpdate(thread, thread.threadId(), null, null, null, null, null, null, null, null, cliVersion, null);
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
                    params == null ? null : params.cwd(),
                    params == null ? null : params.modelProvider(),
                    params == null ? null : params.model(),
                    params == null ? null : params.sandboxMode(),
                    params == null ? null : params.approvalMode(),
                    params.gitSha(),
                    params.gitBranch(),
                    params.gitOriginUrl(),
                    cliVersion,
                    null);
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
                                                 String cliVersion,
                                                 ThreadSource source) {
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
                    cliVersion,
                    source);
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
            ShellCommandResult result = shellCommandTool.runCommand(threadId, command);
            return new ThreadShellCommandResponse(result);
        }

        @Override
        public CommandExecResponse commandExec(CommandExecParams params) {
            ensureReady();
            ensureExecSessionManager();
            ThreadId threadId = requireThreadId(params);
            requireLoadedThread(threadId);
            if (params.command() == null || params.command().isBlank()) {
                throw new IllegalArgumentException("command is required");
            }
            var result = execSessionManager.start(new ExecStartRequest(
                    threadId,
                    params.command(),
                    resolveExecWorkingDirectory(threadId, params.cwd()),
                    durationOrDefault(params.yieldTimeMillis(), DEFAULT_EXEC_YIELD),
                    durationOrDefault(params.maxRuntimeMillis(), Duration.ZERO),
                    Boolean.TRUE.equals(params.pty())));
            return new CommandExecResponse(
                    toCommandExecutionEvent(result.session()),
                    result.stdout(),
                    result.stderr(),
                    result.error());
        }

        @Override
        public CommandExecResponse commandExecWrite(CommandExecWriteParams params) {
            ensureReady();
            ensureExecSessionManager();
            ThreadId threadId = requireThreadId(params);
            requireLoadedThread(threadId);
            ExecSessionSummary session = requireExecSession(threadId, params.sessionId());
            var result = execSessionManager.writeStdin(
                    session.sessionId(),
                    params.input(),
                    durationOrDefault(params.yieldTimeMillis(), DEFAULT_EXEC_POLL_YIELD));
            return new CommandExecResponse(
                    toCommandExecutionEvent(result.session()),
                    result.stdout(),
                    result.stderr(),
                    result.error());
        }

        @Override
        public CommandExecResizeResponse commandExecResize(CommandExecResizeParams params) {
            ensureReady();
            ensureExecSessionManager();
            ThreadId threadId = requireThreadId(params);
            requireLoadedThread(threadId);
            if (params.columns() < 1 || params.rows() < 1) {
                throw new IllegalArgumentException("columns and rows must be >= 1");
            }
            ExecSessionSummary session = requireExecSession(threadId, params.sessionId());
            boolean applied = execSessionManager.resize(session.sessionId(), params.columns(), params.rows());
            ExecSessionSummary current = execSessionManager.session(session.sessionId()).orElse(session);
            return new CommandExecResizeResponse(toCommandExecutionEvent(current), applied);
        }

        @Override
        public CommandExecTerminateResponse commandExecTerminate(CommandExecTerminateParams params) {
            ensureReady();
            ensureExecSessionManager();
            ThreadId threadId = requireThreadId(params);
            requireLoadedThread(threadId);
            ExecSessionSummary session = requireExecSession(threadId, params.sessionId());
            boolean terminated = execSessionManager.terminate(session.sessionId());
            ExecSessionSummary current = execSessionManager.session(session.sessionId()).orElse(session);
            return new CommandExecTerminateResponse(toCommandExecutionEvent(current), terminated);
        }

        @Override
        public ThreadBackgroundTerminalsCleanResponse threadBackgroundTerminalsClean(ThreadBackgroundTerminalsCleanParams params) {
            ensureReady();
            if (execSessionManager == null) {
                return new ThreadBackgroundTerminalsCleanResponse(requireThreadId(params), 0);
            }
            ThreadId threadId = requireThreadId(params);
            List<BackgroundTerminalRef> refs = backgroundTerminals.remove(threadId);
            if (refs == null || refs.isEmpty()) {
                return new ThreadBackgroundTerminalsCleanResponse(threadId, 0);
            }
            for (BackgroundTerminalRef ref : refs) {
                if (ref == null) {
                    continue;
                }
                execSessionManager.terminate(ref.sessionId());
            }
            return new ThreadBackgroundTerminalsCleanResponse(threadId, refs.size());
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
            return new TurnStartResponse(runtimeGateway.turnStart(
                    threadId,
                    params.inputSummary(),
                    org.dean.codex.runtime.springai.runtime.TurnInputMapper.toModelInputItems(params.effectiveInputItems())));
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
        public ModelListResponse modelList(ModelListParams params) {
            ensureReady();
            ConfigGetResponse config = configGet(new ConfigGetParams(params == null ? null : params.threadId()));
            String currentModel = firstNonBlank(config.model(), defaultModel());
            String currentProvider = firstNonBlank(config.modelProvider(), "openai");
            LinkedHashSet<String> modelIds = new LinkedHashSet<>();
            modelIds.add(currentModel);
            modelIds.add(defaultModel());
            modelIds.add("gpt-5.5");
            modelIds.add("gpt-5.4");
            modelIds.add("gpt-5.4-mini");
            modelIds.add("gpt-5.3-codex");
            modelIds.add("gpt-5.3-codex-spark-preview");
            List<String> reasoningEfforts = List.of("low", "medium", "high", "xhigh");
            return new ModelListResponse(modelIds.stream()
                    .filter(model -> model != null && !model.isBlank())
                    .map(model -> new ModelOption(
                            model,
                            model,
                            currentProvider,
                            model.equals(currentModel),
                            model.equals(defaultModel()),
                            reasoningEfforts))
                    .toList());
        }

        @Override
        public ConfigGetResponse configGet(ConfigGetParams params) {
            ensureReady();
            ThreadSummary thread = params == null || params.threadId() == null
                    ? firstLoadedThread()
                    : runtimeGateway.listThreads().stream()
                    .filter(summary -> summary.threadId().equals(params.threadId()))
                    .findFirst()
                    .orElse(null);
            return configResponse(thread);
        }

        @Override
        public ConfigUpdateResponse configUpdate(ConfigUpdateParams params) {
            ensureReady();
            ThreadId threadId = params == null ? null : params.threadId();
            if (threadId == null) {
                ThreadSummary firstLoaded = firstLoadedThread();
                if (firstLoaded == null) {
                    throw new IllegalArgumentException("threadId is required");
                }
                threadId = firstLoaded.threadId();
            }
            ThreadId targetThreadId = threadId;
            ThreadSummary current = runtimeGateway.listThreads().stream()
                    .filter(thread -> thread.threadId().equals(targetThreadId))
                    .findFirst()
                    .orElse(null);
            ThreadSummary updated = applyMetadataUpdate(
                    current,
                    targetThreadId,
                    params == null ? null : params.cwd(),
                    params == null ? null : params.modelProvider(),
                    params == null ? null : params.model(),
                    params == null ? null : params.sandboxMode(),
                    params == null ? null : params.approvalMode(),
                    null,
                    null,
                    null,
                    currentClientVersion(),
                    null);
            publish(new ThreadMetadataUpdatedNotification(updated));
            return new ConfigUpdateResponse(configResponse(updated), updated);
        }

        @Override
        public AutoCloseable subscribe(Consumer<AppServerNotification> listener) {
            ensureReady();
            subscribers.add(listener);
            return () -> subscribers.remove(listener);
        }

        @Override
        public void close() throws Exception {
            if (execSubscription != null) {
                execSubscription.close();
            }
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
            ensureExecSessionManager();
            String actualCommand = normalizeBackgroundCommand(command);
            Path workingDirectory = resolveExecWorkingDirectory(threadId, null);
            if (actualCommand.isBlank()) {
                return new BackgroundTerminalHandle(null, 0L, command == null ? "" : command, workingDirectory.toString(), Instant.now(), new ShellCommandResult(
                        false,
                        command == null ? "" : command,
                        -1,
                        "",
                        "",
                        false,
                        workingDirectory.toString(),
                        false,
                        org.dean.codex.protocol.tool.CommandApprovalDecision.BLOCK,
                        "Command must not be blank.",
                        "Command must not be blank."));
            }
            var result = execSessionManager.start(new ExecStartRequest(
                    threadId,
                    actualCommand,
                    workingDirectory,
                    DEFAULT_EXEC_POLL_YIELD,
                    Duration.ZERO,
                    false));
            ExecSessionSummary session = result.session();
            if (!session.running()) {
                String error = result.error();
                if (error == null || error.isBlank()) {
                    error = "Background command exited before it could remain active.";
                }
                return new BackgroundTerminalHandle(
                        null,
                        session.processId() == null ? 0L : session.processId(),
                        command,
                        session.workingDirectory(),
                        session.startedAt(),
                        new ShellCommandResult(
                                false,
                                command,
                                session.exitCode() == null ? -1 : session.exitCode(),
                                result.stdout(),
                                result.stderr(),
                                false,
                                session.workingDirectory(),
                                !session.status().equals(org.dean.codex.core.exec.ExecSessionStatus.START_FAILED),
                                org.dean.codex.protocol.tool.CommandApprovalDecision.ALLOW,
                                "Background terminal launch requested.",
                                error));
            }

            BackgroundTerminalRef ref = new BackgroundTerminalRef(session.sessionId(), command);
            backgroundTerminals.computeIfAbsent(threadId, ignored -> new CopyOnWriteArrayList<>()).add(ref);
            BackgroundTerminalSummary summary = toBackgroundTerminalSummary(ref, session);
            String startupMessage = "Background terminal started (pid=%d).".formatted(summary.pid());
            String stdout = result.stdout().isBlank() ? startupMessage : startupMessage + System.lineSeparator() + result.stdout();
            return new BackgroundTerminalHandle(
                    summary.terminalId(),
                    summary.pid(),
                    summary.command(),
                    summary.workingDirectory(),
                    summary.startedAt(),
                    new ShellCommandResult(
                            true,
                            command,
                            0,
                            stdout,
                            result.stderr(),
                            false,
                            session.workingDirectory(),
                            true,
                            org.dean.codex.protocol.tool.CommandApprovalDecision.ALLOW,
                            "Background terminal launch requested.",
                            ""));
        }

        private List<BackgroundTerminalSummary> activeBackgroundTerminals(ThreadId threadId) {
            if (threadId == null) {
                return List.of();
            }
            if (execSessionManager == null) {
                return List.of();
            }
            List<BackgroundTerminalRef> refs = backgroundTerminals.get(threadId);
            if (refs == null || refs.isEmpty()) {
                return List.of();
            }
            List<BackgroundTerminalRef> activeRefs = new CopyOnWriteArrayList<>();
            List<BackgroundTerminalSummary> activeSummaries = new CopyOnWriteArrayList<>();
            for (BackgroundTerminalRef ref : refs) {
                if (ref == null) {
                    continue;
                }
                ExecSessionSummary session = execSessionManager.session(ref.sessionId()).orElse(null);
                if (session == null || !session.running()) {
                    continue;
                }
                activeRefs.add(ref);
                activeSummaries.add(toBackgroundTerminalSummary(ref, session));
            }
            if (activeRefs.size() != refs.size()) {
                if (activeRefs.isEmpty()) {
                    backgroundTerminals.remove(threadId, refs);
                }
                else {
                    backgroundTerminals.put(threadId, activeRefs);
                }
            }
            return List.copyOf(activeSummaries);
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

        private BackgroundTerminalSummary toBackgroundTerminalSummary(BackgroundTerminalRef ref, ExecSessionSummary session) {
            return new BackgroundTerminalSummary(
                    ref.sessionId().value(),
                    session.processId() == null ? 0L : session.processId(),
                    ref.command(),
                    session.workingDirectory(),
                    session.startedAt());
        }

        private void publishRuntimeNotification(RuntimeNotification notification) {
            if (notification == null) {
                return;
            }
            AppServerNotification mapped = switch (notification.type()) {
                case TURN_STARTED -> notification.turn() == null ? null : new TurnStartedNotification(notification.turn());
                case TURN_ITEM -> notification.turn() == null ? null : new TurnItemNotification(notification.turn(), notification.item());
                case TURN_COMPLETED -> notification.turn() == null ? null : new TurnCompletedNotification(notification.turn(), notification.finalAnswer());
                case AGENT_MAILBOX_UPDATED -> notification.threadId() == null ? null : new AgentMailboxUpdatedNotification(agentControl().mailboxState(notification.threadId()));
                case THREAD_STARTED, THREAD_RESUMED -> null;
            };
            if (mapped != null) {
                publish(mapped);
            }
        }

        private AutoCloseable subscribeExecNotifications() {
            if (execSessionManager == null) {
                return () -> {
                };
            }
            return execSessionManager.subscribe(this::publishExecNotification);
        }

        private void publishExecNotification(ExecSessionEvent event) {
            if (event == null || event.session() == null || event.session().threadId() == null) {
                return;
            }
            ThreadId threadId = event.session().threadId();
            if (!runtimeSubscriptions.containsKey(threadId)) {
                return;
            }
            AppServerNotification mapped = switch (event.type()) {
                case OUTPUT_DELTA -> new CommandExecutionOutputDeltaNotification(
                        toCommandExecutionEvent(event.session()),
                        event.stdout(),
                        event.stderr());
                case TERMINAL_INTERACTION -> new CommandExecutionTerminalInteractionNotification(
                        toCommandExecutionEvent(event.session()),
                        event.terminalInteraction() == null ? null : event.terminalInteraction().kind(),
                        event.terminalInteraction() == null ? null : event.terminalInteraction().inputLength(),
                        event.terminalInteraction() == null ? null : event.terminalInteraction().columns(),
                        event.terminalInteraction() == null ? null : event.terminalInteraction().rows());
                case COMPLETED -> new CommandExecutionCompletedNotification(toCommandExecutionEvent(event.session()));
            };
            publish(mapped);
        }

        private CommandExecutionEvent toCommandExecutionEvent(ExecSessionSummary session) {
            return new CommandExecutionEvent(
                    session.sessionId().value(),
                    session.threadId(),
                    session.command(),
                    session.workingDirectory(),
                    session.processId(),
                    session.status().name(),
                    session.startedAt(),
                    session.completedAt(),
                    session.exitCode());
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

        private ThreadSource currentThreadSource() {
            return isCliClient() ? ThreadSource.CLI : ThreadSource.APP_SERVER;
        }

        private ThreadSummary firstLoadedThread() {
            return runtimeGateway.listThreads().stream()
                    .filter(ThreadSummary::loaded)
                    .findFirst()
                    .orElseGet(() -> runtimeGateway.listThreads().stream().findFirst().orElse(null));
        }

        private ConfigGetResponse configResponse(ThreadSummary thread) {
            String defaultApproval = codexProperties.getShell() == null
                    ? "review-sensitive"
                    : firstNonBlank(codexProperties.getShell().getApprovalMode(), "review-sensitive");
            return new ConfigGetResponse(
                    thread == null ? null : thread.threadId(),
                    firstNonBlank(thread == null ? null : thread.modelProvider(), "openai"),
                    firstNonBlank(thread == null ? null : thread.model(), defaultModel()),
                    thread == null ? null : thread.sandboxMode(),
                    firstNonBlank(thread == null ? null : thread.approvalMode(), defaultApproval),
                    firstNonBlank(thread == null ? null : thread.cwd(), System.getProperty("user.dir", "")),
                    List.of("tui", "slash-palette", "selection-overlays"));
        }

        private String defaultModel() {
            return firstNonBlank(System.getenv("CODEX_CHAT_MODEL"), "gpt-5.4");
        }

        private String firstNonBlank(String value, String fallback) {
            String normalized = value == null ? "" : value.trim();
            return normalized.isEmpty() ? fallback : normalized;
        }

        private boolean isCliClient() {
            return clientName != null && clientName.contains("cli");
        }

        private String normalizeClientVersion(String version) {
            if (version == null) {
                return null;
            }
            String normalized = version.trim();
            return normalized.isEmpty() ? null : normalized;
        }

        private String normalizeClientName(String name) {
            if (name == null) {
                return null;
            }
            String normalized = name.trim().toLowerCase(Locale.ROOT);
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

    private record BackgroundTerminalRef(ExecSessionId sessionId, String command) {
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

    private ThreadId requireThreadId(CommandExecParams params) {
        if (params == null || params.threadId() == null) {
            throw new IllegalArgumentException("threadId is required");
        }
        return params.threadId();
    }

    private ThreadId requireThreadId(CommandExecWriteParams params) {
        if (params == null || params.threadId() == null) {
            throw new IllegalArgumentException("threadId is required");
        }
        return params.threadId();
    }

    private ThreadId requireThreadId(CommandExecResizeParams params) {
        if (params == null || params.threadId() == null) {
            throw new IllegalArgumentException("threadId is required");
        }
        return params.threadId();
    }

    private ThreadId requireThreadId(CommandExecTerminateParams params) {
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

    private void ensureExecSessionManager() {
        if (execSessionManager == null) {
            throw new IllegalStateException("Command exec service is not configured");
        }
    }

    private Duration durationOrDefault(Long millis, Duration defaultValue) {
        if (millis == null) {
            return defaultValue;
        }
        if (millis < 0) {
            throw new IllegalArgumentException("Duration millis must be >= 0");
        }
        return Duration.ofMillis(millis);
    }

    private Path resolveExecWorkingDirectory(ThreadId threadId, String cwd) {
        String effectiveCwd = cwd;
        if (effectiveCwd == null || effectiveCwd.isBlank()) {
            effectiveCwd = runtimeGateway.listThreads().stream()
                    .filter(thread -> thread.threadId().equals(threadId))
                    .map(ThreadSummary::cwd)
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst()
                    .orElseGet(() -> Path.of("").toAbsolutePath().normalize().toString());
        }
        return Path.of(effectiveCwd).toAbsolutePath().normalize();
    }

    private ExecSessionSummary requireExecSession(ThreadId threadId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId is required");
        }
        ExecSessionSummary session = execSessionManager.session(new ExecSessionId(sessionId))
                .orElseThrow(() -> new IllegalArgumentException("Unknown exec session id: " + sessionId));
        if (!threadId.equals(session.threadId())) {
            throw new IllegalArgumentException("Exec session " + sessionId + " does not belong to thread " + threadId.value());
        }
        return session;
    }

    private static Path defaultCodexHome() {
        return Path.of(System.getProperty("user.home"), DEFAULT_CODEX_HOME_DIRECTORY_NAME)
                .toAbsolutePath()
                .normalize();
    }

    private static Path normalizeCodexHome(Path codexHome) {
        return (codexHome == null ? defaultCodexHome() : codexHome)
                .toAbsolutePath()
                .normalize();
    }
}
