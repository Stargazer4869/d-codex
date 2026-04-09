package org.dean.codex.protocol.appserver;

import org.dean.codex.protocol.conversation.ThreadId;

public record ThreadBackgroundTerminalsCleanResponse(ThreadId threadId, int cleanedCount) {
}
