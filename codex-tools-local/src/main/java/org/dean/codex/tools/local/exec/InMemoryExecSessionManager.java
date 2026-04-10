package org.dean.codex.tools.local.exec;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;
import org.dean.codex.core.exec.ExecSessionEvent;
import org.dean.codex.core.exec.ExecSessionEventType;
import org.dean.codex.core.exec.ExecPollResult;
import org.dean.codex.core.exec.ExecSessionId;
import org.dean.codex.core.exec.ExecSessionManager;
import org.dean.codex.core.exec.ExecSessionStatus;
import org.dean.codex.core.exec.ExecSessionSummary;
import org.dean.codex.core.exec.ExecStartRequest;
import org.dean.codex.core.exec.ExecTerminalInteraction;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Component
public class InMemoryExecSessionManager implements ExecSessionManager, DisposableBean {

    private static final int DEFAULT_PTY_COLUMNS = 80;
    private static final int DEFAULT_PTY_ROWS = 24;

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Map<ExecSessionId, ManagedSession> sessions = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<ExecSessionEvent>> listeners = new CopyOnWriteArrayList<>();

    @Override
    public ExecPollResult start(ExecStartRequest request) {
        String command = request == null ? null : request.command();
        java.nio.file.Path workingPath = request == null ? null : request.workingDirectory();
        ExecSessionId sessionId = new ExecSessionId(UUID.randomUUID().toString());
        String workingDirectory = workingPath == null
                ? ""
                : workingPath.toAbsolutePath().normalize().toString();
        Instant startedAt = Instant.now();
        if (command == null || command.isBlank()) {
            return failedStart(sessionId, command, workingDirectory, request, startedAt, "Command must not be blank.");
        }
        try {
            Process process = startProcess(request, command, workingPath);
            ManagedSession session = new ManagedSession(
                    sessionId,
                    request.threadId(),
                    command,
                    workingDirectory,
                    request != null && request.pty(),
                    process,
                    startedAt);
            sessions.put(sessionId, session);
            session.stdoutReader = executor.submit(() -> streamToBuffer(process.getInputStream(), session, true));
            session.stderrReader = executor.submit(() -> streamToBuffer(process.getErrorStream(), session, false));
            session.exitWatcher = executor.submit(() -> awaitExit(session));
            if (request != null && request.maxRuntime() != null && !request.maxRuntime().isZero()) {
                session.timeoutFuture = scheduler.schedule(
                        () -> timeout(session),
                        request.maxRuntime().toMillis(),
                        TimeUnit.MILLISECONDS);
            }
            return poll(sessionId, request == null ? Duration.ZERO : request.yieldTime());
        }
        catch (Exception exception) {
            return failedStart(sessionId, command, workingDirectory, request, startedAt, exception.getMessage());
        }
    }

    @Override
    public ExecPollResult poll(ExecSessionId sessionId, Duration yieldTime) {
        ManagedSession session = sessions.get(sessionId);
        if (session == null) {
            return new ExecPollResult(
                    new ExecSessionSummary(
                            sessionId,
                            null,
                            "",
                            "",
                            null,
                            false,
                            ExecSessionStatus.START_FAILED,
                            Instant.now(),
                            Instant.now(),
                            null),
                    "",
                    "",
                    "Exec session not found.");
        }
        Duration waitTime = normalize(yieldTime);
        session.awaitChange(waitTime);
        return session.drain();
    }

    @Override
    public ExecPollResult writeStdin(ExecSessionId sessionId, String input, Duration yieldTime) {
        ManagedSession session = sessions.get(sessionId);
        if (session == null) {
            return new ExecPollResult(
                    new ExecSessionSummary(
                            sessionId,
                            null,
                            "",
                            "",
                            null,
                            false,
                            ExecSessionStatus.START_FAILED,
                            Instant.now(),
                            Instant.now(),
                            null),
                    "",
                    "",
                    "Exec session not found.");
        }
        if (input != null && !input.isEmpty()) {
            try {
                synchronized (session.lock) {
                    OutputStreamWriter writer = new OutputStreamWriter(session.process.getOutputStream(), StandardCharsets.UTF_8);
                    writer.write(input);
                    writer.flush();
                }
                publish(new ExecSessionEvent(
                        ExecSessionEventType.TERMINAL_INTERACTION,
                        session.summary(),
                        new ExecTerminalInteraction("stdin", input.length(), null, null),
                        Instant.now()));
            }
            catch (Exception exception) {
                return new ExecPollResult(session.summary(), "", "", exception.getMessage());
            }
        }
        return poll(sessionId, yieldTime);
    }

    @Override
    public boolean resize(ExecSessionId sessionId, int columns, int rows) {
        ManagedSession session = sessions.get(sessionId);
        if (session == null || columns < 1 || rows < 1 || !(session.process instanceof PtyProcess ptyProcess)) {
            return false;
        }
        ptyProcess.setWinSize(new WinSize(columns, rows));
        publish(new ExecSessionEvent(
                ExecSessionEventType.TERMINAL_INTERACTION,
                session.summary(),
                new ExecTerminalInteraction("resize", null, columns, rows),
                Instant.now()));
        return true;
    }

    @Override
    public boolean terminate(ExecSessionId sessionId) {
        ManagedSession session = sessions.get(sessionId);
        if (session == null) {
            return false;
        }
        session.terminated = true;
        Process process = session.process;
        if (!process.isAlive()) {
            return true;
        }
        process.destroy();
        try {
            if (!process.waitFor(1, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        }
        catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
        synchronized (session.lock) {
            session.lock.notifyAll();
        }
        return true;
    }

    @Override
    public Optional<ExecSessionSummary> session(ExecSessionId sessionId) {
        ManagedSession session = sessions.get(sessionId);
        return session == null ? Optional.empty() : Optional.of(session.summary());
    }

    @Override
    public List<ExecSessionSummary> sessions() {
        return sessions.values().stream()
                .map(ManagedSession::summary)
                .toList();
    }

    @Override
    public AutoCloseable subscribe(Consumer<ExecSessionEvent> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    @Override
    public void destroy() {
        sessions.keySet().forEach(this::terminate);
        executor.shutdownNow();
        scheduler.shutdownNow();
    }

    private Process startProcess(ExecStartRequest request,
                                 String command,
                                 java.nio.file.Path workingPath) throws IOException {
        if (request != null && request.pty()) {
            Map<String, String> environment = new HashMap<>(System.getenv());
            environment.putIfAbsent("TERM", "xterm-256color");
            PtyProcessBuilder builder = new PtyProcessBuilder(new String[]{"zsh", "-lc", command})
                    .setEnvironment(environment)
                    .setInitialColumns(DEFAULT_PTY_COLUMNS)
                    .setInitialRows(DEFAULT_PTY_ROWS);
            if (workingPath != null) {
                builder.setDirectory(workingPath.toAbsolutePath().normalize().toString());
            }
            return builder.start();
        }
        return new ProcessBuilder("zsh", "-lc", command)
                .directory(workingPath == null ? null : workingPath.toFile())
                .start();
    }

    private void streamToBuffer(InputStream stream, ManagedSession session, boolean stdout) {
        try (InputStream inputStream = stream) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                String chunk = new String(buffer, 0, read, StandardCharsets.UTF_8);
                synchronized (session.lock) {
                    if (stdout) {
                        session.stdout.append(chunk);
                    }
                    else {
                        session.stderr.append(chunk);
                    }
                    session.lock.notifyAll();
                }
                publish(new ExecSessionEvent(
                        ExecSessionEventType.OUTPUT_DELTA,
                        session.summary(),
                        stdout ? chunk : "",
                        stdout ? "" : chunk,
                        "",
                        Instant.now()));
            }
        }
        catch (Exception exception) {
            if (!session.process.isAlive()) {
                return;
            }
            throw new UncheckedIOException(exception instanceof java.io.IOException ioException
                    ? ioException
                    : new java.io.IOException(exception));
        }
    }

    private void awaitExit(ManagedSession session) {
        try {
            int exitCode = session.process.waitFor();
            ExecSessionEvent completedEvent;
            synchronized (session.lock) {
                session.exitCode = exitCode;
                session.completedAt = Instant.now();
                if (session.timedOut) {
                    session.status = ExecSessionStatus.TIMED_OUT;
                }
                else if (session.terminated) {
                    session.status = ExecSessionStatus.TERMINATED;
                }
                else {
                    session.status = exitCode == 0 ? ExecSessionStatus.COMPLETED : ExecSessionStatus.FAILED;
                }
                if (session.timeoutFuture != null) {
                    session.timeoutFuture.cancel(false);
                }
                session.lock.notifyAll();
                completedEvent = new ExecSessionEvent(
                        ExecSessionEventType.COMPLETED,
                        session.summary(),
                        "",
                        "",
                        "",
                        session.completedAt);
            }
            publish(completedEvent);
        }
        catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            ExecSessionEvent completedEvent;
            synchronized (session.lock) {
                session.completedAt = Instant.now();
                session.status = ExecSessionStatus.TERMINATED;
                session.lock.notifyAll();
                completedEvent = new ExecSessionEvent(
                        ExecSessionEventType.COMPLETED,
                        session.summary(),
                        "",
                        "",
                        "",
                        session.completedAt);
            }
            publish(completedEvent);
        }
    }

    private void timeout(ManagedSession session) {
        if (!session.process.isAlive()) {
            return;
        }
        session.timedOut = true;
        session.process.destroy();
        try {
            if (!session.process.waitFor(1, TimeUnit.SECONDS)) {
                session.process.destroyForcibly();
            }
        }
        catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            session.process.destroyForcibly();
        }
        synchronized (session.lock) {
            session.lock.notifyAll();
        }
    }

    private ExecPollResult failedStart(ExecSessionId sessionId,
                                       String command,
                                       String workingDirectory,
                                       ExecStartRequest request,
                                       Instant startedAt,
                                       String error) {
        return new ExecPollResult(
                new ExecSessionSummary(
                        sessionId,
                        null,
                        command == null ? "" : command,
                        workingDirectory,
                        null,
                        request != null && request.pty(),
                        ExecSessionStatus.START_FAILED,
                        startedAt,
                        Instant.now(),
                        null),
                "",
                "",
                error == null ? "" : error);
    }

    private Duration normalize(Duration duration) {
        if (duration == null || duration.isNegative()) {
            return Duration.ZERO;
        }
        return duration;
    }

    private void publish(ExecSessionEvent event) {
        if (event == null) {
            return;
        }
        for (Consumer<ExecSessionEvent> listener : List.copyOf(listeners)) {
            try {
                listener.accept(event);
            }
            catch (Exception ignored) {
                // Listener failures must not break command execution.
            }
        }
    }

    private static final class ManagedSession {
        private final ExecSessionId sessionId;
        private final org.dean.codex.protocol.conversation.ThreadId threadId;
        private final String command;
        private final String workingDirectory;
        private final boolean pty;
        private final Process process;
        private final Instant startedAt;
        private final Object lock = new Object();
        private final StringBuilder stdout = new StringBuilder();
        private final StringBuilder stderr = new StringBuilder();

        private volatile ExecSessionStatus status = ExecSessionStatus.RUNNING;
        private volatile Instant completedAt;
        private volatile Integer exitCode;
        private volatile boolean timedOut;
        private volatile boolean terminated;
        private volatile int deliveredStdoutLength;
        private volatile int deliveredStderrLength;
        private volatile Future<?> stdoutReader;
        private volatile Future<?> stderrReader;
        private volatile Future<?> exitWatcher;
        private volatile ScheduledFuture<?> timeoutFuture;

        private ManagedSession(ExecSessionId sessionId,
                               org.dean.codex.protocol.conversation.ThreadId threadId,
                               String command,
                               String workingDirectory,
                               boolean pty,
                               Process process,
                               Instant startedAt) {
            this.sessionId = sessionId;
            this.threadId = threadId;
            this.command = command;
            this.workingDirectory = workingDirectory;
            this.pty = pty;
            this.process = process;
            this.startedAt = startedAt;
        }

        private void awaitChange(Duration duration) {
            long deadlineNanos = System.nanoTime() + duration.toNanos();
            synchronized (lock) {
                int initialStdoutLength = stdout.length();
                int initialStderrLength = stderr.length();
                while (status == ExecSessionStatus.RUNNING
                        && stdout.length() == initialStdoutLength
                        && stderr.length() == initialStderrLength) {
                    long remainingNanos = deadlineNanos - System.nanoTime();
                    if (remainingNanos <= 0) {
                        return;
                    }
                    try {
                        TimeUnit.NANOSECONDS.timedWait(lock, remainingNanos);
                    }
                    catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }

        private ExecPollResult drain() {
            synchronized (lock) {
                String stdoutChunk = stdout.substring(deliveredStdoutLength);
                String stderrChunk = stderr.substring(deliveredStderrLength);
                deliveredStdoutLength = stdout.length();
                deliveredStderrLength = stderr.length();
                return new ExecPollResult(summary(), stdoutChunk, stderrChunk, "");
            }
        }

        private ExecSessionSummary summary() {
            return new ExecSessionSummary(
                    sessionId,
                    threadId,
                    command,
                    workingDirectory,
                    process == null ? null : process.pid(),
                    pty,
                    status,
                    startedAt,
                    completedAt,
                    exitCode);
        }
    }
}
