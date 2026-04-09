package org.dean.codex.runtime.springai.prompt;

import org.dean.codex.protocol.conversation.ThreadId;

import java.time.Instant;
import java.util.List;

public record ThreadPromptSnapshot(String baseInstructions,
                                   List<String> userInstructions,
                                   Instant persistedAt,
                                   ThreadId inheritedFromThreadId) {

    public ThreadPromptSnapshot(String baseInstructions,
                                List<String> userInstructions,
                                Instant persistedAt) {
        this(baseInstructions, userInstructions, persistedAt, null);
    }

    public ThreadPromptSnapshot {
        baseInstructions = baseInstructions == null ? "" : baseInstructions;
        userInstructions = userInstructions == null ? List.of() : List.copyOf(userInstructions);
        persistedAt = persistedAt == null ? Instant.now() : persistedAt;
    }

    public ThreadPromptSnapshot inheritedFrom(ThreadId sourceThreadId) {
        return new ThreadPromptSnapshot(
                baseInstructions,
                userInstructions,
                Instant.now(),
                sourceThreadId);
    }
}
