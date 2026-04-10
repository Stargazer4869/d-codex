package org.dean.codex.core.exec;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public interface ExecSessionManager {

    ExecPollResult start(ExecStartRequest request);

    ExecPollResult poll(ExecSessionId sessionId, Duration yieldTime);

    ExecPollResult writeStdin(ExecSessionId sessionId, String input, Duration yieldTime);

    default boolean resize(ExecSessionId sessionId, int columns, int rows) {
        return false;
    }

    boolean terminate(ExecSessionId sessionId);

    Optional<ExecSessionSummary> session(ExecSessionId sessionId);

    List<ExecSessionSummary> sessions();

    AutoCloseable subscribe(Consumer<ExecSessionEvent> listener);
}
