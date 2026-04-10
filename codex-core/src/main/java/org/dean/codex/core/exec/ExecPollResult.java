package org.dean.codex.core.exec;

public record ExecPollResult(ExecSessionSummary session,
                             String stdout,
                             String stderr,
                             String error) {

    public ExecPollResult {
        stdout = stdout == null ? "" : stdout;
        stderr = stderr == null ? "" : stderr;
        error = error == null ? "" : error;
    }
}
