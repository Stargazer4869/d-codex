package org.dean.codex.runtime.springai.prompt;

import org.dean.codex.protocol.conversation.ThreadId;

import java.util.Optional;

public interface ThreadPromptStateStore {

    Optional<ThreadPromptSnapshot> read(ThreadId threadId);

    ThreadPromptSnapshot write(ThreadId threadId, ThreadPromptSnapshot snapshot);

    default ThreadPromptSnapshot writeIfAbsent(ThreadId threadId, ThreadPromptSnapshot snapshot) {
        return read(threadId).orElseGet(() -> write(threadId, snapshot));
    }

    default void copy(ThreadId sourceThreadId, ThreadId targetThreadId) {
        read(sourceThreadId).ifPresent(snapshot -> write(targetThreadId, snapshot));
    }
}
