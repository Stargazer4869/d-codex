package org.dean.codex.cli.interactive;

import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ConsoleAgentPicker {
    private final Terminal terminal;

    public ConsoleAgentPicker(Terminal terminal) {
        this.terminal = terminal;
    }

    public Optional<AgentPickerEntry> choose(List<AgentPickerEntry> entries) throws IOException {
        AgentPickerModel model = new AgentPickerModel(entries);
        if (model.isEmpty()) {
            return Optional.empty();
        }

        Attributes previousAttributes = terminal.enterRawMode();
        int renderedLines = 0;
        try {
            renderedLines = render(model, renderedLines);
            NonBlockingReader reader = terminal.reader();
            while (true) {
                int ch = reader.read();
                if (ch == '\r' || ch == '\n') {
                    return model.selected();
                }
                if (ch == 3) {
                    return Optional.empty();
                }
                if (ch == 27) {
                    int second = reader.read(50L);
                    if (second == '[') {
                        int third = reader.read(50L);
                        if (third == 'A') {
                            model.moveUp();
                            renderedLines = render(model, renderedLines);
                            continue;
                        }
                        if (third == 'B') {
                            model.moveDown();
                            renderedLines = render(model, renderedLines);
                            continue;
                        }
                    }
                    return Optional.empty();
                }
            }
        }
        finally {
            terminal.setAttributes(previousAttributes);
            terminal.writer().flush();
        }
    }

    private int render(AgentPickerModel model, int previousRenderedLines) {
        PrintWriter writer = terminal.writer();
        if (previousRenderedLines > 0) {
            writer.print("\033[" + previousRenderedLines + "A");
        }
        List<String> lines = new ArrayList<>();
        lines.add("Agents");
        lines.addAll(model.renderRows());
        lines.add("Use Up/Down to choose, Enter to switch, Esc to cancel.");
        for (String line : lines) {
            writer.print("\033[2K\r");
            writer.println(line);
        }
        writer.flush();
        return lines.size();
    }
}
