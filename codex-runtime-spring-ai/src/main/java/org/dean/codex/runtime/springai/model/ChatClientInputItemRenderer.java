package org.dean.codex.runtime.springai.model;

import org.dean.codex.core.model.InputImageItem;
import org.dean.codex.core.model.InputTextItem;
import org.dean.codex.core.model.ModelInputItem;

import java.util.List;
import java.util.stream.Collectors;

final class ChatClientInputItemRenderer {

    private ChatClientInputItemRenderer() {
    }

    static String renderInputItems(List<ModelInputItem> inputItems) {
        if (inputItems == null || inputItems.isEmpty()) {
            return "";
        }
        return inputItems.stream()
                .map(ChatClientInputItemRenderer::renderInputItem)
                .collect(Collectors.joining(System.lineSeparator() + System.lineSeparator()));
    }

    private static String renderInputItem(ModelInputItem item) {
        if (item instanceof InputTextItem textItem) {
            return textItem.text();
        }
        if (item instanceof InputImageItem imageItem) {
            return """
                    [Input image]
                    role: %s
                    url: %s
                    detail: %s
                    """.formatted(
                    imageItem.role().name().toLowerCase(),
                    imageItem.imageUrl(),
                    imageItem.detail());
        }
        return "";
    }
}
