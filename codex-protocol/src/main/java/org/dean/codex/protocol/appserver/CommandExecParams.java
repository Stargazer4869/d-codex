package org.dean.codex.protocol.appserver;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.dean.codex.protocol.conversation.ThreadId;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommandExecParams(ThreadId threadId,
                                String command,
                                String cwd,
                                Long yieldTimeMillis,
                                Long maxRuntimeMillis,
                                Boolean pty) {
}
