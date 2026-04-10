package org.dean.codex.protocol.appserver;

public record CommandExecResponse(CommandExecutionEvent commandExecution,
                                  String stdout,
                                  String stderr,
                                  String error) {

    public CommandExecResponse {
        stdout = stdout == null ? "" : stdout;
        stderr = stderr == null ? "" : stderr;
        error = error == null ? "" : error;
    }
}
