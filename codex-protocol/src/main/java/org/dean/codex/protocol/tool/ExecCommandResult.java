package org.dean.codex.protocol.tool;

public record ExecCommandResult(boolean success,
                                String sessionId,
                                String command,
                                String workingDirectory,
                                Long processId,
                                String status,
                                Integer exitCode,
                                boolean pty,
                                String stdout,
                                String stderr,
                                boolean executed,
                                CommandApprovalDecision approvalDecision,
                                String approvalReason,
                                String error) {

    public ExecCommandResult {
        sessionId = sessionId == null ? "" : sessionId;
        command = command == null ? "" : command;
        workingDirectory = workingDirectory == null ? "" : workingDirectory;
        status = status == null ? "" : status;
        stdout = stdout == null ? "" : stdout;
        stderr = stderr == null ? "" : stderr;
        error = error == null ? "" : error;
    }
}
