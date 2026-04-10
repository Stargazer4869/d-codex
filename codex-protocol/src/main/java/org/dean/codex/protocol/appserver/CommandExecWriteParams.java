package org.dean.codex.protocol.appserver;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.dean.codex.protocol.conversation.ThreadId;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommandExecWriteParams(ThreadId threadId,
                                     String sessionId,
                                     String input,
                                     Long yieldTimeMillis) {
}
