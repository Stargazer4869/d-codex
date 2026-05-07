package org.dean.codex.runtime.springai.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dean.codex.core.model.InputTextItem;
import org.dean.codex.core.model.ModelCompactRequest;
import org.dean.codex.core.model.ModelCompactResponse;
import org.dean.codex.core.model.ModelInputRole;
import org.dean.codex.core.model.ModelRequestMetadata;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiResponsesCompactClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void compactUsesResponsesEndpointWithTextOutput() throws Exception {
        AtomicReference<HttpRequest> capturedRequest = new AtomicReference<>();
        HttpClient httpClient = httpClientReturning("""
                {
                  "id": "resp_compact_1",
                  "status": "completed",
                  "conversation": {
                    "id": "conv_compact_1"
                  },
                  "output": [
                    {
                      "id": "msg_1",
                      "type": "message",
                      "role": "assistant",
                      "content": [
                        {
                          "type": "output_text",
                          "text": "compacted summary"
                        }
                      ]
                    }
                  ]
                }
                """, capturedRequest);
        OpenAiResponsesCompactClient compactClient = new OpenAiResponsesCompactClient(
                httpClient,
                objectMapper,
                new OpenAiResponsesSettings(
                        "https://api.openai.com/v1",
                        "/responses",
                        "test-key",
                        "",
                        "",
                        "gpt-5.4",
                        0.2,
                        null,
                        true,
                        false));

        ModelCompactResponse response = compactClient.compact(new ModelCompactRequest(
                "system prompt",
                java.util.List.of(new InputTextItem(ModelInputRole.USER, "Summarize this")),
                new ModelRequestMetadata("thread-1", "", 0)));

        JsonNode requestJson = objectMapper.readTree(OpenAiResponsesModelClientTestHelper.capturedRequestBody(capturedRequest.get()));
        assertEquals("text", requestJson.path("text").path("format").path("type").asText());
        assertEquals(false, requestJson.path("store").asBoolean());
        assertEquals("compacted summary", response.outputText());
        assertEquals("resp_compact_1", response.metadata().responseId());
        assertEquals("conv_compact_1", response.metadata().sessionId());
    }

    private HttpClient httpClientReturning(String body, AtomicReference<HttpRequest> capturedRequest) throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = (HttpResponse<String>) mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(body);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(invocation -> {
            capturedRequest.set(invocation.getArgument(0));
            return response;
        });
        return httpClient;
    }
}
