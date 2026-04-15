package org.dean.codex.runtime.springai.model;

import org.dean.codex.core.model.InputTextItem;
import org.dean.codex.core.model.ModelCompactRequest;
import org.dean.codex.core.model.ModelInputRole;
import org.dean.codex.core.model.ModelRequestMetadata;
import org.dean.codex.core.model.ModelCompactResponse;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatClientResponsesCompactClientTest {

    @Test
    void compactReturnsTrimReadyOutputTextFromChatFallback() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class, RETURNS_SELF);
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(builder.clone()).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content()).thenReturn("compacted summary");

        ChatClientResponsesCompactClient compactClient = new ChatClientResponsesCompactClient(builder);

        ModelCompactResponse response = compactClient.compact(new ModelCompactRequest(
                "system prompt",
                List.of(new InputTextItem(ModelInputRole.USER, "user prompt")),
                new ModelRequestMetadata("thread-1", "", 0)));

        assertEquals("compacted summary", response.outputText());
        assertEquals("completed", response.metadata().finishReason());
    }
}
