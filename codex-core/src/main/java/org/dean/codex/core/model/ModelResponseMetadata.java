package org.dean.codex.core.model;

public record ModelResponseMetadata(String responseId,
                                    String sessionId,
                                    String finishReason) {

    public ModelResponseMetadata {
        responseId = responseId == null ? "" : responseId;
        sessionId = sessionId == null ? "" : sessionId;
        finishReason = finishReason == null ? "" : finishReason;
    }
}
