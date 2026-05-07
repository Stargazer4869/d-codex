package org.dean.codex.runtime.springai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dean.codex.core.model.ResponsesCompactClient;
import org.dean.codex.core.model.ResponsesModelClient;
import org.dean.codex.runtime.springai.model.ChatClientResponsesCompactClient;
import org.dean.codex.runtime.springai.model.ChatClientResponsesModelClient;
import org.dean.codex.runtime.springai.model.OpenAiResponsesCompactClient;
import org.dean.codex.runtime.springai.model.OpenAiResponsesModelClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiConnectionProperties;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;

class CodexRuntimeSpringAiConfigTest {

    private final CodexRuntimeSpringAiConfig config = new CodexRuntimeSpringAiConfig();

    @Test
    void storageRootDefaultsToUserHomeDcodex(@TempDir Path tempDir) throws Exception {
        Path userHome = Files.createDirectories(tempDir.resolve("home"));
        Path workspaceRoot = Files.createDirectories(tempDir.resolve("workspace"));
        CodexProperties properties = new CodexProperties();

        Path storageRoot = withUserHome(userHome, () -> config.codexStorageRoot(properties, workspaceRoot));

        assertEquals(userHome.resolve(".d-codex").toAbsolutePath().normalize(), storageRoot);
        assertTrue(Files.isDirectory(storageRoot));
    }

    @Test
    void storageRootFallsBackToWorkspaceWhenDefaultHomeCannotBeCreated(@TempDir Path tempDir) throws Exception {
        Path invalidHome = tempDir.resolve("home-file");
        Files.writeString(invalidHome, "not-a-directory");
        Path workspaceRoot = Files.createDirectories(tempDir.resolve("workspace"));
        CodexProperties properties = new CodexProperties();

        Path storageRoot = withUserHome(invalidHome, () -> config.codexStorageRoot(properties, workspaceRoot));

        assertEquals(workspaceRoot.resolve(".d-codex").toAbsolutePath().normalize(), storageRoot);
        assertTrue(Files.isDirectory(storageRoot));
    }

    @Test
    void explicitCustomStorageRootStillFailsWhenItCannotBeCreated(@TempDir Path tempDir) throws Exception {
        Path userHome = Files.createDirectories(tempDir.resolve("home"));
        Path invalidParent = tempDir.resolve("invalid-parent");
        Files.writeString(invalidParent, "not-a-directory");
        Path workspaceRoot = Files.createDirectories(tempDir.resolve("workspace"));
        CodexProperties properties = new CodexProperties();
        properties.setStorageRoot(invalidParent.resolve("custom-storage").toString());

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> withUserHome(userHome, () -> config.codexStorageRoot(properties, workspaceRoot)));

        assertTrue(exception.getMessage().contains("Unable to initialize Codex storage root"));
    }

    @Test
    void userSkillsRootDefaultsUnderResolvedStorageRoot(@TempDir Path tempDir) {
        CodexProperties properties = new CodexProperties();
        Path storageRoot = tempDir.resolve("storage-root");

        Path userSkillsRoot = config.codexUserSkillsRoot(properties, storageRoot);

        assertEquals(storageRoot.resolve("skills").toAbsolutePath().normalize(), userSkillsRoot);
    }

    @Test
    void responsesHttpTransportSelectsNativeResponsesClients() {
        CodexProperties properties = new CodexProperties();
        properties.getModel().setTransportMode("responses-http");
        ChatClient.Builder chatClientBuilder = chatClientBuilder();

        ResponsesModelClient modelClient = config.responsesModelClient(
                chatClientBuilder,
                new ObjectMapper(),
                openAiConnectionProperties(),
                openAiChatProperties(),
                properties);
        ResponsesCompactClient compactClient = config.responsesCompactClient(
                chatClientBuilder,
                new ObjectMapper(),
                openAiConnectionProperties(),
                openAiChatProperties(),
                properties);

        assertInstanceOf(OpenAiResponsesModelClient.class, modelClient);
        assertInstanceOf(OpenAiResponsesCompactClient.class, compactClient);
    }

    @Test
    void chatFallbackTransportRemainsDefault() {
        CodexProperties properties = new CodexProperties();
        ChatClient.Builder chatClientBuilder = chatClientBuilder();

        ResponsesModelClient modelClient = config.responsesModelClient(
                chatClientBuilder,
                new ObjectMapper(),
                openAiConnectionProperties(),
                openAiChatProperties(),
                properties);
        ResponsesCompactClient compactClient = config.responsesCompactClient(
                chatClientBuilder,
                new ObjectMapper(),
                openAiConnectionProperties(),
                openAiChatProperties(),
                properties);

        assertInstanceOf(ChatClientResponsesModelClient.class, modelClient);
        assertInstanceOf(ChatClientResponsesCompactClient.class, compactClient);
    }

    @Test
    void invalidTransportModeFailsFast() {
        CodexProperties properties = new CodexProperties();
        properties.getModel().setTransportMode("not-real");
        ChatClient.Builder chatClientBuilder = chatClientBuilder();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> config.responsesModelClient(
                        chatClientBuilder,
                        new ObjectMapper(),
                        openAiConnectionProperties(),
                        openAiChatProperties(),
                        properties));

        assertTrue(exception.getMessage().contains("Unsupported codex.model.transport-mode"));
    }

    @Test
    void responsesHttpTransportUsesV1ResponsesPathForProxyStyleBaseUrl() {
        assertEquals("/v1/responses",
                config.responsesPath("https://proxy.example.com/openai", "/v1/chat/completions"));
        assertEquals("/responses",
                config.responsesPath("https://api.openai.com/v1", "/chat/completions"));
    }

    private <T> T withUserHome(Path userHome, ThrowingSupplier<T> supplier) throws Exception {
        String originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", userHome.toString());
        try {
            return supplier.get();
        }
        finally {
            if (originalUserHome == null) {
                System.clearProperty("user.home");
            }
            else {
                System.setProperty("user.home", originalUserHome);
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private OpenAiChatProperties openAiChatProperties() {
        OpenAiChatProperties properties = new OpenAiChatProperties();
        properties.getOptions().setModel("gpt-5.4");
        properties.getOptions().setTemperature(0.2);
        return properties;
    }

    private OpenAiConnectionProperties openAiConnectionProperties() {
        OpenAiConnectionProperties properties = new OpenAiConnectionProperties();
        properties.setBaseUrl("https://api.openai.com/v1");
        properties.setApiKey("test-key");
        return properties;
    }

    private ChatClient.Builder chatClientBuilder() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class, RETURNS_SELF);
        org.springframework.ai.chat.client.ChatClient chatClient = mock(org.springframework.ai.chat.client.ChatClient.class);
        org.springframework.ai.chat.client.ChatClient.Builder clonedBuilder = mock(org.springframework.ai.chat.client.ChatClient.Builder.class, RETURNS_SELF);
        org.mockito.Mockito.when(builder.clone()).thenReturn(clonedBuilder);
        org.mockito.Mockito.when(clonedBuilder.build()).thenReturn(chatClient);
        return builder;
    }
}
