package org.dean.codex.runtime.springai.appserver.transport.jsonrpc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import org.dean.codex.core.appserver.CodexAppServer;
import org.dean.codex.core.appserver.CodexAppServerSession;
import org.dean.codex.protocol.appserver.AppServerClientInfo;
import org.dean.codex.protocol.appserver.AppServerNotification;
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
import org.dean.codex.protocol.appserver.InitializeParams;
import org.dean.codex.protocol.appserver.InitializeResponse;
import org.dean.codex.protocol.appserver.InitializedNotification;
import org.dean.codex.protocol.appserver.SkillsListParams;
import org.dean.codex.protocol.appserver.SkillsListResponse;
import org.dean.codex.protocol.appserver.ThreadArchiveParams;
import org.dean.codex.protocol.appserver.ThreadArchiveResponse;
import org.dean.codex.protocol.appserver.ThreadBackgroundTerminalsCleanParams;
import org.dean.codex.protocol.appserver.ThreadBackgroundTerminalsCleanResponse;
import org.dean.codex.protocol.appserver.ThreadCompactStartParams;
import org.dean.codex.protocol.appserver.ThreadCompactStartResponse;
import org.dean.codex.protocol.appserver.ThreadForkParams;
import org.dean.codex.protocol.appserver.ThreadForkResponse;
import org.dean.codex.protocol.appserver.ThreadClosedNotification;
import org.dean.codex.protocol.appserver.ThreadListParams;
import org.dean.codex.protocol.appserver.ThreadListResponse;
import org.dean.codex.protocol.appserver.ThreadLoadedListParams;
import org.dean.codex.protocol.appserver.ThreadLoadedListResponse;
import org.dean.codex.protocol.appserver.ThreadMetadataUpdateParams;
import org.dean.codex.protocol.appserver.ThreadMetadataUpdateResponse;
import org.dean.codex.protocol.appserver.ThreadMetadataUpdatedNotification;
import org.dean.codex.protocol.appserver.ThreadNameSetParams;
import org.dean.codex.protocol.appserver.ThreadNameSetResponse;
import org.dean.codex.protocol.appserver.ThreadNameUpdatedNotification;
import org.dean.codex.protocol.appserver.ThreadReadParams;
import org.dean.codex.protocol.appserver.ThreadReadResponse;
import org.dean.codex.protocol.appserver.ThreadRollbackParams;
import org.dean.codex.protocol.appserver.ThreadRollbackResponse;
import org.dean.codex.protocol.appserver.ThreadResumeParams;
import org.dean.codex.protocol.appserver.ThreadResumeResponse;
import org.dean.codex.protocol.appserver.ThreadStartParams;
import org.dean.codex.protocol.appserver.ThreadStartResponse;
import org.dean.codex.protocol.appserver.ThreadStartedNotification;
import org.dean.codex.protocol.appserver.ThreadStatusChangedNotification;
import org.dean.codex.protocol.appserver.ThreadShellCommandParams;
import org.dean.codex.protocol.appserver.ThreadShellCommandResponse;
import org.dean.codex.protocol.appserver.ThreadUnarchiveParams;
import org.dean.codex.protocol.appserver.ThreadUnarchiveResponse;
import org.dean.codex.protocol.appserver.ThreadUnsubscribeParams;
import org.dean.codex.protocol.appserver.ThreadUnsubscribeResponse;
import org.dean.codex.protocol.appserver.TurnInterruptParams;
import org.dean.codex.protocol.appserver.TurnInterruptResponse;
import org.dean.codex.protocol.appserver.TurnResumeParams;
import org.dean.codex.protocol.appserver.TurnResumeResponse;
import org.dean.codex.protocol.appserver.TurnStartParams;
import org.dean.codex.protocol.appserver.TurnStartResponse;
import org.dean.codex.protocol.appserver.TurnStartedNotification;
import org.dean.codex.protocol.appserver.TurnSteerParams;
import org.dean.codex.protocol.appserver.TurnSteerResponse;
import org.dean.codex.protocol.appserver.jsonrpc.JsonRpcRequestMessage;
import org.dean.codex.protocol.conversation.ThreadId;
import org.dean.codex.protocol.conversation.ThreadSummary;
import org.dean.codex.protocol.conversation.TurnId;
import org.dean.codex.protocol.conversation.TurnStatus;
import org.dean.codex.protocol.agent.AgentMessage;
import org.dean.codex.protocol.agent.AgentMailboxState;
import org.dean.codex.protocol.agent.AgentSummary;
import org.dean.codex.protocol.agent.AgentWaitResult;
import org.dean.codex.protocol.agent.AgentStatus;
import org.dean.codex.protocol.agent.AgentSpawnRequest;
import org.dean.codex.protocol.runtime.RuntimeTurn;
import org.dean.codex.protocol.tool.CommandApprovalDecision;
import org.dean.codex.protocol.tool.ShellCommandResult;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StdioJsonRpcAppServerHostTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void stdioHostHandlesInitializeThreadStartTurnStartAndNotifications() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        StdioJsonRpcAppServerHost host = new StdioJsonRpcAppServerHost(
                new JsonRpcAppServerDispatcher(new StubAppServer()),
                new ByteArrayInputStream(inputLines(
                        new JsonRpcRequestMessage("2.0", IntNode.valueOf(1), "initialize",
                                objectMapper.valueToTree(new InitializeParams(new AppServerClientInfo("test-client", "Test Client", "1.0.0"), null))),
                        new JsonRpcRequestMessage("2.0", null, "initialized", objectMapper.valueToTree(new InitializedNotification())),
                        new JsonRpcRequestMessage("2.0", IntNode.valueOf(2), "thread/start",
                                objectMapper.valueToTree(new ThreadStartParams("Demo thread"))),
                        new JsonRpcRequestMessage("2.0", IntNode.valueOf(3), "turn/start",
                                objectMapper.valueToTree(new TurnStartParams(new ThreadId("thread-1"), "Inspect repo"))))),
                output);

        host.run();

        List<JsonNode> messages = outputMessages(output);
        assertTrue(messages.stream().anyMatch(message ->
                message.path("id").asInt() == 1
                        && "test-client".equals(message.path("result").path("userAgent").asText())));
        assertTrue(messages.stream().anyMatch(message ->
                message.path("id").asInt() == 2
                        && "thread-1".equals(message.path("result").path("thread").path("threadId").path("value").asText())));
        assertTrue(messages.stream().anyMatch(message ->
                message.path("id").asInt() == 3
                        && "turn-1".equals(message.path("result").path("turn").path("turnId").path("value").asText())));
        assertTrue(messages.stream().anyMatch(message ->
                "turn/started".equals(message.path("method").asText())));
    }

    @Test
    void stdioHostHandlesThreadRenameMetadataUpdateAndUnsubscribe() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        StdioJsonRpcAppServerHost host = new StdioJsonRpcAppServerHost(
                new JsonRpcAppServerDispatcher(new StubAppServer()),
                new ByteArrayInputStream(inputLines(
                        new JsonRpcRequestMessage("2.0", IntNode.valueOf(1), "initialize",
                                objectMapper.valueToTree(new InitializeParams(new AppServerClientInfo("test-client", "Test Client", "1.0.0"), null))),
                        new JsonRpcRequestMessage("2.0", null, "initialized", objectMapper.valueToTree(new InitializedNotification())),
                        new JsonRpcRequestMessage("2.0", IntNode.valueOf(2), "thread/start",
                                objectMapper.valueToTree(new ThreadStartParams("Demo thread"))),
                        new JsonRpcRequestMessage("2.0", IntNode.valueOf(3), "thread/name/set",
                                objectMapper.valueToTree(new ThreadNameSetParams(new ThreadId("thread-1"), "Renamed thread"))),
                        new JsonRpcRequestMessage("2.0", IntNode.valueOf(4), "thread/metadata/update",
                                objectMapper.valueToTree(new ThreadMetadataUpdateParams(new ThreadId("thread-1"), "/workspace/app", "openai", "gpt-5.4"))),
                        new JsonRpcRequestMessage("2.0", IntNode.valueOf(5), "thread/unsubscribe",
                                objectMapper.valueToTree(new ThreadUnsubscribeParams(new ThreadId("thread-1")))))),
                output);

        host.run();

        List<JsonNode> messages = outputMessages(output);
        assertTrue(messages.stream().anyMatch(message ->
                message.path("id").asInt() == 3
                        && message.path("result").isObject()));
        assertTrue(messages.stream().anyMatch(message ->
                message.path("id").asInt() == 4
                        && "/workspace/app".equals(message.path("result").path("thread").path("cwd").asText())));
        assertTrue(messages.stream().anyMatch(message ->
                message.path("id").asInt() == 5
                        && "unsubscribed".equals(message.path("result").path("status").asText())));
        assertTrue(messages.stream().anyMatch(message ->
                "thread/name/updated".equals(message.path("method").asText())
                        && "Renamed thread".equals(message.path("params").path("thread").path("title").asText())));
        assertTrue(messages.stream().anyMatch(message ->
                "thread/metadata/updated".equals(message.path("method").asText())));
        assertTrue(messages.stream().anyMatch(message ->
                "thread/closed".equals(message.path("method").asText())));
    }

    @Test
    void stdioHostHandlesThreadResumeStatusChange() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        StdioJsonRpcAppServerHost host = new StdioJsonRpcAppServerHost(
                new JsonRpcAppServerDispatcher(new StubAppServer()),
                new ByteArrayInputStream(inputLines(
                        new JsonRpcRequestMessage("2.0", IntNode.valueOf(1), "initialize",
                                objectMapper.valueToTree(new InitializeParams(new AppServerClientInfo("test-client", "Test Client", "1.0.0"), null))),
                        new JsonRpcRequestMessage("2.0", null, "initialized", objectMapper.valueToTree(new InitializedNotification())),
                        new JsonRpcRequestMessage("2.0", IntNode.valueOf(2), "thread/start",
                                objectMapper.valueToTree(new ThreadStartParams("Demo thread"))),
                        new JsonRpcRequestMessage("2.0", IntNode.valueOf(3), "thread/resume",
                                objectMapper.valueToTree(new ThreadResumeParams(new ThreadId("thread-1")))))),
                output);

        host.run();

        List<JsonNode> messages = outputMessages(output);
        assertTrue(messages.stream().anyMatch(message ->
                message.path("id").asInt() == 3
                        && "thread-1".equals(message.path("result").path("thread").path("threadId").path("value").asText())));
        assertTrue(messages.stream().anyMatch(message ->
                "thread/status/changed".equals(message.path("method").asText())));
    }

    @Test
    void stdioHostHandlesThreadShellCommand() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        StdioJsonRpcAppServerHost host = new StdioJsonRpcAppServerHost(
                new JsonRpcAppServerDispatcher(new StubAppServer()),
                new ByteArrayInputStream(inputLines(
                        new JsonRpcRequestMessage("2.0", IntNode.valueOf(1), "initialize",
                                objectMapper.valueToTree(new InitializeParams(new AppServerClientInfo("test-client", "Test Client", "1.0.0"), null))),
                        new JsonRpcRequestMessage("2.0", null, "initialized", objectMapper.valueToTree(new InitializedNotification())),
                        new JsonRpcRequestMessage("2.0", IntNode.valueOf(2), "thread/start",
                                objectMapper.valueToTree(new ThreadStartParams("Demo thread"))),
                        new JsonRpcRequestMessage("2.0", IntNode.valueOf(3), "thread/shellCommand",
                                objectMapper.valueToTree(new ThreadShellCommandParams(new ThreadId("thread-1"), "printf 'hello'"))))),
                output);

        host.run();

        List<JsonNode> messages = outputMessages(output);
        assertTrue(messages.stream().anyMatch(message ->
                message.path("id").asInt() == 3
                        && "hello".equals(message.path("result").path("result").path("stdout").asText())));
    }

    @Test
    void stdioHostHandlesThreadBackgroundTerminalsClean() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        StdioJsonRpcAppServerHost host = new StdioJsonRpcAppServerHost(
                new JsonRpcAppServerDispatcher(new StubAppServer()),
                new ByteArrayInputStream(inputLines(
                        new JsonRpcRequestMessage("2.0", IntNode.valueOf(1), "initialize",
                                objectMapper.valueToTree(new InitializeParams(new AppServerClientInfo("test-client", "Test Client", "1.0.0"), null))),
                        new JsonRpcRequestMessage("2.0", null, "initialized", objectMapper.valueToTree(new InitializedNotification())),
                        new JsonRpcRequestMessage("2.0", IntNode.valueOf(2), "thread/start",
                                objectMapper.valueToTree(new ThreadStartParams("Demo thread"))),
                        new JsonRpcRequestMessage("2.0", IntNode.valueOf(3), "thread/backgroundTerminals/clean",
                                objectMapper.valueToTree(new ThreadBackgroundTerminalsCleanParams(new ThreadId("thread-1")))))),
                output);

        host.run();

        List<JsonNode> messages = outputMessages(output);
        assertTrue(messages.stream().anyMatch(message ->
                message.path("id").asInt() == 3
                        && 1 == message.path("result").path("cleanedCount").asInt()));
    }

    @Test
    void stdioHostHandlesAgentControlMethods() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        StdioJsonRpcAppServerHost host = new StdioJsonRpcAppServerHost(
                new JsonRpcAppServerDispatcher(new StubAppServer()),
                new ByteArrayInputStream(inputLines(
                        new JsonRpcRequestMessage("2.0", IntNode.valueOf(1), "initialize",
                                objectMapper.valueToTree(new InitializeParams(new AppServerClientInfo("test-client", "Test Client", "1.0.0"), null))),
                        new JsonRpcRequestMessage("2.0", null, "initialized", objectMapper.valueToTree(new InitializedNotification())),
                        new JsonRpcRequestMessage("2.0", IntNode.valueOf(2), "agent/spawn",
                                objectMapper.valueToTree(new AgentSpawnParams(new AgentSpawnRequest(
                                        new ThreadId("thread-1"), "Investigate", "Please inspect", "worker-1", "worker", null, null, null, null)))),
                        new JsonRpcRequestMessage("2.0", IntNode.valueOf(3), "agent/sendMessage",
                                objectMapper.valueToTree(new AgentSendMessageParams(
                                        new ThreadId("agent-1"),
                                        new AgentMessage(new ThreadId("thread-1"), new ThreadId("agent-1"), "message only", Instant.parse("2026-03-31T00:00:00Z"))))),
                        new JsonRpcRequestMessage("2.0", IntNode.valueOf(4), "agent/assignTask",
                                objectMapper.valueToTree(new AgentAssignTaskParams(
                                        new ThreadId("agent-1"),
                                        new AgentMessage(new ThreadId("thread-1"), new ThreadId("agent-1"), "continue", Instant.parse("2026-03-31T00:00:00Z")),
                                        false))),
                        new JsonRpcRequestMessage("2.0", IntNode.valueOf(5), "agent/sendInput",
                                objectMapper.valueToTree(new AgentSendInputParams(
                                        new ThreadId("agent-1"),
                                        new AgentMessage(new ThreadId("thread-1"), new ThreadId("agent-1"), "compat", Instant.parse("2026-03-31T00:00:00Z")),
                                        false))),
                        new JsonRpcRequestMessage("2.0", IntNode.valueOf(6), "agent/list",
                                objectMapper.valueToTree(new AgentListParams(new ThreadId("thread-1"), true))),
                        new JsonRpcRequestMessage("2.0", IntNode.valueOf(7), "agent/wait",
                                objectMapper.valueToTree(new AgentWaitParams(List.of(new ThreadId("agent-1")), 500))),
                        new JsonRpcRequestMessage("2.0", IntNode.valueOf(8), "agent/resume",
                                objectMapper.valueToTree(new AgentResumeParams(new ThreadId("agent-1")))),
                        new JsonRpcRequestMessage("2.0", IntNode.valueOf(9), "agent/close",
                                objectMapper.valueToTree(new AgentCloseParams(new ThreadId("agent-1")))))),
                output);

        host.run();

        List<JsonNode> messages = outputMessages(output);
        assertTrue(messages.stream().anyMatch(message ->
                message.path("id").asInt() == 2
                        && "agent-1".equals(message.path("result").path("agent").path("threadId").path("value").asText())));
        assertTrue(messages.stream().anyMatch(message ->
                message.path("id").asInt() == 3
                        && "agent-1".equals(message.path("result").path("agent").path("threadId").path("value").asText())));
        assertTrue(messages.stream().anyMatch(message ->
                message.path("id").asInt() == 4
                        && "agent-1".equals(message.path("result").path("agent").path("threadId").path("value").asText())));
        assertTrue(messages.stream().anyMatch(message ->
                message.path("id").asInt() == 5
                        && "agent-1".equals(message.path("result").path("agent").path("threadId").path("value").asText())));
        assertTrue(messages.stream().anyMatch(message ->
                message.path("id").asInt() == 6
                        && 1 == message.path("result").path("agents").size()));
        assertTrue(messages.stream().anyMatch(message ->
                message.path("id").asInt() == 7
                        && "agent-1".equals(message.path("result").path("result").path("threadId").path("value").asText())));
        assertTrue(messages.stream().anyMatch(message ->
                message.path("id").asInt() == 8
                        && "agent-1".equals(message.path("result").path("agent").path("threadId").path("value").asText())));
        assertTrue(messages.stream().anyMatch(message ->
                message.path("id").asInt() == 9
                        && "agent-1".equals(message.path("result").path("agent").path("threadId").path("value").asText())));
    }

    @Test
    void stdioHostReturnsErrorWhenRequestArrivesBeforeInitialize() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        StdioJsonRpcAppServerHost host = new StdioJsonRpcAppServerHost(
                new JsonRpcAppServerDispatcher(new StubAppServer()),
                new ByteArrayInputStream(inputLines(
                        new JsonRpcRequestMessage("2.0", IntNode.valueOf(9), "thread/list", null))),
                output);

        host.run();

        List<JsonNode> messages = outputMessages(output);
        assertEquals(1, messages.size());
        assertEquals(-32000, messages.get(0).path("error").path("code").asInt());
        assertTrue(messages.get(0).path("error").path("message").asText().contains("Not initialized"));
    }

    private byte[] inputLines(JsonRpcRequestMessage... messages) throws Exception {
        StringBuilder builder = new StringBuilder();
        for (JsonRpcRequestMessage message : messages) {
            builder.append(objectMapper.writeValueAsString(message)).append(System.lineSeparator());
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private List<JsonNode> outputMessages(ByteArrayOutputStream output) throws Exception {
        List<JsonNode> messages = new ArrayList<>();
        for (String line : output.toString(StandardCharsets.UTF_8).split("\\R")) {
            if (line == null || line.isBlank()) {
                continue;
            }
            messages.add(objectMapper.readTree(line));
        }
        return messages;
    }

    private static final class StubAppServer implements CodexAppServer {

        @Override
        public CodexAppServerSession connect() {
            return new StubSession();
        }
    }

    private static final class StubSession implements CodexAppServerSession {

        private final List<Consumer<AppServerNotification>> listeners = new ArrayList<>();
        private final Set<ThreadId> loadedThreadIds = new LinkedHashSet<>();
        private boolean initializeCalled;
        private boolean initializedAcknowledged;

        @Override
        public InitializeResponse initialize(InitializeParams params) {
            if (initializeCalled) {
                throw new IllegalStateException("Already initialized");
            }
            initializeCalled = true;
            return new InitializeResponse(params.clientInfo().name(), "/tmp/.codex-java", "desktop", "test");
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
            ThreadSummary thread = new ThreadSummary(new ThreadId("thread-1"), params.title(), Instant.parse("2026-03-31T00:00:00Z"), Instant.parse("2026-03-31T00:00:00Z"), 0);
            loadedThreadIds.add(thread.threadId());
            publish(new ThreadStartedNotification(thread));
            return new ThreadStartResponse(thread);
        }

        @Override
        public ThreadResumeResponse threadResume(ThreadResumeParams params) {
            ensureReady();
            loadedThreadIds.add(params.threadId());
            ThreadSummary thread = new ThreadSummary(params.threadId(), "Demo thread", Instant.now(), Instant.now(), 0)
                    .withRuntime(org.dean.codex.protocol.conversation.ThreadStatus.IDLE, List.of());
            publish(new ThreadStatusChangedNotification(thread));
            return new ThreadResumeResponse(thread);
        }

        @Override
        public ThreadListResponse threadList(ThreadListParams params) {
            ensureReady();
            return new ThreadListResponse(List.of());
        }

        @Override
        public ThreadLoadedListResponse threadLoadedList(ThreadLoadedListParams params) {
            ensureReady();
            return new ThreadLoadedListResponse(List.of(), null);
        }

        @Override
        public ThreadReadResponse threadRead(ThreadReadParams params) {
            ensureReady();
            return new ThreadReadResponse(new ThreadSummary(params.threadId(), "Demo thread", Instant.now(), Instant.now(), 0), List.of(), null, null);
        }

        @Override
        public ThreadForkResponse threadFork(ThreadForkParams params) {
            ensureReady();
            throw new UnsupportedOperationException("Not used in this test");
        }

        @Override
        public ThreadArchiveResponse threadArchive(ThreadArchiveParams params) {
            ensureReady();
            throw new UnsupportedOperationException("Not used in this test");
        }

        @Override
        public ThreadUnarchiveResponse threadUnarchive(ThreadUnarchiveParams params) {
            ensureReady();
            throw new UnsupportedOperationException("Not used in this test");
        }

        @Override
        public ThreadUnsubscribeResponse threadUnsubscribe(ThreadUnsubscribeParams params) {
            ensureReady();
            ThreadId threadId = params.threadId();
            if (loadedThreadIds.remove(threadId)) {
                publish(new ThreadClosedNotification(new ThreadSummary(params.threadId(), "Demo thread", Instant.now(), Instant.now(), 0)));
                return new ThreadUnsubscribeResponse("unsubscribed");
            }
            return new ThreadUnsubscribeResponse("notSubscribed");
        }

        @Override
        public ThreadNameSetResponse threadNameSet(ThreadNameSetParams params) {
            ensureReady();
            ThreadSummary thread = new ThreadSummary(params.threadId(), params.title(), Instant.now(), Instant.now(), 0);
            publish(new ThreadNameUpdatedNotification(thread));
            return new ThreadNameSetResponse();
        }

        @Override
        public ThreadMetadataUpdateResponse threadMetadataUpdate(ThreadMetadataUpdateParams params) {
            ensureReady();
            ThreadSummary thread = new ThreadSummary(
                    params.threadId(),
                    "Demo thread",
                    Instant.now(),
                    Instant.now(),
                    0,
                    "Demo thread",
                    false,
                    params.modelProvider(),
                    params.model(),
                    org.dean.codex.protocol.conversation.ThreadStatus.NOT_LOADED,
                    List.of(),
                    null,
                    params.cwd(),
                    org.dean.codex.protocol.conversation.ThreadSource.UNKNOWN,
                    true,
                    null,
                    null,
                    null,
                    null);
            publish(new ThreadMetadataUpdatedNotification(thread));
            return new ThreadMetadataUpdateResponse(thread);
        }

        @Override
        public ThreadShellCommandResponse threadShellCommand(ThreadShellCommandParams params) {
            ensureReady();
            if (!loadedThreadIds.contains(params.threadId())) {
                throw new IllegalStateException("Thread is not loaded: " + params.threadId().value());
            }
            return new ThreadShellCommandResponse(new ShellCommandResult(
                    true,
                    params.command(),
                    0,
                    "hello",
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
            return new ThreadBackgroundTerminalsCleanResponse(params.threadId(), 1);
        }

        @Override
        public AgentSpawnResponse agentSpawn(AgentSpawnParams params) {
            ensureReady();
            AgentSummary summary = new AgentSummary(
                    new ThreadId("agent-1"),
                    params == null || params.request() == null ? null : params.request().parentThreadId(),
                    "worker-1",
                    "worker",
                    "root/worker-1",
                    1,
                    AgentStatus.IDLE,
                    Instant.parse("2026-03-31T00:00:00Z"),
                    Instant.parse("2026-03-31T00:00:01Z"),
                    null);
            return new AgentSpawnResponse(summary);
        }

        @Override
        public AgentSendInputResponse agentSendInput(AgentSendInputParams params) {
            ensureReady();
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
                    "worker-1",
                    "worker",
                    "root/worker-1",
                    1,
                    AgentStatus.IDLE,
                    Instant.parse("2026-03-31T00:00:00Z"),
                    Instant.parse("2026-03-31T00:00:01Z"),
                    null));
        }

        @Override
        public AgentAssignTaskResponse agentAssignTask(AgentAssignTaskParams params) {
            ensureReady();
            AgentSummary summary = new AgentSummary(
                    params.agentThreadId(),
                    new ThreadId("thread-1"),
                    "worker-1",
                    "worker",
                    "root/worker-1",
                    1,
                    AgentStatus.RUNNING,
                    Instant.parse("2026-03-31T00:00:00Z"),
                    Instant.parse("2026-03-31T00:00:01Z"),
                    null);
            return new AgentAssignTaskResponse(summary);
        }

        @Override
            public AgentWaitResponse agentWait(AgentWaitParams params) {
                ensureReady();
                return new AgentWaitResponse(new AgentWaitResult(new ThreadId("agent-1"), null, AgentStatus.IDLE, AgentStatus.IDLE, false, "Agent is idle.", "", new AgentMailboxState(new ThreadId("agent-1"), 0L, 0, Instant.parse("2026-03-31T00:00:02Z")), Instant.parse("2026-03-31T00:00:02Z")));
            }

        @Override
        public AgentResumeResponse agentResume(AgentResumeParams params) {
            ensureReady();
            return new AgentResumeResponse(new AgentSummary(
                    params.agentThreadId(),
                    new ThreadId("thread-1"),
                    "worker-1",
                    "worker",
                    "root/worker-1",
                    1,
                    AgentStatus.IDLE,
                    Instant.parse("2026-03-31T00:00:00Z"),
                    Instant.parse("2026-03-31T00:00:01Z"),
                    null));
        }

        @Override
        public AgentCloseResponse agentClose(AgentCloseParams params) {
            ensureReady();
            return new AgentCloseResponse(new AgentSummary(
                    params.agentThreadId(),
                    new ThreadId("thread-1"),
                    "worker-1",
                    "worker",
                    "root/worker-1",
                    1,
                    AgentStatus.SHUTDOWN,
                    Instant.parse("2026-03-31T00:00:00Z"),
                    Instant.parse("2026-03-31T00:00:01Z"),
                    Instant.parse("2026-03-31T00:00:03Z")));
        }

        @Override
        public AgentListResponse agentList(AgentListParams params) {
            ensureReady();
            return new AgentListResponse(List.of(new AgentSummary(
                    new ThreadId("agent-1"),
                    new ThreadId("thread-1"),
                    "worker-1",
                    "worker",
                    "root/worker-1",
                    1,
                    AgentStatus.IDLE,
                    Instant.parse("2026-03-31T00:00:00Z"),
                    Instant.parse("2026-03-31T00:00:01Z"),
                    null)));
        }

        @Override
        public ThreadRollbackResponse threadRollback(ThreadRollbackParams params) {
            ensureReady();
            throw new UnsupportedOperationException("Not used in this test");
        }

        @Override
        public ThreadCompactStartResponse threadCompactStart(ThreadCompactStartParams params) {
            ensureReady();
            throw new UnsupportedOperationException("Not used in this test");
        }

        @Override
        public TurnStartResponse turnStart(TurnStartParams params) {
            ensureReady();
            RuntimeTurn turn = new RuntimeTurn(params.threadId(), new TurnId("turn-1"), TurnStatus.RUNNING, Instant.parse("2026-03-31T00:00:01Z"), null);
            publish(new TurnStartedNotification(turn));
            return new TurnStartResponse(turn);
        }

        @Override
        public TurnResumeResponse turnResume(TurnResumeParams params) {
            ensureReady();
            throw new UnsupportedOperationException("Not used in this test");
        }

        @Override
        public TurnInterruptResponse turnInterrupt(TurnInterruptParams params) {
            ensureReady();
            return new TurnInterruptResponse(params.turnId(), true);
        }

        @Override
        public TurnSteerResponse turnSteer(TurnSteerParams params) {
            ensureReady();
            return new TurnSteerResponse(params.turnId(), true);
        }

        @Override
        public SkillsListResponse skillsList(SkillsListParams params) {
            ensureReady();
            return new SkillsListResponse(List.of());
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

        private void publish(AppServerNotification notification) {
            for (Consumer<AppServerNotification> listener : List.copyOf(listeners)) {
                listener.accept(notification);
            }
        }

        private void ensureReady() {
            if (!initializeCalled || !initializedAcknowledged) {
                throw new IllegalStateException("Not initialized");
            }
        }
    }
}
