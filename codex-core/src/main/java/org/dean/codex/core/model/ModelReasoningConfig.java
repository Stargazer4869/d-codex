package org.dean.codex.core.model;

public record ModelReasoningConfig(String effort, String summaryMode) {

    public ModelReasoningConfig {
        effort = normalize(effort);
        summaryMode = normalize(summaryMode);
    }

    private static String normalize(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? "" : normalized;
    }
}
