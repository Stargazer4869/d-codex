package org.dean.codex.protocol.appserver;

import org.dean.codex.protocol.conversation.ThreadId;

import java.util.List;
import java.util.stream.Collectors;

public record TurnStartParams(ThreadId threadId,
                              String input,
                              List<TurnInputItem> inputItems) {

    public TurnStartParams {
        input = input == null ? "" : input;
        inputItems = inputItems == null ? List.of() : List.copyOf(inputItems);
    }

    public TurnStartParams(ThreadId threadId, String input) {
        this(threadId, input, List.of());
    }

    public List<TurnInputItem> effectiveInputItems() {
        if (!inputItems.isEmpty()) {
            return inputItems;
        }
        if (input.isBlank()) {
            return List.of();
        }
        return List.of(new TurnTextInputItem(input));
    }

    public String inputSummary() {
        if (!input.isBlank()) {
            return input;
        }
        if (effectiveInputItems().isEmpty()) {
            return "";
        }
        return effectiveInputItems().stream()
                .map(this::summarizeInputItem)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private String summarizeInputItem(TurnInputItem item) {
        if (item instanceof TurnTextInputItem textInputItem) {
            return textInputItem.text();
        }
        if (item instanceof TurnImageInputItem imageInputItem) {
            String url = imageInputItem.imageUrl().isBlank() ? "(image)" : imageInputItem.imageUrl();
            if (imageInputItem.detail().isBlank()) {
                return "[Image] " + url;
            }
            return "[Image] " + url + " (detail=" + imageInputItem.detail() + ")";
        }
        return "";
    }
}
