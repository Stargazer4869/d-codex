package org.dean.codex.runtime.springai.model;

public record OpenAiResponsesSettings(String baseUrl,
                                      String responsesPath,
                                      String apiKey,
                                      String organizationId,
                                      String projectId,
                                      String model,
                                      Double temperature,
                                      Double topP,
                                      boolean store,
                                      boolean emitTools) {

    public OpenAiResponsesSettings {
        baseUrl = normalize(baseUrl);
        responsesPath = normalize(responsesPath);
        apiKey = normalize(apiKey);
        organizationId = normalize(organizationId);
        projectId = normalize(projectId);
        model = normalize(model);
    }

    private static String normalize(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? "" : normalized;
    }
}
