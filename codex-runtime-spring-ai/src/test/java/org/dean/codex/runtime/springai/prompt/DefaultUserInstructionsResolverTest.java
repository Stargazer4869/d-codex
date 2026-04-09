package org.dean.codex.runtime.springai.prompt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultUserInstructionsResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void resolveUserInstructionsReturnsProjectAndUserSections() throws Exception {
        Path workspaceRoot = tempDir.resolve("workspace");
        Files.createDirectories(workspaceRoot);
        Files.writeString(workspaceRoot.resolve("PROJECT.md"), "Respect module boundaries in {{workspaceRoot}}.");
        DefaultUserInstructionsResolver resolver = new DefaultUserInstructionsResolver(
                workspaceRoot,
                32 * 1024,
                null,
                "PROJECT.md",
                "Answer tersely for {{workspaceRoot}}",
                null);

        List<String> sections = resolver.resolveUserInstructions();

        assertEquals(2, sections.size());
        assertTrue(sections.get(0).startsWith("Project instructions:\n"));
        assertTrue(sections.get(0).contains("Respect module boundaries in " + workspaceRoot));
        assertTrue(sections.get(1).startsWith("User instructions:\n"));
        assertTrue(sections.get(1).contains("Answer tersely for " + workspaceRoot));
    }

    @Test
    void emptyOverridesProduceNoUserSections() {
        DefaultUserInstructionsResolver resolver = new DefaultUserInstructionsResolver(Path.of("/tmp/workspace"));

        assertTrue(resolver.resolveUserInstructions().isEmpty());
    }

    @Test
    void discoversWorkspaceAgentsFileWhenNoExplicitProjectOverrideExists() throws Exception {
        Path workspaceRoot = tempDir.resolve("workspace");
        Files.createDirectories(workspaceRoot);
        Files.writeString(workspaceRoot.resolve("AGENTS.md"), "Be careful with migrations.");
        DefaultUserInstructionsResolver resolver = new DefaultUserInstructionsResolver(workspaceRoot, 32 * 1024, null, null, null, null);

        List<String> sections = resolver.resolveUserInstructions();

        assertEquals(1, sections.size());
        assertTrue(sections.get(0).startsWith("# AGENTS.md instructions for " + workspaceRoot));
        assertTrue(sections.get(0).contains("Be careful with migrations."));
        assertTrue(sections.get(0).contains("<INSTRUCTIONS>"));
    }

    @Test
    void prefersAgentsOverrideOverAgentsMd() throws Exception {
        Path workspaceRoot = tempDir.resolve("workspace");
        Files.createDirectories(workspaceRoot);
        Files.writeString(workspaceRoot.resolve("AGENTS.md"), "base instructions");
        Files.writeString(workspaceRoot.resolve("AGENTS.override.md"), "override instructions");
        DefaultUserInstructionsResolver resolver = new DefaultUserInstructionsResolver(workspaceRoot, 32 * 1024, null, null, null, null);

        String section = resolver.resolveUserInstructions().get(0);

        assertTrue(section.contains("override instructions"));
        assertFalse(section.contains("base instructions"));
    }

    @Test
    void concatenatesAgentsFilesFromProjectRootToWorkspace() throws Exception {
        Path repoRoot = tempDir.resolve("repo");
        Path workspaceRoot = repoRoot.resolve("nested/workspace");
        Files.createDirectories(workspaceRoot);
        Files.writeString(repoRoot.resolve(".git"), "gitdir: mock");
        Files.writeString(repoRoot.resolve("AGENTS.md"), "root doc");
        Files.writeString(workspaceRoot.resolve("AGENTS.md"), "child doc");
        DefaultUserInstructionsResolver resolver = new DefaultUserInstructionsResolver(workspaceRoot, 32 * 1024, null, null, null, null);

        String section = resolver.resolveUserInstructions().get(0);

        assertTrue(section.indexOf("root doc") < section.indexOf("child doc"));
    }
}
