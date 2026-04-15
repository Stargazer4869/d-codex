package org.dean.codex.protocol.history;

import org.dean.codex.protocol.conversation.TurnId;

import java.time.Instant;

public record HistoryImageItem(TurnId turnId,
                               String imageUrl,
                               String detail,
                               Instant createdAt) implements ThreadHistoryItem {

    public HistoryImageItem {
        turnId = turnId == null ? new TurnId("") : turnId;
        imageUrl = imageUrl == null ? "" : imageUrl;
        detail = detail == null ? "" : detail;
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
