package org.dean.codex.core.model;

public record ModelReasoningItem(String id, String summary, String content) implements ModelOutputItem {

    public ModelReasoningItem {
        id = id == null ? "" : id;
        summary = summary == null ? "" : summary;
        content = content == null ? "" : content;
    }

    @Override
    public String type() {
        return "reasoning";
    }
}
