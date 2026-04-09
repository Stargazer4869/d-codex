package org.dean.codex.core.agent;

import org.dean.codex.protocol.agent.AgentMessage;
import org.dean.codex.protocol.agent.AgentMailboxState;
import org.dean.codex.protocol.agent.AgentSpawnRequest;
import org.dean.codex.protocol.agent.AgentSummary;
import org.dean.codex.protocol.agent.AgentWaitResult;
import org.dean.codex.protocol.conversation.ThreadId;

import java.util.List;

public interface AgentControl {

    AgentSummary spawnAgent(AgentSpawnRequest request);

    AgentSummary sendInput(ThreadId agentThreadId, AgentMessage message, boolean interrupt);

    default AgentSummary sendMessage(ThreadId agentThreadId, AgentMessage message) {
        return sendInput(agentThreadId, message, false);
    }

    default AgentSummary assignTask(ThreadId agentThreadId, AgentMessage message, boolean interrupt) {
        return sendInput(agentThreadId, message, interrupt);
    }

    AgentWaitResult waitAgent(List<ThreadId> agentThreadIds, long timeoutMillis);

    default AgentMailboxState mailboxState(ThreadId agentThreadId) {
        return null;
    }

    AgentSummary resumeAgent(ThreadId agentThreadId);

    AgentSummary closeAgent(ThreadId agentThreadId);

    List<AgentSummary> listAgents(ThreadId parentThreadId, boolean recursive);
}
