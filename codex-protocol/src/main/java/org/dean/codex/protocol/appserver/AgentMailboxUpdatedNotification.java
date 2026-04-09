package org.dean.codex.protocol.appserver;

import org.dean.codex.protocol.agent.AgentMailboxState;

public record AgentMailboxUpdatedNotification(AgentMailboxState mailbox) implements AppServerNotification {

    @Override
    public String method() {
        return "agent/mailbox/updated";
    }
}
