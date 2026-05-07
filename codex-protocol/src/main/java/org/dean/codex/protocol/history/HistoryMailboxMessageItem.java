package org.dean.codex.protocol.history;

import org.dean.codex.protocol.conversation.ThreadId;
import org.dean.codex.protocol.conversation.TurnId;
import org.dean.codex.protocol.item.MailboxDeliveryKind;

import java.time.Instant;

public record HistoryMailboxMessageItem(TurnId turnId,
                                        ThreadId senderThreadId,
                                        ThreadId receiverThreadId,
                                        MailboxDeliveryKind deliveryKind,
                                        String text,
                                        Instant createdAt) implements ThreadHistoryItem {

    public HistoryMailboxMessageItem(ThreadId senderThreadId,
                                     ThreadId receiverThreadId,
                                     MailboxDeliveryKind deliveryKind,
                                     String text,
                                     Instant createdAt) {
        this(new TurnId(""), senderThreadId, receiverThreadId, deliveryKind, text, createdAt);
    }

    public HistoryMailboxMessageItem {
        turnId = turnId == null ? new TurnId("") : turnId;
        deliveryKind = deliveryKind == null ? MailboxDeliveryKind.QUEUE_ONLY : deliveryKind;
        text = text == null ? "" : text.trim();
    }
}
