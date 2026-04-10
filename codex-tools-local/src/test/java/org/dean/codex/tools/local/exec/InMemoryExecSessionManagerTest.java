package org.dean.codex.tools.local.exec;

import org.dean.codex.core.exec.ExecPollResult;
import org.dean.codex.core.exec.ExecSessionEvent;
import org.dean.codex.core.exec.ExecSessionStatus;
import org.dean.codex.core.exec.ExecStartRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryExecSessionManagerTest {

    @TempDir
    Path workspaceRoot;

    private final InMemoryExecSessionManager manager = new InMemoryExecSessionManager();

    @AfterEach
    void tearDown() {
        manager.destroy();
    }

    @Test
    void startAndPollExposeIncrementalCommandOutput() {
        ExecPollResult start = manager.start(new ExecStartRequest(
                null,
                "printf 'one\\n'; sleep 0.2; printf 'two\\n'",
                workspaceRoot,
                Duration.ofMillis(50),
                Duration.ofSeconds(2),
                false));

        ExecPollResult current = start;
        StringBuilder stdout = new StringBuilder(start.stdout());
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (current.session().running() && System.nanoTime() < deadline) {
            current = manager.poll(current.session().sessionId(), Duration.ofMillis(100));
            stdout.append(current.stdout());
        }

        assertTrue(stdout.toString().contains("one"));
        assertTrue(stdout.toString().contains("two"));
        assertEquals(ExecSessionStatus.COMPLETED, current.session().status());
    }

    @Test
    void writeStdinCanUnblockWaitingProcess() {
        ExecPollResult start = manager.start(new ExecStartRequest(
                null,
                "read value; printf '%s' \"$value\"",
                workspaceRoot,
                Duration.ofMillis(25),
                Duration.ofSeconds(2),
                false));

        ExecPollResult result = manager.writeStdin(start.session().sessionId(), "hello\n", Duration.ofMillis(100));
        StringBuilder stdout = new StringBuilder(result.stdout());
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (result.session().running() && System.nanoTime() < deadline) {
            result = manager.poll(result.session().sessionId(), Duration.ofMillis(100));
            stdout.append(result.stdout());
        }

        assertEquals(ExecSessionStatus.COMPLETED, result.session().status());
        assertEquals("hello", stdout.toString());
    }

    @Test
    void maxRuntimeMarksLongRunningSessionTimedOut() {
        ExecPollResult start = manager.start(new ExecStartRequest(
                null,
                "sleep 5",
                workspaceRoot,
                Duration.ofMillis(25),
                Duration.ofMillis(200),
                false));

        ExecPollResult current = start;
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (current.session().running() && System.nanoTime() < deadline) {
            current = manager.poll(current.session().sessionId(), Duration.ofMillis(100));
        }

        assertEquals(ExecSessionStatus.TIMED_OUT, current.session().status());
    }

    @Test
    void ptySessionsSupportResizeAndEmitTerminalInteractionEvents() throws Exception {
        List<ExecSessionEvent> events = new CopyOnWriteArrayList<>();
        try (AutoCloseable ignored = manager.subscribe(events::add)) {
            ExecPollResult start = manager.start(new ExecStartRequest(
                    null,
                    "stty size; stty -echo; read value; stty echo; stty size; printf 'got:%s\\n' \"$value\"",
                    workspaceRoot,
                    Duration.ofMillis(200),
                    Duration.ofSeconds(5),
                    true));

            assertTrue(start.session().pty());

            assertTrue(manager.resize(start.session().sessionId(), 120, 40));
            assertFalse(manager.resize(start.session().sessionId(), 0, 40));

            ExecPollResult current = manager.writeStdin(start.session().sessionId(), "hello\n", Duration.ofMillis(500));
            StringBuilder combinedStdout = new StringBuilder(start.stdout()).append(current.stdout());
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (current.session().running() && System.nanoTime() < deadline) {
                current = manager.poll(current.session().sessionId(), Duration.ofMillis(200));
                combinedStdout.append(current.stdout());
            }

            String normalized = normalize(combinedStdout.toString());
            assertEquals(ExecSessionStatus.COMPLETED, current.session().status());
            assertTrue(normalized.contains("24 80"), normalized);
            assertTrue(normalized.contains("40 120"), normalized);
            assertTrue(normalized.contains("got:hello"), normalized);
            assertTrue(events.stream().anyMatch(event ->
                    event.type().name().equals("TERMINAL_INTERACTION")
                            && event.terminalInteraction() != null
                            && "resize".equals(event.terminalInteraction().kind())
                            && Integer.valueOf(120).equals(event.terminalInteraction().columns())
                            && Integer.valueOf(40).equals(event.terminalInteraction().rows())));
            assertTrue(events.stream().anyMatch(event ->
                    event.type().name().equals("TERMINAL_INTERACTION")
                            && event.terminalInteraction() != null
                            && "stdin".equals(event.terminalInteraction().kind())
                            && Integer.valueOf(6).equals(event.terminalInteraction().inputLength())));
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n');
    }
}
