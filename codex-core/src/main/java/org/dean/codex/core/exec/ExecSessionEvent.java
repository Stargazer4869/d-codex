package org.dean.codex.core.exec;

import java.time.Instant;

public record ExecSessionEvent(ExecSessionEventType type,
                               ExecSessionSummary session,
                               String stdout,
                               String stderr,
                               String error,
                               ExecTerminalInteraction terminalInteraction,
                               Instant createdAt) {

    public ExecSessionEvent {
        stdout = stdout == null ? "" : stdout;
        stderr = stderr == null ? "" : stderr;
        error = error == null ? "" : error;
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public ExecSessionEvent(ExecSessionEventType type,
                            ExecSessionSummary session,
                            String stdout,
                            String stderr,
                            String error,
                            Instant createdAt) {
        this(type, session, stdout, stderr, error, null, createdAt);
    }

    public ExecSessionEvent(ExecSessionEventType type,
                            ExecSessionSummary session,
                            ExecTerminalInteraction terminalInteraction,
                            Instant createdAt) {
        this(type, session, "", "", "", terminalInteraction, createdAt);
    }
}
