package org.dean.codex.runtime.springai.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dean.codex.core.model.ModelOutputItem;
import org.dean.codex.core.model.ModelRequest;
import org.dean.codex.core.model.ModelResponse;
import org.dean.codex.core.model.ResponsesModelClient;

import java.net.http.HttpClient;
import java.util.function.Consumer;

public class OpenAiResponsesModelClient implements ResponsesModelClient {

    private final OpenAiResponsesApiClient apiClient;
    private final OpenAiResponsesPayloadMapper payloadMapper;
    private final OpenAiResponsesSettings settings;

    public OpenAiResponsesModelClient(HttpClient httpClient,
                                      ObjectMapper objectMapper,
                                      OpenAiResponsesSettings settings) {
        this(new OpenAiResponsesApiClient(httpClient, objectMapper, settings),
                new OpenAiResponsesPayloadMapper(objectMapper),
                settings);
    }

    OpenAiResponsesModelClient(OpenAiResponsesApiClient apiClient,
                               OpenAiResponsesPayloadMapper payloadMapper,
                               OpenAiResponsesSettings settings) {
        this.apiClient = apiClient;
        this.payloadMapper = payloadMapper;
        this.settings = settings;
    }

    @Override
    public ModelResponse complete(ModelRequest request, Consumer<ModelOutputItem> outputItemConsumer) {
        JsonNode root = apiClient.createResponse(payloadMapper.toModelRequestBody(request, settings));
        return payloadMapper.toModelResponse(root, outputItemConsumer);
    }
}
