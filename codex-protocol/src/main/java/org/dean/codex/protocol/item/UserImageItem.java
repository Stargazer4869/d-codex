package org.dean.codex.protocol.item;

import org.dean.codex.protocol.conversation.ItemId;

import java.time.Instant;

public record UserImageItem(ItemId itemId,
                            String imageUrl,
                            String detail,
                            Instant createdAt) implements TurnItem {

    public UserImageItem {
        imageUrl = imageUrl == null ? "" : imageUrl;
        detail = detail == null ? "" : detail;
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
