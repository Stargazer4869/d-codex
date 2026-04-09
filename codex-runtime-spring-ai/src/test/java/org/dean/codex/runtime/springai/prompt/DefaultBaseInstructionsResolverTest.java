package org.dean.codex.runtime.springai.prompt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultBaseInstructionsResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void defaultTemplateIncludesWorkspaceRoot() {
        DefaultBaseInstructionsResolver resolver = new DefaultBaseInstructionsResolver(Path.of("/tmp/workspace"));

        String resolved = resolver.resolveBaseInstructions();

        assertTrue(resolved.contains("You are Codex"));
        assertTrue(resolved.contains("Workspace root: /tmp/workspace"));
    }

    @Test
    void textOverrideTakesPrecedenceOverFileOverride() throws Exception {
        Path overrideFile = tempDir.resolve("base.md");
        Files.writeString(overrideFile, "File override {{workspaceRoot}}");
        DefaultBaseInstructionsResolver resolver = new DefaultBaseInstructionsResolver(
                Path.of("/tmp/workspace"),
                "Text override {{workspaceRoot}}",
                overrideFile.toString());

        String resolved = resolver.resolveBaseInstructions();

        assertEquals("Text override /tmp/workspace", resolved);
    }

    @Test
    void fileOverrideLoadsCustomTemplate() throws Exception {
        Path overrideFile = tempDir.resolve("base.md");
        Files.writeString(overrideFile, "Follow repo policy in {{workspaceRoot}}");
        DefaultBaseInstructionsResolver resolver = new DefaultBaseInstructionsResolver(
                Path.of("/tmp/workspace"),
                null,
                overrideFile.toString());

        String resolved = resolver.resolveBaseInstructions();

        assertEquals("Follow repo policy in /tmp/workspace", resolved);
    }
}
