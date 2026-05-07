package org.dean.codex.cli.appserver.transport.jsonrpc;

import org.dean.codex.cli.appserver.CodexCliDiagnosticsProperties;
import org.dean.codex.protocol.appserver.jsonrpc.JsonRpcError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CliDiagnosticsSessionTest {

    @TempDir
    Path tempDir;

    @Test
    void diagnosticsSessionWritesSessionAndStderrLogs() throws Exception {
        CodexCliDiagnosticsProperties properties = new CodexCliDiagnosticsProperties();
        properties.setEnabled(true);
        properties.setDirectory(tempDir.resolve("diagnostics").toString());

        Path sessionDirectory;
        try (CliDiagnosticsSession session = CliDiagnosticsSession.open(properties)) {
            sessionDirectory = session.sessionDirectory().orElseThrow();
            session.recordLifecycle("session test");
            session.recordLauncherCommand(List.of("java", "-jar", "codex-cli.jar"));
            session.recordJsonRpcRequest("thread/start", "1", "ThreadStartParams", 1);
            session.recordJsonRpcResponse("1", false, true, 0);
            session.recordJsonRpcError("thread/start", "1", new JsonRpcError(-32000, "boom", null));
            session.recordAppServerStderr("stderr line");
        }

        String sessionLog = Files.readString(sessionDirectory.resolve("session.log"));
        String stderrLog = Files.readString(sessionDirectory.resolve("app-server.stderr.log"));
        assertTrue(sessionLog.contains("launcher command=java -jar codex-cli.jar"));
        assertTrue(sessionLog.contains("jsonrpc request method=thread/start"));
        assertTrue(sessionLog.contains("jsonrpc error method=thread/start"));
        assertTrue(stderrLog.contains("stderr line"));
    }
}
