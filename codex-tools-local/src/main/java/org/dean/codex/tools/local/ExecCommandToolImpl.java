package org.dean.codex.tools.local;

import org.dean.codex.core.exec.ExecPollResult;
import org.dean.codex.core.exec.ExecSessionId;
import org.dean.codex.core.exec.ExecSessionManager;
import org.dean.codex.core.exec.ExecSessionStatus;
import org.dean.codex.core.exec.ExecSessionSummary;
import org.dean.codex.core.exec.ExecStartRequest;
import org.dean.codex.core.tool.local.CommandApprovalPolicy;
import org.dean.codex.core.tool.local.ExecCommandTool;
import org.dean.codex.protocol.conversation.ThreadId;
import org.dean.codex.protocol.tool.CommandApproval;
import org.dean.codex.protocol.tool.CommandApprovalDecision;
import org.dean.codex.protocol.tool.ExecCommandResult;
import org.dean.codex.tools.local.exec.InMemoryExecSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;

@Component
public class ExecCommandToolImpl implements ExecCommandTool {

    private static final Logger logger = LoggerFactory.getLogger(ExecCommandToolImpl.class);
    private static final Duration DEFAULT_EXEC_YIELD = Duration.ofSeconds(10);
    private static final Duration DEFAULT_WRITE_STDIN_YIELD = Duration.ofMillis(250);
    private static final int MAX_COMMAND_LOG_LENGTH = 160;

    private final Path workspaceRoot;
    private final CommandApprovalPolicy commandApprovalPolicy;
    private final Duration commandTimeout;
    private final ExecSessionManager execSessionManager;

    public ExecCommandToolImpl(@Qualifier("codexWorkspaceRoot") Path workspaceRoot,
                               CommandApprovalPolicy commandApprovalPolicy,
                               @Qualifier("codexCommandTimeout") Duration commandTimeout) {
        this(workspaceRoot, commandApprovalPolicy, commandTimeout, new InMemoryExecSessionManager());
    }

    @Autowired
    public ExecCommandToolImpl(@Qualifier("codexWorkspaceRoot") Path workspaceRoot,
                               CommandApprovalPolicy commandApprovalPolicy,
                               @Qualifier("codexCommandTimeout") Duration commandTimeout,
                               ExecSessionManager execSessionManager) {
        this.workspaceRoot = workspaceRoot;
        this.commandApprovalPolicy = commandApprovalPolicy;
        this.commandTimeout = commandTimeout;
        this.execSessionManager = execSessionManager;
    }

    @Override
    public ExecCommandResult execCommand(ThreadId threadId,
                                         String command,
                                         Long yieldTimeMillis,
                                         Long maxRuntimeMillis,
                                         boolean pty) {
        logger.info("Copilot tool execCommand used with command={} in {}", summarizeCommand(command), workspaceRoot);
        if (command == null || command.isBlank()) {
            return notExecutedResult(
                    command,
                    workspaceRoot.toString(),
                    "BLOCKED",
                    CommandApprovalDecision.BLOCK,
                    "Command must not be blank.",
                    "Command must not be blank.");
        }

        CommandApproval approval = commandApprovalPolicy.evaluate(command);
        if (approval.decision() == CommandApprovalDecision.BLOCK) {
            return notExecutedResult(
                    command,
                    workspaceRoot.toString(),
                    "BLOCKED",
                    approval.decision(),
                    approval.reason(),
                    "Command blocked by approval policy.");
        }
        if (approval.decision() == CommandApprovalDecision.REQUIRE_APPROVAL) {
            return notExecutedResult(
                    command,
                    workspaceRoot.toString(),
                    "APPROVAL_REQUIRED",
                    approval.decision(),
                    approval.reason(),
                    "Command requires approval before execution.");
        }

        try {
            ExecPollResult result = execSessionManager.start(new ExecStartRequest(
                    threadId,
                    command,
                    workspaceRoot,
                    durationOrDefault(yieldTimeMillis, DEFAULT_EXEC_YIELD),
                    durationOrDefault(maxRuntimeMillis, commandTimeout),
                    pty));
            return toExecCommandResult(result, approval);
        }
        catch (Exception exception) {
            return notExecutedResult(
                    command,
                    workspaceRoot.toString(),
                    "START_FAILED",
                    approval.decision(),
                    approval.reason(),
                    exception.getMessage());
        }
    }

    @Override
    public ExecCommandResult writeStdin(ThreadId threadId,
                                        String sessionId,
                                        String input,
                                        Long yieldTimeMillis) {
        if (sessionId == null || sessionId.isBlank()) {
            return notExecutedResult(
                    "",
                    workspaceRoot.toString(),
                    "SESSION_NOT_FOUND",
                    CommandApprovalDecision.BLOCK,
                    "Session id is required.",
                    "Session id is required.");
        }
        ExecSessionId execSessionId = new ExecSessionId(sessionId);
        ExecSessionSummary session = execSessionManager.session(execSessionId)
                .orElse(null);
        if (session == null) {
            return notExecutedResult(
                    "",
                    workspaceRoot.toString(),
                    "SESSION_NOT_FOUND",
                    CommandApprovalDecision.BLOCK,
                    "Unknown exec session id.",
                    "Unknown exec session id: " + sessionId);
        }
        if (threadId != null && session.threadId() != null && !threadId.equals(session.threadId())) {
            return notExecutedResult(
                    session.command(),
                    session.workingDirectory(),
                    "THREAD_MISMATCH",
                    CommandApprovalDecision.BLOCK,
                    "Exec session belongs to a different thread.",
                    "Exec session " + sessionId + " does not belong to thread " + threadId.value());
        }

        ExecPollResult result = execSessionManager.writeStdin(
                execSessionId,
                input,
                durationOrDefault(yieldTimeMillis, DEFAULT_WRITE_STDIN_YIELD));
        return toExecCommandResult(result,
                new CommandApproval(CommandApprovalDecision.ALLOW, "Previously approved when the command was started."));
    }

    private Duration durationOrDefault(Long millis, Duration defaultValue) {
        if (millis == null) {
            return defaultValue;
        }
        return millis < 0 ? Duration.ZERO : Duration.ofMillis(millis);
    }

    private ExecCommandResult toExecCommandResult(ExecPollResult result, CommandApproval approval) {
        ExecSessionSummary session = result.session();
        ExecSessionStatus status = session.status();
        boolean success = switch (status) {
            case RUNNING -> true;
            case COMPLETED -> session.exitCode() == null || session.exitCode() == 0;
            default -> false;
        };
        return new ExecCommandResult(
                success,
                session.sessionId().value(),
                session.command(),
                session.workingDirectory(),
                session.processId(),
                status.name(),
                session.exitCode(),
                session.pty(),
                result.stdout(),
                result.stderr(),
                true,
                approval.decision(),
                approval.reason(),
                result.error());
    }

    private ExecCommandResult notExecutedResult(String command,
                                                String workingDirectory,
                                                String status,
                                                CommandApprovalDecision approvalDecision,
                                                String approvalReason,
                                                String error) {
        return new ExecCommandResult(
                false,
                "",
                command,
                workingDirectory,
                null,
                status,
                null,
                false,
                "",
                "",
                false,
                approvalDecision,
                approvalReason,
                error);
    }

    private String summarizeCommand(String command) {
        if (command == null || command.isBlank()) {
            return "<blank>";
        }
        String normalized = command.replaceAll("\\s+", " ").trim();
        return normalized.length() <= MAX_COMMAND_LOG_LENGTH
                ? normalized
                : normalized.substring(0, MAX_COMMAND_LOG_LENGTH) + "…";
    }
}
