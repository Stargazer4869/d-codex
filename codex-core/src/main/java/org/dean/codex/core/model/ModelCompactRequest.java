package org.dean.codex.core.model;

import java.util.List;

public record ModelCompactRequest(String systemInstructions,
                                  List<ModelInputItem> inputItems,
                                  ModelRequestMetadata metadata) {

    public ModelCompactRequest {
        systemInstructions = systemInstructions == null ? "" : systemInstructions;
        inputItems = inputItems == null ? List.of() : List.copyOf(inputItems);
        metadata = metadata == null ? new ModelRequestMetadata("", "", 0) : metadata;
    }
}
