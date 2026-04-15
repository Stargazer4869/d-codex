package org.dean.codex.protocol.conversation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ThreadModelSessionSummary(Instant persistedAt,
                                        ThreadId inheritedFromThreadId,
                                        ThreadId rootThreadId,
                                        ThreadId parentThreadId,
                                        String agentPath,
                                        Integer agentDepth,
                                        String responseId,
                                        String sessionId,
                                        TurnId lastTurnId) {
}
