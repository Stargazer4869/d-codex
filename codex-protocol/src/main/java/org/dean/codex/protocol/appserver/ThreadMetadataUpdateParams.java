package org.dean.codex.protocol.appserver;

import org.dean.codex.protocol.conversation.ThreadId;

public record ThreadMetadataUpdateParams(ThreadId threadId,
                                         String cwd,
                                         String modelProvider,
                                         String model) {
}
