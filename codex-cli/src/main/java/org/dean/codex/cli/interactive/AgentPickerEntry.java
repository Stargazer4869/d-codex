package org.dean.codex.cli.interactive;

import org.dean.codex.protocol.conversation.ThreadId;

import java.util.Objects;

public record AgentPickerEntry(ThreadId threadId,
                               String label,
                               String description,
                               boolean current,
                               boolean closed) {

    public AgentPickerEntry {
        threadId = Objects.requireNonNull(threadId, "threadId");
        label = normalize(label, "Agent");
        description = normalize(description, "");
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
