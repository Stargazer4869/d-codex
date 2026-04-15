package org.dean.codex.protocol.item;

import org.dean.codex.protocol.conversation.ItemId;

import java.time.Instant;

public record ReasoningItem(ItemId itemId,
                            String summary,
                            String detail,
                            Instant createdAt) implements TurnItem {
}
