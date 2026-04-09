package org.dean.codex.protocol.appserver;

import org.dean.codex.protocol.conversation.ThreadSummary;

public record ThreadStatusChangedNotification(ThreadSummary thread) implements AppServerNotification {

    @Override
    public String method() {
        return "thread/status/changed";
    }
}
