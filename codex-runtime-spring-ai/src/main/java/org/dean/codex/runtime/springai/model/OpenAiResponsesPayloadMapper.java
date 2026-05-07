package org.dean.codex.runtime.springai.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.dean.codex.core.model.InputImageItem;
import org.dean.codex.core.model.InputTextItem;
import org.dean.codex.core.model.ModelAssistantMessageItem;
import org.dean.codex.core.model.ModelCompactRequest;
import org.dean.codex.core.model.ModelCompactResponse;
import org.dean.codex.core.model.ModelInputItem;
import org.dean.codex.core.model.ModelInputRole;
import org.dean.codex.core.model.ModelOutputItem;
import org.dean.codex.core.model.ModelReasoningConfig;
import org.dean.codex.core.model.ModelReasoningItem;
import org.dean.codex.core.model.ModelRequest;
import org.dean.codex.core.model.ModelRequestMetadata;
import org.dean.codex.core.model.ModelResponse;
import org.dean.codex.core.model.ModelResponseMetadata;
import org.dean.codex.core.model.ModelToolCallItem;
import org.dean.codex.core.model.ModelToolResultItem;
import org.dean.codex.core.model.ModelToolSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

final class OpenAiResponsesPayloadMapper {

    private final ObjectMapper objectMapper;

    OpenAiResponsesPayloadMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    ObjectNode toModelRequestBody(ModelRequest request, OpenAiResponsesSettings settings) {
        ObjectNode body = baseRequestBody(settings, request.systemInstructions(), request.inputItems(), request.metadata(), true);
        if (settings.emitTools() && !request.toolSpecs().isEmpty()) {
            body.set("tools", toolSpecs(request.toolSpecs()));
            body.put("parallel_tool_calls", request.parallelToolCalls());
        }
        applyReasoning(body, request.reasoningConfig());
        return body;
    }

    ObjectNode toCompactRequestBody(ModelCompactRequest request, OpenAiResponsesSettings settings) {
        ObjectNode body = baseRequestBody(settings, request.systemInstructions(), request.inputItems(), request.metadata(), false);
        body.put("store", false);
        return body;
    }

    ModelResponse toModelResponse(JsonNode root, Consumer<ModelOutputItem> outputItemConsumer) {
        List<ModelOutputItem> items = new ArrayList<>();
        JsonNode outputNode = root.path("output");
        if (outputNode.isArray()) {
            for (JsonNode itemNode : outputNode) {
                ModelOutputItem item = toOutputItem(itemNode);
                if (item != null) {
                    items.add(item);
                    if (outputItemConsumer != null) {
                        outputItemConsumer.accept(item);
                    }
                }
            }
        }
        if (items.stream().noneMatch(ModelAssistantMessageItem.class::isInstance)) {
            String fallbackText = text(root.path("output_text"));
            if (!fallbackText.isBlank()) {
                ModelAssistantMessageItem fallbackItem = new ModelAssistantMessageItem(UUID.randomUUID().toString(), fallbackText);
                items.add(fallbackItem);
                if (outputItemConsumer != null) {
                    outputItemConsumer.accept(fallbackItem);
                }
            }
        }
        return new ModelResponse(metadata(root), items);
    }

    ModelCompactResponse toCompactResponse(JsonNode root) {
        ModelResponse response = toModelResponse(root, null);
        return new ModelCompactResponse(response.metadata(), response.assistantText());
    }

    private ObjectNode baseRequestBody(OpenAiResponsesSettings settings,
                                       String instructions,
                                       List<ModelInputItem> inputItems,
                                       ModelRequestMetadata metadata,
                                       boolean jsonObjectOutput) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", settings.model());
        body.put("store", settings.store());
        if (instructions != null && !instructions.isBlank()) {
            body.put("instructions", instructions);
        }
        body.set("input", inputItems(inputItems));
        body.set("text", textConfig(jsonObjectOutput));
        ObjectNode metadataNode = metadata(metadata);
        if (metadataNode.size() > 0) {
            body.set("metadata", metadataNode);
        }
        if (settings.temperature() != null) {
            body.put("temperature", settings.temperature());
        }
        if (settings.topP() != null) {
            body.put("top_p", settings.topP());
        }
        return body;
    }

    private void applyReasoning(ObjectNode body, ModelReasoningConfig reasoningConfig) {
        if (reasoningConfig == null) {
            return;
        }
        ObjectNode reasoning = objectMapper.createObjectNode();
        if (!reasoningConfig.effort().isBlank()) {
            reasoning.put("effort", reasoningConfig.effort());
        }
        if (!reasoningConfig.summaryMode().isBlank()) {
            reasoning.put("summary", reasoningConfig.summaryMode());
        }
        if (reasoning.size() > 0) {
            body.set("reasoning", reasoning);
        }
    }

    private ObjectNode textConfig(boolean jsonObjectOutput) {
        ObjectNode text = objectMapper.createObjectNode();
        ObjectNode format = objectMapper.createObjectNode();
        format.put("type", jsonObjectOutput ? "json_object" : "text");
        text.set("format", format);
        return text;
    }

    private ArrayNode inputItems(List<ModelInputItem> inputItems) {
        ArrayNode messages = objectMapper.createArrayNode();
        if (inputItems == null || inputItems.isEmpty()) {
            return messages;
        }
        ModelInputRole currentRole = null;
        ArrayNode currentContent = null;
        for (ModelInputItem inputItem : inputItems) {
            if (inputItem == null) {
                continue;
            }
            if (currentRole != inputItem.role() || currentContent == null) {
                currentRole = inputItem.role();
                currentContent = objectMapper.createArrayNode();
                ObjectNode message = objectMapper.createObjectNode();
                message.put("role", role(currentRole));
                message.set("content", currentContent);
                messages.add(message);
            }
            JsonNode contentItem = contentItem(inputItem);
            if (contentItem != null) {
                currentContent.add(contentItem);
            }
        }
        return messages;
    }

    private JsonNode contentItem(ModelInputItem inputItem) {
        if (inputItem instanceof InputTextItem textItem) {
            ObjectNode content = objectMapper.createObjectNode();
            content.put("type", "input_text");
            content.put("text", textItem.text());
            return content;
        }
        if (inputItem instanceof InputImageItem imageItem) {
            ObjectNode content = objectMapper.createObjectNode();
            content.put("type", "input_image");
            if (!imageItem.imageUrl().isBlank()) {
                content.put("image_url", imageItem.imageUrl());
            }
            if (!imageItem.detail().isBlank()) {
                content.put("detail", imageItem.detail());
            }
            return content;
        }
        return null;
    }

    private ArrayNode toolSpecs(List<ModelToolSpec> toolSpecs) {
        ArrayNode tools = objectMapper.createArrayNode();
        for (ModelToolSpec toolSpec : toolSpecs) {
            if (toolSpec == null || toolSpec.name().isBlank()) {
                continue;
            }
            ObjectNode tool = objectMapper.createObjectNode();
            tool.put("type", "function");
            tool.put("name", toolSpec.name());
            if (!toolSpec.description().isBlank() || !toolSpec.supplementaryInstructions().isEmpty()) {
                StringBuilder description = new StringBuilder(toolSpec.description());
                for (String instruction : toolSpec.supplementaryInstructions()) {
                    if (instruction == null || instruction.isBlank()) {
                        continue;
                    }
                    if (!description.isEmpty()) {
                        description.append(System.lineSeparator());
                    }
                    description.append(instruction);
                }
                tool.put("description", description.toString());
            }
            tool.set("parameters", parseSchema(toolSpec.inputSchema()));
            tools.add(tool);
        }
        return tools;
    }

    private JsonNode parseSchema(String inputSchema) {
        if (inputSchema != null && !inputSchema.isBlank()) {
            try {
                JsonNode parsed = objectMapper.readTree(inputSchema);
                if (parsed.isObject()) {
                    return parsed;
                }
            }
            catch (Exception ignored) {
                // Fall back to an unconstrained object schema when the existing prompt-level schema is not valid JSON.
            }
        }
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", objectMapper.createObjectNode());
        return schema;
    }

    private ModelOutputItem toOutputItem(JsonNode itemNode) {
        String type = text(itemNode.path("type"));
        String id = text(itemNode.path("id"));
        if ("message".equals(type)) {
            String messageText = renderText(itemNode.path("content"));
            if (!messageText.isBlank()) {
                return new ModelAssistantMessageItem(id.isBlank() ? UUID.randomUUID().toString() : id, messageText);
            }
            return null;
        }
        if ("reasoning".equals(type)) {
            String summary = renderText(itemNode.path("summary"));
            String content = renderText(itemNode.path("content"));
            return new ModelReasoningItem(
                    id.isBlank() ? UUID.randomUUID().toString() : id,
                    summary,
                    content);
        }
        if ("function_call".equals(type) || "custom_tool_call".equals(type)) {
            String callId = text(itemNode.path("call_id"));
            String toolName = text(itemNode.path("name"));
            String arguments = text(itemNode.path("arguments"));
            if (arguments.isBlank()) {
                arguments = text(itemNode.path("input"));
            }
            return new ModelToolCallItem(
                    id.isBlank() ? (callId.isBlank() ? UUID.randomUUID().toString() : callId) : id,
                    toolName,
                    arguments);
        }
        if ("function_call_output".equals(type) || "custom_tool_call_output".equals(type)) {
            String callId = text(itemNode.path("call_id"));
            String outputText = itemNode.path("output").isTextual()
                    ? text(itemNode.path("output"))
                    : renderText(itemNode.path("output"));
            return new ModelToolResultItem(
                    id.isBlank() ? (callId.isBlank() ? UUID.randomUUID().toString() : callId) : id,
                    "",
                    outputText,
                    false);
        }
        return null;
    }

    private ModelResponseMetadata metadata(JsonNode root) {
        String responseId = text(root.path("id"));
        String sessionId = text(root.path("conversation").path("id"));
        String finishReason = text(root.path("status"));
        if (finishReason.isBlank()) {
            finishReason = text(root.path("incomplete_details").path("reason"));
        }
        if (finishReason.isBlank()) {
            finishReason = "completed";
        }
        return new ModelResponseMetadata(responseId, sessionId, finishReason);
    }

    private ObjectNode metadata(ModelRequestMetadata metadata) {
        ObjectNode node = objectMapper.createObjectNode();
        if (metadata == null) {
            return node;
        }
        put(node, "codex_thread_id", metadata.threadId());
        put(node, "codex_turn_id", metadata.turnId());
        if (metadata.step() > 0) {
            node.put("codex_step", String.valueOf(metadata.step()));
        }
        put(node, "codex_root_thread_id", metadata.rootThreadId());
        put(node, "codex_parent_thread_id", metadata.parentThreadId());
        put(node, "codex_agent_path", metadata.agentPath());
        if (metadata.agentDepth() != null) {
            node.put("codex_agent_depth", String.valueOf(metadata.agentDepth()));
        }
        put(node, "codex_inherited_from_thread_id", metadata.inheritedFromThreadId());
        put(node, "codex_previous_response_id", metadata.previousResponseId());
        put(node, "codex_provider_session_id", metadata.providerSessionId());
        return node;
    }

    private void put(ObjectNode node, String fieldName, String value) {
        if (value != null && !value.isBlank()) {
            node.put(fieldName, value);
        }
    }

    private String renderText(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return text(node);
        }
        if (!node.isArray()) {
            return text(node.path("text"));
        }
        List<String> parts = new ArrayList<>();
        for (JsonNode element : node) {
            String value = text(element.path("text"));
            if (value.isBlank()) {
                value = text(element.path("output_text"));
            }
            if (value.isBlank() && element.isTextual()) {
                value = text(element);
            }
            if (!value.isBlank()) {
                parts.add(value);
            }
        }
        return String.join(System.lineSeparator(), parts);
    }

    private String role(ModelInputRole role) {
        if (role == null) {
            return "user";
        }
        return switch (role) {
            case SYSTEM -> "system";
            case DEVELOPER -> "developer";
            case USER -> "user";
        };
    }

    private String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        String value = node.asText("");
        return value == null ? "" : value;
    }
}
