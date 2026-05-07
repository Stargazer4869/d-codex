package org.dean.codex.runtime.springai.prompt;

import org.dean.codex.core.skill.ResolvedSkill;
import org.dean.codex.protocol.context.ReconstructedThreadContext;
import org.dean.codex.protocol.context.ReconstructedTurnActivity;
import org.dean.codex.protocol.conversation.ConversationMessage;
import org.dean.codex.protocol.conversation.ThreadId;
import org.dean.codex.protocol.skill.SkillMetadata;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class DefaultPromptAssemblyService implements PromptAssemblyService {
    private final BaseInstructionsResolver baseInstructionsResolver;
    private final UserInstructionsResolver userInstructionsResolver;
    private final ThreadPromptStateStore threadPromptStateStore;
    private final ToolContractResolver toolContractResolver;
    private final ToolContractPromptRenderer toolContractPromptRenderer;
    private final PromptOutputContractRenderer outputContractRenderer;
    private final int maxSteps;
    private final int maxActionsPerStep;

    public DefaultPromptAssemblyService(Path workspaceRoot, int maxSteps, int maxActionsPerStep) {
        this(new DefaultBaseInstructionsResolver(workspaceRoot),
                new DefaultUserInstructionsResolver(workspaceRoot),
                null,
                new DefaultToolContractResolver(),
                new DefaultToolContractPromptRenderer(),
                new DefaultPromptOutputContractRenderer(),
                maxSteps,
                maxActionsPerStep);
    }

    public DefaultPromptAssemblyService(BaseInstructionsResolver baseInstructionsResolver,
                                        UserInstructionsResolver userInstructionsResolver,
                                        ToolContractResolver toolContractResolver,
                                        ToolContractPromptRenderer toolContractPromptRenderer,
                                        PromptOutputContractRenderer outputContractRenderer,
                                        int maxSteps,
                                        int maxActionsPerStep) {
        this(baseInstructionsResolver,
                userInstructionsResolver,
                null,
                toolContractResolver,
                toolContractPromptRenderer,
                outputContractRenderer,
                maxSteps,
                maxActionsPerStep);
    }

    public DefaultPromptAssemblyService(BaseInstructionsResolver baseInstructionsResolver,
                                        UserInstructionsResolver userInstructionsResolver,
                                        ThreadPromptStateStore threadPromptStateStore,
                                        ToolContractResolver toolContractResolver,
                                        ToolContractPromptRenderer toolContractPromptRenderer,
                                        PromptOutputContractRenderer outputContractRenderer,
                                        int maxSteps,
                                        int maxActionsPerStep) {
        this.baseInstructionsResolver = baseInstructionsResolver;
        this.userInstructionsResolver = userInstructionsResolver == null ? List::of : userInstructionsResolver;
        this.threadPromptStateStore = threadPromptStateStore;
        this.toolContractResolver = toolContractResolver;
        this.toolContractPromptRenderer = toolContractPromptRenderer;
        this.outputContractRenderer = outputContractRenderer;
        this.maxSteps = Math.max(1, maxSteps);
        this.maxActionsPerStep = Math.max(1, maxActionsPerStep);
    }

    @Override
    public ResolvedPrompt assemblePlannerPrompt(ReconstructedThreadContext reconstructedContext,
                                                String input,
                                                String scratchpad,
                                                int step,
                                                List<ResolvedSkill> selectedSkills,
                                                List<SkillMetadata> availableSkills,
                                                List<String> steeringInputs,
                                                List<PromptExecSessionContext> activeExecSessions) {
        ThreadId threadId = reconstructedContext == null ? null : reconstructedContext.threadId();
        ResolvedPromptInstructions instructions = resolveInstructions(threadId, availableSkills);
        ResolvedToolContract toolContract = resolveToolContract();
        ResolvedPromptOutputContract outputContract = resolveOutputContract(toolContract);
        ResolvedPromptContext context = resolveContext(
                reconstructedContext,
                input,
                scratchpad,
                step,
                selectedSkills,
                steeringInputs,
                activeExecSessions);
        return new ResolvedPrompt(
                instructions,
                toolContract,
                context,
                outputContract,
                renderSystemPrompt(instructions, toolContract, outputContract),
                renderUserPrompt(context));
    }

    @Override
    public String buildSystemPrompt(List<SkillMetadata> availableSkills) {
        ResolvedToolContract toolContract = resolveToolContract();
        return renderSystemPrompt(resolveInstructions(null, availableSkills), toolContract, resolveOutputContract(toolContract));
    }

    @Override
    public String buildUserPrompt(ReconstructedThreadContext reconstructedContext,
                                  String input,
                                  String scratchpad,
                                  int step,
                                  List<ResolvedSkill> selectedSkills,
                                  List<String> steeringInputs,
                                  List<PromptExecSessionContext> activeExecSessions) {
        return renderUserPrompt(resolveContext(
                reconstructedContext,
                input,
                scratchpad,
                step,
                selectedSkills,
                steeringInputs,
                activeExecSessions));
    }

    private ResolvedPromptInstructions resolveInstructions(ThreadId threadId, List<SkillMetadata> availableSkills) {
        ThreadPromptSnapshot snapshot = resolvePersistedSnapshot(threadId);
        String baseText = snapshot == null ? baseInstructionsResolver.resolveBaseInstructions() : snapshot.baseInstructions();
        List<String> developerSections = availableSkills == null || availableSkills.isEmpty()
                ? List.of()
                : List.of("""
                Available skills:
                %s

                Skills are selected explicitly by user input, usually with `$skill-name`.
                When a skill is selected, its instructions are injected into the turn context.
                """.formatted(renderAvailableSkills(availableSkills)));
        List<String> userSections = snapshot == null ? userInstructionsResolver.resolveUserInstructions() : snapshot.userInstructions();
        return new ResolvedPromptInstructions(baseText, developerSections, userSections);
    }

    private ResolvedToolContract resolveToolContract() {
        return toolContractResolver.resolvePlannerToolContract();
    }

    private ResolvedPromptOutputContract resolveOutputContract(ResolvedToolContract toolContract) {
        return new ResolvedPromptOutputContract(
                "json",
                renderOutputSchema(toolContract),
                List.of(
                        "Return JSON only. Do not wrap it in prose.",
                        "Return either a non-empty actions array or finalAnswer.",
                        "Do not return more than %d actions in one step.".formatted(maxActionsPerStep),
                        "Actions are executed in the order you provide them.",
                        "Independent read-only actions may be batched together; keep writes, shell commands, approvals, and sub-agent control separate.",
                        "Omit unused fields or set them to null.",
                        "Prefer LIST_DIR to discover directory structure before reading full files.",
                        "Prefer WEB_SEARCH for external documentation, ecosystem facts, or recent references when local search is not enough.",
                        "Prefer SEARCH_FILES to locate code before reading full files.",
                        "Prefer APPLY_PATCH for targeted edits and WRITE_FILE only for new files or full rewrites.",
                        "Include editPlan whenever you expect to modify files.",
                        "Read an existing file before editing it.",
                        "Use agent delegation when a task is clearer as a focused sub-task than as a local edit batch.",
                        "After spawning a sub-agent, prefer wait_agent to observe progress instead of immediately reassigning or resuming it.",
                        "If wait_agent returns timedOut=true while the agent status is RUNNING, WAITING, or PENDING_INIT, the agent is still working. Wait again rather than duplicating the task.",
                        "If wait_agent or a MAILBOX message provides a non-empty finalAnswer from a sub-agent, treat that as the delegated result and continue or answer with it.",
                        "If a recent MAILBOX child-completion message already answers the delegated task, do not call wait_agent again for the same result.",
                        "Once you have enough evidence to answer the user's request, stop exploring and provide the answer.",
                        "For repository overviews and summaries, inspect a bounded set of the most relevant files instead of exhaustively traversing the tree.",
                        "Prefer inspection, tests, and small verified edits.",
                        "Shell commands may be allowed, require approval, or be blocked. If a command is not executed, use the tool result to adapt.",
                        "Prefer exec_command and write_stdin for long-running commands that may need repeated polling or stdin.",
                        "Avoid destructive shell commands.",
                        "Keep all paths relative to the workspace root.",
                        "After each batch, use the observation from all executed actions before deciding the next step."),
                maxActionsPerStep);
    }

    private ResolvedPromptContext resolveContext(ReconstructedThreadContext reconstructedContext,
                                                 String input,
                                                 String scratchpad,
                                                 int step,
                                                 List<ResolvedSkill> selectedSkills,
                                                 List<String> steeringInputs,
                                                 List<PromptExecSessionContext> activeExecSessions) {
        ThreadId threadId = reconstructedContext == null ? null : reconstructedContext.threadId();
        List<ConversationMessage> recentMessages = reconstructedContext == null ? List.of() : reconstructedContext.recentMessages();
        List<ReconstructedTurnActivity> recentActivities = reconstructedContext == null ? List.of() : reconstructedContext.recentActivities();
        return new ResolvedPromptContext(
                threadId,
                recentMessages,
                recentActivities,
                activeExecSessions,
                input,
                scratchpad,
                step,
                maxSteps,
                selectedSkills,
                steeringInputs);
    }

    private String renderSystemPrompt(ResolvedPromptInstructions instructions,
                                      ResolvedToolContract toolContract,
                                      ResolvedPromptOutputContract outputContract) {
        String lineSeparator = System.lineSeparator();
        StringBuilder prompt = new StringBuilder();
        prompt.append(instructions.baseText());
        prompt.append(lineSeparator).append(lineSeparator);
        prompt.append(toolContractPromptRenderer.render(toolContract));
        if (!toolContract.supplementaryInstructions().isEmpty()) {
            prompt.append(lineSeparator);
            prompt.append(lineSeparator);
            prompt.append(String.join(lineSeparator + lineSeparator, toolContract.supplementaryInstructions()));
        }
        prompt.append(lineSeparator).append(lineSeparator);
        prompt.append(outputContractRenderer.render(outputContract));
        if (!instructions.developerSections().isEmpty()) {
            prompt.append(lineSeparator);
            prompt.append(String.join(lineSeparator + lineSeparator, instructions.developerSections()));
        }
        if (!instructions.userSections().isEmpty()) {
            prompt.append(lineSeparator).append(lineSeparator);
            prompt.append(String.join(lineSeparator + lineSeparator, instructions.userSections()));
        }
        return prompt.toString().stripTrailing();
    }

    private String renderUserPrompt(ResolvedPromptContext context) {
        String historyBlock = context.recentMessages().stream()
                .map(message -> message.role().name() + ": " + message.content())
                .collect(Collectors.joining(System.lineSeparator()));
        String eventBlock = context.recentActivities().stream()
                .map(activity -> renderActivity(context.threadId(), activity))
                .collect(Collectors.joining(System.lineSeparator()));
        String execSessionBlock = renderActiveExecSessions(context.activeExecSessions());

        return """
                Planner step number: %d of %d

                Active thread:
                %s

                Recent conversation:
                %s

                Recent turn events:
                %s

                Active exec sessions:
                %s

                Selected skill instructions:
                %s

                Steering requests since last step:
                %s

                Latest user request:
                %s

                Scratchpad so far:
                %s

                Choose the next tool batch and return JSON only.
                """.formatted(
                context.step(),
                context.maxSteps(),
                context.threadId() == null ? "(none)" : context.threadId().value(),
                historyBlock.isBlank() ? "(none)" : historyBlock,
                eventBlock.isBlank() ? "(none)" : eventBlock,
                execSessionBlock,
                renderSelectedSkills(context.selectedSkills()),
                context.steeringInputs().isEmpty() ? "(none)" : String.join(System.lineSeparator(), context.steeringInputs()),
                context.latestUserRequest(),
                context.scratchpad().isBlank() ? "(none yet)" : context.scratchpad());
    }

    private String renderActivity(ThreadId threadId, ReconstructedTurnActivity activity) {
        String threadValue = threadId == null || threadId.value() == null || threadId.value().isBlank()
                ? "(none)"
                : threadId.value();
        String turnValue = activity == null || activity.turnId() == null || activity.turnId().value() == null || activity.turnId().value().isBlank()
                ? "(none)"
                : activity.turnId().value();
        return threadValue + "/" + turnValue + " "
                + blankToPlaceholder(activity == null ? null : activity.sourceType())
                + ": "
                + blankToPlaceholder(activity == null ? null : activity.detail());
    }

    private String renderAvailableSkills(List<SkillMetadata> availableSkills) {
        return availableSkills.stream()
                .map(skill -> "- %s: %s (file: %s)"
                        .formatted(skill.name(), blankToPlaceholder(skill.shortDescription()), skill.path()))
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private String renderSelectedSkills(List<ResolvedSkill> selectedSkills) {
        if (selectedSkills == null || selectedSkills.isEmpty()) {
            return "(none)";
        }
        return selectedSkills.stream()
                .map(skill -> """
                        Skill: %s
                        Path: %s
                        Instructions:
                        %s
                        """.formatted(
                        skill.metadata().name(),
                        skill.metadata().path(),
                        skill.instructions().trim()))
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private String renderActiveExecSessions(List<PromptExecSessionContext> activeExecSessions) {
        if (activeExecSessions == null || activeExecSessions.isEmpty()) {
            return "(none)";
        }
        return activeExecSessions.stream()
                .map(session -> """
                        - sessionId: %s
                          status: %s
                          command: %s
                          processId: %s
                          pty: %s
                          pollCount: %d
                          latest stdout: %s
                          latest stderr: %s
                        """.formatted(
                        blankToPlaceholder(session.sessionId()),
                        blankToPlaceholder(session.status()),
                        blankToPlaceholder(session.command()),
                        session.processId() == null ? "(none)" : session.processId(),
                        session.pty(),
                        session.pollCount(),
                        blankToPlaceholder(session.stdoutPreview()),
                        blankToPlaceholder(session.stderrPreview())))
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private String blankToPlaceholder(String value) {
        return value == null || value.isBlank() ? "(none)" : value;
    }

    private ThreadPromptSnapshot resolvePersistedSnapshot(ThreadId threadId) {
        if (threadId == null || threadPromptStateStore == null) {
            return null;
        }
        return threadPromptStateStore.read(threadId).orElse(null);
    }

    private String renderOutputSchema(ResolvedToolContract toolContract) {
        String actionBlocks = toolContract.plannerActionSchemaFragments().stream()
                .collect(Collectors.joining("," + System.lineSeparator()));
        return """
                  {
                    "summary": "brief summary of the next step",
                    "editPlan": {
                      "summary": "optional edit plan for intended file changes",
                      "edits": [
                        {
                          "path": "relative/path",
                          "type": "CREATE | MODIFY | DELETE | VERIFY",
                          "description": "what you intend to change"
                        }
                      ]
                    },
                    "actions": [
                %s
                    ],
                    "finalAnswer": "final answer when the task is complete"
                  }""".formatted(indentActionBlocks(actionBlocks));
    }

    private String indentActionBlocks(String actionBlocks) {
        if (actionBlocks == null || actionBlocks.isBlank()) {
            return "      ";
        }
        return actionBlocks.lines()
                .map(line -> "      " + line)
                .collect(Collectors.joining(System.lineSeparator()));
    }
}
