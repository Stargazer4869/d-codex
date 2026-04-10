package org.dean.codex.core.tool.local;

import org.dean.codex.protocol.tool.ListDirResult;

public interface ListDirTool {

    ListDirResult listDir(String path, Integer maxDepth);
}
