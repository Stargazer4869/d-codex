package org.dean.codex.protocol.appserver;

import org.dean.codex.protocol.conversation.ThreadId;

public record ConfigUpdateParams(ThreadId threadId,
                                 String modelProvider,
                                 String model,
                                 String sandboxMode,
                                 String approvalMode,
                                 String cwd) {
}
