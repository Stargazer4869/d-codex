package org.dean.codex.runtime.springai.model;

import org.dean.codex.core.model.ModelRequestMetadata;
import org.dean.codex.core.model.ModelResponseMetadata;
import org.dean.codex.protocol.conversation.ThreadId;
import org.dean.codex.protocol.conversation.ThreadModelSessionSummary;
import org.dean.codex.protocol.conversation.ThreadSummary;
import org.dean.codex.protocol.conversation.TurnId;

import java.time.Instant;

public record ThreadModelSessionSnapshot(Instant persistedAt,
                                         ThreadId inheritedFromThreadId,
                                         ThreadId rootThreadId,
                                         ThreadId parentThreadId,
                                         String agentPath,
                                         Integer agentDepth,
                                         String responseId,
                                         String sessionId,
                                         TurnId lastTurnId) {

    public ThreadModelSessionSnapshot {
        persistedAt = persistedAt == null ? Instant.now() : persistedAt;
        agentPath = normalize(agentPath);
        responseId = normalize(responseId);
        sessionId = normalize(sessionId);
    }

    public static ThreadModelSessionSnapshot initial(ThreadSummary summary) {
        ThreadId threadId = summary == null ? null : summary.threadId();
        ThreadId rootThreadId = summary != null && summary.parentThreadId() != null ? summary.parentThreadId() : threadId;
        return new ThreadModelSessionSnapshot(
                Instant.now(),
                null,
                rootThreadId,
                summary == null ? null : summary.parentThreadId(),
                summary == null ? null : summary.agentPath(),
                summary == null ? null : summary.agentDepth(),
                "",
                "",
                null);
    }

    public ThreadModelSessionSnapshot inheritedFrom(ThreadId sourceThreadId, ThreadSummary targetSummary) {
        ThreadId nextRootThreadId = rootThreadId == null ? rootThreadId(targetSummary) : rootThreadId;
        return new ThreadModelSessionSnapshot(
                Instant.now(),
                sourceThreadId,
                nextRootThreadId,
                targetSummary == null ? null : targetSummary.parentThreadId(),
                targetSummary == null ? null : targetSummary.agentPath(),
                targetSummary == null ? null : targetSummary.agentDepth(),
                responseId,
                sessionId,
                lastTurnId);
    }

    public ThreadModelSessionSnapshot advance(TurnId currentTurnId, ModelResponseMetadata responseMetadata) {
        String nextResponseId = responseMetadata == null || responseMetadata.responseId().isBlank()
                ? responseId
                : responseMetadata.responseId();
        String nextSessionId = responseMetadata == null || responseMetadata.sessionId().isBlank()
                ? sessionId
                : responseMetadata.sessionId();
        return new ThreadModelSessionSnapshot(
                Instant.now(),
                inheritedFromThreadId,
                rootThreadId,
                parentThreadId,
                agentPath,
                agentDepth,
                nextResponseId,
                nextSessionId,
                currentTurnId);
    }

    public ModelRequestMetadata toRequestMetadata(ThreadId threadId, TurnId turnId, int step) {
        String rootValue = rootThreadId == null ? (threadId == null ? "" : threadId.value()) : rootThreadId.value();
        return new ModelRequestMetadata(
                threadId == null ? "" : threadId.value(),
                turnId == null ? "" : turnId.value(),
                step,
                rootValue,
                parentThreadId == null ? "" : parentThreadId.value(),
                agentPath,
                agentDepth,
                inheritedFromThreadId == null ? "" : inheritedFromThreadId.value(),
                responseId,
                sessionId);
    }

    public ThreadModelSessionSummary toSummary() {
        return new ThreadModelSessionSummary(
                persistedAt,
                inheritedFromThreadId,
                rootThreadId,
                parentThreadId,
                agentPath,
                agentDepth,
                responseId,
                sessionId,
                lastTurnId);
    }

    private static ThreadId rootThreadId(ThreadSummary summary) {
        if (summary == null) {
            return null;
        }
        if (summary.parentThreadId() != null) {
            return summary.parentThreadId();
        }
        return summary.threadId();
    }

    private static String normalize(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? "" : normalized;
    }
}
