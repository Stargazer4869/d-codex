package org.dean.codex.runtime.springai.config;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodexRuntimeApplicationConfigTest {

    @Test
    void runtimeApplicationImportsSharedDefaultsAndDefinesShellProperties() throws Exception {
        try (var inputStream = CodexRuntimeApplicationConfigTest.class.getResourceAsStream("/application.yml")) {
            assertNotNull(inputStream);
            String config = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(config.contains("import: optional:classpath:codex-runtime-defaults.yml"));
            assertTrue(config.contains("web-application-type: none"));
            assertTrue(config.contains("approval-mode: ${CODEX_SHELL_APPROVAL_MODE:review-sensitive}"));
            assertTrue(config.contains("timeout-seconds: ${CODEX_SHELL_TIMEOUT_SECONDS:60}"));
        }
    }
}
