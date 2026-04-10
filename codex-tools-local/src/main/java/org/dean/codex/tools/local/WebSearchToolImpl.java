package org.dean.codex.tools.local;

import org.dean.codex.core.tool.local.WebSearchBackend;
import org.dean.codex.core.tool.local.WebSearchTool;
import org.dean.codex.protocol.tool.WebSearchResult;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class WebSearchToolImpl implements WebSearchTool {

    private final WebSearchBackend webSearchBackend;

    public WebSearchToolImpl(WebSearchBackend webSearchBackend) {
        this.webSearchBackend = webSearchBackend;
    }

    @Override
    @Tool(description = "Search the public web for concise external references when repository-local search is not enough. Use this for documentation, ecosystem facts, or recent external details. Results are compact and bounded.")
    public WebSearchResult search(String query, Integer maxResults) {
        return webSearchBackend.search(query, maxResults);
    }
}
