package org.dean.codex.protocol.appserver;

import org.dean.codex.protocol.conversation.ThreadSummary;

public record ThreadNameUpdatedNotification(ThreadSummary thread) implements AppServerNotification {

    @Override
    public String method() {
        return "thread/name/updated";
    }
}
