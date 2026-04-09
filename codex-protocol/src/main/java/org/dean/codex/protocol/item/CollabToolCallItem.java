package org.dean.codex.protocol.item;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.dean.codex.protocol.agent.AgentStatus;
import org.dean.codex.protocol.agent.AgentMailboxState;
import org.dean.codex.protocol.conversation.ItemId;
import org.dean.codex.protocol.conversation.ThreadId;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record CollabToolCallItem(ItemId itemId,
                                 String tool,
                                 CollabToolCallStatus status,
                                 CollabDeliveryState deliveryState,
                                 ThreadId senderThreadId,
                                 List<ThreadId> receiverThreadIds,
                                 ThreadId newThreadId,
                                 String prompt,
                                 Map<String, AgentStatus> agentStatuses,
                                 Map<String, AgentMailboxState> mailboxes,
                                 String wakeupCause,
                                 Instant createdAt) implements TurnItem {

    public CollabToolCallItem {
        tool = tool == null ? "" : tool.trim();
        deliveryState = deliveryState == null ? null : deliveryState;
        receiverThreadIds = receiverThreadIds == null ? List.of() : List.copyOf(receiverThreadIds);
        prompt = prompt == null || prompt.isBlank() ? null : prompt.trim();
        agentStatuses = agentStatuses == null ? Map.of() : Map.copyOf(agentStatuses);
        mailboxes = mailboxes == null ? Map.of() : Map.copyOf(mailboxes);
        wakeupCause = wakeupCause == null || wakeupCause.isBlank() ? null : wakeupCause.trim();
        status = status == null ? CollabToolCallStatus.COMPLETED : status;
    }
}
