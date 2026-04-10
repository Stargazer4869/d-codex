package org.dean.codex.protocol.tool;

public record WebSearchHit(int rank,
                           String title,
                           String url,
                           String snippet) {

    public WebSearchHit {
        title = title == null ? "" : title;
        url = url == null ? "" : url;
        snippet = snippet == null ? "" : snippet;
        rank = Math.max(1, rank);
    }
}
