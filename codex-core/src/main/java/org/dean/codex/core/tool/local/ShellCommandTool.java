package org.dean.codex.core.tool.local;

import org.dean.codex.protocol.conversation.ThreadId;
import org.dean.codex.protocol.tool.ShellCommandResult;

public interface ShellCommandTool {

    ShellCommandResult runCommand(String command);

    default ShellCommandResult runCommand(ThreadId threadId, String command) {
        return runCommand(command);
    }

    ShellCommandResult runApprovedCommand(String command);

    default ShellCommandResult runApprovedCommand(ThreadId threadId, String command) {
        return runApprovedCommand(command);
    }
}
