package org.dean.codex.protocol.tool;

public record ListDirEntry(String path,
                           boolean directory,
                           int depth) {

    public ListDirEntry {
        path = path == null ? "" : path;
        depth = Math.max(0, depth);
    }
}
