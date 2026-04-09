package org.dean.codex.protocol.appserver;

import org.dean.codex.protocol.agent.AgentMessage;
import org.dean.codex.protocol.conversation.ThreadId;

public record AgentAssignTaskParams(ThreadId agentThreadId, AgentMessage message, boolean interrupt) {
}
