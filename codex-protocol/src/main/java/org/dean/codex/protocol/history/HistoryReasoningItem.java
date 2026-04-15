package org.dean.codex.protocol.history;

import org.dean.codex.protocol.conversation.TurnId;

import java.time.Instant;

public record HistoryReasoningItem(TurnId turnId,
                                   String summary,
                                   String detail,
                                   Instant createdAt) implements ThreadHistoryItem {

    public HistoryReasoningItem(String summary, String detail, Instant createdAt) {
        this(new TurnId(""), summary, detail, createdAt);
    }

    public HistoryReasoningItem {
        turnId = turnId == null ? new TurnId("") : turnId;
        summary = summary == null ? "" : summary;
        detail = detail == null ? "" : detail;
    }
}
