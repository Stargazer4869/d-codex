package org.dean.codex.protocol.appserver;

public record CommandExecResizeResponse(CommandExecutionEvent commandExecution,
                                        boolean applied) {
}
