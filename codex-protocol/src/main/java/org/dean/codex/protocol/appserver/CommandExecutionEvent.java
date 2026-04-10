package org.dean.codex.protocol.appserver;

import org.dean.codex.protocol.conversation.ThreadId;

import java.time.Instant;

public record CommandExecutionEvent(String sessionId,
                                    ThreadId threadId,
                                    String command,
                                    String workingDirectory,
                                    Long processId,
                                    String status,
                                    Instant startedAt,
                                    Instant completedAt,
                                    Integer exitCode) {
}
