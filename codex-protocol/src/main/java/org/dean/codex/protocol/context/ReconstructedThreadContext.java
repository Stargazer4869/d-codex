package org.dean.codex.protocol.context;

import org.dean.codex.protocol.conversation.ConversationMessage;
import org.dean.codex.protocol.conversation.ConversationTurn;
import org.dean.codex.protocol.conversation.ThreadId;

import java.time.Instant;
import java.util.List;

public record ReconstructedThreadContext(ThreadId threadId,
                                         ThreadMemory threadMemory,
                                         List<ConversationMessage> recentMessages,
                                         List<ConversationTurn> recentTurns,
                                         List<ReconstructedTurnActivity> recentActivities,
                                         List<ReconstructedReplayItem> replaySummary,
                                         Instant reconstructedAt) {

    public ReconstructedThreadContext {
        recentMessages = recentMessages == null ? List.of() : List.copyOf(recentMessages);
        recentTurns = recentTurns == null ? List.of() : List.copyOf(recentTurns);
        recentActivities = recentActivities == null ? List.of() : List.copyOf(recentActivities);
        replaySummary = replaySummary == null ? List.of() : List.copyOf(replaySummary);
    }

    public ReconstructedThreadContext(ThreadId threadId,
                                      ThreadMemory threadMemory,
                                      List<ConversationMessage> recentMessages,
                                      List<ConversationTurn> recentTurns,
                                      List<ReconstructedTurnActivity> recentActivities,
                                      Instant reconstructedAt) {
        this(threadId, threadMemory, recentMessages, recentTurns, recentActivities, List.of(), reconstructedAt);
    }
}
