package org.dean.codex.protocol.tool;

import java.util.List;

public record WebSearchResult(boolean success,
                              String query,
                              String backend,
                              List<WebSearchHit> hits,
                              int totalHits,
                              boolean truncated,
                              String error) {

    public WebSearchResult {
        query = query == null ? "" : query;
        backend = backend == null ? "" : backend;
        hits = hits == null ? List.of() : List.copyOf(hits);
        totalHits = Math.max(0, totalHits);
        error = error == null ? "" : error;
    }
}
