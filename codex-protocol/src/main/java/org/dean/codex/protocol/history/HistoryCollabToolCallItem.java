package org.dean.codex.protocol.history;

import org.dean.codex.protocol.agent.AgentStatus;
import org.dean.codex.protocol.agent.AgentMailboxState;
import org.dean.codex.protocol.conversation.ThreadId;
import org.dean.codex.protocol.conversation.TurnId;
import org.dean.codex.protocol.item.CollabToolCallStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record HistoryCollabToolCallItem(TurnId turnId,
                                        String tool,
                                        CollabToolCallStatus status,
                                        org.dean.codex.protocol.item.CollabDeliveryState deliveryState,
                                        ThreadId senderThreadId,
                                        List<ThreadId> receiverThreadIds,
                                        ThreadId newThreadId,
                                        String prompt,
                                        Map<String, AgentStatus> agentStatuses,
                                        Map<String, AgentMailboxState> mailboxes,
                                        String wakeupCause,
                                        Instant createdAt) implements ThreadHistoryItem {

    public HistoryCollabToolCallItem(String tool,
                                     CollabToolCallStatus status,
                                     org.dean.codex.protocol.item.CollabDeliveryState deliveryState,
                                     ThreadId senderThreadId,
                                     List<ThreadId> receiverThreadIds,
                                     ThreadId newThreadId,
                                     String prompt,
                                     Map<String, AgentStatus> agentStatuses,
                                     Map<String, AgentMailboxState> mailboxes,
                                     String wakeupCause,
                                     Instant createdAt) {
        this(new TurnId(""), tool, status, deliveryState, senderThreadId, receiverThreadIds, newThreadId, prompt, agentStatuses, mailboxes, wakeupCause, createdAt);
    }

    public HistoryCollabToolCallItem {
        turnId = turnId == null ? new TurnId("") : turnId;
        tool = tool == null ? "" : tool;
        deliveryState = deliveryState == null ? null : deliveryState;
        receiverThreadIds = receiverThreadIds == null ? List.of() : List.copyOf(receiverThreadIds);
        prompt = prompt == null ? null : prompt;
        agentStatuses = agentStatuses == null ? Map.of() : Map.copyOf(agentStatuses);
        mailboxes = mailboxes == null ? Map.of() : Map.copyOf(mailboxes);
        wakeupCause = wakeupCause == null || wakeupCause.isBlank() ? null : wakeupCause;
        status = status == null ? CollabToolCallStatus.COMPLETED : status;
    }
}
