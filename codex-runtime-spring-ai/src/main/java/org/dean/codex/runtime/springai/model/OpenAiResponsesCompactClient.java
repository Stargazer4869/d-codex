package org.dean.codex.runtime.springai.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dean.codex.core.model.ModelCompactRequest;
import org.dean.codex.core.model.ModelCompactResponse;
import org.dean.codex.core.model.ResponsesCompactClient;

import java.net.http.HttpClient;

public class OpenAiResponsesCompactClient implements ResponsesCompactClient {

    private final OpenAiResponsesApiClient apiClient;
    private final OpenAiResponsesPayloadMapper payloadMapper;
    private final OpenAiResponsesSettings settings;

    public OpenAiResponsesCompactClient(HttpClient httpClient,
                                        ObjectMapper objectMapper,
                                        OpenAiResponsesSettings settings) {
        this(new OpenAiResponsesApiClient(httpClient, objectMapper, settings),
                new OpenAiResponsesPayloadMapper(objectMapper),
                settings);
    }

    OpenAiResponsesCompactClient(OpenAiResponsesApiClient apiClient,
                                 OpenAiResponsesPayloadMapper payloadMapper,
                                 OpenAiResponsesSettings settings) {
        this.apiClient = apiClient;
        this.payloadMapper = payloadMapper;
        this.settings = settings;
    }

    @Override
    public ModelCompactResponse compact(ModelCompactRequest request) {
        // The current Java compaction contract stores a plain-text handoff summary, so we use /responses
        // with text output until the runtime can persist opaque native compaction items end to end.
        JsonNode root = apiClient.createResponse(payloadMapper.toCompactRequestBody(request, settings));
        return payloadMapper.toCompactResponse(root);
    }
}
