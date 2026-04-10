package org.dean.codex.protocol.tool;

import java.util.List;

public record ListDirResult(boolean success,
                            String path,
                            int maxDepth,
                            List<ListDirEntry> entries,
                            int totalEntries,
                            boolean truncated,
                            String error) {

    public ListDirResult {
        path = path == null ? "" : path;
        maxDepth = Math.max(0, maxDepth);
        entries = entries == null ? List.of() : List.copyOf(entries);
        totalEntries = Math.max(0, totalEntries);
        error = error == null ? "" : error;
    }
}
