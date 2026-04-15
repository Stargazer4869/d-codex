package org.dean.codex.runtime.springai.model;

import org.dean.codex.core.conversation.ConversationStore;
import org.dean.codex.protocol.conversation.ThreadId;
import org.dean.codex.protocol.conversation.ThreadSummary;
import org.dean.codex.runtime.springai.conversation.FileSystemConversationStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileSystemThreadModelSessionStateStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void persistsModelSessionSnapshotAcrossStoreInstances() {
        ConversationStore conversationStore = new FileSystemConversationStore(tempDir);
        ThreadId threadId = conversationStore.createThread("Model session thread");
        FileSystemThreadModelSessionStateStore firstStore = new FileSystemThreadModelSessionStateStore(conversationStore, tempDir);
        ThreadSummary threadSummary = conversationStore.listThreads().stream()
                .filter(summary -> summary.threadId().equals(threadId))
                .findFirst()
                .orElseThrow();
        ThreadModelSessionSnapshot snapshot = ThreadModelSessionSnapshot.initial(threadSummary)
                .advance(new org.dean.codex.protocol.conversation.TurnId("turn-1"),
                        new org.dean.codex.core.model.ModelResponseMetadata("response-1", "session-1", "completed"));

        firstStore.write(threadId, snapshot);

        ConversationStore restartedConversationStore = new FileSystemConversationStore(tempDir);
        FileSystemThreadModelSessionStateStore restartedStore = new FileSystemThreadModelSessionStateStore(restartedConversationStore, tempDir);

        assertEquals(snapshot, restartedStore.read(threadId).orElseThrow());
    }

    @Test
    void rejectsUnknownThreads() {
        ConversationStore conversationStore = new FileSystemConversationStore(tempDir);
        FileSystemThreadModelSessionStateStore store = new FileSystemThreadModelSessionStateStore(conversationStore, tempDir);
        ThreadModelSessionSnapshot snapshot = new ThreadModelSessionSnapshot(
                Instant.parse("2026-04-13T00:00:00Z"),
                null,
                new ThreadId("thread-1"),
                null,
                null,
                null,
                "response-1",
                "session-1",
                null);

        assertThrows(IllegalArgumentException.class, () -> store.read(new ThreadId("missing")));
        assertThrows(IllegalArgumentException.class, () -> store.write(new ThreadId("missing"), snapshot));
    }
}
