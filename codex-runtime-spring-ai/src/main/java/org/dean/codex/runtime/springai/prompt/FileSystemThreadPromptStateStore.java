package org.dean.codex.runtime.springai.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dean.codex.core.conversation.ConversationStore;
import org.dean.codex.protocol.conversation.ThreadId;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

public class FileSystemThreadPromptStateStore implements ThreadPromptStateStore {

    private static final String THREADS_DIRECTORY = "threads";
    private static final String THREAD_PROMPT_FILE = "thread-prompt.json";

    private final ConversationStore conversationStore;
    private final ObjectMapper objectMapper;
    private final Path threadsRoot;

    public FileSystemThreadPromptStateStore(ConversationStore conversationStore, Path storageRoot) {
        this.conversationStore = conversationStore;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.threadsRoot = storageRoot.toAbsolutePath().normalize().resolve(THREADS_DIRECTORY);
    }

    @Override
    public synchronized Optional<ThreadPromptSnapshot> read(ThreadId threadId) {
        validateThread(threadId);
        Path stateFile = stateFile(threadId);
        if (!Files.exists(stateFile)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(stateFile.toFile(), ThreadPromptSnapshot.class));
        }
        catch (IOException exception) {
            throw new IllegalStateException("Unable to read prompt state for thread " + threadId.value(), exception);
        }
    }

    @Override
    public synchronized ThreadPromptSnapshot write(ThreadId threadId, ThreadPromptSnapshot snapshot) {
        validateThread(threadId);
        Path stateFile = stateFile(threadId);
        try {
            Files.createDirectories(stateFile.getParent());
            Path tempFile = Files.createTempFile(stateFile.getParent(), stateFile.getFileName().toString(), ".tmp");
            try {
                objectMapper.writeValue(tempFile.toFile(), snapshot);
                Files.move(tempFile, stateFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            finally {
                Files.deleteIfExists(tempFile);
            }
            return snapshot;
        }
        catch (IOException exception) {
            throw new IllegalStateException("Unable to write prompt state for thread " + threadId.value(), exception);
        }
    }

    @Override
    public synchronized void copy(ThreadId sourceThreadId, ThreadId targetThreadId) {
        validateThread(sourceThreadId);
        validateThread(targetThreadId);
        read(sourceThreadId).ifPresent(snapshot -> write(targetThreadId, snapshot));
    }

    private void validateThread(ThreadId threadId) {
        if (threadId == null || !conversationStore.exists(threadId)) {
            throw new IllegalArgumentException("Unknown thread: " + (threadId == null ? "(null)" : threadId.value()));
        }
    }

    private Path stateFile(ThreadId threadId) {
        return threadsRoot.resolve(threadId.value()).resolve(THREAD_PROMPT_FILE);
    }
}
