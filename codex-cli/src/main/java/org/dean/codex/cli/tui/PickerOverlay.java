package org.dean.codex.cli.tui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PickerOverlay {

    public enum Kind {
        SLASH,
        AGENT,
        RESUME,
        MODEL,
        SKILLS,
        APPROVALS,
        HELP
    }

    private final Kind kind;
    private final String title;
    private final String hint;
    private final List<PickerItem> items;
    private String filter = "";
    private int selectedIndex;

    public PickerOverlay(Kind kind, String title, String hint, List<PickerItem> items) {
        this.kind = kind == null ? Kind.HELP : kind;
        this.title = blankToDefault(title, this.kind.name().toLowerCase(Locale.ROOT));
        this.hint = hint == null ? "" : hint.trim();
        this.items = items == null ? List.of() : List.copyOf(items);
        this.selectedIndex = firstEnabledIndex(filteredItems());
    }

    public Kind kind() {
        return kind;
    }

    public String title() {
        return title;
    }

    public String hint() {
        return hint;
    }

    public String filter() {
        return filter;
    }

    public void setFilter(String filter) {
        this.filter = filter == null ? "" : filter.trim();
        List<PickerItem> filtered = filteredItems();
        if (filtered.isEmpty()) {
            selectedIndex = 0;
        }
        else if (selectedIndex >= filtered.size() || filtered.get(selectedIndex).disabled()) {
            selectedIndex = firstEnabledIndex(filtered);
        }
    }

    public List<PickerItem> allItems() {
        return items;
    }

    public List<PickerItem> filteredItems() {
        if (filter.isBlank()) {
            return items;
        }
        String needle = filter.toLowerCase(Locale.ROOT);
        List<PickerItem> filtered = new ArrayList<>();
        for (PickerItem item : items) {
            String haystack = (item.label() + " " + item.detail() + " " + item.id()).toLowerCase(Locale.ROOT);
            if (haystack.contains(needle)) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    public int selectedIndex() {
        return selectedIndex;
    }

    public PickerItem selectedItem() {
        List<PickerItem> filtered = filteredItems();
        if (filtered.isEmpty() || selectedIndex < 0 || selectedIndex >= filtered.size()) {
            return null;
        }
        PickerItem item = filtered.get(selectedIndex);
        return item.disabled() ? null : item;
    }

    public void moveSelection(int delta) {
        List<PickerItem> filtered = filteredItems();
        if (filtered.isEmpty()) {
            selectedIndex = 0;
            return;
        }
        int index = selectedIndex;
        for (int attempt = 0; attempt < filtered.size(); attempt++) {
            index = Math.floorMod(index + delta, filtered.size());
            if (!filtered.get(index).disabled()) {
                selectedIndex = index;
                return;
            }
        }
        selectedIndex = 0;
    }

    private int firstEnabledIndex(List<PickerItem> filtered) {
        for (int index = 0; index < filtered.size(); index++) {
            if (!filtered.get(index).disabled()) {
                return index;
            }
        }
        return 0;
    }

    private static String blankToDefault(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? fallback : normalized;
    }
}
