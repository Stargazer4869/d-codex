package org.dean.codex.protocol.appserver;

import org.dean.codex.protocol.tool.ShellCommandResult;

public record ThreadShellCommandResponse(ShellCommandResult result,
                                         BackgroundTerminalSummary backgroundTerminal) {

    public ThreadShellCommandResponse(ShellCommandResult result) {
        this(result, null);
    }
}
