package org.dean.codex.core.model;

public record ModelCompactResponse(ModelResponseMetadata metadata,
                                   String outputText) {

    public ModelCompactResponse {
        metadata = metadata == null ? new ModelResponseMetadata("", "", "") : metadata;
        outputText = outputText == null ? "" : outputText;
    }
}
