package org.dean.codex.protocol.item;

import org.dean.codex.protocol.conversation.ItemId;

import java.time.Instant;

public record RawModelOutputItem(ItemId itemId,
                                 String modelItemType,
                                 String modelItemId,
                                 String streamId,
                                 int streamSequence,
                                 String threadId,
                                 String turnId,
                                 int step,
                                 String responseId,
                                 String providerSessionId,
                                 String finishReason,
                                 String payloadJson,
                                 Instant createdAt) implements TurnItem {

    public RawModelOutputItem {
        modelItemType = normalize(modelItemType);
        modelItemId = normalize(modelItemId);
        streamId = normalize(streamId);
        streamSequence = Math.max(0, streamSequence);
        threadId = normalize(threadId);
        turnId = normalize(turnId);
        step = Math.max(0, step);
        responseId = normalize(responseId);
        providerSessionId = normalize(providerSessionId);
        finishReason = normalize(finishReason);
        payloadJson = payloadJson == null ? "" : payloadJson;
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public RawModelOutputItem(ItemId itemId,
                              String modelItemType,
                              String modelItemId,
                              String payloadJson,
                              Instant createdAt) {
        this(itemId, modelItemType, modelItemId, "", 0, "", "", 0, "", "", "", payloadJson, createdAt);
    }

    private static String normalize(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? "" : normalized;
    }
}
