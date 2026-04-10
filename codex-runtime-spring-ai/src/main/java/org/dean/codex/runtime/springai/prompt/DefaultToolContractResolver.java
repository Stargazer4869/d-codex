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
        List<ResolvedToolDefinition> visibleTools = toolCapabilityRegistry.plannerToolCapabilities().stream()
                .map(ToolCapability::definition)
                .toList();
        return new ResolvedToolContract(visibleTools, false);
    }
}
