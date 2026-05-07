package org.dean.codex.cli.appserver.transport.jsonrpc;

import org.dean.codex.cli.appserver.CodexCliAppServerProperties;
import org.dean.codex.cli.appserver.CodexCliDiagnosticsProperties;
import org.dean.codex.core.appserver.CodexAppServer;
import org.dean.codex.core.appserver.CodexAppServerSession;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class StdioProcessCodexAppServer implements CodexAppServer {

    private static final String NETTY_DNS_WARNING = "io.netty.resolver.dns.DnsServerAddressStreamProviders -- Unable to load io.netty.resolver.dns.macos.MacOSDnsServerAddressStreamProvider";
    private static final String OPENAI_HEADER_WARNING = "org.springframework.ai.openai.metadata.support.OpenAiResponseHeaderExtractor -- Value [x-ratelimit-";
    private static final String LOCAL_TOOL_INFO_LOG = " INFO org.dean.codex.tools.local.";

    private final CodexCliAppServerProperties properties;
    private final CodexCliDiagnosticsProperties diagnosticsProperties;

    public StdioProcessCodexAppServer(CodexCliAppServerProperties properties,
                                      CodexCliDiagnosticsProperties diagnosticsProperties) {
        this.properties = properties;
        this.diagnosticsProperties = diagnosticsProperties;
    }

    @Override
    public CodexAppServerSession connect() {
        CliDiagnosticsSession diagnostics = CliDiagnosticsSession.open(diagnosticsProperties);
        ProcessBuilder processBuilder = new ProcessBuilder(properties.resolvedCommand());
        diagnostics.recordLauncherCommand(processBuilder.command());
        diagnostics.recordLifecycle("app-server cwd=" + java.nio.file.Path.of("").toAbsolutePath().normalize());
        diagnostics.sessionDirectory()
                .ifPresent(directory -> System.out.println("[diagnostics] Troubleshooting logs: " + directory));
        try {
            Process process = processBuilder.start();
            diagnostics.recordLifecycle("app-server process started pid=" + process.pid());
            Thread stderrPump = startErrorPump(process, diagnostics);
            return new JsonRpcCodexAppServerSession(
                    process.getInputStream(),
                    process.getOutputStream(),
                    () -> closeProcess(process, stderrPump, diagnostics),
                    Duration.ofSeconds(Math.max(1, properties.getRequestTimeoutSeconds())),
                    diagnostics);
        }
        catch (IOException exception) {
            diagnostics.recordFailure("app-server-launch", exception);
            diagnostics.close();
            throw new IllegalStateException("Unable to launch app-server process.", exception);
        }
    }

    private Thread startErrorPump(Process process, CliDiagnosticsSession diagnostics) {
        Thread stderrPump = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    diagnostics.recordAppServerStderr(line);
                    if (shouldEchoAppServerStderr(line)) {
                        System.err.println(line);
                    }
                }
            }
            catch (IOException exception) {
                diagnostics.recordFailure("app-server-stderr", exception);
            }
        }, "codex-cli-appserver-stderr-" + process.pid());
        stderrPump.setDaemon(true);
        stderrPump.start();
        return stderrPump;
    }

    private void closeProcess(Process process, Thread stderrPump, CliDiagnosticsSession diagnostics) throws Exception {
        try {
            process.getOutputStream().close();
        }
        catch (Exception ignored) {
            // Ignore cleanup failures.
        }
        try {
            process.getInputStream().close();
        }
        catch (Exception ignored) {
            // Ignore cleanup failures.
        }
        try {
            process.getErrorStream().close();
        }
        catch (Exception ignored) {
            // Ignore cleanup failures.
        }

        if (!process.waitFor(1, TimeUnit.SECONDS)) {
            diagnostics.recordLifecycle("app-server process destroy requested pid=" + process.pid());
            process.destroy();
            if (!process.waitFor(1, TimeUnit.SECONDS)) {
                diagnostics.recordLifecycle("app-server process destroyForcibly pid=" + process.pid());
                process.destroyForcibly();
            }
        }
        if (stderrPump != null) {
            stderrPump.join(1_000);
        }
        try {
            diagnostics.recordLifecycle("app-server process exited exitCode=" + process.exitValue());
        }
        catch (IllegalThreadStateException ignored) {
            diagnostics.recordLifecycle("app-server process exit code unavailable");
        }
        diagnostics.close();
    }

    static boolean shouldEchoAppServerStderr(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        return !line.contains(NETTY_DNS_WARNING)
                && !line.contains(OPENAI_HEADER_WARNING)
                && !line.contains(LOCAL_TOOL_INFO_LOG);
    }
}
