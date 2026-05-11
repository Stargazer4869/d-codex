package org.dean.codex.protocol.appserver;

import java.util.List;

public record ModelOption(String id,
                          String displayName,
                          String provider,
                          boolean current,
                          boolean defaultModel,
                          List<String> reasoningEfforts) {

    public ModelOption {
        id = normalizeRequired(id, "id");
        displayName = normalize(displayName);
        if (displayName == null) {
            displayName = id;
        }
        provider = normalize(provider);
        reasoningEfforts = reasoningEfforts == null ? List.of() : List.copyOf(reasoningEfforts);
    }

    private static String normalizeRequired(String value, String label) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return normalized;
    }

    private static String normalize(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
