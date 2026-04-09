package org.dean.codex.protocol.appserver;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BackgroundTerminalSummary(String terminalId,
                                        long pid,
                                        String command,
                                        String workingDirectory,
                                        Instant startedAt) {
}
