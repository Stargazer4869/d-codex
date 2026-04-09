package org.dean.codex.runtime.springai.config;

import org.dean.codex.core.approval.CommandApprovalService;
import org.dean.codex.core.approval.CommandApprovalStore;
import org.dean.codex.core.context.ContextManager;
import org.dean.codex.core.context.ThreadContextReconstructionService;
import org.dean.codex.core.conversation.ConversationStore;
import org.dean.codex.core.history.ThreadHistoryStore;
import org.dean.codex.core.skill.SkillService;
import org.dean.codex.core.tool.local.CommandApprovalPolicy;
import org.dean.codex.core.tool.local.ShellCommandTool;
import org.dean.codex.runtime.springai.approval.DefaultCommandApprovalService;
import org.dean.codex.runtime.springai.context.DefaultThreadContextReconstructionService;
import org.dean.codex.runtime.springai.approval.FileSystemCommandApprovalStore;
import org.dean.codex.runtime.springai.context.FileSystemContextManager;
import org.dean.codex.runtime.springai.context.SpringAiThreadCompactionSummarizer;
import org.dean.codex.runtime.springai.context.ThreadCompactionSummarizer;
import org.dean.codex.runtime.springai.conversation.FileSystemConversationStore;
import org.dean.codex.runtime.springai.history.FileSystemThreadHistoryStore;
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
import org.dean.codex.runtime.springai.prompt.UserInstructionsResolver;
import org.dean.codex.runtime.springai.skills.FileSystemSkillService;
import org.dean.codex.tools.local.PatternCommandApprovalPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.chat.client.ChatClient;

import java.nio.file.Path;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(CodexProperties.class)
public class CodexRuntimeSpringAiConfig {

    @Bean("codexWorkspaceRoot")
    public Path codexWorkspaceRoot(CodexProperties properties) {
        String configuredRoot = properties.getWorkspaceRoot();
        String resolvedRoot = configuredRoot == null || configuredRoot.isBlank()
                ? System.getProperty("user.dir")
                : configuredRoot;
        return Path.of(resolvedRoot).toAbsolutePath().normalize();
    }

    @Bean("codexStorageRoot")
    public Path codexStorageRoot(CodexProperties properties) {
        String configuredRoot = properties.getStorageRoot();
        String resolvedRoot = configuredRoot == null || configuredRoot.isBlank()
                ? Path.of(System.getProperty("user.home"), ".codex-java").toString()
                : configuredRoot;
        return Path.of(resolvedRoot).toAbsolutePath().normalize();
    }

    @Bean("codexUserSkillsRoot")
    public Path codexUserSkillsRoot(CodexProperties properties) {
        String configuredRoot = properties.getSkills().getUserRoot();
        String resolvedRoot = configuredRoot == null || configuredRoot.isBlank()
                ? Path.of(System.getProperty("user.home"), ".codex", "skills").toString()
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
    public ThreadCompactionSummarizer threadCompactionSummarizer(ChatClient.Builder chatClientBuilder) {
        return new SpringAiThreadCompactionSummarizer(chatClientBuilder);
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
    public ToolContractResolver toolContractResolver() {
        return new DefaultToolContractResolver();
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

    @Bean("codexCommandTimeout")
    public Duration codexCommandTimeout(CodexProperties properties) {
        return Duration.ofSeconds(Math.max(1, properties.getShell().getTimeoutSeconds()));
    }
}
