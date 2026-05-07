package org.dean.codex.runtime.springai.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

final class OpenAiResponsesApiClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(5);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final OpenAiResponsesSettings settings;

    OpenAiResponsesApiClient(HttpClient httpClient,
                             ObjectMapper objectMapper,
                             OpenAiResponsesSettings settings) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.settings = settings;
    }

    JsonNode createResponse(ObjectNode requestBody) {
        requireConfigured("base URL", settings.baseUrl());
        requireConfigured("API key", settings.apiKey());
        requireConfigured("model", settings.model());
        try {
            String requestJson = objectMapper.writeValueAsString(requestBody);
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(resolve())
                    .timeout(REQUEST_TIMEOUT)
                    .header("Authorization", "Bearer " + settings.apiKey())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8));
            if (!settings.organizationId().isBlank()) {
                requestBuilder.header("OpenAI-Organization", settings.organizationId());
            }
            if (!settings.projectId().isBlank()) {
                requestBuilder.header("OpenAI-Project", settings.projectId());
            }
            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String responseBody = response.body() == null ? "" : response.body();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("OpenAI Responses request failed with HTTP "
                        + response.statusCode() + ": " + summarize(responseBody));
            }
            JsonNode root = objectMapper.readTree(responseBody);
            String providerError = text(root.path("error").path("message"));
            if (!providerError.isBlank()) {
                throw new IllegalStateException("OpenAI Responses request failed: " + providerError);
            }
            return root;
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenAI Responses request was interrupted", exception);
        }
        catch (IOException exception) {
            throw new IllegalStateException("OpenAI Responses request failed", exception);
        }
    }

    private URI resolve() {
        String normalizedBase = settings.baseUrl().endsWith("/")
                ? settings.baseUrl().substring(0, settings.baseUrl().length() - 1)
                : settings.baseUrl();
        String configuredPath = settings.responsesPath().isBlank() ? "/responses" : settings.responsesPath();
        String normalizedPath = configuredPath.startsWith("/") ? configuredPath : "/" + configuredPath;
        return URI.create(normalizedBase + normalizedPath);
    }

    private void requireConfigured(String label, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("OpenAI Responses " + label + " is not configured");
        }
    }

    private static String summarize(String body) {
        if (body == null || body.isBlank()) {
            return "(empty response body)";
        }
        String normalized = body.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 512 ? normalized : normalized.substring(0, 512) + "...";
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        String value = node.asText("");
        return value == null ? "" : value;
    }
}
