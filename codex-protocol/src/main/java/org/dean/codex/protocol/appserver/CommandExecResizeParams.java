package org.dean.codex.protocol.appserver;

import org.dean.codex.protocol.conversation.ThreadId;

public record CommandExecResizeParams(ThreadId threadId,
                                      String sessionId,
                                      int columns,
                                      int rows) {
}
