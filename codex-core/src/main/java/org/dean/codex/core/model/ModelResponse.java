package org.dean.codex.core.model;

import java.util.List;
import java.util.Optional;

public record ModelResponse(ModelResponseMetadata metadata,
                            List<ModelOutputItem> outputItems) {

    public ModelResponse {
        metadata = metadata == null ? new ModelResponseMetadata("", "", "") : metadata;
        outputItems = outputItems == null ? List.of() : List.copyOf(outputItems);
    }

    public Optional<ModelAssistantMessageItem> firstAssistantMessage() {
        return outputItems.stream()
                .filter(ModelAssistantMessageItem.class::isInstance)
                .map(ModelAssistantMessageItem.class::cast)
                .findFirst();
    }

    public String assistantText() {
        return outputItems.stream()
                .filter(ModelAssistantMessageItem.class::isInstance)
                .map(ModelAssistantMessageItem.class::cast)
                .map(ModelAssistantMessageItem::text)
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("");
    }
}
