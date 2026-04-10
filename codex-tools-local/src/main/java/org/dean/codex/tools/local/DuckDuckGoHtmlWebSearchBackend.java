package org.dean.codex.tools.local;

import org.dean.codex.core.tool.local.WebSearchBackend;
import org.dean.codex.protocol.tool.WebSearchHit;
import org.dean.codex.protocol.tool.WebSearchResult;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DuckDuckGoHtmlWebSearchBackend implements WebSearchBackend {

    private static final int DEFAULT_MAX_RESULTS = 5;
    private static final int HARD_MAX_RESULTS = 8;
    private static final URI DEFAULT_BASE_URI = URI.create("https://html.duckduckgo.com/html/");
    private static final Pattern RESULT_TITLE_PATTERN = Pattern.compile("(?s)<a[^>]*class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>");
    private static final Pattern SNIPPET_PATTERN = Pattern.compile("(?s)<(?:a|div)[^>]*class=\"result__snippet\"[^>]*>(.*?)</(?:a|div)>");
    private static final Pattern STRIP_TAGS_PATTERN = Pattern.compile("(?s)<[^>]+>");
    private static final Pattern NUMERIC_ENTITY_PATTERN = Pattern.compile("&#(x?[0-9a-fA-F]+);");

    private final HttpClient httpClient;
    private final URI baseUri;

    public DuckDuckGoHtmlWebSearchBackend() {
        this(HttpClient.newHttpClient(), DEFAULT_BASE_URI);
    }

    public DuckDuckGoHtmlWebSearchBackend(HttpClient httpClient, URI baseUri) {
        this.httpClient = httpClient == null ? HttpClient.newHttpClient() : httpClient;
        this.baseUri = baseUri == null ? DEFAULT_BASE_URI : baseUri;
    }

    @Override
    public WebSearchResult search(String query, Integer maxResults) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isBlank()) {
            return new WebSearchResult(false, "", "duckduckgo-html", List.of(), 0, false, "Search query must not be blank.");
        }

        int boundedMaxResults = normalizeMaxResults(maxResults);
        try {
            URI requestUri = searchUri(normalizedQuery);
            HttpRequest request = HttpRequest.newBuilder(requestUri)
                    .header("User-Agent", "Mozilla/5.0 (Codex Java CLI)")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new WebSearchResult(false, normalizedQuery, "duckduckgo-html", List.of(), 0, false,
                        "DuckDuckGo returned HTTP " + response.statusCode());
            }
            return parseResponse(normalizedQuery, response.body(), boundedMaxResults);
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new WebSearchResult(false, normalizedQuery, "duckduckgo-html", List.of(), 0, false, exception.getMessage());
        }
        catch (Exception exception) {
            return new WebSearchResult(false, normalizedQuery, "duckduckgo-html", List.of(), 0, false, exception.getMessage());
        }
    }

    private WebSearchResult parseResponse(String query, String html, int maxResults) {
        List<TitleMatch> titleMatches = new ArrayList<>();
        Matcher titleMatcher = RESULT_TITLE_PATTERN.matcher(html == null ? "" : html);
        while (titleMatcher.find()) {
            titleMatches.add(new TitleMatch(titleMatcher.start(), titleMatcher.end(), titleMatcher.group(1), titleMatcher.group(2)));
        }

        List<WebSearchHit> hits = new ArrayList<>();
        for (int index = 0; index < titleMatches.size(); index++) {
            TitleMatch current = titleMatches.get(index);
            String block = html.substring(current.end(), index + 1 < titleMatches.size() ? titleMatches.get(index + 1).start() : html.length());
            String snippet = extractSnippet(block);
            if (hits.size() < maxResults) {
                hits.add(new WebSearchHit(
                        hits.size() + 1,
                        cleanText(current.titleHtml),
                        normalizeUrl(current.href),
                        cleanText(snippet)));
            }
        }

        return new WebSearchResult(true, query, "duckduckgo-html", List.copyOf(hits), titleMatches.size(), titleMatches.size() > hits.size(), "");
    }

    private int normalizeMaxResults(Integer maxResults) {
        int requested = maxResults == null ? DEFAULT_MAX_RESULTS : Math.max(1, maxResults);
        return Math.min(requested, HARD_MAX_RESULTS);
    }

    private URI searchUri(String query) {
        String encoded = java.net.URLEncoder.encode(query, StandardCharsets.UTF_8);
        String base = baseUri.toString();
        if (!base.contains("?")) {
            base = base.endsWith("/") ? base : base + "/";
            base = base + "?q=" + encoded + "&kl=us-en";
        }
        else {
            base = base + "&q=" + encoded + "&kl=us-en";
        }
        return URI.create(base);
    }

    private String extractSnippet(String block) {
        Matcher matcher = SNIPPET_PATTERN.matcher(block == null ? "" : block);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1);
    }

    private String normalizeUrl(String href) {
        String value = href == null ? "" : href.trim();
        if (value.isBlank()) {
            return "";
        }
        if (value.startsWith("//")) {
            value = "https:" + value;
        }
        try {
            URI uri = URI.create(value);
            String query = uri.getQuery();
            if (query != null) {
                for (String pair : query.split("&")) {
                    int separator = pair.indexOf('=');
                    if (separator > 0 && "uddg".equalsIgnoreCase(pair.substring(0, separator))) {
                        return URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
                    }
                }
            }
        }
        catch (Exception ignored) {
            // Fall through to the cleaned value.
        }
        return value;
    }

    private String cleanText(String value) {
        String normalized = decodeHtmlEntities(STRIP_TAGS_PATTERN.matcher(value == null ? "" : value).replaceAll(" "));
        normalized = normalized.replaceAll("\\s+", " ").trim();
        return normalized;
    }

    private String decodeHtmlEntities(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String decoded = value
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&#x27;", "'");
        Matcher matcher = NUMERIC_ENTITY_PATTERN.matcher(decoded);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String token = matcher.group(1);
            int codePoint;
            try {
                codePoint = token.toLowerCase(Locale.ROOT).startsWith("x")
                        ? Integer.parseInt(token.substring(1), 16)
                        : Integer.parseInt(token, 10);
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(new String(Character.toChars(codePoint))));
            }
            catch (Exception exception) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private record TitleMatch(int start,
                              int end,
                              String href,
                              String titleHtml) {
    }
}
