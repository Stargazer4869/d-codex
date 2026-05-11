package org.dean.codex.cli.tui;

public record TranscriptCell(String role,
                             String title,
                             String body) {

    public TranscriptCell {
        role = normalize(role, "info");
        title = normalize(title, role);
        body = body == null ? "" : body.strip();
    }

    private static String normalize(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? fallback : normalized;
    }
}
