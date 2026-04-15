package org.dean.codex.core.model;

public record InputTextItem(ModelInputRole role, String text) implements ModelInputItem {

    public InputTextItem {
        role = role == null ? ModelInputRole.USER : role;
        text = text == null ? "" : text;
    }

    @Override
    public String type() {
        return "input_text";
    }
}
