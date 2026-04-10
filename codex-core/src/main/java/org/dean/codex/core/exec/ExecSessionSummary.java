package org.dean.codex.core.exec;

import org.dean.codex.protocol.conversation.ThreadId;

import java.time.Instant;

public record ExecSessionSummary(ExecSessionId sessionId,
                                 ThreadId threadId,
                                 String command,
                                 String workingDirectory,
                                 Long processId,
                                 boolean pty,
                                 ExecSessionStatus status,
                                 Instant startedAt,
                                 Instant completedAt,
                                 Integer exitCode) {

    public boolean running() {
        return status == ExecSessionStatus.RUNNING;
    }
}
