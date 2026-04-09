package org.dean.codex.protocol.appserver;

import org.dean.codex.protocol.conversation.ThreadId;

import java.util.List;

public record AgentWaitParams(List<ThreadId> agentThreadIds, long timeoutMillis) {
}
