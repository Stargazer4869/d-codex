package org.dean.codex.protocol.appserver;

public record CommandExecutionOutputDeltaNotification(CommandExecutionEvent commandExecution,
                                                      String stdout,
                                                      String stderr) implements AppServerNotification {

    @Override
    public String method() {
        return "item/commandExecution/outputDelta";
    }
}
