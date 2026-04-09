package org.dean.codex.protocol.appserver;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.dean.codex.protocol.conversation.ConversationTurn;
import org.dean.codex.protocol.context.ReconstructedReplayItem;
import org.dean.codex.protocol.conversation.ThreadSummary;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ThreadRollbackResponse(ThreadSummary thread,
                                     List<ConversationTurn> turns,
                                     List<ReconstructedReplayItem> replaySummary) {

    public ThreadRollbackResponse(ThreadSummary thread, List<ConversationTurn> turns) {
        this(thread, turns, List.of());
    }

    public ThreadRollbackResponse {
        turns = turns == null ? List.of() : List.copyOf(turns);
        replaySummary = replaySummary == null ? List.of() : List.copyOf(replaySummary);
    }
}
