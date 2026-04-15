package org.dean.codex.core.model;

public record ModelToolCallItem(String id, String toolName, String argumentsJson) implements ModelOutputItem {

    public ModelToolCallItem {
        id = id == null ? "" : id;
        toolName = toolName == null ? "" : toolName;
        argumentsJson = argumentsJson == null ? "" : argumentsJson;
    }

    @Override
    public String type() {
        return "tool_call";
    }
}
