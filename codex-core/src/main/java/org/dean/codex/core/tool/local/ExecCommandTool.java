package org.dean.codex.core.tool.local;

import org.dean.codex.protocol.conversation.ThreadId;
import org.dean.codex.protocol.tool.ExecCommandResult;

public interface ExecCommandTool {

    ExecCommandResult execCommand(ThreadId threadId,
                                  String command,
                                  Long yieldTimeMillis,
                                  Long maxRuntimeMillis,
                                  boolean pty);

    ExecCommandResult writeStdin(ThreadId threadId,
                                 String sessionId,
                                 String input,
                                 Long yieldTimeMillis);
}
