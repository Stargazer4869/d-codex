package org.dean.codex.core.tool.local;

import org.dean.codex.protocol.tool.WebSearchResult;

public interface WebSearchTool {

    WebSearchResult search(String query, Integer maxResults);
}
