package org.dean.codex.protocol.conversation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ThreadPromptStateSummary(Instant persistedAt,
                                       ThreadId inheritedFromThreadId,
                                       int userInstructionSectionCount) {
}
