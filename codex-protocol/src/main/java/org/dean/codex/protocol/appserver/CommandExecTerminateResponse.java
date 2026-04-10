package org.dean.codex.protocol.appserver;

public record CommandExecTerminateResponse(CommandExecutionEvent commandExecution,
                                           boolean terminated) {
}
