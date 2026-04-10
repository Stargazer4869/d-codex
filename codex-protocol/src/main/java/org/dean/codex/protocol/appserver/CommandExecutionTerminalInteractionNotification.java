package org.dean.codex.protocol.appserver;

public record CommandExecutionTerminalInteractionNotification(CommandExecutionEvent commandExecution,
                                                              String kind,
                                                              Integer inputLength,
                                                              Integer columns,
                                                              Integer rows) implements AppServerNotification {

    @Override
    public String method() {
        return "item/commandExecution/terminalInteraction";
    }
}
