package org.dean.codex.protocol.appserver;

public record TurnTextInputItem(String text) implements TurnInputItem {

    public TurnTextInputItem {
        text = text == null ? "" : text;
    }
}
