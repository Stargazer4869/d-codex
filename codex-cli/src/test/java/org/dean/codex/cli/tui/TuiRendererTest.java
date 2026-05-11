package org.dean.codex.cli.tui;

import org.dean.codex.protocol.appserver.ConfigGetResponse;
import org.dean.codex.protocol.conversation.ThreadId;
import org.dean.codex.protocol.conversation.ThreadSummary;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TuiRendererTest {

    @Test
    void rendersTranscriptComposerFooterAndOverlay() {
        TuiAppState state = new TuiAppState();
        ThreadId threadId = new ThreadId("thread-123456789");
        state.activeThread(new ThreadSummary(threadId, "Inspect UX", Instant.EPOCH, Instant.EPOCH, 2));
        state.config(new ConfigGetResponse(threadId, "openai", "gpt-5.4", "workspace-write", "review-sensitive", "/repo", List.of()));
        state.appendCell(new TranscriptCell("user", "user", "Please inspect the terminal UX."));
        state.appendCell(new TranscriptCell("assistant", "assistant", "The composer and picker are visible."));
        state.composer("/ag");
        PickerOverlay overlay = new PickerOverlay(
                PickerOverlay.Kind.SLASH,
                "Commands",
                "Type to filter commands",
                List.of(
                        new PickerItem("agent", "/agent", "Navigate agents"),
                        new PickerItem("resume", "/resume", "Resume a session")));
        overlay.setFilter("ag");
        state.overlay(overlay);

        List<String> lines = new TuiRenderer().renderLines(state, 80, 18);

        assertEquals(18, lines.size());
        assertTrue(lines.stream().anyMatch(line -> line.contains("Codex Java TUI")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Please inspect the terminal UX.")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("/agent")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("model=gpt-5.4")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("> /ag")));
    }

    @Test
    void pickerSelectionSkipsDisabledRows() {
        PickerOverlay overlay = new PickerOverlay(
                PickerOverlay.Kind.APPROVALS,
                "Approvals",
                "hint",
                List.of(
                        new PickerItem("none", "No approvals", "", true),
                        new PickerItem("approval-123", "approval-123", "Run tests")));

        assertEquals("approval-123", overlay.selectedItem().id());

        overlay.moveSelection(1);

        assertEquals("approval-123", overlay.selectedItem().id());
    }

    @Test
    void transcriptCanScrollBackToOlderContent() {
        TuiAppState state = new TuiAppState();
        for (int index = 1; index <= 20; index++) {
            state.appendCell(new TranscriptCell("assistant", "assistant", "message-" + index));
        }
        TuiRenderer renderer = new TuiRenderer();

        List<String> bottom = renderer.renderLines(state, 80, 10);

        assertTrue(bottom.stream().anyMatch(line -> line.contains("message-20")));

        state.scrollTranscript(24);
        List<String> scrolled = renderer.renderLines(state, 80, 10);

        assertTrue(scrolled.stream().anyMatch(line -> line.contains("message-8")));
    }
}
