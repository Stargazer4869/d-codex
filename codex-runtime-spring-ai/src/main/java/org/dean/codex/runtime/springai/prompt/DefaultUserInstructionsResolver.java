package org.dean.codex.runtime.springai.prompt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DefaultUserInstructionsResolver implements UserInstructionsResolver {

    static final String DEFAULT_PROJECT_DOC_FILENAME = "AGENTS.md";
    static final String LOCAL_PROJECT_DOC_FILENAME = "AGENTS.override.md";

    private final Path workspaceRoot;
    private final int projectDocMaxBytes;
    private final String projectInstructionsTextOverride;
    private final String projectInstructionsFileOverride;
    private final String userInstructionsTextOverride;
    private final String userInstructionsFileOverride;

    public DefaultUserInstructionsResolver(Path workspaceRoot) {
        this(workspaceRoot, 32 * 1024, null, null, null, null);
    }

    public DefaultUserInstructionsResolver(Path workspaceRoot,
                                           int projectDocMaxBytes,
                                           String projectInstructionsTextOverride,
                                           String projectInstructionsFileOverride,
                                           String userInstructionsTextOverride,
                                           String userInstructionsFileOverride) {
        this.workspaceRoot = PromptOverrideSupport.normalizeWorkspaceRoot(workspaceRoot);
        this.projectDocMaxBytes = Math.max(0, projectDocMaxBytes);
        this.projectInstructionsTextOverride = projectInstructionsTextOverride;
        this.projectInstructionsFileOverride = projectInstructionsFileOverride;
        this.userInstructionsTextOverride = userInstructionsTextOverride;
        this.userInstructionsFileOverride = userInstructionsFileOverride;
    }

    @Override
    public List<String> resolveUserInstructions() {
        List<String> sections = new ArrayList<>();
        addSection(sections, "Project instructions", projectInstructionsTextOverride, projectInstructionsFileOverride);
        String projectDocSection = discoverProjectDocSection();
        if (!projectDocSection.isBlank()) {
            sections.add(projectDocSection);
        }
        addSection(sections, "User instructions", userInstructionsTextOverride, userInstructionsFileOverride);
        return List.copyOf(sections);
    }

    private void addSection(List<String> sections, String title, String textOverride, String fileOverride) {
        String resolvedText = PromptOverrideSupport.resolveOverrideText(workspaceRoot, textOverride, fileOverride);
        if (resolvedText.isBlank()) {
            return;
        }
        sections.add(title + ":\n" + resolvedText);
    }

    private String discoverProjectDocSection() {
        if (projectDocMaxBytes == 0) {
            return "";
        }
        List<Path> docPaths = discoverProjectDocPaths();
        if (docPaths.isEmpty()) {
            return "";
        }

        long remaining = projectDocMaxBytes;
        List<String> parts = new ArrayList<>();
        for (Path path : docPaths) {
            if (remaining <= 0) {
                break;
            }
            String text = readProjectDoc(path, remaining);
            if (text.isBlank()) {
                continue;
            }
            parts.add(text);
            remaining -= text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        }

        if (parts.isEmpty()) {
            return "";
        }

        return """
                # AGENTS.md instructions for %s

                <INSTRUCTIONS>
                %s
                </INSTRUCTIONS>""".formatted(workspaceRoot, String.join("\n\n", parts));
    }

    private List<Path> discoverProjectDocPaths() {
        List<Path> directories = directoriesFromProjectRootToWorkspace();
        List<Path> paths = new ArrayList<>();
        for (Path directory : directories) {
            Path preferred = directory.resolve(LOCAL_PROJECT_DOC_FILENAME);
            if (isRegularFile(preferred)) {
                paths.add(preferred);
                continue;
            }
            Path primary = directory.resolve(DEFAULT_PROJECT_DOC_FILENAME);
            if (isRegularFile(primary)) {
                paths.add(primary);
            }
        }
        return List.copyOf(paths);
    }

    private List<Path> directoriesFromProjectRootToWorkspace() {
        Path projectRoot = findProjectRoot();
        List<Path> directories = new ArrayList<>();
        for (Path current = workspaceRoot; current != null; current = current.getParent()) {
            directories.add(current);
            if (current.equals(projectRoot)) {
                break;
            }
        }
        Collections.reverse(directories);
        return directories;
    }

    private Path findProjectRoot() {
        for (Path current = workspaceRoot; current != null; current = current.getParent()) {
            if (Files.exists(current.resolve(".git"))) {
                return current;
            }
        }
        return workspaceRoot;
    }

    private String readProjectDoc(Path path, long remainingBytes) {
        try {
            byte[] data = Files.readAllBytes(path);
            if (data.length > remainingBytes) {
                data = java.util.Arrays.copyOf(data, Math.toIntExact(remainingBytes));
            }
            return new String(data, java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n")
                    .stripTrailing();
        }
        catch (IOException exception) {
            throw new IllegalStateException("Failed to read project instructions from " + path, exception);
        }
    }

    private boolean isRegularFile(Path path) {
        return Files.isRegularFile(path);
    }
}
