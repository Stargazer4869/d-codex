package org.dean.codex.protocol.appserver;

import org.dean.codex.protocol.agent.AgentMessage;
import org.dean.codex.protocol.conversation.ThreadId;

public record AgentSendMessageParams(ThreadId agentThreadId, AgentMessage message) {
}
