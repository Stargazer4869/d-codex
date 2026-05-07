package org.dean.codex.runtime.springai.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dean.codex.core.model.InputImageItem;
import org.dean.codex.core.model.InputTextItem;
import org.dean.codex.core.model.ModelInputRole;
import org.dean.codex.core.model.ModelOutputItem;
import org.dean.codex.core.model.ModelReasoningConfig;
import org.dean.codex.core.model.ModelRequest;
import org.dean.codex.core.model.ModelRequestMetadata;
import org.dean.codex.core.model.ModelResponse;
import org.dean.codex.core.model.ModelToolKind;
import org.dean.codex.core.model.ModelToolSpec;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiResponsesModelClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void completePostsPlannerRequestToResponsesAndParsesReturnedItems() throws Exception {
        AtomicReference<HttpRequest> capturedRequest = new AtomicReference<>();
        HttpClient httpClient = httpClientReturning("""
                {
                  "id": "resp_123",
                  "status": "completed",
                  "conversation": {
                    "id": "conv_456"
                  },
                  "output": [
                    {
                      "id": "rs_1",
                      "type": "reasoning",
                      "summary": [
                        {
                          "type": "summary_text",
                          "text": "Thinking"
                        }
                      ],
                      "content": [
                        {
                          "type": "reasoning_text",
                          "text": "Detailed reasoning"
                        }
                      ]
                    },
                    {
                        "id": "msg_1",
                      "type": "message",
                      "role": "assistant",
                      "content": [
                        {
                          "type": "output_text",
                          "text": "{\\\"summary\\\":\\\"Done\\\",\\\"finalAnswer\\\":\\\"Finished\\\"}"
                        }
                      ]
                    }
                  ]
                }
                """, capturedRequest);
        OpenAiResponsesModelClient modelClient = new OpenAiResponsesModelClient(
                httpClient,
                objectMapper,
                new OpenAiResponsesSettings(
                        "https://api.openai.com/v1",
                        "/responses",
                        "test-key",
                        "org-test",
                        "proj-test",
                        "gpt-5.4",
                        0.2,
                        0.9,
                        true,
                        false));
        List<ModelOutputItem> streamedItems = new ArrayList<>();

        ModelResponse response = modelClient.complete(new ModelRequest(
                "system prompt",
                List.of(
                        new InputTextItem(ModelInputRole.USER, "Describe the image"),
                        new InputImageItem(ModelInputRole.USER, "file:///tmp/screenshot.png", "high")),
                List.of(),
                false,
                new ModelReasoningConfig("high", "concise"),
                new ModelRequestMetadata("thread-1", "turn-1", 2, "root-1", "parent-1", "worker", 1, "thread-0", "resp_prev", "conv_prev")),
                streamedItems::add);

        JsonNode requestJson = objectMapper.readTree(OpenAiResponsesModelClientTestHelper.capturedRequestBody(capturedRequest.get()));
        assertEquals(URI.create("https://api.openai.com/v1/responses"), capturedRequest.get().uri());
        assertEquals("gpt-5.4", requestJson.path("model").asText());
        assertEquals("system prompt", requestJson.path("instructions").asText());
        assertEquals("json_object", requestJson.path("text").path("format").path("type").asText());
        assertEquals("high", requestJson.path("reasoning").path("effort").asText());
        assertEquals("concise", requestJson.path("reasoning").path("summary").asText());
        assertEquals("thread-1", requestJson.path("metadata").path("codex_thread_id").asText());
        assertEquals("turn-1", requestJson.path("metadata").path("codex_turn_id").asText());
        assertEquals("Describe the image", requestJson.path("input").get(0).path("content").get(0).path("text").asText());
        assertEquals("file:///tmp/screenshot.png", requestJson.path("input").get(0).path("content").get(1).path("image_url").asText());
        assertFalse(requestJson.has("tools"));

        assertEquals("resp_123", response.metadata().responseId());
        assertEquals("conv_456", response.metadata().sessionId());
        assertEquals("completed", response.metadata().finishReason());
        assertEquals("{\"summary\":\"Done\",\"finalAnswer\":\"Finished\"}", response.assistantText());
        assertEquals(2, response.outputItems().size());
        assertEquals(2, streamedItems.size());
    }

    @Test
    void completeOptionallyIncludesToolDefinitions() throws Exception {
        AtomicReference<HttpRequest> capturedRequest = new AtomicReference<>();
        HttpClient httpClient = httpClientReturning("""
                {
                  "id": "resp_456",
                  "status": "completed",
                  "output": [
                    {
                      "id": "msg_1",
                      "type": "message",
                      "role": "assistant",
                      "content": [
                        {
                          "type": "output_text",
                          "text": "{\\\"finalAnswer\\\":\\\"done\\\"}"
                        }
                      ]
                    }
                  ]
                }
                """, capturedRequest);
        OpenAiResponsesModelClient modelClient = new OpenAiResponsesModelClient(
                httpClient,
                objectMapper,
                new OpenAiResponsesSettings(
                        "https://api.openai.com/v1",
                        "/responses",
                        "test-key",
                        "",
                        "",
                        "gpt-5.4",
                        null,
                        null,
                        true,
                        true));

        modelClient.complete(new ModelRequest(
                "system prompt",
                List.of(new InputTextItem(ModelInputRole.USER, "Use a tool")),
                List.of(new ModelToolSpec(
                        ModelToolKind.FUNCTION,
                        "read_file",
                        "Read a file",
                        "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}}}",
                        "",
                        true,
                        List.of("Prefer relative paths"))),
                true,
                null,
                new ModelRequestMetadata("thread-1", "turn-1", 1)));

        JsonNode requestJson = objectMapper.readTree(OpenAiResponsesModelClientTestHelper.capturedRequestBody(capturedRequest.get()));
        assertTrue(requestJson.has("tools"));
        assertEquals("function", requestJson.path("tools").get(0).path("type").asText());
        assertEquals("read_file", requestJson.path("tools").get(0).path("name").asText());
        assertEquals(true, requestJson.path("parallel_tool_calls").asBoolean());
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
