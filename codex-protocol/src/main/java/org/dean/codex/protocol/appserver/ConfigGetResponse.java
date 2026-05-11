package org.dean.codex.protocol.appserver;

import org.dean.codex.protocol.conversation.ThreadId;

import java.util.List;

public record ConfigGetResponse(ThreadId threadId,
                                String modelProvider,
                                String model,
                                String sandboxMode,
                                String approvalMode,
                                String cwd,
                                List<String> featureFlags) {

    public ConfigGetResponse {
        modelProvider = normalize(modelProvider);
        model = normalize(model);
        sandboxMode = normalize(sandboxMode);
        approvalMode = normalize(approvalMode);
        cwd = normalize(cwd);
        featureFlags = featureFlags == null ? List.of() : List.copyOf(featureFlags);
    }

    private static String normalize(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
