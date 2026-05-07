package org.dean.codex.core.agent;

import org.dean.codex.protocol.conversation.ThreadId;
import org.dean.codex.protocol.item.MailboxDeliveryKind;

import java.time.Instant;

public record MailboxTurnMessage(ThreadId senderThreadId,
                                 ThreadId receiverThreadId,
                                 MailboxDeliveryKind deliveryKind,
                                 String text,
                                 Instant createdAt) {

    public MailboxTurnMessage {
        deliveryKind = deliveryKind == null ? MailboxDeliveryKind.QUEUE_ONLY : deliveryKind;
        text = text == null ? "" : text.trim();
    }
}
