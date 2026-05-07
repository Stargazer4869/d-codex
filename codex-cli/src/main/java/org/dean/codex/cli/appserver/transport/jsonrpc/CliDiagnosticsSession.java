package org.dean.codex.cli.appserver.transport.jsonrpc;

import org.dean.codex.cli.appserver.CodexCliDiagnosticsProperties;
import org.dean.codex.protocol.appserver.jsonrpc.JsonRpcError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;

final class CliDiagnosticsSession implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(CliDiagnosticsSession.class);
    private static final DateTimeFormatter SESSION_DIRECTORY_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter LINE_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());
    private static final int MAX_FIELD_LENGTH = 240;

    private final Path sessionDirectory;
    private final BufferedWriter sessionWriter;
    private final BufferedWriter stderrWriter;
    private final Object monitor = new Object();
    private final boolean enabled;
    private volatile boolean closed;

    private CliDiagnosticsSession() {
        this.sessionDirectory = null;
        this.sessionWriter = null;
        this.stderrWriter = null;
        this.enabled = false;
        this.closed = true;
    }

    private CliDiagnosticsSession(Path sessionDirectory,
                                  BufferedWriter sessionWriter,
                                  BufferedWriter stderrWriter) {
        this.sessionDirectory = sessionDirectory;
        this.sessionWriter = sessionWriter;
        this.stderrWriter = stderrWriter;
        this.enabled = true;
        this.closed = false;
    }

    static CliDiagnosticsSession disabled() {
        return new CliDiagnosticsSession();
    }

    static CliDiagnosticsSession open(CodexCliDiagnosticsProperties properties) {
        if (properties == null || !properties.isEnabled()) {
            return disabled();
        }

        Path baseDirectory = properties.resolvedDirectory();
        try {
            Files.createDirectories(baseDirectory);
            Path sessionDirectory = createSessionDirectory(baseDirectory);
            Files.createDirectories(sessionDirectory);
            BufferedWriter sessionWriter = Files.newBufferedWriter(
                    sessionDirectory.resolve("session.log"),
                    StandardCharsets.UTF_8,
                    CREATE_NEW,
                    WRITE);
            BufferedWriter stderrWriter = Files.newBufferedWriter(
                    sessionDirectory.resolve("app-server.stderr.log"),
                    StandardCharsets.UTF_8,
                    CREATE_NEW,
                    WRITE);
            CliDiagnosticsSession session = new CliDiagnosticsSession(sessionDirectory, sessionWriter, stderrWriter);
            session.recordLifecycle("diagnostics initialized pid=" + ProcessHandle.current().pid());
            return session;
        }
        catch (IOException exception) {
            logger.warn("Unable to initialize CLI diagnostics under {}", baseDirectory, exception);
            return disabled();
        }
    }

    boolean isEnabled() {
        return enabled;
    }

    Optional<Path> sessionDirectory() {
        return Optional.ofNullable(sessionDirectory);
    }

    void recordLifecycle(String message) {
        if (!enabled) {
            return;
        }
        writeSessionLine("lifecycle " + sanitize(message));
    }

    void recordLauncherCommand(List<String> command) {
        if (!enabled) {
            return;
        }
        String renderedCommand = command == null ? "(null)" : shellQuote(command);
        writeSessionLine("launcher command=" + renderedCommand);
    }

    void recordJsonRpcRequest(String method, String id, String paramsType, int pendingCount) {
        if (!enabled) {
            return;
        }
        writeSessionLine("jsonrpc request method=" + sanitize(method)
                + " id=" + sanitize(id)
                + " paramsType=" + sanitize(paramsType)
                + " pending=" + pendingCount);
    }

    void recordJsonRpcNotification(String direction, String method, String type) {
        if (!enabled) {
            return;
        }
        writeSessionLine("jsonrpc notification direction=" + sanitize(direction)
                + " method=" + sanitize(method)
                + " type=" + sanitize(type));
    }

    void recordJsonRpcResponse(String id, boolean hasError, boolean hasResult, int pendingCount) {
        if (!enabled) {
            return;
        }
        writeSessionLine("jsonrpc response id=" + sanitize(id)
                + " hasError=" + hasError
                + " hasResult=" + hasResult
                + " pending=" + pendingCount);
    }

    void recordJsonRpcError(String method, String id, JsonRpcError error) {
        if (!enabled || error == null) {
            return;
        }
        String dataHead = error.data() == null || error.data().isNull()
                ? "(null)"
                : sanitize(error.data().toString());
        writeSessionLine("jsonrpc error method=" + sanitize(method)
                + " id=" + sanitize(id)
                + " code=" + error.code()
                + " message=" + sanitize(error.message())
                + " data=" + dataHead);
    }

    void recordTransportLine(String source, String line) {
        if (!enabled) {
            return;
        }
        writeSessionLine("transport source=" + sanitize(source) + " head=" + sanitize(line));
    }

    void recordAppServerStderr(String line) {
        if (!enabled || closed) {
            return;
        }
        synchronized (monitor) {
            if (closed) {
                return;
            }
            try {
                stderrWriter.write(line == null ? "" : line);
                stderrWriter.newLine();
                stderrWriter.flush();
            }
            catch (IOException exception) {
                logger.warn("Unable to write app-server stderr diagnostics.", exception);
            }
        }
        writeSessionLine("app-server stderr head=" + sanitize(line));
    }

    void recordFailure(String source, Throwable throwable) {
        if (!enabled || throwable == null || closed) {
            return;
        }
        synchronized (monitor) {
            if (closed) {
                return;
            }
            try {
                sessionWriter.write(timestamp());
                sessionWriter.write(" failure source=");
                sessionWriter.write(sanitize(source));
                sessionWriter.write(" type=");
                sessionWriter.write(sanitize(throwable.getClass().getSimpleName()));
                sessionWriter.write(" message=");
                sessionWriter.write(sanitize(throwable.getMessage()));
                sessionWriter.newLine();

                StringWriter stackTrace = new StringWriter();
                throwable.printStackTrace(new PrintWriter(stackTrace));
                sessionWriter.write(stackTrace.toString());
                if (!stackTrace.toString().endsWith(System.lineSeparator())) {
                    sessionWriter.newLine();
                }
                sessionWriter.flush();
            }
            catch (IOException exception) {
                logger.warn("Unable to write diagnostics failure record.", exception);
            }
        }
    }

    @Override
    public void close() {
        if (!enabled) {
            return;
        }
        synchronized (monitor) {
            closed = true;
            closeQuietly(stderrWriter);
            closeQuietly(sessionWriter);
        }
    }

    private void writeSessionLine(String body) {
        if (closed) {
            return;
        }
        synchronized (monitor) {
            if (closed) {
                return;
            }
            try {
                sessionWriter.write(timestamp());
                sessionWriter.write(' ');
                sessionWriter.write(body);
                sessionWriter.newLine();
                sessionWriter.flush();
            }
            catch (IOException exception) {
                logger.warn("Unable to write diagnostics session line.", exception);
            }
        }
    }

    private String timestamp() {
        return LINE_TIMESTAMP_FORMATTER.format(Instant.now());
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "(blank)";
        }
        String compact = value.replace('\n', ' ')
                .replace('\r', ' ')
                .replace('\t', ' ')
                .trim();
        if (compact.length() <= MAX_FIELD_LENGTH) {
            return compact;
        }
        return compact.substring(0, MAX_FIELD_LENGTH) + "...";
    }

    private String shellQuote(List<String> command) {
        return command.stream()
                .map(this::shellQuoteToken)
                .reduce((left, right) -> left + " " + right)
                .orElse("(empty)");
    }

    private String shellQuoteToken(String token) {
        if (token == null || token.isBlank()) {
            return "''";
        }
        if (token.matches("[A-Za-z0-9_./:=+\\-]+")) {
            return token;
        }
        return "'" + token.replace("'", "'\"'\"'") + "'";
    }

    private static Path createSessionDirectory(Path baseDirectory) throws IOException {
        String seed = "session-" + SESSION_DIRECTORY_FORMATTER.format(Instant.now()) + "-" + ProcessHandle.current().pid();
        Path candidate = baseDirectory.resolve(seed);
        int suffix = 1;
        while (Files.exists(candidate)) {
            candidate = baseDirectory.resolve(seed + "-" + suffix);
            suffix++;
        }
        return candidate;
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        }
        catch (Exception ignored) {
            // Ignore cleanup failures during shutdown.
        }
    }
}
