package org.dean.codex.core.model;

public record ModelToolResultItem(String id, String toolName, String outputText, boolean error) implements ModelOutputItem {

    public ModelToolResultItem {
        id = id == null ? "" : id;
        toolName = toolName == null ? "" : toolName;
        outputText = outputText == null ? "" : outputText;
    }

    @Override
    public String type() {
        return "tool_result";
    }
}
