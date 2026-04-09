package org.dean.codex.protocol.appserver;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.dean.codex.protocol.context.ReconstructedReplayItem;
import org.dean.codex.protocol.conversation.ThreadSummary;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ThreadResumeResponse(ThreadSummary thread,
                                   List<ReconstructedReplayItem> replaySummary,
                                   List<BackgroundTerminalSummary> backgroundTerminals) {

    public ThreadResumeResponse(ThreadSummary thread) {
        this(thread, List.of(), List.of());
    }

    public ThreadResumeResponse(ThreadSummary thread,
                                List<ReconstructedReplayItem> replaySummary) {
        this(thread, replaySummary, List.of());
    }

    public ThreadResumeResponse {
        replaySummary = replaySummary == null ? List.of() : List.copyOf(replaySummary);
        backgroundTerminals = backgroundTerminals == null ? List.of() : List.copyOf(backgroundTerminals);
    }
}
