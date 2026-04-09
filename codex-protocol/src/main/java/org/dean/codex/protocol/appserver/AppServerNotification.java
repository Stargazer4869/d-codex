package org.dean.codex.protocol.appserver;

public sealed interface AppServerNotification permits ThreadStartedNotification,
        ThreadClosedNotification,
        ThreadStatusChangedNotification,
        ThreadNameUpdatedNotification,
        ThreadMetadataUpdatedNotification,
        ThreadCompactionStartedNotification,
        ThreadCompactedNotification,
        AgentMailboxUpdatedNotification,
        TurnStartedNotification,
        TurnCompletedNotification,
        TurnItemNotification {

    String method();
}
