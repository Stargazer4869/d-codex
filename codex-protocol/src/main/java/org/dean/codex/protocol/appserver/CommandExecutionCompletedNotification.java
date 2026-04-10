package org.dean.codex.protocol.appserver;

public record CommandExecutionCompletedNotification(CommandExecutionEvent commandExecution) implements AppServerNotification {

    @Override
    public String method() {
        return "item/commandExecution/completed";
    }
}
