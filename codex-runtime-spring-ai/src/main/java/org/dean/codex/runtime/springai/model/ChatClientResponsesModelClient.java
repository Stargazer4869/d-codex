package org.dean.codex.runtime.springai.model;

import org.dean.codex.core.model.ModelAssistantMessageItem;
import org.dean.codex.core.model.ModelOutputItem;
import org.dean.codex.core.model.ModelRequest;
import org.dean.codex.core.model.ModelResponse;
import org.dean.codex.core.model.ModelResponseMetadata;
import org.dean.codex.core.model.ResponsesModelClient;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class ChatClientResponsesModelClient implements ResponsesModelClient {

    private final ChatClient chatClient;

    public ChatClientResponsesModelClient(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.clone().build();
    }

    @Override
    public ModelResponse complete(ModelRequest request, Consumer<ModelOutputItem> outputItemConsumer) {
        String response = chatClient.prompt()
                .system(ChatClientSystemInstructionRenderer.renderSystemInstructions(
                        request.systemInstructions(),
                        request.reasoningConfig()))
                .user(ChatClientInputItemRenderer.renderInputItems(request.inputItems()))
                .call()
                .content();
        ModelAssistantMessageItem assistantMessage = new ModelAssistantMessageItem(
                UUID.randomUUID().toString(),
                response == null ? "" : response);
        if (outputItemConsumer != null) {
            outputItemConsumer.accept(assistantMessage);
        }
        return new ModelResponse(
                new ModelResponseMetadata("", "", "completed"),
                List.of(assistantMessage));
    }
}
