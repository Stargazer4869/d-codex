package org.dean.codex.cli.appserver.transport.jsonrpc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StdioProcessCodexAppServerTest {

    @Test
    void suppressesKnownLowSignalAppServerWarningsFromInteractiveConsole() {
        assertFalse(StdioProcessCodexAppServer.shouldEchoAppServerStderr(
                "17:10:24.797 [codex-runtime-1] ERROR io.netty.resolver.dns.DnsServerAddressStreamProviders -- Unable to load io.netty.resolver.dns.macos.MacOSDnsServerAddressStreamProvider, fallback to system defaults."));
        assertFalse(StdioProcessCodexAppServer.shouldEchoAppServerStderr(
                "17:10:29.359 [codex-runtime-1] WARN org.springframework.ai.openai.metadata.support.OpenAiResponseHeaderExtractor -- Value [x-ratelimit-limit-tokens] for HTTP header [250000.0] is not valid: For input string: \"250000.0\""));
        assertFalse(StdioProcessCodexAppServer.shouldEchoAppServerStderr(
                "17:10:29.446 [codex-runtime-1] INFO org.dean.codex.tools.local.ShellCommandToolImpl -- Copilot tool runCommand used with command=curl -I https://example.com in /Users/chenzhu/Git/d-codex"));
        assertTrue(StdioProcessCodexAppServer.shouldEchoAppServerStderr(
                "17:10:29.999 [codex-runtime-1] ERROR org.dean.codex.runtime.springai.runtime.DefaultTurnExecutor -- Runtime failed"));
    }
}
