package org.dean.codex.tools.local;

import org.dean.codex.protocol.tool.WebSearchResult;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuckDuckGoHtmlWebSearchBackendTest {

    @Test
    void searchParsesCompactHitsFromHtmlResponse() throws Exception {
        String html = """
                <html>
                  <body>
                    <div class="result">
                      <a class="result__a" href="//example.com/article?uddg=https%3A%2F%2Fexample.com%2Farticle">Example Title</a>
                      <a class="result__snippet">A compact snippet with <b>markup</b> &amp; entities.</a>
                    </div>
                    <div class="result">
                      <a class="result__a" href="https://example.org/docs">Second Title</a>
                      <div class="result__snippet">Another snippet</div>
                    </div>
                  </body>
                </html>
                """;

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/html/", exchange -> respondHtml(exchange, html));
            server.setExecutor(Executors.newSingleThreadExecutor());
            server.start();

            HttpClient httpClient = HttpClient.newHttpClient();
            URI baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/html/");
            DuckDuckGoHtmlWebSearchBackend backend = new DuckDuckGoHtmlWebSearchBackend(httpClient, baseUri);

            WebSearchResult result = backend.search("codex java", 2);

            assertTrue(result.success());
            assertEquals("codex java", result.query());
            assertEquals("duckduckgo-html", result.backend());
            assertEquals(2, result.hits().size());
            assertEquals(2, result.totalHits());
            assertFalse(result.truncated());
            assertEquals("Example Title", result.hits().get(0).title());
            assertEquals("https://example.com/article", result.hits().get(0).url());
            assertTrue(result.hits().get(0).snippet().contains("markup"));
        }
        finally {
            server.stop(0);
        }
    }

    @Test
    void searchTruncatesHitsToRequestedBound() throws Exception {
        String html = """
                <html>
                  <body>
                    <div class="result">
                      <a class="result__a" href="https://example.com/one">One</a>
                      <div class="result__snippet">First</div>
                    </div>
                    <div class="result">
                      <a class="result__a" href="https://example.com/two">Two</a>
                      <div class="result__snippet">Second</div>
                    </div>
                  </body>
                </html>
                """;

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/html/", exchange -> respondHtml(exchange, html));
            server.setExecutor(Executors.newSingleThreadExecutor());
            server.start();

            HttpClient httpClient = HttpClient.newHttpClient();
            URI baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/html/");
            DuckDuckGoHtmlWebSearchBackend backend = new DuckDuckGoHtmlWebSearchBackend(httpClient, baseUri);

            WebSearchResult result = backend.search("codex java", 1);

            assertTrue(result.success());
            assertEquals(2, result.totalHits());
            assertTrue(result.truncated());
            assertEquals(1, result.hits().size());
        }
        finally {
            server.stop(0);
        }
    }

    private void respondHtml(HttpExchange exchange, String html) throws IOException {
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }
}
