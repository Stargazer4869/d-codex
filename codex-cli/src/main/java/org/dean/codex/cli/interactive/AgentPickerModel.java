package org.dean.codex.cli.interactive;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AgentPickerModel {
    private final List<AgentPickerEntry> entries;
    private int selectedIndex;

    public AgentPickerModel(List<AgentPickerEntry> entries) {
        this.entries = entries == null ? List.of() : List.copyOf(entries);
        this.selectedIndex = initialSelectedIndex(this.entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int selectedIndex() {
        return selectedIndex;
    }

    public Optional<AgentPickerEntry> selected() {
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(entries.get(selectedIndex));
    }

    public void moveUp() {
        if (entries.isEmpty()) {
            return;
        }
        selectedIndex = selectedIndex == 0 ? entries.size() - 1 : selectedIndex - 1;
    }

    public void moveDown() {
        if (entries.isEmpty()) {
            return;
        }
        selectedIndex = (selectedIndex + 1) % entries.size();
    }

    public List<String> renderRows() {
        List<String> rows = new ArrayList<>(entries.size());
        for (int index = 0; index < entries.size(); index++) {
            AgentPickerEntry entry = entries.get(index);
            StringBuilder row = new StringBuilder();
            row.append(index == selectedIndex ? "> " : "  ");
            row.append(entry.current() ? "* " : "  ");
            row.append(entry.label());
            if (entry.closed()) {
                row.append(" [closed]");
            }
            if (!entry.description().isBlank()) {
                row.append("  ").append(entry.description());
            }
            rows.add(row.toString());
        }
        return List.copyOf(rows);
    }

    private int initialSelectedIndex(List<AgentPickerEntry> entries) {
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).current()) {
                return index;
            }
        }
        return 0;
    }
}
