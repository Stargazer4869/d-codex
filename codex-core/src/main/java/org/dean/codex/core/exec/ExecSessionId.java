package org.dean.codex.core.exec;

public record ExecSessionId(String value) {

    public ExecSessionId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Exec session id must not be blank.");
        }
    }
}
