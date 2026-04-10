package org.dean.codex.runtime.springai.prompt;

import java.util.List;

public interface ToolCapabilityRegistry {

    List<ToolCapability> plannerToolCapabilities();

    default boolean supportsParallelExecution(String actionName) {
        if (actionName == null || actionName.isBlank()) {
            return false;
        }
        return plannerToolCapabilities().stream()
                .filter(capability -> capability.definition() != null)
                .filter(capability -> actionName.equalsIgnoreCase(capability.definition().name()))
                .findFirst()
                .map(ToolCapability::supportsParallelExecution)
                .orElse(false);
    }
}
