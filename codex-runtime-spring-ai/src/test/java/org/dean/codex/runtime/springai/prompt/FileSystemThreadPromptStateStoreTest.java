package org.dean.codex.runtime.springai.prompt;

import org.dean.codex.core.conversation.ConversationStore;
import org.dean.codex.protocol.conversation.ThreadId;
import org.dean.codex.runtime.springai.conversation.FileSystemConversationStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileSystemThreadPromptStateStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void persistsPromptSnapshotAcrossStoreInstances() {
        ConversationStore conversationStore = new FileSystemConversationStore(tempDir);
        ThreadId threadId = conversationStore.createThread("Prompt thread");
        FileSystemThreadPromptStateStore firstStore = new FileSystemThreadPromptStateStore(conversationStore, tempDir);
        ThreadPromptSnapshot snapshot = new ThreadPromptSnapshot(
                "Base instructions",
                List.of("Project instructions:\nStay sharp."),
                Instant.parse("2026-04-09T00:00:00Z"));

        firstStore.write(threadId, snapshot);

        ConversationStore restartedConversationStore = new FileSystemConversationStore(tempDir);
        FileSystemThreadPromptStateStore restartedStore = new FileSystemThreadPromptStateStore(restartedConversationStore, tempDir);

        assertEquals(snapshot, restartedStore.read(threadId).orElseThrow());
    }

    @Test
    void copyClonesPersistedSnapshotForTargetThread() {
        ConversationStore conversationStore = new FileSystemConversationStore(tempDir);
        ThreadId sourceThreadId = conversationStore.createThread("Source");
        ThreadId targetThreadId = conversationStore.createThread("Target");
        FileSystemThreadPromptStateStore store = new FileSystemThreadPromptStateStore(conversationStore, tempDir);
        ThreadPromptSnapshot snapshot = new ThreadPromptSnapshot(
                "Base instructions",
                List.of("User instructions:\nBe careful."),
                Instant.parse("2026-04-09T00:00:00Z"));
        store.write(sourceThreadId, snapshot);

        store.copy(sourceThreadId, targetThreadId);

        assertEquals(snapshot, store.read(targetThreadId).orElseThrow());
    }

    @Test
    void rejectsUnknownThreads() {
        ConversationStore conversationStore = new FileSystemConversationStore(tempDir);
        FileSystemThreadPromptStateStore store = new FileSystemThreadPromptStateStore(conversationStore, tempDir);
        ThreadPromptSnapshot snapshot = new ThreadPromptSnapshot("base", List.of(), Instant.parse("2026-04-09T00:00:00Z"));

        assertThrows(IllegalArgumentException.class, () -> store.read(new ThreadId("missing")));
        assertThrows(IllegalArgumentException.class, () -> store.write(new ThreadId("missing"), snapshot));
    }
}
