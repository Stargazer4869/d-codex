package org.dean.codex.runtime.springai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dean.codex.core.approval.CommandApprovalService;
import org.dean.codex.core.approval.CommandApprovalStore;
import org.dean.codex.core.context.ContextManager;
import org.dean.codex.core.context.ThreadContextReconstructionService;
import org.dean.codex.core.conversation.ConversationStore;
import org.dean.codex.core.history.ThreadHistoryStore;
import org.dean.codex.core.model.ResponsesCompactClient;
import org.dean.codex.core.model.ResponsesModelClient;
import org.dean.codex.core.skill.SkillService;
import org.dean.codex.core.tool.local.CommandApprovalPolicy;
import org.dean.codex.core.tool.local.WebSearchBackend;
import org.dean.codex.core.tool.local.ShellCommandTool;
import org.dean.codex.runtime.springai.approval.DefaultCommandApprovalService;
import org.dean.codex.runtime.springai.context.DefaultThreadContextReconstructionService;
import org.dean.codex.runtime.springai.approval.FileSystemCommandApprovalStore;
import org.dean.codex.runtime.springai.context.FileSystemContextManager;
import org.dean.codex.runtime.springai.context.SpringAiThreadCompactionSummarizer;
import org.dean.codex.runtime.springai.context.ThreadCompactionSummarizer;
import org.dean.codex.runtime.springai.model.ChatClientResponsesCompactClient;
import org.dean.codex.runtime.springai.conversation.FileSystemConversationStore;
import org.dean.codex.runtime.springai.history.FileSystemThreadHistoryStore;
import org.dean.codex.runtime.springai.model.ChatClientResponsesModelClient;
import org.dean.codex.runtime.springai.model.FileSystemThreadModelSessionStateStore;
import org.dean.codex.runtime.springai.model.OpenAiResponsesCompactClient;
import org.dean.codex.runtime.springai.model.OpenAiResponsesModelClient;
import org.dean.codex.runtime.springai.model.OpenAiResponsesSettings;
import org.dean.codex.runtime.springai.model.ThreadModelSessionStateStore;
import org.dean.codex.runtime.springai.prompt.BaseInstructionsResolver;
import org.dean.codex.runtime.springai.prompt.DefaultBaseInstructionsResolver;
import org.dean.codex.runtime.springai.prompt.DefaultPromptAssemblyService;
import org.dean.codex.runtime.springai.prompt.DefaultPromptOutputContractRenderer;
import org.dean.codex.runtime.springai.prompt.DefaultThreadPromptSnapshotResolver;
import org.dean.codex.runtime.springai.prompt.DefaultToolContractPromptRenderer;
import org.dean.codex.runtime.springai.prompt.DefaultToolContractResolver;
import org.dean.codex.runtime.springai.prompt.DefaultUserInstructionsResolver;
import org.dean.codex.runtime.springai.prompt.FileSystemThreadPromptStateStore;
import org.dean.codex.runtime.springai.prompt.PromptAssemblyService;
import org.dean.codex.runtime.springai.prompt.PromptOutputContractRenderer;
import org.dean.codex.runtime.springai.prompt.ThreadPromptSnapshotResolver;
import org.dean.codex.runtime.springai.prompt.ThreadPromptStateStore;
import org.dean.codex.runtime.springai.prompt.ToolContractPromptRenderer;
import org.dean.codex.runtime.springai.prompt.ToolContractResolver;
import org.dean.codex.runtime.springai.prompt.ToolCapabilityRegistry;
import org.dean.codex.runtime.springai.prompt.UserInstructionsResolver;
import org.dean.codex.runtime.springai.skills.FileSystemSkillService;
import org.dean.codex.tools.local.DuckDuckGoHtmlWebSearchBackend;
import org.dean.codex.tools.local.PatternCommandApprovalPolicy;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiConnectionProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(CodexProperties.class)
public class CodexRuntimeSpringAiConfig {

    private static final String DEFAULT_CODEX_HOME_DIRECTORY_NAME = ".d-codex";

    @Bean("codexWorkspaceRoot")
    public Path codexWorkspaceRoot(CodexProperties properties) {
        String configuredRoot = properties.getWorkspaceRoot();
        String resolvedRoot = configuredRoot == null || configuredRoot.isBlank()
                ? System.getProperty("user.dir")
                : configuredRoot;
        return Path.of(resolvedRoot).toAbsolutePath().normalize();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean("codexStorageRoot")
    public Path codexStorageRoot(CodexProperties properties,
                                 @Qualifier("codexWorkspaceRoot") Path workspaceRoot) {
        String configuredRoot = properties.getStorageRoot();
        Path defaultCodexHome = defaultCodexHome();
        Path requestedRoot = configuredRoot == null || configuredRoot.isBlank()
                ? defaultCodexHome
                : Path.of(configuredRoot).toAbsolutePath().normalize();
        try {
            return ensureWritableDirectory(requestedRoot, "Codex storage root");
        }
        catch (IllegalStateException exception) {
            if (!requestedRoot.equals(defaultCodexHome)) {
                throw exception;
            }
            Path workspaceFallback = workspaceRoot.resolve(DEFAULT_CODEX_HOME_DIRECTORY_NAME)
                    .toAbsolutePath()
                    .normalize();
            System.err.printf("[storage] Default Codex home %s is not writable; using %s instead.%n",
                    requestedRoot,
                    workspaceFallback);
            return ensureWritableDirectory(workspaceFallback, "workspace fallback Codex storage root");
        }
    }

    @Bean("codexUserSkillsRoot")
    public Path codexUserSkillsRoot(CodexProperties properties,
                                    @Qualifier("codexStorageRoot") Path storageRoot) {
        String configuredRoot = properties.getSkills().getUserRoot();
        String resolvedRoot = configuredRoot == null || configuredRoot.isBlank()
                ? storageRoot.resolve("skills").toString()
                : configuredRoot;
        return Path.of(resolvedRoot).toAbsolutePath().normalize();
    }

    @Bean
    public ConversationStore conversationStore(@org.springframework.beans.factory.annotation.Qualifier("codexStorageRoot") Path storageRoot) {
        return new FileSystemConversationStore(storageRoot);
    }

    @Bean
    public ThreadHistoryStore threadHistoryStore(ConversationStore conversationStore,
                                                 @org.springframework.beans.factory.annotation.Qualifier("codexStorageRoot") Path storageRoot) {
        return new FileSystemThreadHistoryStore(conversationStore, storageRoot);
    }

    @Bean
    public CommandApprovalStore commandApprovalStore(@org.springframework.beans.factory.annotation.Qualifier("codexStorageRoot") Path storageRoot) {
        return new FileSystemCommandApprovalStore(storageRoot);
    }

    @Bean
    public SkillService skillService(@org.springframework.beans.factory.annotation.Qualifier("codexWorkspaceRoot") Path workspaceRoot,
                                     @org.springframework.beans.factory.annotation.Qualifier("codexUserSkillsRoot") Path userSkillsRoot,
                                     CodexProperties properties) {
        String workspaceRelativeRoot = properties.getSkills().getWorkspaceRelativeRoot();
        Path workspaceSkillsRoot = workspaceRelativeRoot == null || workspaceRelativeRoot.isBlank()
                ? workspaceRoot.resolve(".codex/skills")
                : workspaceRoot.resolve(workspaceRelativeRoot);
        return new FileSystemSkillService(
                properties.getSkills().isEnabled(),
                workspaceSkillsRoot,
                userSkillsRoot);
    }

    @Bean
    public ResponsesCompactClient responsesCompactClient(ChatClient.Builder chatClientBuilder,
                                                         ObjectMapper objectMapper,
                                                         OpenAiConnectionProperties openAiConnectionProperties,
                                                         OpenAiChatProperties openAiChatProperties,
                                                         CodexProperties properties) {
        return switch (ModelTransportMode.from(properties.getModel().getTransportMode())) {
            case CHAT_FALLBACK -> new ChatClientResponsesCompactClient(chatClientBuilder);
            case RESPONSES_HTTP -> new OpenAiResponsesCompactClient(
                    responsesHttpClient(),
                    objectMapper,
                    responsesSettings(openAiConnectionProperties, openAiChatProperties, properties));
        };
    }

    @Bean
    public ThreadCompactionSummarizer threadCompactionSummarizer(ResponsesCompactClient responsesCompactClient) {
        return new SpringAiThreadCompactionSummarizer(responsesCompactClient);
    }

    @Bean
    public ResponsesModelClient responsesModelClient(ChatClient.Builder chatClientBuilder,
                                                     ObjectMapper objectMapper,
                                                     OpenAiConnectionProperties openAiConnectionProperties,
                                                     OpenAiChatProperties openAiChatProperties,
                                                     CodexProperties properties) {
        return switch (ModelTransportMode.from(properties.getModel().getTransportMode())) {
            case CHAT_FALLBACK -> new ChatClientResponsesModelClient(chatClientBuilder);
            case RESPONSES_HTTP -> new OpenAiResponsesModelClient(
                    responsesHttpClient(),
                    objectMapper,
                    responsesSettings(openAiConnectionProperties, openAiChatProperties, properties));
        };
    }

    @Bean
    public ThreadModelSessionStateStore threadModelSessionStateStore(ConversationStore conversationStore,
                                                                     @org.springframework.beans.factory.annotation.Qualifier("codexStorageRoot") Path storageRoot) {
        return new FileSystemThreadModelSessionStateStore(conversationStore, storageRoot);
    }

    @Bean
    public BaseInstructionsResolver baseInstructionsResolver(@org.springframework.beans.factory.annotation.Qualifier("codexWorkspaceRoot") Path workspaceRoot,
                                                             CodexProperties properties) {
        return new DefaultBaseInstructionsResolver(
                workspaceRoot,
                properties.getPrompt().getBaseInstructionsText(),
                properties.getPrompt().getBaseInstructionsFile());
    }

    @Bean
    public PromptOutputContractRenderer promptOutputContractRenderer() {
        return new DefaultPromptOutputContractRenderer();
    }

    @Bean
    public UserInstructionsResolver userInstructionsResolver(@org.springframework.beans.factory.annotation.Qualifier("codexWorkspaceRoot") Path workspaceRoot,
                                                             CodexProperties properties) {
        return new DefaultUserInstructionsResolver(
                workspaceRoot,
                properties.getPrompt().getProjectDocMaxBytes(),
                properties.getPrompt().getProjectInstructionsText(),
                properties.getPrompt().getProjectInstructionsFile(),
                properties.getPrompt().getUserInstructionsText(),
                properties.getPrompt().getUserInstructionsFile());
    }

    @Bean
    public ThreadPromptSnapshotResolver threadPromptSnapshotResolver(BaseInstructionsResolver baseInstructionsResolver,
                                                                    UserInstructionsResolver userInstructionsResolver) {
        return new DefaultThreadPromptSnapshotResolver(baseInstructionsResolver, userInstructionsResolver);
    }

    @Bean
    public ThreadPromptStateStore threadPromptStateStore(ConversationStore conversationStore,
                                                         @org.springframework.beans.factory.annotation.Qualifier("codexStorageRoot") Path storageRoot) {
        return new FileSystemThreadPromptStateStore(conversationStore, storageRoot);
    }

    @Bean
    public ToolContractResolver toolContractResolver(ToolCapabilityRegistry toolCapabilityRegistry) {
        return new DefaultToolContractResolver(toolCapabilityRegistry);
    }

    @Bean
    public ToolContractPromptRenderer toolContractPromptRenderer() {
        return new DefaultToolContractPromptRenderer();
    }

    @Bean
    public PromptAssemblyService promptAssemblyService(BaseInstructionsResolver baseInstructionsResolver,
                                                       UserInstructionsResolver userInstructionsResolver,
                                                       ThreadPromptStateStore threadPromptStateStore,
                                                       ToolContractResolver toolContractResolver,
                                                       ToolContractPromptRenderer toolContractPromptRenderer,
                                                       PromptOutputContractRenderer promptOutputContractRenderer,
                                                        CodexProperties properties) {
        return new DefaultPromptAssemblyService(
                baseInstructionsResolver,
                userInstructionsResolver,
                threadPromptStateStore,
                toolContractResolver,
                toolContractPromptRenderer,
                promptOutputContractRenderer,
                properties.getAgent().getMaxSteps(),
                properties.getAgent().getMaxActionsPerStep());
    }

    @Bean
    public ContextManager contextManager(ConversationStore conversationStore,
                                         ThreadHistoryStore threadHistoryStore,
                                         ThreadCompactionSummarizer threadCompactionSummarizer,
                                         @org.springframework.beans.factory.annotation.Qualifier("codexStorageRoot") Path storageRoot,
                                         CodexProperties properties) {
        return new FileSystemContextManager(
                conversationStore,
                threadHistoryStore,
                threadCompactionSummarizer,
                storageRoot,
                properties.getContext().getPreserveRecentTurns());
    }

    @Bean
    public ThreadContextReconstructionService threadContextReconstructionService(ConversationStore conversationStore,
                                                                                 ThreadHistoryStore threadHistoryStore,
                                                                                 ContextManager contextManager,
                                                                                 CodexProperties properties) {
        return new DefaultThreadContextReconstructionService(
                conversationStore,
                threadHistoryStore,
                contextManager,
                properties.getAgent().getHistoryWindow());
    }

    @Bean
    public CommandApprovalService commandApprovalService(CommandApprovalStore commandApprovalStore,
                                                         ConversationStore conversationStore,
                                                         ThreadHistoryStore threadHistoryStore,
                                                         ShellCommandTool shellCommandTool) {
        return new DefaultCommandApprovalService(commandApprovalStore, conversationStore, threadHistoryStore, shellCommandTool);
    }

    @Bean
    public CommandApprovalPolicy commandApprovalPolicy(CodexProperties properties) {
        return new PatternCommandApprovalPolicy(
                PatternCommandApprovalPolicy.Mode.from(properties.getShell().getApprovalMode()));
    }

    @Bean
    public WebSearchBackend webSearchBackend() {
        return new DuckDuckGoHtmlWebSearchBackend(
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build(),
                URI.create("https://html.duckduckgo.com/html/"));
    }

    @Bean("codexCommandTimeout")
    public Duration codexCommandTimeout(CodexProperties properties) {
        return Duration.ofSeconds(Math.max(1, properties.getShell().getTimeoutSeconds()));
    }

    private Path defaultCodexHome() {
        return Path.of(System.getProperty("user.home"), DEFAULT_CODEX_HOME_DIRECTORY_NAME)
                .toAbsolutePath()
                .normalize();
    }

    private HttpClient responsesHttpClient() {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    private OpenAiResponsesSettings responsesSettings(OpenAiConnectionProperties openAiConnectionProperties,
                                                      OpenAiChatProperties openAiChatProperties,
                                                      CodexProperties properties) {
        return new OpenAiResponsesSettings(
                openAiConnectionProperties.getBaseUrl(),
                responsesPath(openAiConnectionProperties.getBaseUrl(), openAiChatProperties.getCompletionsPath()),
                openAiConnectionProperties.getApiKey(),
                openAiConnectionProperties.getOrganizationId(),
                openAiConnectionProperties.getProjectId(),
                openAiChatProperties.getOptions() == null ? "" : openAiChatProperties.getOptions().getModel(),
                openAiChatProperties.getOptions() == null ? null : openAiChatProperties.getOptions().getTemperature(),
                openAiChatProperties.getOptions() == null ? null : openAiChatProperties.getOptions().getTopP(),
                properties.getModel().isResponsesStore(),
                properties.getModel().isResponsesEmitTools());
    }

    String responsesPath(String baseUrl, String completionsPath) {
        String normalizedCompletionsPath = completionsPath == null ? "" : completionsPath.trim();
        if (!normalizedCompletionsPath.isBlank()) {
            if (normalizedCompletionsPath.endsWith("/chat/completions")) {
                return normalizedCompletionsPath.substring(0,
                        normalizedCompletionsPath.length() - "/chat/completions".length()) + "/responses";
            }
            if (normalizedCompletionsPath.endsWith("chat/completions")) {
                return normalizedCompletionsPath.substring(0,
                        normalizedCompletionsPath.length() - "chat/completions".length()) + "responses";
            }
        }
        String normalizedBaseUrl = baseUrl == null ? "" : baseUrl.trim();
        return normalizedBaseUrl.endsWith("/v1") ? "/responses" : "/v1/responses";
    }

    private Path ensureWritableDirectory(Path path, String label) {
        try {
            Files.createDirectories(path);
        }
        catch (IOException exception) {
            throw new IllegalStateException("Unable to initialize " + label + " at " + path, exception);
        }
        if (!Files.isDirectory(path)) {
            throw new IllegalStateException(label + " is not a directory: " + path);
        }
        if (!Files.isWritable(path)) {
            throw new IllegalStateException(label + " is not writable: " + path);
        }
        return path;
    }
}
