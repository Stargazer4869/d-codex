package org.dean.codex.protocol.appserver;

import org.dean.codex.protocol.conversation.ThreadId;

public record CommandExecTerminateParams(ThreadId threadId,
                                         String sessionId) {
}
