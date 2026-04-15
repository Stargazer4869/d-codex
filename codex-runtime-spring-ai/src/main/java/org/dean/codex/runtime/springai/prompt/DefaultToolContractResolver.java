package org.dean.codex.runtime.springai.prompt;

import java.util.List;

public class DefaultToolContractResolver implements ToolContractResolver {

    private final ToolCapabilityRegistry toolCapabilityRegistry;

    public DefaultToolContractResolver() {
        this(new DefaultToolCapabilityRegistry());
    }

    public DefaultToolContractResolver(ToolCapabilityRegistry toolCapabilityRegistry) {
        this.toolCapabilityRegistry = toolCapabilityRegistry == null
                ? new DefaultToolCapabilityRegistry()
                : toolCapabilityRegistry;
    }

    @Override
    public ResolvedToolContract resolvePlannerToolContract() {
        List<ToolCapability> capabilities = toolCapabilityRegistry.plannerToolCapabilities();
        List<ResolvedToolDefinition> visibleTools = capabilities.stream()
                .map(ToolCapability::definition)
                .toList();
        boolean supportsParallelToolCalls = capabilities.stream()
                .anyMatch(ToolCapability::supportsParallelExecution);
        return new ResolvedToolContract(visibleTools, supportsParallelToolCalls);
    }
}
