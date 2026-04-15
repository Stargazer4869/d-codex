package org.dean.codex.core.model;

public record ModelAssistantMessageItem(String id, String text) implements ModelOutputItem {

    public ModelAssistantMessageItem {
        id = id == null ? "" : id;
        text = text == null ? "" : text;
    }

    @Override
    public String type() {
        return "assistant_message";
    }
}
