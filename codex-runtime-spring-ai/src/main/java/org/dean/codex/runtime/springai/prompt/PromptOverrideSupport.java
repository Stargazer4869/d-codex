package org.dean.codex.runtime.springai.prompt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class PromptOverrideSupport {

    static final String WORKSPACE_ROOT_TOKEN = "{{workspaceRoot}}";

    private PromptOverrideSupport() {
    }

    static String resolveOverrideText(Path workspaceRoot, String textOverride, String fileOverride) {
        String normalizedTextOverride = blankToNull(textOverride);
        if (normalizedTextOverride != null) {
            return renderWorkspaceRoot(workspaceRoot, normalize(normalizedTextOverride));
        }
        Path resolvedFile = resolveOverridePath(workspaceRoot, fileOverride);
        if (resolvedFile == null) {
            return "";
        }
        try {
            return renderWorkspaceRoot(workspaceRoot, normalize(Files.readString(resolvedFile)));
        }
        catch (IOException exception) {
            throw new IllegalStateException("Failed to read prompt override file: " + resolvedFile, exception);
        }
    }

    static String renderWorkspaceRoot(Path workspaceRoot, String template) {
        if (template == null || template.isBlank()) {
            return "";
        }
        String workspaceRootText = normalizeWorkspaceRoot(workspaceRoot).toString();
        if (template.contains(WORKSPACE_ROOT_TOKEN)) {
            return template.replace(WORKSPACE_ROOT_TOKEN, workspaceRootText);
        }
        if (template.contains("%s")) {
            return template.replace("%s", workspaceRootText);
        }
        return template;
    }

    static Path normalizeWorkspaceRoot(Path workspaceRoot) {
        return workspaceRoot == null ? Path.of(".").toAbsolutePath().normalize() : workspaceRoot.toAbsolutePath().normalize();
    }

    static String normalize(String value) {
        return value == null ? "" : value.replace("\r\n", "\n").stripTrailing();
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    static Path resolveOverridePath(Path workspaceRoot, String fileOverride) {
        String normalizedPath = blankToNull(fileOverride);
        if (normalizedPath == null) {
            return null;
        }
        Path configuredPath = Path.of(normalizedPath);
        return configuredPath.isAbsolute()
                ? configuredPath.normalize()
                : normalizeWorkspaceRoot(workspaceRoot).resolve(configuredPath).normalize();
    }
}
