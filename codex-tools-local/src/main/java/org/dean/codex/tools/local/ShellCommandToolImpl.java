package org.dean.codex.tools.local;

import org.dean.codex.core.exec.ExecPollResult;
import org.dean.codex.core.exec.ExecSessionManager;
import org.dean.codex.core.exec.ExecSessionStatus;
import org.dean.codex.core.exec.ExecStartRequest;
import org.dean.codex.core.tool.local.CommandApprovalPolicy;
import org.dean.codex.core.tool.local.ShellCommandTool;
import org.dean.codex.protocol.conversation.ThreadId;
import org.dean.codex.protocol.tool.CommandApproval;
import org.dean.codex.protocol.tool.CommandApprovalDecision;
import org.dean.codex.protocol.tool.ShellCommandResult;
import org.dean.codex.tools.local.exec.InMemoryExecSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;

@Component
public class ShellCommandToolImpl implements ShellCommandTool {

    private static final Logger logger = LoggerFactory.getLogger(ShellCommandToolImpl.class);
    private static final int MAX_COMMAND_LOG_LENGTH = 160;
    private static final Duration POLL_INTERVAL = Duration.ofMillis(50);

    private final Path workspaceRoot;
    private final CommandApprovalPolicy commandApprovalPolicy;
    private final Duration commandTimeout;
    private final ExecSessionManager execSessionManager;

    public ShellCommandToolImpl(@Qualifier("codexWorkspaceRoot") Path workspaceRoot,
                                CommandApprovalPolicy commandApprovalPolicy,
                                @Qualifier("codexCommandTimeout") Duration commandTimeout) {
        this(workspaceRoot, commandApprovalPolicy, commandTimeout, new InMemoryExecSessionManager());
    }

    @Autowired
    public ShellCommandToolImpl(@Qualifier("codexWorkspaceRoot") Path workspaceRoot,
                                CommandApprovalPolicy commandApprovalPolicy,
                                @Qualifier("codexCommandTimeout") Duration commandTimeout,
                                ExecSessionManager execSessionManager) {
        this.workspaceRoot = workspaceRoot;
        this.commandApprovalPolicy = commandApprovalPolicy;
        this.commandTimeout = commandTimeout;
        this.execSessionManager = execSessionManager;
    }

    @Override
    @Tool(description = "Run a zsh shell command from the workspace root. Safe inspection and verification commands may run automatically, while sensitive commands can be returned as approval-required or blocked by policy.")
    public ShellCommandResult runCommand(String command) {
        return runCommand(null, command);
    }

    @Override
    public ShellCommandResult runCommand(ThreadId threadId, String command) {
        logger.info("Copilot tool runCommand used with command={} in {}", summarizeCommand(command), workspaceRoot);
        if (command == null || command.isBlank()) {
            return new ShellCommandResult(
                    false,
                    "",
                    -1,
                    "",
                    "",
                    false,
                    workspaceRoot.toString(),
                    false,
                    CommandApprovalDecision.BLOCK,
                    "Command must not be blank.",
                    "Command must not be blank.");
        }

        CommandApproval approval = commandApprovalPolicy.evaluate(command);
        if (approval.decision() == CommandApprovalDecision.BLOCK) {
            return new ShellCommandResult(
                    false,
                    command,
                    -1,
                    "",
                    "",
                    false,
                    workspaceRoot.toString(),
                    false,
                    approval.decision(),
                    approval.reason(),
                    "Command blocked by approval policy.");
        }
        if (approval.decision() == CommandApprovalDecision.REQUIRE_APPROVAL) {
            return new ShellCommandResult(
                    false,
                    command,
                    -1,
                    "",
                    "",
                    false,
                    workspaceRoot.toString(),
                    false,
                    approval.decision(),
                    approval.reason(),
                    "Command requires approval before execution.");
        }

        return executeCommand(threadId, command, approval);
    }

    @Override
    public ShellCommandResult runApprovedCommand(String command) {
        return runApprovedCommand(null, command);
    }

    @Override
    public ShellCommandResult runApprovedCommand(ThreadId threadId, String command) {
        logger.info("Codex approved shell execution for command={} in {}", summarizeCommand(command), workspaceRoot);
        if (command == null || command.isBlank()) {
            return new ShellCommandResult(
                    false,
                    "",
                    -1,
                    "",
                    "",
                    false,
                    workspaceRoot.toString(),
                    false,
                    CommandApprovalDecision.BLOCK,
                    "Command must not be blank.",
                    "Command must not be blank.");
        }

        return executeCommand(threadId, command, new CommandApproval(CommandApprovalDecision.ALLOW, "Explicitly approved from CLI."));
    }

    private ShellCommandResult executeCommand(ThreadId threadId, String command, CommandApproval approval) {
        try {
            ExecPollResult result = execSessionManager.start(new ExecStartRequest(
                    threadId,
                    command,
                    workspaceRoot,
                    POLL_INTERVAL,
                    commandTimeout,
                    false));
            StringBuilder stdout = new StringBuilder(result.stdout());
            StringBuilder stderr = new StringBuilder(result.stderr());
            long deadlineNanos = System.nanoTime() + commandTimeout.plusSeconds(1).toNanos();
            while (result.session().running() && System.nanoTime() < deadlineNanos) {
                result = execSessionManager.poll(result.session().sessionId(), POLL_INTERVAL);
                stdout.append(result.stdout());
                stderr.append(result.stderr());
            }
            if (result.session().running()) {
                execSessionManager.terminate(result.session().sessionId());
                return new ShellCommandResult(
                        false,
                        command,
                        -1,
                        stdout.toString(),
                        stderr.toString(),
                        true,
                        workspaceRoot.toString(),
                        true,
                        approval.decision(),
                        approval.reason(),
                        "Command timed out after %d seconds.".formatted(commandTimeout.toSeconds()));
            }
            return toShellCommandResult(command, approval, result, stdout.toString(), stderr.toString());
        }
        catch (Exception exception) {
            return new ShellCommandResult(
                    false,
                    command,
                    -1,
                    "",
                    "",
                    false,
                    workspaceRoot.toString(),
                    true,
                    approval.decision(),
                    approval.reason(),
                    exception.getMessage());
        }
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

    private ShellCommandResult toShellCommandResult(String command,
                                                    CommandApproval approval,
                                                    ExecPollResult result,
                                                    String stdout,
                                                    String stderr) {
        ExecSessionStatus status = result.session().status();
        int exitCode = result.session().exitCode() == null ? -1 : result.session().exitCode();
        if (status == ExecSessionStatus.START_FAILED) {
            return new ShellCommandResult(
                    false,
                    command,
                    -1,
                    stdout,
                    stderr,
                    false,
                    workspaceRoot.toString(),
                    true,
                    approval.decision(),
                    approval.reason(),
                    result.error());
        }
        if (status == ExecSessionStatus.TIMED_OUT) {
            return new ShellCommandResult(
                    false,
                    command,
                    exitCode,
                    stdout,
                    stderr,
                    true,
                    workspaceRoot.toString(),
                    true,
                    approval.decision(),
                    approval.reason(),
                    "Command timed out after %d seconds.".formatted(commandTimeout.toSeconds()));
        }
        if (status == ExecSessionStatus.TERMINATED) {
            return new ShellCommandResult(
                    false,
                    command,
                    exitCode,
                    stdout,
                    stderr,
                    false,
                    workspaceRoot.toString(),
                    true,
                    approval.decision(),
                    approval.reason(),
                    "Command was terminated before completion.");
        }
        boolean success = status == ExecSessionStatus.COMPLETED && exitCode == 0;
        return new ShellCommandResult(
                success,
                command,
                exitCode,
                stdout,
                stderr,
                false,
                workspaceRoot.toString(),
                true,
                approval.decision(),
                approval.reason(),
                success ? "" : "Command exited with a non-zero status.");
    }
}
