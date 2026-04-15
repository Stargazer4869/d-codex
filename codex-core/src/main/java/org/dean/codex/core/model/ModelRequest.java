package org.dean.codex.core.model;

import java.util.List;

public record ModelRequest(String systemInstructions,
                           List<ModelInputItem> inputItems,
                           List<ModelToolSpec> toolSpecs,
                           boolean parallelToolCalls,
                           ModelReasoningConfig reasoningConfig,
                           ModelRequestMetadata metadata) {

    public ModelRequest {
        systemInstructions = systemInstructions == null ? "" : systemInstructions;
        inputItems = inputItems == null ? List.of() : List.copyOf(inputItems);
        toolSpecs = toolSpecs == null ? List.of() : List.copyOf(toolSpecs);
        reasoningConfig = reasoningConfig == null ? new ModelReasoningConfig("", "") : reasoningConfig;
        metadata = metadata == null ? new ModelRequestMetadata("", "", 0) : metadata;
    }
}
