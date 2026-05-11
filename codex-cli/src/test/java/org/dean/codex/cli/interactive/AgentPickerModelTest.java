package org.dean.codex.cli.interactive;

import org.dean.codex.protocol.conversation.ThreadId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentPickerModelTest {

    @Test
    void startsOnCurrentEntryAndWrapsNavigation() {
        ThreadId main = new ThreadId("thread-main");
        ThreadId first = new ThreadId("thread-first");
        ThreadId second = new ThreadId("thread-second");
        AgentPickerModel model = new AgentPickerModel(List.of(
                new AgentPickerEntry(main, "Main [default]", "thread-main", false, false),
                new AgentPickerEntry(first, "Scout [explorer]", "thread-first", true, false),
                new AgentPickerEntry(second, "Builder [worker]", "thread-second", false, true)
        ));

        assertEquals(1, model.selectedIndex());
        assertEquals(first, model.selected().orElseThrow().threadId());

        model.moveDown();
        assertEquals(second, model.selected().orElseThrow().threadId());

        model.moveDown();
        assertEquals(main, model.selected().orElseThrow().threadId());

        model.moveUp();
        assertEquals(second, model.selected().orElseThrow().threadId());
    }

    @Test
    void renderedRowsMarkSelectionCurrentAndClosedState() {
        AgentPickerModel model = new AgentPickerModel(List.of(
                new AgentPickerEntry(new ThreadId("thread-main"), "Main [default]", "thread-main", true, false),
                new AgentPickerEntry(new ThreadId("thread-agent"), "Scout [explorer]", "thread-agent", false, true)
        ));

        assertEquals(List.of(
                "> * Main [default]  thread-main",
                "    Scout [explorer] [closed]  thread-agent"
        ), model.renderRows());
    }
}
