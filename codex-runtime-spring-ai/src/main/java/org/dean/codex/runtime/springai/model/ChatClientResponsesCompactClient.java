package org.dean.codex.runtime.springai.model;

import org.dean.codex.core.model.ModelCompactRequest;
import org.dean.codex.core.model.ModelCompactResponse;
import org.dean.codex.core.model.ModelResponseMetadata;
import org.dean.codex.core.model.ResponsesCompactClient;
import org.springframework.ai.chat.client.ChatClient;

public class ChatClientResponsesCompactClient implements ResponsesCompactClient {

    private final ChatClient chatClient;

    public ChatClientResponsesCompactClient(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.clone().build();
    }

    @Override
    public ModelCompactResponse compact(ModelCompactRequest request) {
        String response = chatClient.prompt()
                .system(request.systemInstructions())
                .user(ChatClientInputItemRenderer.renderInputItems(request.inputItems()))
                .call()
                .content();
        return new ModelCompactResponse(
                new ModelResponseMetadata("", "", "completed"),
                response == null ? "" : response);
    }
}
