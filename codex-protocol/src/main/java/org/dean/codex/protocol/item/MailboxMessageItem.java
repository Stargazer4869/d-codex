package org.dean.codex.protocol.item;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.dean.codex.protocol.conversation.ItemId;
import org.dean.codex.protocol.conversation.ThreadId;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record MailboxMessageItem(ItemId itemId,
                                 ThreadId senderThreadId,
                                 ThreadId receiverThreadId,
                                 MailboxDeliveryKind deliveryKind,
                                 String text,
                                 Instant createdAt) implements TurnItem {

    public MailboxMessageItem {
        deliveryKind = deliveryKind == null ? MailboxDeliveryKind.QUEUE_ONLY : deliveryKind;
        text = text == null ? "" : text.trim();
    }
}
