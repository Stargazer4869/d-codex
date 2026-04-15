package org.dean.codex.core.model;

public record ModelRequestMetadata(String threadId,
                                   String turnId,
                                   int step,
                                   String rootThreadId,
                                   String parentThreadId,
                                   String agentPath,
                                   Integer agentDepth,
                                   String inheritedFromThreadId,
                                   String previousResponseId,
                                   String providerSessionId) {

    public ModelRequestMetadata {
        threadId = normalize(threadId);
        turnId = normalize(turnId);
        rootThreadId = normalize(rootThreadId);
        parentThreadId = normalize(parentThreadId);
        agentPath = normalize(agentPath);
        inheritedFromThreadId = normalize(inheritedFromThreadId);
        previousResponseId = normalize(previousResponseId);
        providerSessionId = normalize(providerSessionId);
        step = Math.max(0, step);
    }

    public ModelRequestMetadata(String threadId, String turnId, int step) {
        this(threadId, turnId, step, null, null, null, null, null, null, null);
    }

    private static String normalize(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? "" : normalized;
    }
}
