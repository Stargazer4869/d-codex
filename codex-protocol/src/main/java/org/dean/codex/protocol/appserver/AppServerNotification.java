package org.dean.codex.protocol.appserver;

public sealed interface AppServerNotification permits ThreadStartedNotification,
        ThreadClosedNotification,
        ThreadStatusChangedNotification,
        ThreadNameUpdatedNotification,
        ThreadMetadataUpdatedNotification,
        ThreadCompactionStartedNotification,
        ThreadCompactedNotification,
        AgentMailboxUpdatedNotification,
        CommandExecutionOutputDeltaNotification,
        CommandExecutionTerminalInteractionNotification,
        CommandExecutionCompletedNotification,
        TurnStartedNotification,
        TurnCompletedNotification,
        TurnItemNotification {

    String method();
}
