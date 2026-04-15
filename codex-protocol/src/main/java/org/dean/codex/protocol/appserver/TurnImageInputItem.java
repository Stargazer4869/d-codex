package org.dean.codex.protocol.appserver;

public record TurnImageInputItem(String imageUrl, String detail) implements TurnInputItem {

    public TurnImageInputItem {
        imageUrl = imageUrl == null ? "" : imageUrl;
        detail = detail == null ? "" : detail;
    }
}
