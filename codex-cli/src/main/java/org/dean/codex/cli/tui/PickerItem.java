package org.dean.codex.cli.tui;

public record PickerItem(String id,
                         String label,
                         String detail,
                         boolean disabled) {

    public PickerItem {
        id = normalize(id, label);
        label = normalize(label, id);
        detail = detail == null ? "" : detail.trim();
    }

    public PickerItem(String id, String label, String detail) {
        this(id, label, detail, false);
    }

    private static String normalize(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.isEmpty()) {
            return normalized;
        }
        String normalizedFallback = fallback == null ? "" : fallback.trim();
        return normalizedFallback.isEmpty() ? "(unknown)" : normalizedFallback;
    }
}
