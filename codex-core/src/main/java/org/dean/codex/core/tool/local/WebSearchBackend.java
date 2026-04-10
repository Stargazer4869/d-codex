package org.dean.codex.core.tool.local;

import org.dean.codex.protocol.tool.WebSearchResult;

public interface WebSearchBackend {

    WebSearchResult search(String query, Integer maxResults);
}
