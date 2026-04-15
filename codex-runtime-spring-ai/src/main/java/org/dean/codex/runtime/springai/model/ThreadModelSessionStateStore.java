package org.dean.codex.runtime.springai.model;

import org.dean.codex.protocol.conversation.ThreadId;

import java.util.Optional;

public interface ThreadModelSessionStateStore {

    Optional<ThreadModelSessionSnapshot> read(ThreadId threadId);

    ThreadModelSessionSnapshot write(ThreadId threadId, ThreadModelSessionSnapshot snapshot);

    default ThreadModelSessionSnapshot writeIfAbsent(ThreadId threadId, ThreadModelSessionSnapshot snapshot) {
        return read(threadId).orElseGet(() -> write(threadId, snapshot));
    }
}
