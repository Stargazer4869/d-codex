package org.dean.codex.protocol.context;

import org.dean.codex.protocol.conversation.TurnId;
import org.dean.codex.protocol.item.CollabDeliveryState;

import java.time.Instant;

public record ReconstructedReplayItem(TurnId turnId,
                                     String kind,
                                     String detail,
                                     CollabDeliveryState deliveryState,
                                     String mailboxSummary,
                                     String wakeupCause,
                                     Instant createdAt) {

    public ReconstructedReplayItem {
        turnId = turnId == null ? new TurnId("") : turnId;
        kind = kind == null || kind.isBlank() ? "replay" : kind.trim();
        detail = detail == null ? "" : detail;
        mailboxSummary = mailboxSummary == null || mailboxSummary.isBlank() ? null : mailboxSummary.trim();
        wakeupCause = wakeupCause == null || wakeupCause.isBlank() ? null : wakeupCause.trim();
    }
}
