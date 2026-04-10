package org.dean.codex.tools.local;

import org.dean.codex.core.tool.local.ListDirTool;
import org.dean.codex.protocol.tool.ListDirEntry;
import org.dean.codex.protocol.tool.ListDirResult;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class ListDirToolImpl implements ListDirTool {

    private static final int MAX_ENTRIES = 200;
    private static final int MAX_DEPTH = 5;

    private final Path workspaceRoot;

    public ListDirToolImpl(@Qualifier("codexWorkspaceRoot") Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    @Override
    @Tool(description = "List directory contents relative to the workspace root. Use this to discover repository structure before reading files. The path must stay inside the workspace root, and listing is bounded.")
    public ListDirResult listDir(String path, Integer maxDepth) {
        try {
            Path target = resolvePath(path);
            if (!Files.exists(target)) {
                return new ListDirResult(false, normalizedPath(path), normalizeMaxDepth(maxDepth), List.of(), 0, false, "Path does not exist.");
            }
            if (!Files.isDirectory(target)) {
                return new ListDirResult(false, normalizedPath(path), normalizeMaxDepth(maxDepth), List.of(), 0, false, "Path points to a file, not a directory.");
            }

            int normalizedMaxDepth = normalizeMaxDepth(maxDepth);
            int walkDepth = normalizedMaxDepth;
            List<Path> paths = new ArrayList<>();
            try (var stream = Files.walk(target, walkDepth)) {
                stream.filter(candidate -> !candidate.equals(target))
                        .forEach(paths::add);
            }

            paths.sort(Comparator.comparing(this::relativePath));
            int totalEntries = paths.size();
            boolean truncated = totalEntries > MAX_ENTRIES;
            List<ListDirEntry> entries = paths.stream()
                    .limit(MAX_ENTRIES)
                    .map(candidate -> new ListDirEntry(
                            relativePath(candidate),
                            Files.isDirectory(candidate),
                            target.relativize(candidate).getNameCount()))
                    .toList();

            return new ListDirResult(true,
                    normalizedPath(path),
                    normalizedMaxDepth,
                    entries,
                    totalEntries,
                    truncated,
                    "");
        }
        catch (Exception exception) {
            return new ListDirResult(false, normalizedPath(path), normalizeMaxDepth(maxDepth), List.of(), 0, false, exception.getMessage());
        }
    }

    private int normalizeMaxDepth(Integer maxDepth) {
        int requested = maxDepth == null ? 1 : Math.max(1, maxDepth);
        return Math.min(requested, MAX_DEPTH);
    }

    private Path resolvePath(String path) {
        Path target = workspaceRoot.resolve(path == null || path.isBlank() ? "." : path).normalize();
        if (!target.startsWith(workspaceRoot)) {
            throw new IllegalArgumentException("Path must stay inside the workspace root.");
        }
        return target;
    }

    private String normalizedPath(String path) {
        if (path == null || path.isBlank()) {
            return ".";
        }
        String normalized = path.trim();
        return normalized.startsWith("./") ? normalized.substring(2) : normalized;
    }

    private String relativePath(Path path) {
        return normalizedPath(workspaceRoot.relativize(path).toString());
    }
}
