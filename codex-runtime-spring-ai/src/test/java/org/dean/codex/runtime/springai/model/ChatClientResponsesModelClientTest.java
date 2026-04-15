package org.dean.codex.runtime.springai.model;

import org.dean.codex.core.model.InputImageItem;
import org.dean.codex.core.model.InputTextItem;
import org.dean.codex.core.model.ModelAssistantMessageItem;
import org.dean.codex.core.model.ModelInputRole;
import org.dean.codex.core.model.ModelOutputItem;
import org.dean.codex.core.model.ModelReasoningConfig;
import org.dean.codex.core.model.ModelRequest;
import org.dean.codex.core.model.ModelRequestMetadata;
import org.dean.codex.core.model.ModelResponse;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatClientResponsesModelClientTest {

    @Test
    void completeReturnsAssistantMessageItemAndStreamsItToConsumer() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class, RETURNS_SELF);
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(builder.clone()).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content()).thenReturn("assistant reply");

        ChatClientResponsesModelClient modelClient = new ChatClientResponsesModelClient(builder);
        AtomicReference<ModelOutputItem> streamed = new AtomicReference<>();

        ModelResponse response = modelClient.complete(
                new ModelRequest(
                        "system prompt",
                        List.of(new InputTextItem(ModelInputRole.USER, "user prompt")),
                        List.of(),
                        false,
                        null,
                        new ModelRequestMetadata("thread-1", "turn-1", 1)),
                streamed::set);

        assertEquals("assistant reply", response.assistantText());
        assertInstanceOf(ModelAssistantMessageItem.class, response.outputItems().get(0));
        assertEquals("assistant reply", ((ModelAssistantMessageItem) response.outputItems().get(0)).text());
        assertInstanceOf(ModelAssistantMessageItem.class, streamed.get());
        assertEquals("assistant reply", ((ModelAssistantMessageItem) streamed.get()).text());
    }

    @Test
    void completeRendersImageInputItemsIntoFallbackChatPrompt() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class, RETURNS_SELF);
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(builder.clone()).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);

        String rendered = ChatClientInputItemRenderer.renderInputItems(List.of(
                new InputTextItem(ModelInputRole.USER, "Describe this screenshot"),
                new InputImageItem(ModelInputRole.USER, "file:///tmp/screenshot.png", "high")));

        assertEquals("""
                Describe this screenshot

                [Input image]
                role: user
                url: file:///tmp/screenshot.png
                detail: high
                """, rendered);
    }

    @Test
    void completeRendersReasoningConfigIntoFallbackSystemInstructions() {
        String rendered = ChatClientSystemInstructionRenderer.renderSystemInstructions(
                "base instructions",
                new ModelReasoningConfig("high", "concise"));

        assertEquals("""
                Reasoning configuration for this request:
                - effort: high
                - summary: concise
                Use these settings when applicable. Do not change the required output contract.

                base instructions""", rendered);
    }
}
