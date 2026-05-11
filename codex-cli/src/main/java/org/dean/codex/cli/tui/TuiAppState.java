package org.dean.codex.cli.tui;

import org.dean.codex.protocol.appserver.ConfigGetResponse;
import org.dean.codex.protocol.conversation.ThreadId;
import org.dean.codex.protocol.conversation.ThreadSummary;
import org.dean.codex.protocol.conversation.TurnId;
import org.dean.codex.protocol.conversation.TurnStatus;
import org.dean.codex.protocol.runtime.RuntimeTurn;

import java.util.ArrayList;
import java.util.List;

public final class TuiAppState {

    private ThreadId activeThreadId;
    private ThreadSummary activeThread;
    private RuntimeTurn activeTurn;
    private final List<TranscriptCell> transcript = new ArrayList<>();
    private final List<ThreadSummary> relatedThreads = new ArrayList<>();
    private final List<ThreadSummary> sessions = new ArrayList<>();
    private ConfigGetResponse config;
    private PickerOverlay overlay;
    private String composer = "";
    private String statusMessage = "Ready";
    private int transcriptScroll;
    private boolean exitRequested;
    private boolean preferFreshPromptOnStart;

    public ThreadId activeThreadId() {
        return activeThreadId;
    }

    public void activeThread(ThreadSummary thread) {
        this.activeThread = thread;
        this.activeThreadId = thread == null ? null : thread.threadId();
    }

    public ThreadSummary activeThread() {
        return activeThread;
    }

    public RuntimeTurn activeTurn() {
        return activeTurn;
    }

    public TurnId activeTurnId() {
        return activeTurn == null ? null : activeTurn.turnId();
    }

    public void activeTurn(RuntimeTurn activeTurn) {
        this.activeTurn = activeTurn;
    }

    public boolean hasActiveTurn() {
        return activeTurn != null
                && (activeTurn.status() == TurnStatus.RUNNING || activeTurn.status() == TurnStatus.AWAITING_APPROVAL);
    }

    public List<TranscriptCell> transcript() {
        return transcript;
    }

    public void replaceTranscript(List<TranscriptCell> cells) {
        transcript.clear();
        if (cells != null) {
            transcript.addAll(cells);
        }
        transcriptScroll = 0;
    }

    public void appendCell(TranscriptCell cell) {
        if (cell != null) {
            transcript.add(cell);
            transcriptScroll = 0;
        }
    }

    public List<ThreadSummary> relatedThreads() {
        return relatedThreads;
    }

    public void replaceRelatedThreads(List<ThreadSummary> threads) {
        relatedThreads.clear();
        if (threads != null) {
            relatedThreads.addAll(threads);
        }
    }

    public List<ThreadSummary> sessions() {
        return sessions;
    }

    public void replaceSessions(List<ThreadSummary> threads) {
        sessions.clear();
        if (threads != null) {
            sessions.addAll(threads);
        }
    }

    public ConfigGetResponse config() {
        return config;
    }

    public void config(ConfigGetResponse config) {
        this.config = config;
    }

    public PickerOverlay overlay() {
        return overlay;
    }

    public void overlay(PickerOverlay overlay) {
        this.overlay = overlay;
    }

    public boolean overlayOpen() {
        return overlay != null;
    }

    public String composer() {
        return composer;
    }

    public void composer(String composer) {
        this.composer = composer == null ? "" : composer;
    }

    public String consumeComposer() {
        String value = composer.trim();
        composer = "";
        return value;
    }

    public String statusMessage() {
        return statusMessage;
    }

    public void statusMessage(String statusMessage) {
        this.statusMessage = statusMessage == null || statusMessage.isBlank() ? "Ready" : statusMessage.trim();
    }

    public int transcriptScroll() {
        return transcriptScroll;
    }

    public void scrollTranscript(int delta) {
        transcriptScroll = Math.max(0, transcriptScroll + delta);
    }

    public void scrollTranscriptToBottom() {
        transcriptScroll = 0;
    }

    public boolean exitRequested() {
        return exitRequested;
    }

    public void requestExit() {
        exitRequested = true;
    }

    public boolean preferFreshPromptOnStart() {
        return preferFreshPromptOnStart;
    }

    public void preferFreshPromptOnStart(boolean preferFreshPromptOnStart) {
        this.preferFreshPromptOnStart = preferFreshPromptOnStart;
    }
}
