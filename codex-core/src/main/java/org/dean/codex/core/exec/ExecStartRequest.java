package org.dean.codex.core.exec;

import org.dean.codex.protocol.conversation.ThreadId;

import java.nio.file.Path;
import java.time.Duration;

public record ExecStartRequest(ThreadId threadId,
                               String command,
                               Path workingDirectory,
                               Duration yieldTime,
                               Duration maxRuntime,
                               boolean pty) {

    public ExecStartRequest {
        yieldTime = yieldTime == null || yieldTime.isNegative() ? Duration.ZERO : yieldTime;
        maxRuntime = maxRuntime == null || maxRuntime.isNegative() ? Duration.ZERO : maxRuntime;
    }
}
