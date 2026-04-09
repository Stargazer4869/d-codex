package org.dean.codex.runtime.springai.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CodexRuntimeDefaultsTest {

    @Test
    void chatPromptAndCompletionLoggingAreDisabledByDefaultWithEnvOverrides() throws Exception {
        String yaml = new String(new ClassPathResource("codex-runtime-defaults.yml").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);

        assertTrue(yaml.contains("log-prompt: ${CODEX_CHAT_LOG_PROMPT:false}"));
        assertTrue(yaml.contains("log-completion: ${CODEX_CHAT_LOG_COMPLETION:false}"));
        assertTrue(yaml.contains("project-doc-max-bytes: ${CODEX_PROJECT_DOC_MAX_BYTES:32768}"));
        assertTrue(yaml.contains("base-instructions-text: ${CODEX_BASE_INSTRUCTIONS_TEXT:}"));
        assertTrue(yaml.contains("base-instructions-file: ${CODEX_BASE_INSTRUCTIONS_FILE:}"));
        assertTrue(yaml.contains("project-instructions-text: ${CODEX_PROJECT_INSTRUCTIONS_TEXT:}"));
        assertTrue(yaml.contains("project-instructions-file: ${CODEX_PROJECT_INSTRUCTIONS_FILE:}"));
        assertTrue(yaml.contains("user-instructions-text: ${CODEX_USER_INSTRUCTIONS_TEXT:}"));
        assertTrue(yaml.contains("user-instructions-file: ${CODEX_USER_INSTRUCTIONS_FILE:}"));
    }
}
