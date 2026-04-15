package org.dean.codex.runtime.springai.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.dean.codex.core.agent.AgentControl;
import org.dean.codex.core.approval.CommandApprovalService;
import org.dean.codex.core.agent.CodexAgent;
import org.dean.codex.core.agent.TurnControl;
import org.dean.codex.core.context.ContextManager;
import org.dean.codex.core.context.ThreadContextReconstructionService;
import org.dean.codex.core.model.InputTextItem;
import org.dean.codex.core.model.ModelAssistantMessageItem;
import org.dean.codex.core.model.ModelInputItem;
import org.dean.codex.core.model.ModelInputRole;
import org.dean.codex.core.model.ModelRequestMetadata;
import org.dean.codex.core.model.ModelRequest;
import org.dean.codex.core.model.ModelReasoningConfig;
import org.dean.codex.core.model.ModelResponse;
import org.dean.codex.core.model.ModelOutputItem;
import org.dean.codex.core.model.ModelReasoningItem;
import org.dean.codex.core.model.ModelToolCallItem;
import org.dean.codex.core.model.ModelToolKind;
import org.dean.codex.core.model.ModelToolResultItem;
import org.dean.codex.core.model.ModelToolSpec;
import org.dean.codex.core.model.ResponsesModelClient;
import org.dean.codex.core.skill.ResolvedSkill;
import org.dean.codex.core.skill.SkillService;
import org.dean.codex.core.tool.local.FilePatchTool;
import org.dean.codex.core.tool.local.FileReaderTool;
import org.dean.codex.core.tool.local.FileSearchTool;
import org.dean.codex.core.tool.local.ListDirTool;
import org.dean.codex.core.tool.local.ExecCommandTool;
import org.dean.codex.core.tool.local.FileWriterTool;
import org.dean.codex.core.tool.local.ShellCommandTool;
import org.dean.codex.core.tool.local.WebSearchTool;
import org.dean.codex.protocol.planning.EditPlan;
import org.dean.codex.protocol.planning.PlannedEdit;
import org.dean.codex.protocol.planning.PlannedEditType;
import org.dean.codex.protocol.conversation.ItemId;
import org.dean.codex.protocol.conversation.ThreadId;
import org.dean.codex.protocol.conversation.ThreadSummary;
import org.dean.codex.protocol.conversation.TurnId;
import org.dean.codex.protocol.conversation.TurnStatus;
import org.dean.codex.protocol.event.CodexTurnResult;
import org.dean.codex.protocol.event.TurnEvent;
import org.dean.codex.protocol.agent.AgentMessage;
import org.dean.codex.protocol.agent.AgentStatus;
import org.dean.codex.protocol.agent.AgentMailboxState;
import org.dean.codex.protocol.agent.AgentSpawnRequest;
import org.dean.codex.protocol.agent.AgentSummary;
import org.dean.codex.protocol.agent.AgentWaitResult;
import org.dean.codex.protocol.item.CollabToolCallItem;
import org.dean.codex.protocol.item.CollabToolCallStatus;
import org.dean.codex.protocol.item.AgentMessageItem;
import org.dean.codex.protocol.item.ApprovalItem;
import org.dean.codex.protocol.item.ApprovalState;
import org.dean.codex.protocol.item.PlanItem;
import org.dean.codex.protocol.item.RawModelOutputItem;
import org.dean.codex.protocol.item.ReasoningItem;
import org.dean.codex.protocol.item.RuntimeErrorItem;
import org.dean.codex.protocol.item.SkillUseItem;
import org.dean.codex.protocol.item.ToolCallItem;
import org.dean.codex.protocol.item.ToolResultItem;
import org.dean.codex.protocol.item.TurnItem;
import org.dean.codex.protocol.item.UserMessageItem;
import org.dean.codex.protocol.approval.CommandApprovalRequest;
import org.dean.codex.protocol.context.ReconstructedThreadContext;
import org.dean.codex.protocol.context.ReconstructedTurnActivity;
import org.dean.codex.protocol.skill.SkillMetadata;
import org.dean.codex.protocol.tool.CommandApprovalDecision;
import org.dean.codex.protocol.tool.ExecCommandResult;
import org.dean.codex.runtime.springai.config.CodexProperties;
import org.dean.codex.runtime.springai.model.ChatClientResponsesModelClient;
import org.dean.codex.runtime.springai.model.ThreadModelSessionSnapshot;
import org.dean.codex.runtime.springai.model.ThreadModelSessionStateStore;
import org.dean.codex.runtime.springai.prompt.DefaultPromptAssemblyService;
import org.dean.codex.runtime.springai.prompt.DefaultToolObservationReducer;
import org.dean.codex.runtime.springai.prompt.DefaultToolCapabilityRegistry;
import org.dean.codex.runtime.springai.prompt.PromptExecSessionContext;
import org.dean.codex.runtime.springai.prompt.PromptAssemblyService;
import org.dean.codex.runtime.springai.prompt.ResolvedPrompt;
import org.dean.codex.runtime.springai.prompt.ToolObservationReducer;
import org.dean.codex.runtime.springai.prompt.ToolCapabilityRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
public class SpringAiCodexAgent implements CodexAgent {

    private static final Logger logger = LoggerFactory.getLogger(SpringAiCodexAgent.class);

    private final ResponsesModelClient responsesModelClient;
    private final FileReaderTool fileReaderTool;
    private final FileSearchTool fileSearchTool;
    private final ListDirTool listDirTool;
    private final WebSearchTool webSearchTool;
    private final FilePatchTool filePatchTool;
    private final FileWriterTool fileWriterTool;
    private final ShellCommandTool shellCommandTool;
    private final ExecCommandTool execCommandTool;
    private final CommandApprovalService commandApprovalService;
    private final Supplier<AgentControl> agentControlSupplier;
    private final ThreadContextReconstructionService threadContextReconstructionService;
    private final ContextManager contextManager;
    private final SkillService skillService;
    private final PromptAssemblyService promptAssemblyService;
    private final ThreadModelSessionStateStore threadModelSessionStateStore;
    private final ToolCapabilityRegistry toolCapabilityRegistry;
    private final ToolObservationReducer toolObservationReducer;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Path workspaceRoot;
    private final int maxSteps;
    private final int maxActionsPerStep;
    private final int autoCompactTokenLimit;
    private final int contextWindow;
    private final boolean emitRawOutputItems;
    private final ModelReasoningConfig modelReasoningConfig;
    private List<PromptExecSessionContext> activeExecSessionsForPrompt = List.of();
    private transient List<TurnItem> activePlannerStreamItems = new ArrayList<>();
    private transient Consumer<TurnItem> activePlannerItemConsumer;
    private transient List<String> activePlannerAssistantTexts = new ArrayList<>();

    @Autowired
    public SpringAiCodexAgent(ResponsesModelClient responsesModelClient,
                              FileReaderTool fileReaderTool,
                              FileSearchTool fileSearchTool,
                              ListDirTool listDirTool,
                              WebSearchTool webSearchTool,
                              FilePatchTool filePatchTool,
                              FileWriterTool fileWriterTool,
                              ShellCommandTool shellCommandTool,
                              ObjectProvider<ExecCommandTool> execCommandToolProvider,
                              CommandApprovalService commandApprovalService,
                              ObjectProvider<AgentControl> agentControlProvider,
                              ThreadContextReconstructionService threadContextReconstructionService,
                              ContextManager contextManager,
                              SkillService skillService,
                              @Qualifier("codexWorkspaceRoot") Path workspaceRoot,
                              CodexProperties codexProperties,
                              ObjectProvider<ThreadModelSessionStateStore> threadModelSessionStateStoreProvider,
                              PromptAssemblyService promptAssemblyService) {
        this(responsesModelClient,
                fileReaderTool,
                fileSearchTool,
                listDirTool,
                webSearchTool,
                filePatchTool,
                fileWriterTool,
                shellCommandTool,
                execCommandToolProvider == null ? null : execCommandToolProvider.getIfAvailable(),
                commandApprovalService,
                agentControlProvider == null ? () -> null : agentControlProvider::getIfAvailable,
                threadContextReconstructionService,
                contextManager,
                skillService,
                workspaceRoot,
                codexProperties,
                threadModelSessionStateStoreProvider == null ? null : threadModelSessionStateStoreProvider.getIfAvailable(),
                promptAssemblyService);
    }

    SpringAiCodexAgent(ChatClient.Builder chatClientBuilder,
                       FileReaderTool fileReaderTool,
                       FileSearchTool fileSearchTool,
                       ListDirTool listDirTool,
                       WebSearchTool webSearchTool,
                       FilePatchTool filePatchTool,
                       FileWriterTool fileWriterTool,
                       ShellCommandTool shellCommandTool,
                       CommandApprovalService commandApprovalService,
                       AgentControl agentControl,
                       ThreadContextReconstructionService threadContextReconstructionService,
                       ContextManager contextManager,
                       SkillService skillService,
                       Path workspaceRoot,
                       CodexProperties codexProperties) {
        this(new ChatClientResponsesModelClient(chatClientBuilder),
                fileReaderTool,
                fileSearchTool,
                listDirTool,
                webSearchTool,
                filePatchTool,
                fileWriterTool,
                shellCommandTool,
                null,
                commandApprovalService,
                agentControl,
                threadContextReconstructionService,
                contextManager,
                skillService,
                workspaceRoot,
                codexProperties);
    }

    SpringAiCodexAgent(ChatClient.Builder chatClientBuilder,
                       FileReaderTool fileReaderTool,
                       FileSearchTool fileSearchTool,
                       ListDirTool listDirTool,
                       WebSearchTool webSearchTool,
                       FilePatchTool filePatchTool,
                       FileWriterTool fileWriterTool,
                       ShellCommandTool shellCommandTool,
                       ExecCommandTool execCommandTool,
                       CommandApprovalService commandApprovalService,
                       AgentControl agentControl,
                       ThreadContextReconstructionService threadContextReconstructionService,
                       ContextManager contextManager,
                       SkillService skillService,
                       Path workspaceRoot,
                       CodexProperties codexProperties) {
        this(new ChatClientResponsesModelClient(chatClientBuilder),
                fileReaderTool,
                fileSearchTool,
                listDirTool,
                webSearchTool,
                filePatchTool,
                fileWriterTool,
                shellCommandTool,
                execCommandTool,
                commandApprovalService,
                () -> agentControl,
                threadContextReconstructionService,
                contextManager,
                skillService,
                workspaceRoot,
                codexProperties,
                null,
                null);
    }

    SpringAiCodexAgent(ResponsesModelClient responsesModelClient,
                       FileReaderTool fileReaderTool,
                       FileSearchTool fileSearchTool,
                       ListDirTool listDirTool,
                       WebSearchTool webSearchTool,
                       FilePatchTool filePatchTool,
                       FileWriterTool fileWriterTool,
                       ShellCommandTool shellCommandTool,
                       ExecCommandTool execCommandTool,
                       CommandApprovalService commandApprovalService,
                       AgentControl agentControl,
                       ThreadContextReconstructionService threadContextReconstructionService,
                       ContextManager contextManager,
                       SkillService skillService,
                       Path workspaceRoot,
                       CodexProperties codexProperties) {
        this(responsesModelClient,
                fileReaderTool,
                fileSearchTool,
                listDirTool,
                webSearchTool,
                filePatchTool,
                fileWriterTool,
                shellCommandTool,
                execCommandTool,
                commandApprovalService,
                () -> agentControl,
                threadContextReconstructionService,
                contextManager,
                skillService,
                workspaceRoot,
                codexProperties,
                null,
                null);
    }

    SpringAiCodexAgent(ResponsesModelClient responsesModelClient,
                       FileReaderTool fileReaderTool,
                       FileSearchTool fileSearchTool,
                       ListDirTool listDirTool,
                       WebSearchTool webSearchTool,
                       FilePatchTool filePatchTool,
                       FileWriterTool fileWriterTool,
                       ShellCommandTool shellCommandTool,
                       ExecCommandTool execCommandTool,
                       CommandApprovalService commandApprovalService,
                       AgentControl agentControl,
                       ThreadContextReconstructionService threadContextReconstructionService,
                       ContextManager contextManager,
                       SkillService skillService,
                       Path workspaceRoot,
                       CodexProperties codexProperties,
                       ThreadModelSessionStateStore threadModelSessionStateStore) {
        this(responsesModelClient,
                fileReaderTool,
                fileSearchTool,
                listDirTool,
                webSearchTool,
                filePatchTool,
                fileWriterTool,
                shellCommandTool,
                execCommandTool,
                commandApprovalService,
                () -> agentControl,
                threadContextReconstructionService,
                contextManager,
                skillService,
                workspaceRoot,
                codexProperties,
                threadModelSessionStateStore,
                null);
    }

    private SpringAiCodexAgent(ResponsesModelClient responsesModelClient,
                               FileReaderTool fileReaderTool,
                               FileSearchTool fileSearchTool,
                               ListDirTool listDirTool,
                               WebSearchTool webSearchTool,
                               FilePatchTool filePatchTool,
                               FileWriterTool fileWriterTool,
                               ShellCommandTool shellCommandTool,
                               ExecCommandTool execCommandTool,
                               CommandApprovalService commandApprovalService,
                               Supplier<AgentControl> agentControlSupplier,
                               ThreadContextReconstructionService threadContextReconstructionService,
                               ContextManager contextManager,
                               SkillService skillService,
                               Path workspaceRoot,
                               CodexProperties codexProperties,
                               ThreadModelSessionStateStore threadModelSessionStateStore,
                               PromptAssemblyService promptAssemblyService) {
        this.responsesModelClient = responsesModelClient;
        this.fileReaderTool = fileReaderTool;
        this.fileSearchTool = fileSearchTool;
        this.listDirTool = listDirTool;
        this.webSearchTool = webSearchTool;
        this.filePatchTool = filePatchTool;
        this.fileWriterTool = fileWriterTool;
        this.shellCommandTool = shellCommandTool;
        this.execCommandTool = execCommandTool;
        this.commandApprovalService = commandApprovalService;
        this.agentControlSupplier = agentControlSupplier == null ? () -> null : agentControlSupplier;
        this.threadContextReconstructionService = threadContextReconstructionService;
        this.contextManager = contextManager;
        this.skillService = skillService;
        this.workspaceRoot = workspaceRoot;
        this.threadModelSessionStateStore = threadModelSessionStateStore;
        this.toolCapabilityRegistry = new DefaultToolCapabilityRegistry();
        this.toolObservationReducer = new DefaultToolObservationReducer();
        CodexProperties.Agent agent = codexProperties.getAgent();
        this.maxSteps = Math.max(1, agent.getMaxSteps());
        this.maxActionsPerStep = Math.max(1, agent.getMaxActionsPerStep());
        CodexProperties.Model model = codexProperties.getModel();
        this.autoCompactTokenLimit = Math.max(0, model.getAutoCompactTokenLimit());
        this.contextWindow = Math.max(0, model.getContextWindow());
        this.emitRawOutputItems = model.isEmitRawOutputItems();
        this.modelReasoningConfig = new ModelReasoningConfig(
                model.getReasoningEffort(),
                model.getReasoningSummaryMode());
        this.promptAssemblyService = promptAssemblyService == null
                ? new DefaultPromptAssemblyService(this.workspaceRoot, this.maxSteps, this.maxActionsPerStep)
                : promptAssemblyService;
    }

    @Override
    public synchronized CodexTurnResult handleTurn(ThreadId threadId, TurnId turnId, String input) {
        return handleTurn(threadId, turnId, input, null);
    }

    @Override
    public synchronized CodexTurnResult handleTurn(ThreadId threadId, TurnId turnId, String input, Consumer<TurnItem> itemConsumer) {
        return handleTurn(threadId, turnId, input, itemConsumer, new TurnControl() { });
    }

    @Override
    public synchronized CodexTurnResult handleTurn(ThreadId threadId,
                                                   TurnId turnId,
                                                   String input,
                                                   List<ModelInputItem> inputItems,
                                                   Consumer<TurnItem> itemConsumer) {
        return handleTurn(threadId, turnId, input, inputItems, itemConsumer, new TurnControl() { });
    }

    @Override
    public synchronized CodexTurnResult handleTurn(ThreadId threadId,
                                                   TurnId turnId,
                                                   String input,
                                                   List<ModelInputItem> inputItems,
                                                   Consumer<TurnItem> itemConsumer,
                                                   TurnControl turnControl) {
        String safeInput = input == null ? "" : input.trim();
        List<ModelInputItem> safeInputItems = inputItems == null ? List.of() : List.copyOf(inputItems);
        return doHandleTurn(threadId, turnId, safeInput, safeInputItems, itemConsumer, turnControl);
    }

    @Override
    public synchronized CodexTurnResult handleTurn(ThreadId threadId,
                                                   TurnId turnId,
                                                   String input,
                                                   Consumer<TurnItem> itemConsumer,
                                                   TurnControl turnControl) {
        return doHandleTurn(threadId, turnId, input == null ? "" : input.trim(), List.of(), itemConsumer, turnControl);
    }

    private CodexTurnResult doHandleTurn(ThreadId threadId,
                                         TurnId turnId,
                                         String safeInput,
                                         List<ModelInputItem> inputItems,
                                         Consumer<TurnItem> itemConsumer,
                                         TurnControl turnControl) {
        TurnControl safeTurnControl = turnControl == null ? new TurnControl() { } : turnControl;
        try {
            activeExecSessionsForPrompt = List.of();
            List<ResolvedSkill> selectedSkills = selectedSkillsForInput(safeInput);
            List<SkillMetadata> availableSkills = skillService.listSkills(false);
            logger.debug("planner turn start thread={} turn={} inputChars={} inputItems={} selectedSkills={} availableSkills={}",
                    threadId.value(),
                    turnId.value(),
                    safeInput.length(),
                    inputItems.size(),
                    selectedSkills.size(),
                    availableSkills.size());
            List<TurnItem> preludeItems = new ArrayList<>();
            if (!selectedSkills.isEmpty()) {
                SkillUseItem skillUseItem = skillUseItem(selectedSkills.stream().map(ResolvedSkill::metadata).toList());
                emitItem(preludeItems, itemConsumer, skillUseItem);
            }
            ExecutionOutcome outcome = runPlanningLoop(
                    threadId,
                    turnId,
                    safeInput,
                    inputItems,
                    itemConsumer,
                    safeTurnControl,
                    preludeItems,
                    selectedSkills,
                    availableSkills);
            String finalAnswer = outcome.finalAnswer() == null || outcome.finalAnswer().isBlank()
                    ? "I couldn't produce a response for that request."
                    : outcome.finalAnswer();
            return new CodexTurnResult(threadId, turnId, outcome.status(), outcome.items(), finalAnswer);
        }
        catch (Exception exception) {
            logger.debug("Codex turn failed for thread {} with input: {}", threadId.value(), safeInput, exception);
            RuntimeErrorItem errorItem = runtimeErrorItem(safeMessage(exception.getMessage()));
            emitItem(new ArrayList<>(), itemConsumer, errorItem);
            return new CodexTurnResult(
                    threadId,
                    turnId,
                    TurnStatus.FAILED,
                    List.of(errorItem),
                    "The Codex agent hit an error: " + safeMessage(exception.getMessage()));
        }
        finally {
            activeExecSessionsForPrompt = List.of();
        }
    }

    private ExecutionOutcome runPlanningLoop(ThreadId threadId,
                                             TurnId turnId,
                                             String input,
                                             List<ModelInputItem> inputItems,
                                             Consumer<TurnItem> itemConsumer,
                                             TurnControl turnControl,
                                             List<TurnItem> preludeItems,
                                             List<ResolvedSkill> selectedSkills,
                                             List<SkillMetadata> availableSkills) {
        List<TurnItem> items = new ArrayList<>(preludeItems);
        StringBuilder scratchpad = new StringBuilder();
        String lastObservation = "(none)";
        boolean skipNextPreSamplingAutoCompaction = false;
        Map<String, ActiveExecSessionState> activeExecSessions = new LinkedHashMap<>();

        for (int step = 1; step <= maxSteps; step++) {
            if (turnControl.interruptionRequested()) {
                return interruptedOutcome(items, itemConsumer);
            }
            List<String> steeringInputs = turnControl.drainSteeringInputs();
            List<PromptExecSessionContext> execSessionContexts = execSessionContexts(activeExecSessions);
            activeExecSessionsForPrompt = execSessionContexts;
            if (!steeringInputs.isEmpty()) {
                steeringInputs.forEach(steeringInput ->
                        emitItem(items, itemConsumer, new UserMessageItem(new ItemId(UUID.randomUUID().toString()), steeringInput, Instant.now())));
            }
            if (skipNextPreSamplingAutoCompaction) {
                skipNextPreSamplingAutoCompaction = false;
            }
            else {
                maybeAutoCompactBeforeSampling(
                        threadId,
                        input,
                        scratchpad.toString(),
                        step,
                        selectedSkills,
                        availableSkills,
                        steeringInputs,
                        execSessionContexts);
            }
            logger.debug("planner step start thread={} turn={} step={} scratchpadChars={} steeringInputs={} selectedSkills={} availableSkills={}",
                    threadId.value(),
                    turnId.value(),
                    step,
                    scratchpad.length(),
                    steeringInputs.size(),
                    selectedSkills.size(),
                    availableSkills.size());
            activePlannerStreamItems = items;
            activePlannerItemConsumer = itemConsumer;
            activePlannerAssistantTexts = new ArrayList<>();
            PlannerStep decision;
            try {
                decision = requestDecision(
                        threadId,
                        turnId,
                        input,
                        inputItems,
                        scratchpad.toString(),
                        step,
                        selectedSkills,
                        availableSkills,
                        steeringInputs);
            }
            finally {
                activePlannerStreamItems = new ArrayList<>();
                activePlannerItemConsumer = null;
            }
            if (turnControl.interruptionRequested()) {
                return interruptedOutcome(items, itemConsumer);
            }
            if (decision.editPlan() != null && !decision.editPlan().edits().isEmpty()) {
                emitItem(items, itemConsumer, planItem(decision.editPlan()));
            }
            if (decision.isFinished()) {
                String finalAnswer = decision.finalAnswer() == null || decision.finalAnswer().isBlank()
                        ? "I have finished the task, but the model did not provide a final answer."
                        : decision.finalAnswer();
                if (!assistantTextAlreadyStreamed(finalAnswer)) {
                    emitItem(items, itemConsumer, agentMessageItem(finalAnswer));
                }
                return new ExecutionOutcome(
                        TurnStatus.COMPLETED,
                        List.copyOf(items),
                        finalAnswer);
            }

            String observation;
            BatchExecutionOutcome batchOutcome = null;
            if (decision.validationError() == null) {
                batchOutcome = executeActions(threadId, turnId, decision.actions(), items, itemConsumer);
                observation = batchOutcome.observation();
                updateActiveExecSessions(activeExecSessions, batchOutcome.execSessionObservations());
            }
            else {
                observation = createErrorObservation(decision.validationError());
                emitItem(items, itemConsumer, runtimeErrorItem(decision.validationError()));
            }

            lastObservation = observation;
            scratchpad.append("Step ").append(step).append(':').append(System.lineSeparator())
                    .append("Summary: ").append(blankToPlaceholder(decision.summary())).append(System.lineSeparator())
                    .append("Edit plan: ").append(summarizeEditPlan(decision.editPlan())).append(System.lineSeparator())
                    .append("Actions: ").append(describeActions(decision.actions())).append(System.lineSeparator())
                    .append("Observation: ").append(observation).append(System.lineSeparator()).append(System.lineSeparator());

            if (batchOutcome != null && batchOutcome.awaitingApproval()) {
                String approvalList = batchOutcome.approvalIds().isEmpty()
                        ? "(unknown approval id)"
                        : batchOutcome.approvalIds().stream()
                        .map(this::shortApprovalId)
                        .collect(Collectors.joining(", "));
                String approvalMessage = "Approval required for command execution. Review with :approvals and continue with :approve <id-prefix> or :reject <id-prefix>. Pending approval ids: " + approvalList;
                emitItem(items, itemConsumer, agentMessageItem(approvalMessage));
                return new ExecutionOutcome(
                        TurnStatus.AWAITING_APPROVAL,
                        List.copyOf(items),
                        approvalMessage);
            }
            if (turnControl.interruptionRequested()) {
                return interruptedOutcome(items, itemConsumer);
            }
            boolean compacted = maybeAutoCompactAfterActions(
                    threadId,
                    input,
                    scratchpad.toString(),
                    step + 1,
                    selectedSkills,
                    availableSkills,
                    List.of(),
                    execSessionContexts(activeExecSessions));
            if (compacted) {
                skipNextPreSamplingAutoCompaction = true;
                continue;
            }
        }

        emitItem(items, itemConsumer, runtimeErrorItem("Stopped after " + maxSteps + " planner steps."));
        String finalAnswer = "I stopped after %d planner steps without reaching a final answer. Last observation:%n%s"
                .formatted(maxSteps, lastObservation);
        emitItem(items, itemConsumer, agentMessageItem(finalAnswer));
        return new ExecutionOutcome(
                TurnStatus.COMPLETED,
                List.copyOf(items),
                finalAnswer);
    }

    private ExecutionOutcome interruptedOutcome(List<TurnItem> items, Consumer<TurnItem> itemConsumer) {
        String finalAnswer = "Turn interrupted.";
        emitItem(items, itemConsumer, agentMessageItem(finalAnswer));
        return new ExecutionOutcome(
                TurnStatus.INTERRUPTED,
                List.copyOf(items),
                finalAnswer);
    }

    protected PlannerStep requestDecision(ThreadId threadId,
                                          TurnId turnId,
                                          String input,
                                          List<ModelInputItem> inputItems,
                                          String scratchpad,
                                          int step,
                                          List<ResolvedSkill> selectedSkills,
                                          List<SkillMetadata> availableSkills,
                                          List<String> steeringInputs) {
        ReconstructedThreadContext reconstructedContext = threadContextReconstructionService.reconstruct(threadId);
        ResolvedPrompt resolvedPrompt = promptAssemblyService.assemblePlannerPrompt(
                reconstructedContext,
                input,
                scratchpad,
                step,
                selectedSkills,
                availableSkills,
                steeringInputs,
                activeExecSessionsForPrompt);
        String systemPrompt = resolvedPrompt.systemPrompt();
        String userPrompt = resolvedPrompt.userPrompt();
        ModelRequestMetadata requestMetadata = requestMetadata(threadId, turnId, step);
        ModelRequest modelRequest = new ModelRequest(
                systemPrompt,
                composeModelInputItems(userPrompt, inputItems),
                toModelToolSpecs(resolvedPrompt),
                resolvedPrompt.toolContract().supportsParallelToolCalls(),
                modelReasoningConfig,
                requestMetadata);
        logger.debug("planner request start thread={} turn={} step={} systemChars={} userChars={} recentMessages={} recentTurns={} recentActivities={} selectedSkills={} availableSkills={} steeringInputs={}",
                threadId.value(),
                turnId == null ? "(null)" : turnId.value(),
                step,
                systemPrompt.length(),
                userPrompt.length(),
                reconstructedContext.recentMessages().size(),
                reconstructedContext.recentTurns().size(),
                reconstructedContext.recentActivities().size(),
                selectedSkills.size(),
                availableSkills.size(),
                steeringInputs.size());

        long startedNanos = System.nanoTime();
        String outputStreamId = UUID.randomUUID().toString();
        AtomicInteger outputSequence = new AtomicInteger();
        ModelResponse modelResponse = responsesModelClient.complete(modelRequest, outputItem ->
                handleModelOutputItem(
                        outputItem,
                        activePlannerStreamItems,
                        activePlannerItemConsumer,
                        requestMetadata,
                        outputStreamId,
                        outputSequence.incrementAndGet()));
        emitRawModelResponseMetadataItem(
                modelResponse,
                activePlannerStreamItems,
                activePlannerItemConsumer,
                requestMetadata,
                outputStreamId,
                outputSequence.incrementAndGet());
        persistModelSessionSnapshot(threadId, turnId, modelResponse);
        String response = modelResponse.assistantText();
        logger.debug("planner request complete thread={} turn={} step={} elapsedMs={} responseChars={} responseNull={} outputItems={}",
                threadId.value(),
                turnId == null ? "(null)" : turnId.value(),
                step,
                (System.nanoTime() - startedNanos) / 1_000_000L,
                response == null ? 0 : response.length(),
                response == null,
                modelResponse.outputItems().size());

        PlannerStep decision = parseDecision(response);
        logger.debug("planner decision parsed thread={} turn={} step={} actions={} finished={} summaryChars={}",
                threadId.value(),
                turnId == null ? "(null)" : turnId.value(),
                step,
                decision.actions().size(),
                decision.isFinished(),
                decision.summary() == null ? 0 : decision.summary().length());
        return decision;
    }

    private ModelRequestMetadata requestMetadata(ThreadId threadId, TurnId turnId, int step) {
        ThreadModelSessionSnapshot snapshot = modelSessionSnapshot(threadId);
        if (snapshot == null) {
            return new ModelRequestMetadata(
                    threadId == null ? "" : threadId.value(),
                    turnId == null ? "" : turnId.value(),
                    step);
        }
        return snapshot.toRequestMetadata(threadId, turnId, step);
    }

    private ThreadModelSessionSnapshot modelSessionSnapshot(ThreadId threadId) {
        if (threadModelSessionStateStore == null || threadId == null) {
            return null;
        }
        return threadModelSessionStateStore.read(threadId)
                .orElseGet(() -> ThreadModelSessionSnapshot.initial(new ThreadSummary(
                        threadId,
                        "Thread " + threadId.value(),
                        Instant.now(),
                        Instant.now(),
                        0)));
    }

    private void persistModelSessionSnapshot(ThreadId threadId, TurnId turnId, ModelResponse modelResponse) {
        if (threadModelSessionStateStore == null || threadId == null || turnId == null) {
            return;
        }
        ThreadModelSessionSnapshot snapshot = modelSessionSnapshot(threadId);
        if (snapshot == null) {
            return;
        }
        threadModelSessionStateStore.write(threadId, snapshot.advance(turnId, modelResponse == null ? null : modelResponse.metadata()));
    }

    private List<ModelInputItem> composeModelInputItems(String userPrompt, List<ModelInputItem> inputItems) {
        List<ModelInputItem> items = new ArrayList<>();
        items.add(new InputTextItem(ModelInputRole.USER, userPrompt));
        if (inputItems != null) {
            inputItems.stream()
                    .filter(item -> !(item instanceof InputTextItem))
                    .forEach(items::add);
        }
        return List.copyOf(items);
    }

    private void handleModelOutputItem(ModelOutputItem outputItem,
                                       List<TurnItem> items,
                                       Consumer<TurnItem> itemConsumer,
                                       ModelRequestMetadata requestMetadata,
                                       String outputStreamId,
                                       int outputSequence) {
        emitRawModelOutputItem(outputItem, items, itemConsumer, requestMetadata, outputStreamId, outputSequence);
        if (outputItem instanceof ModelAssistantMessageItem assistantMessageItem) {
            String text = assistantMessageItem.text();
            if (shouldStreamAssistantMessage(text)) {
                recordStreamedAssistantText(text);
                emitItem(items, itemConsumer, new AgentMessageItem(
                        new ItemId(assistantMessageItem.id().isBlank() ? UUID.randomUUID().toString() : assistantMessageItem.id()),
                        text,
                        Instant.now()));
            }
            return;
        }
        if (outputItem instanceof ModelReasoningItem reasoningItem) {
            emitItem(items, itemConsumer, new ReasoningItem(
                    new ItemId(reasoningItem.id().isBlank() ? UUID.randomUUID().toString() : reasoningItem.id()),
                    reasoningItem.summary(),
                    reasoningItem.content(),
                    Instant.now()));
            return;
        }
        if (outputItem instanceof ModelToolCallItem toolCallItem) {
            emitItem(items, itemConsumer, new ToolCallItem(
                    new ItemId(toolCallItem.id().isBlank() ? UUID.randomUUID().toString() : toolCallItem.id()),
                    toolCallItem.toolName(),
                    toolCallItem.argumentsJson(),
                    Instant.now()));
            return;
        }
        if (outputItem instanceof ModelToolResultItem toolResultItem) {
            emitItem(items, itemConsumer, new ToolResultItem(
                    new ItemId(toolResultItem.id().isBlank() ? UUID.randomUUID().toString() : toolResultItem.id()),
                    toolResultItem.toolName(),
                    toolResultItem.outputText(),
                    Instant.now()));
        }
    }

    private void emitRawModelOutputItem(ModelOutputItem outputItem,
                                        List<TurnItem> items,
                                        Consumer<TurnItem> itemConsumer,
                                        ModelRequestMetadata requestMetadata,
                                        String outputStreamId,
                                        int outputSequence) {
        if (!emitRawOutputItems || outputItem == null) {
            return;
        }
        emitItem(items, itemConsumer, new RawModelOutputItem(
                new ItemId(UUID.randomUUID().toString()),
                outputItem.type(),
                outputItem.id(),
                outputStreamId,
                outputSequence,
                requestMetadata == null ? "" : requestMetadata.threadId(),
                requestMetadata == null ? "" : requestMetadata.turnId(),
                requestMetadata == null ? 0 : requestMetadata.step(),
                "",
                "",
                "",
                serializeModelOutputItem(outputItem),
                Instant.now()));
    }

    private void emitRawModelResponseMetadataItem(ModelResponse modelResponse,
                                                  List<TurnItem> items,
                                                  Consumer<TurnItem> itemConsumer,
                                                  ModelRequestMetadata requestMetadata,
                                                  String outputStreamId,
                                                  int outputSequence) {
        if (!emitRawOutputItems || modelResponse == null) {
            return;
        }
        emitItem(items, itemConsumer, new RawModelOutputItem(
                new ItemId(UUID.randomUUID().toString()),
                "response_metadata",
                modelResponse.metadata().responseId(),
                outputStreamId,
                outputSequence,
                requestMetadata == null ? "" : requestMetadata.threadId(),
                requestMetadata == null ? "" : requestMetadata.turnId(),
                requestMetadata == null ? 0 : requestMetadata.step(),
                modelResponse.metadata().responseId(),
                modelResponse.metadata().sessionId(),
                modelResponse.metadata().finishReason(),
                serializeRawPayload(modelResponse.metadata()),
                Instant.now()));
    }

    private String serializeModelOutputItem(ModelOutputItem outputItem) {
        return serializeRawPayload(outputItem);
    }

    private String serializeRawPayload(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        }
        catch (Exception exception) {
            logger.debug("Unable to serialize raw model payload {}", value == null ? "(null)" : value.getClass().getSimpleName(), exception);
            return "";
        }
    }

    private boolean shouldStreamAssistantMessage(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String trimmed = text.trim();
        if (!(trimmed.startsWith("{") && trimmed.endsWith("}"))) {
            return true;
        }
        try {
            JsonNode node = objectMapper.readTree(trimmed);
            return !(node.isObject() && (node.has("actions") || node.has("finalAnswer") || node.has("summary") || node.has("thought")));
        }
        catch (Exception ignored) {
            return true;
        }
    }

    private void recordStreamedAssistantText(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (activePlannerAssistantTexts == null) {
            activePlannerAssistantTexts = new ArrayList<>();
        }
        activePlannerAssistantTexts.add(text.trim());
    }

    private boolean assistantTextAlreadyStreamed(String finalAnswer) {
        if (finalAnswer == null || finalAnswer.isBlank() || activePlannerAssistantTexts == null || activePlannerAssistantTexts.isEmpty()) {
            return false;
        }
        String normalized = finalAnswer.trim();
        return activePlannerAssistantTexts.stream().anyMatch(normalized::equals);
    }

    private List<ModelToolSpec> toModelToolSpecs(ResolvedPrompt resolvedPrompt) {
        if (resolvedPrompt == null || resolvedPrompt.toolContract() == null) {
            return List.of();
        }
        return resolvedPrompt.toolContract().visibleTools().stream()
                .map(tool -> new ModelToolSpec(
                        ModelToolKind.FUNCTION,
                        tool.name(),
                        tool.description(),
                        tool.inputSchema(),
                        tool.outputSchema(),
                        tool.supportsParallelExecution(),
                        tool.supplementaryInstructions()))
                .toList();
    }

    private boolean maybeAutoCompactBeforeSampling(ThreadId threadId,
                                                   String input,
                                                   String scratchpad,
                                                   int step,
                                                   List<ResolvedSkill> selectedSkills,
                                                   List<SkillMetadata> availableSkills,
                                                   List<String> steeringInputs,
                                                   List<PromptExecSessionContext> activeExecSessions) {
        return maybeAutoCompact(threadId, input, scratchpad, step, selectedSkills, availableSkills, steeringInputs, activeExecSessions,
                "before sampling");
    }

    private boolean maybeAutoCompactAfterActions(ThreadId threadId,
                                                 String input,
                                                 String scratchpad,
                                                 int step,
                                                 List<ResolvedSkill> selectedSkills,
                                                 List<SkillMetadata> availableSkills,
                                                 List<String> steeringInputs,
                                                 List<PromptExecSessionContext> activeExecSessions) {
        return maybeAutoCompact(threadId, input, scratchpad, step, selectedSkills, availableSkills, steeringInputs, activeExecSessions,
                "after actions");
    }

    private boolean maybeAutoCompact(ThreadId threadId,
                                     String input,
                                     String scratchpad,
                                     int step,
                                     List<ResolvedSkill> selectedSkills,
                                     List<SkillMetadata> availableSkills,
                                     List<String> steeringInputs,
                                     List<PromptExecSessionContext> activeExecSessions,
                                     String phase) {
        int limit = effectiveAutoCompactTokenLimit();
        if (limit <= 0) {
            return false;
        }

        int estimatedTokens = estimatePlannerPromptTokens(
                threadId,
                input,
                scratchpad,
                step,
                selectedSkills,
                availableSkills,
                steeringInputs,
                activeExecSessions);
        if (estimatedTokens <= limit) {
            return false;
        }

        logger.debug("Auto-compacting thread {} {} at step {}: estimatedTokens={} limit={}",
                threadId.value(),
                phase,
                step,
                estimatedTokens,
                limit);
        contextManager.compactThread(threadId);
        return true;
    }

    private int estimatePlannerPromptTokens(ThreadId threadId,
                                            String input,
                                            String scratchpad,
                                            int step,
                                            List<ResolvedSkill> selectedSkills,
                                            List<SkillMetadata> availableSkills,
                                            List<String> steeringInputs,
                                            List<PromptExecSessionContext> activeExecSessions) {
        ReconstructedThreadContext reconstructedContext = threadContextReconstructionService.reconstruct(threadId);
        ResolvedPrompt resolvedPrompt = promptAssemblyService.assemblePlannerPrompt(
                reconstructedContext,
                input,
                scratchpad,
                step,
                selectedSkills,
                availableSkills,
                steeringInputs,
                activeExecSessions);
        String systemPrompt = resolvedPrompt.systemPrompt();
        String userPrompt = resolvedPrompt.userPrompt();
        return estimateTokens(systemPrompt) + estimateTokens(userPrompt);
    }

    private int effectiveAutoCompactTokenLimit() {
        int limit = autoCompactTokenLimit;
        if (contextWindow > 0) {
            limit = limit <= 0 ? contextWindow : Math.min(limit, contextWindow);
        }
        return Math.max(0, limit);
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, (text.length() + 3) / 4);
    }

    BatchExecutionOutcome executeActions(ThreadId threadId,
                                         TurnId turnId,
                                         List<ToolActionRequest> actions,
                                         List<TurnItem> items,
                                         Consumer<TurnItem> itemConsumer) {
        List<Map<String, Object>> results = new ArrayList<>();
        List<String> approvalIds = new ArrayList<>();
        List<ExecSessionObservation> execSessionObservations = new ArrayList<>();
        boolean awaitingApproval = false;
        for (int index = 0; index < actions.size(); ) {
            ToolActionRequest action = actions.get(index);
            if (isParallelSafe(action.action())) {
                int waveStart = index;
                int waveEnd = index;
                while (waveEnd < actions.size() && isParallelSafe(actions.get(waveEnd).action())) {
                    waveEnd++;
                }
                List<ToolActionRequest> waveActions = actions.subList(waveStart, waveEnd);
                if (waveActions.size() > 1) {
                    List<ParallelActionExecution> parallelExecutions = executeParallelWave(
                            threadId,
                            turnId,
                            waveStart,
                            waveActions);
                    for (ParallelActionExecution execution : parallelExecutions) {
                        appendActionExecution(
                                execution,
                                results,
                                approvalIds,
                                execSessionObservations,
                                items,
                                itemConsumer);
                    }
                    index = waveEnd;
                    continue;
                }
            }

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("index", index + 1);
            entry.put("action", action.action());
            entry.put("target", describeTarget(action));
            ActionExecutionOutcome actionOutcome = executeAction(threadId, turnId, action, items, itemConsumer);
            entry.put("result", parseObservation(reduceObservation(action.action(), actionOutcome.observation())));
            results.add(entry);
            approvalIds.addAll(actionOutcome.approvalIds());
            if (actionOutcome.execSessionObservation() != null) {
                execSessionObservations.add(actionOutcome.execSessionObservation());
            }
            if (actionOutcome.awaitingApproval()) {
                awaitingApproval = true;
                break;
            }
            index++;
        }

        try {
            return new BatchExecutionOutcome(
                    objectMapper.writeValueAsString(Map.of("results", results)),
                    awaitingApproval,
                    List.copyOf(approvalIds),
                    List.copyOf(execSessionObservations));
        }
        catch (Exception exception) {
            return new BatchExecutionOutcome(
                    createErrorObservation(exception.getMessage()),
                    awaitingApproval,
                    List.copyOf(approvalIds),
                    List.copyOf(execSessionObservations));
        }
    }

    private List<ParallelActionExecution> executeParallelWave(ThreadId threadId,
                                                              TurnId turnId,
                                                              int waveStartIndex,
                                                              List<ToolActionRequest> waveActions) {
        List<java.util.concurrent.CompletableFuture<ParallelActionExecution>> futures = new ArrayList<>();
        for (int offset = 0; offset < waveActions.size(); offset++) {
            final int actionIndex = waveStartIndex + offset;
            final ToolActionRequest action = waveActions.get(offset);
            futures.add(java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                List<TurnItem> localItems = new ArrayList<>();
                ActionExecutionOutcome outcome = executeAction(threadId, turnId, action, localItems, null);
                return new ParallelActionExecution(
                        actionIndex,
                        action,
                        outcome,
                        List.copyOf(localItems));
            }));
        }

        List<ParallelActionExecution> executions = new ArrayList<>();
        for (java.util.concurrent.CompletableFuture<ParallelActionExecution> future : futures) {
            executions.add(future.join());
        }
        executions.sort(java.util.Comparator.comparingInt(ParallelActionExecution::index));
        return executions;
    }

    private void appendActionExecution(ParallelActionExecution execution,
                                       List<Map<String, Object>> results,
                                       List<String> approvalIds,
                                       List<ExecSessionObservation> execSessionObservations,
                                       List<TurnItem> items,
                                       Consumer<TurnItem> itemConsumer) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("index", execution.index() + 1);
        entry.put("action", execution.action().action());
        entry.put("target", describeTarget(execution.action()));
        entry.put("result", parseObservation(reduceObservation(execution.action().action(), execution.outcome().observation())));
        results.add(entry);
        approvalIds.addAll(execution.outcome().approvalIds());
        if (execution.outcome().execSessionObservation() != null) {
            execSessionObservations.add(execution.outcome().execSessionObservation());
        }
        for (TurnItem item : execution.items()) {
            emitItem(items, itemConsumer, item);
        }
    }

    ActionExecutionOutcome executeAction(ThreadId threadId,
                                         TurnId turnId,
                                         ToolActionRequest action,
                                         List<TurnItem> items,
                                         Consumer<TurnItem> itemConsumer) {
        emitItem(items, itemConsumer, toolCallItem(action.action().name(), describeTarget(action)));
        boolean collaborationAction = isCollaborationAction(action.action());
        Map<String, AgentMailboxState> collabMailboxes = Map.of();
        if (collaborationAction) {
            emitCollabToolCall(
                    threadId,
                    action,
                    items,
                    itemConsumer,
                    CollabToolCallStatus.IN_PROGRESS,
                    collaborationDeliveryState(action.action(), false),
                    null,
                    null,
                    List.of(),
                    Map.of(),
                    Map.of(),
                    null,
                    describeTarget(action));
        }
        String observation;
        ThreadId collabNewThreadId = null;
        List<ThreadId> collabReceiverThreadIds = List.of();
        Map<String, AgentStatus> collabAgentStatuses = Map.of();
        String collabPrompt = null;
        String collabWakeupCause = null;
        try {
            observation = switch (action.action()) {
                case READ_FILE -> objectMapper.writeValueAsString(fileReaderTool.readFile(action.path()));
                case SEARCH_FILES -> objectMapper.writeValueAsString(fileSearchTool.search(action.query(), action.path()));
                case LIST_DIR -> objectMapper.writeValueAsString(listDirTool.listDir(action.path(), action.maxDepth()));
                case WEB_SEARCH -> objectMapper.writeValueAsString(webSearchTool.search(action.query(), action.maxResults()));
                case APPLY_PATCH -> objectMapper.writeValueAsString(
                        filePatchTool.applyPatch(action.path(), action.oldText(), action.newText(), action.replaceAll()));
                case WRITE_FILE -> objectMapper.writeValueAsString(fileWriterTool.writeFile(action.path(), action.content()));
                case RUN_COMMAND -> objectMapper.writeValueAsString(shellCommandTool.runCommand(action.command()));
                case EXEC_COMMAND -> objectMapper.writeValueAsString(requireExecCommandTool().execCommand(
                        threadId,
                        action.command(),
                        action.yieldTimeMillis(),
                        action.maxRuntimeMillis(),
                        action.pty()));
                case WRITE_STDIN -> objectMapper.writeValueAsString(requireExecCommandTool().writeStdin(
                        threadId,
                        action.sessionId(),
                        action.input(),
                        action.yieldTimeMillis()));
                case SPAWN_AGENT -> {
                    AgentControl agentControl = requireAgentControl();
                    AgentSummary spawnedAgent = agentControl.spawnAgent(new AgentSpawnRequest(
                            threadId,
                            action.taskName(),
                            firstNonBlank(action.prompt(), action.taskName()),
                            action.nickname(),
                            action.role(),
                            action.depth(),
                            action.modelProvider(),
                            action.model(),
                            action.cwd()));
                    LinkedHashMap<String, Object> response = new LinkedHashMap<>();
                    response.put("success", true);
                    response.put("action", "spawn_agent");
                    response.put("parentThreadId", threadId.value());
                    response.put("threadId", spawnedAgent.threadId().value());
                    response.put("status", spawnedAgent.status());
                    response.put("agent", spawnedAgent);
                    response.put("taskName", action.taskName());
                    response.put("prompt", firstNonBlank(action.prompt(), action.taskName()));
                    collabNewThreadId = spawnedAgent.threadId();
                    collabReceiverThreadIds = List.of(spawnedAgent.threadId());
                    collabAgentStatuses = Map.of(spawnedAgent.threadId().value(), spawnedAgent.status());
                    collabMailboxes = mailboxStateMap(agentControl, spawnedAgent.threadId());
                    collabPrompt = firstNonBlank(action.prompt(), action.taskName());
                    yield objectMapper.writeValueAsString(response);
                }
                case SEND_MESSAGE -> {
                    AgentControl agentControl = requireAgentControl();
                    ThreadId agentThreadId = new ThreadId(action.threadId());
                    AgentSummary agentSummary = agentControl.sendMessage(
                            agentThreadId,
                            new AgentMessage(threadId, agentThreadId, action.content(), Instant.now()));
                    LinkedHashMap<String, Object> response = new LinkedHashMap<>();
                    response.put("success", true);
                    response.put("action", "send_message");
                    response.put("senderThreadId", threadId.value());
                    response.put("threadId", agentThreadId.value());
                    response.put("status", agentSummary.status());
                    response.put("agent", agentSummary);
                    response.put("content", action.content());
                    collabReceiverThreadIds = List.of(agentThreadId);
                    collabAgentStatuses = Map.of(agentThreadId.value(), agentSummary.status());
                    collabMailboxes = mailboxStateMap(agentControl, agentThreadId);
                    collabPrompt = action.content();
                    yield objectMapper.writeValueAsString(response);
                }
                case ASSIGN_TASK, SEND_INPUT -> {
                    AgentControl agentControl = requireAgentControl();
                    ThreadId agentThreadId = new ThreadId(action.threadId());
                    AgentSummary agentSummary = agentControl.assignTask(
                            agentThreadId,
                            new AgentMessage(threadId, agentThreadId, action.content(), Instant.now()),
                            action.interrupt());
                    LinkedHashMap<String, Object> response = new LinkedHashMap<>();
                    response.put("success", true);
                    response.put("action", action.action() == ToolAction.SEND_INPUT ? "send_input" : "assign_task");
                    response.put("senderThreadId", threadId.value());
                    response.put("threadId", agentThreadId.value());
                    response.put("status", agentSummary.status());
                    response.put("agent", agentSummary);
                    response.put("content", action.content());
                    response.put("interrupt", action.interrupt());
                    collabReceiverThreadIds = List.of(agentThreadId);
                    collabAgentStatuses = Map.of(agentThreadId.value(), agentSummary.status());
                    collabMailboxes = mailboxStateMap(agentControl, agentThreadId);
                    collabPrompt = action.content();
                    yield objectMapper.writeValueAsString(response);
                }
                case WAIT_AGENT -> {
                    AgentControl agentControl = requireAgentControl();
                    List<ThreadId> waitTargets = action.threadIds() == null ? List.of() : action.threadIds();
                    AgentWaitResult waitResult = agentControl.waitAgent(waitTargets, action.timeoutMillis() == null ? 1000L : action.timeoutMillis());
                    LinkedHashMap<String, Object> response = new LinkedHashMap<>();
                    response.put("success", true);
                    response.put("action", "wait_agent");
                    response.put("threadId", waitResult.threadId() == null ? null : waitResult.threadId().value());
                    response.put("turnId", waitResult.turnId() == null ? null : waitResult.turnId().value());
                    response.put("previousStatus", waitResult.previousStatus());
                    response.put("status", waitResult.status());
                    response.put("timedOut", waitResult.timedOut());
                    response.put("message", waitResult.message());
                    response.put("finalAnswer", waitResult.finalAnswer());
                    response.put("completedAt", waitResult.completedAt());
                    response.put("result", waitResult);
                    response.put("threadIds", waitTargets.stream().map(ThreadId::value).toList());
                    response.put("timeoutMillis", action.timeoutMillis() == null ? 1000L : action.timeoutMillis());
                    collabReceiverThreadIds = List.copyOf(waitTargets);
                    if (waitResult.threadId() != null && waitResult.status() != null) {
                        collabAgentStatuses = Map.of(waitResult.threadId().value(), waitResult.status());
                    }
                    LinkedHashMap<String, AgentMailboxState> waitMailboxes = new LinkedHashMap<>();
                    for (ThreadId waitTarget : waitTargets) {
                        if (waitTarget == null) {
                            continue;
                        }
                        AgentMailboxState mailboxState = agentControl.mailboxState(waitTarget);
                        if (mailboxState != null) {
                            waitMailboxes.put(waitTarget.value(), mailboxState);
                        }
                    }
                    collabMailboxes = waitMailboxes;
                    collabWakeupCause = normalizeWakeupCause(waitResult.message(), waitResult.timedOut());
                    collabPrompt = "wait_agent";
                    yield objectMapper.writeValueAsString(response);
                }
                case RESUME_AGENT -> {
                    AgentControl agentControl = requireAgentControl();
                    ThreadId agentThreadId = new ThreadId(action.threadId());
                    AgentSummary agentSummary = agentControl.resumeAgent(agentThreadId);
                    LinkedHashMap<String, Object> response = new LinkedHashMap<>();
                    response.put("success", true);
                    response.put("action", "resume_agent");
                    response.put("threadId", agentThreadId.value());
                    response.put("status", agentSummary.status());
                    response.put("agent", agentSummary);
                    collabReceiverThreadIds = List.of(agentThreadId);
                    collabAgentStatuses = Map.of(agentThreadId.value(), agentSummary.status());
                    collabMailboxes = mailboxStateMap(agentControl, agentThreadId);
                    yield objectMapper.writeValueAsString(response);
                }
                case CLOSE_AGENT -> {
                    AgentControl agentControl = requireAgentControl();
                    ThreadId agentThreadId = new ThreadId(action.threadId());
                    AgentSummary agentSummary = agentControl.closeAgent(agentThreadId);
                    LinkedHashMap<String, Object> response = new LinkedHashMap<>();
                    response.put("success", true);
                    response.put("action", "close_agent");
                    response.put("threadId", agentThreadId.value());
                    response.put("status", agentSummary.status());
                    response.put("closed", agentSummary.closed());
                    response.put("agent", agentSummary);
                    collabReceiverThreadIds = List.of(agentThreadId);
                    collabAgentStatuses = Map.of(agentThreadId.value(), agentSummary.status());
                    collabMailboxes = mailboxStateMap(agentControl, agentThreadId);
                    yield objectMapper.writeValueAsString(response);
                }
                case LIST_AGENTS -> {
                    AgentControl agentControl = requireAgentControl();
                    ThreadId parentThreadId = action.threadId().isBlank() ? threadId : new ThreadId(action.threadId());
                    List<AgentSummary> agents = agentControl.listAgents(parentThreadId, action.recursive());
                    LinkedHashMap<String, Object> response = new LinkedHashMap<>();
                    response.put("success", true);
                    response.put("action", "list_agents");
                    response.put("parentThreadId", parentThreadId.value());
                    response.put("recursive", action.recursive());
                    response.put("agentCount", agents.size());
                    response.put("agents", agents);
                    yield objectMapper.writeValueAsString(response);
                }
            };
        }
        catch (Exception exception) {
            observation = createErrorObservation(exception.getMessage());
        }
        emitCollabToolCall(
                threadId,
                action,
                items,
                itemConsumer,
                observation != null && observation.contains("\"success\":false")
                        ? CollabToolCallStatus.FAILED
                        : CollabToolCallStatus.COMPLETED,
                collaborationDeliveryState(action.action(), true),
                observation,
                collabNewThreadId,
                collabReceiverThreadIds,
                collabAgentStatuses,
                collabMailboxes,
                collabWakeupCause,
                collabPrompt);
        ActionExecutionOutcome outcome = enrichApprovalObservation(threadId, turnId, action, observation, items, itemConsumer);
        observation = outcome.observation();
        emitItem(items, itemConsumer, toolResultItem(action.action().name(), summarizeToolResult(action.action(), observation)));
        return new ActionExecutionOutcome(
                outcome.observation(),
                outcome.awaitingApproval(),
                outcome.approvalIds(),
                extractExecSessionObservation(action.action(), outcome.observation()));
    }

    PlannerStep parseDecision(String response) {
        try {
            String cleaned = stripCodeFences(response);
            JsonNode root = objectMapper.readTree(cleaned);
            String summary = firstNonBlank(textValue(root.get("summary")), textValue(root.get("thought")));
            String finalAnswer = textValue(root.get("finalAnswer"));
            EditPlan editPlan = parseEditPlan(root.get("editPlan"));
            List<ToolActionRequest> actions = parseActions(root);

            if (!finalAnswer.isBlank()) {
                if (!actions.isEmpty()) {
                    return PlannerStep.invalid(summary, editPlan, actions,
                            "Return either actions or finalAnswer, but not both in the same planner step.");
                }
                return PlannerStep.finish(summary, editPlan, finalAnswer);
            }

            if (actions.isEmpty()) {
                return PlannerStep.invalid(summary, editPlan, List.of(),
                        "I could not determine any tool actions from the model response.");
            }
            if (actions.size() > maxActionsPerStep) {
                return PlannerStep.invalid(summary, editPlan, actions,
                        "The model selected %d actions, exceeding the configured per-step limit of %d."
                                .formatted(actions.size(), maxActionsPerStep));
            }

            String validationError = validateActions(actions);
            return validationError == null
                    ? PlannerStep.actions(summary, editPlan, actions)
                    : PlannerStep.invalid(summary, editPlan, actions, validationError);
        }
        catch (Exception exception) {
            String fallbackMessage = response == null || response.isBlank()
                    ? "I couldn't parse a valid planner step from the model response."
                    : response.trim();
            logger.debug("planner parse failed responseChars={} responseBlank={}",
                    response == null ? 0 : response.length(),
                    response == null || response.isBlank());
            return PlannerStep.invalid("The model returned a non-JSON response.", null, List.of(),
                    "Invalid planner JSON response: " + fallbackMessage);
        }
    }

    String buildSystemPrompt(List<SkillMetadata> availableSkills) {
        return promptAssemblyService.buildSystemPrompt(availableSkills);
    }

    String buildUserPrompt(ReconstructedThreadContext reconstructedContext,
                           String input,
                           String scratchpad,
                           int step,
                           List<ResolvedSkill> selectedSkills,
                           List<String> steeringInputs) {
        return promptAssemblyService.buildUserPrompt(
                reconstructedContext,
                input,
                scratchpad,
                step,
                selectedSkills,
                steeringInputs,
                List.of());
    }

    List<PromptExecSessionContext> currentExecSessionsForPrompt() {
        return activeExecSessionsForPrompt;
    }

    private String describeActions(List<ToolActionRequest> actions) {
        if (actions.isEmpty()) {
            return "(none)";
        }
        return actions.stream()
                .map(this::describe)
                .collect(Collectors.joining(", "));
    }

    private String describe(ToolActionRequest action) {
        return switch (action.action()) {
            case READ_FILE, WRITE_FILE -> action.action() + " path=" + blankToPlaceholder(action.path());
            case SEARCH_FILES -> action.action()
                    + " query=" + blankToPlaceholder(action.query())
                    + " scope=" + blankToPlaceholder(action.path());
            case LIST_DIR -> action.action()
                    + " path=" + blankToPlaceholder(action.path())
                    + " maxDepth=" + (action.maxDepth() == null ? "(default)" : action.maxDepth());
            case WEB_SEARCH -> action.action()
                    + " query=" + blankToPlaceholder(action.query())
                    + " maxResults=" + (action.maxResults() == null ? "(default)" : action.maxResults());
            case APPLY_PATCH -> action.action()
                    + " path=" + blankToPlaceholder(action.path())
                    + " replaceAll=" + action.replaceAll();
            case RUN_COMMAND -> action.action() + " command=" + blankToPlaceholder(action.command());
            case EXEC_COMMAND -> action.action()
                    + " command=" + blankToPlaceholder(action.command())
                    + " yieldTimeMillis=" + (action.yieldTimeMillis() == null ? "(default)" : action.yieldTimeMillis())
                    + " maxRuntimeMillis=" + (action.maxRuntimeMillis() == null ? "(default)" : action.maxRuntimeMillis())
                    + " pty=" + action.pty();
            case WRITE_STDIN -> action.action()
                    + " sessionId=" + blankToPlaceholder(action.sessionId())
                    + " input=" + (action.input().isBlank() ? "(empty)" : "(provided)")
                    + " yieldTimeMillis=" + (action.yieldTimeMillis() == null ? "(default)" : action.yieldTimeMillis());
            case SPAWN_AGENT -> action.action()
                    + " taskName=" + blankToPlaceholder(action.taskName())
                    + " nickname=" + blankToPlaceholder(action.nickname())
                    + " role=" + blankToPlaceholder(action.role());
            case SEND_MESSAGE -> action.action()
                    + " threadId=" + blankToPlaceholder(action.threadId());
            case ASSIGN_TASK -> action.action()
                    + " threadId=" + blankToPlaceholder(action.threadId())
                    + " interrupt=" + action.interrupt();
            case SEND_INPUT -> action.action()
                    + " threadId=" + blankToPlaceholder(action.threadId())
                    + " interrupt=" + action.interrupt() + " (compat)";
            case WAIT_AGENT -> action.action()
                    + " threadIds=" + (action.threadIds().isEmpty() ? "(none)" : action.threadIds())
                    + " timeoutMillis=" + (action.timeoutMillis() == null ? "(default)" : action.timeoutMillis());
            case RESUME_AGENT, CLOSE_AGENT -> action.action() + " threadId=" + blankToPlaceholder(action.threadId());
            case LIST_AGENTS -> action.action()
                    + " threadId=" + blankToPlaceholder(action.threadId())
                    + " recursive=" + action.recursive();
        };
    }

    private String describeTarget(ToolActionRequest action) {
        return switch (action.action()) {
            case READ_FILE, WRITE_FILE, APPLY_PATCH -> blankToPlaceholder(action.path());
            case SEARCH_FILES -> "query=" + blankToPlaceholder(action.query()) + ", scope=" + blankToPlaceholder(action.path());
            case LIST_DIR -> blankToPlaceholder(action.path());
            case WEB_SEARCH -> "query=" + blankToPlaceholder(action.query());
            case RUN_COMMAND -> blankToPlaceholder(action.command());
            case EXEC_COMMAND -> blankToPlaceholder(action.command());
            case WRITE_STDIN -> blankToPlaceholder(action.sessionId());
            case SPAWN_AGENT -> blankToPlaceholder(action.taskName());
            case SEND_MESSAGE, ASSIGN_TASK, SEND_INPUT, RESUME_AGENT, CLOSE_AGENT -> blankToPlaceholder(action.threadId());
            case WAIT_AGENT -> action.threadIds().isEmpty()
                    ? "(none)"
                    : action.threadIds().stream().map(ThreadId::value).collect(Collectors.joining(", "));
            case LIST_AGENTS -> blankToPlaceholder(action.threadId());
        };
    }

    private String summarizeToolResult(ToolAction action, String observation) {
        try {
            JsonNode root = objectMapper.readTree(observation);
            if (root.has("success")) {
                StringBuilder summary = new StringBuilder(action.name()).append(" success=").append(root.path("success").asBoolean());
                if (root.has("path") && !root.path("path").asText("").isBlank()) {
                    summary.append(" path=").append(root.path("path").asText());
                }
                if (root.has("scope") && !root.path("scope").asText("").isBlank()) {
                    summary.append(" scope=").append(root.path("scope").asText());
                }
                if (root.has("branch") && !root.path("branch").asText("").isBlank()) {
                    summary.append(" branch=").append(root.path("branch").asText());
                }
                if (root.has("stagedCount")) {
                    summary.append(" staged=").append(root.path("stagedCount").asInt());
                }
                if (root.has("commitHash") && !root.path("commitHash").asText("").isBlank()) {
                    summary.append(" commit=").append(root.path("commitHash").asText(), 0,
                            Math.min(8, root.path("commitHash").asText().length()));
                }
                if (root.has("clean")) {
                    summary.append(" clean=").append(root.path("clean").asBoolean());
                }
                if (root.has("entries") && root.path("entries").isArray()) {
                    summary.append(" entries=").append(root.path("entries").size());
                }
                if (root.has("hits") && root.path("hits").isArray()) {
                    summary.append(" hits=").append(root.path("hits").size());
                }
                if (root.has("maxDepth")) {
                    summary.append(" maxDepth=").append(root.path("maxDepth").asInt());
                }
                if (root.has("totalEntries")) {
                    summary.append(" totalEntries=").append(root.path("totalEntries").asInt());
                }
                if (root.has("truncated")) {
                    summary.append(" truncated=").append(root.path("truncated").asBoolean());
                }
                if (root.has("committedEntries") && root.path("committedEntries").isArray()) {
                    summary.append(" committed=").append(root.path("committedEntries").size());
                }
                if (root.has("target") && !root.path("target").asText("").isBlank()) {
                    summary.append(" target=").append(root.path("target").asText());
                }
                if (root.has("reference") && !root.path("reference").asText("").isBlank()) {
                    summary.append(" reference=").append(root.path("reference").asText());
                }
                if (root.has("totalCharacters")) {
                    summary.append(" chars=").append(root.path("totalCharacters").asInt());
                }
                if (root.has("totalMatches")) {
                    summary.append(" matches=").append(root.path("totalMatches").asInt());
                }
                if (root.has("totalHits")) {
                    summary.append(" totalHits=").append(root.path("totalHits").asInt());
                }
                if (root.has("replacements")) {
                    summary.append(" replacements=").append(root.path("replacements").asInt());
                }
                if (root.has("exitCode")) {
                    summary.append(" exitCode=").append(root.path("exitCode").asInt());
                }
                if (root.has("sessionId") && !root.path("sessionId").asText("").isBlank()) {
                    summary.append(" sessionId=").append(root.path("sessionId").asText());
                }
                if (root.has("processId") && !root.path("processId").asText("").isBlank()) {
                    summary.append(" processId=").append(root.path("processId").asText());
                }
                if (root.has("executed")) {
                    summary.append(" executed=").append(root.path("executed").asBoolean());
                }
                if (root.has("approvalDecision") && !root.path("approvalDecision").asText("").isBlank()) {
                    summary.append(" approval=").append(root.path("approvalDecision").asText());
                }
                if (root.has("threadId") && !root.path("threadId").asText("").isBlank()) {
                    summary.append(" threadId=").append(root.path("threadId").asText());
                }
                if (root.has("backend") && !root.path("backend").asText("").isBlank()) {
                    summary.append(" backend=").append(root.path("backend").asText());
                }
                if (root.has("query") && !root.path("query").asText("").isBlank()) {
                    summary.append(" query=").append(root.path("query").asText());
                }
                if (root.has("turnId") && !root.path("turnId").asText("").isBlank()) {
                    summary.append(" turnId=").append(root.path("turnId").asText());
                }
                if (root.has("parentThreadId") && !root.path("parentThreadId").asText("").isBlank()) {
                    summary.append(" parentThreadId=").append(root.path("parentThreadId").asText());
                }
                if (root.has("agentCount")) {
                    summary.append(" agents=").append(root.path("agentCount").asInt());
                }
                if (root.has("status") && !root.path("status").asText("").isBlank()) {
                    summary.append(" status=").append(root.path("status").asText());
                }
                if (root.has("previousStatus") && !root.path("previousStatus").asText("").isBlank()) {
                    summary.append(" previous=").append(root.path("previousStatus").asText());
                }
                if (root.has("closed")) {
                    summary.append(" closed=").append(root.path("closed").asBoolean());
                }
                if (root.has("finalAnswer") && !root.path("finalAnswer").asText("").isBlank()) {
                    summary.append(" finalAnswer=").append(root.path("finalAnswer").asText());
                }
                if (root.has("timedOut") && root.path("timedOut").asBoolean()) {
                    summary.append(" timedOut=true");
                }
                if (root.has("truncated") && root.path("truncated").asBoolean()) {
                    summary.append(" truncated=true");
                }
                return summary.toString();
            }
        }
        catch (Exception ignored) {
            // Fall through to the generic summary.
        }
        return action.name() + " completed";
    }

    private void updateActiveExecSessions(Map<String, ActiveExecSessionState> activeExecSessions,
                                          List<ExecSessionObservation> execSessionObservations) {
        if (activeExecSessions == null || execSessionObservations == null || execSessionObservations.isEmpty()) {
            return;
        }
        for (ExecSessionObservation observation : execSessionObservations) {
            if (observation == null || observation.sessionId().isBlank()) {
                continue;
            }
            if (!observation.running()) {
                activeExecSessions.remove(observation.sessionId());
                continue;
            }
            ActiveExecSessionState current = activeExecSessions.get(observation.sessionId());
            activeExecSessions.put(
                    observation.sessionId(),
                    current == null ? ActiveExecSessionState.from(observation) : current.merge(observation));
        }
    }

    private List<PromptExecSessionContext> execSessionContexts(Map<String, ActiveExecSessionState> activeExecSessions) {
        if (activeExecSessions == null || activeExecSessions.isEmpty()) {
            return List.of();
        }
        return activeExecSessions.values().stream()
                .map(ActiveExecSessionState::toPromptContext)
                .toList();
    }

    private ExecSessionObservation extractExecSessionObservation(ToolAction action, String observation) {
        if (action != ToolAction.EXEC_COMMAND && action != ToolAction.WRITE_STDIN) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(observation);
            String sessionId = root.path("sessionId").asText("").trim();
            if (sessionId.isBlank()) {
                return null;
            }
            String status = root.path("status").asText("");
            return new ExecSessionObservation(
                    sessionId,
                    root.path("command").asText(""),
                    status,
                    root.path("processId").canConvertToLong() ? root.path("processId").longValue() : null,
                    root.path("pty").asBoolean(false),
                    previewExecOutput(root.path("stdout").asText("")),
                    previewExecOutput(root.path("stderr").asText("")),
                    isRunningExecStatus(status),
                    action == ToolAction.WRITE_STDIN ? 1 : 0);
        }
        catch (Exception ignored) {
            return null;
        }
    }

    private boolean isRunningExecStatus(String status) {
        return "RUNNING".equalsIgnoreCase(status);
    }

    private String previewExecOutput(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.length() <= 240) {
            return normalized;
        }
        return normalized.substring(0, 237) + "...";
    }

    private ActionExecutionOutcome enrichApprovalObservation(ThreadId threadId,
                                                             TurnId turnId,
                                                             ToolActionRequest action,
                                                             String observation,
                                                             List<TurnItem> items,
                                                             Consumer<TurnItem> itemConsumer) {
        if (action != null && action.action() != ToolAction.RUN_COMMAND && action.action() != ToolAction.EXEC_COMMAND) {
            return new ActionExecutionOutcome(observation, false, List.of(), null);
        }

        try {
            JsonNode root = objectMapper.readTree(observation);
            if (!(root instanceof ObjectNode objectNode)) {
                return new ActionExecutionOutcome(observation, false, List.of(), null);
            }

            String decision = objectNode.path("approvalDecision").asText("");
            String reason = objectNode.path("approvalReason").asText("");
            boolean executed = objectNode.path("executed").asBoolean(false);
            if (CommandApprovalDecision.REQUIRE_APPROVAL.name().equals(decision) && !executed) {
                CommandApprovalRequest request = commandApprovalService.requestApproval(
                        threadId,
                        turnId,
                        action.command(),
                        objectNode.path("workingDirectory").asText(""),
                        reason);
                objectNode.put("approvalRequestId", request.approvalId().value());
                emitItem(items, itemConsumer, approvalItem(
                        ApprovalState.REQUIRED,
                        request.approvalId().value(),
                        action.command(),
                        "Approval " + shortApprovalId(request.approvalId().value()) + " required for command: " + action.command()));
                return new ActionExecutionOutcome(
                        objectMapper.writeValueAsString(objectNode),
                        true,
                        List.of(request.approvalId().value()),
                        null);
            }
            if (CommandApprovalDecision.BLOCK.name().equals(decision) && !executed) {
                emitItem(items, itemConsumer, approvalItem(
                        ApprovalState.BLOCKED,
                        "",
                        action.command(),
                        "Command blocked: " + (reason.isBlank() ? "No reason provided." : reason)));
            }
        }
        catch (Exception ignored) {
            // Keep the original observation if enrichment fails.
        }
        return new ActionExecutionOutcome(observation, false, List.of(), null);
    }

    private String shortApprovalId(String approvalId) {
        if (approvalId == null || approvalId.isBlank()) {
            return "(unknown)";
        }
        return approvalId.length() <= 8 ? approvalId : approvalId.substring(0, 8);
    }

    private String stripCodeFences(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("```") && trimmed.endsWith("```")) {
            int firstLineBreak = trimmed.indexOf('\n');
            trimmed = firstLineBreak >= 0 ? trimmed.substring(firstLineBreak + 1, trimmed.length() - 3).trim() : trimmed;
        }
        return trimmed;
    }

    private String blankToPlaceholder(String value) {
        return value == null || value.isBlank() ? "(none)" : value;
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private String safeMessage(String message) {
        return message == null || message.isBlank() ? "Unknown error" : message;
    }

    private String escapeForJson(String value) {
        return safeMessage(value).replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private JsonNode parseObservation(String observation) {
        try {
            return objectMapper.readTree(observation);
        }
        catch (Exception exception) {
            return objectMapper.getNodeFactory().textNode(observation);
        }
    }

    private String reduceObservation(ToolAction action, String observation) {
        return toolObservationReducer == null ? observation : toolObservationReducer.reduce(action == null ? null : action.name(), observation);
    }

    private String createErrorObservation(String error) {
        return "{\"success\":false,\"error\":\"" + escapeForJson(error) + "\"}";
    }

    private String summarizeEditPlan(EditPlan editPlan) {
        if (editPlan == null || editPlan.edits().isEmpty()) {
            return "(none)";
        }
        String summary = blankToPlaceholder(editPlan.summary());
        String edits = editPlan.edits().stream()
                .map(edit -> edit.type() + " " + blankToPlaceholder(edit.path()) + ": " + blankToPlaceholder(edit.description()))
                .collect(Collectors.joining("; "));
        return summary + " | " + edits;
    }

    private void emitItem(List<TurnItem> items, Consumer<TurnItem> itemConsumer, TurnItem item) {
        items.add(item);
        if (itemConsumer != null) {
            itemConsumer.accept(item);
        }
    }

    private void emitCollabToolCall(ThreadId senderThreadId,
                                    ToolActionRequest action,
                                    List<TurnItem> items,
                                    Consumer<TurnItem> itemConsumer,
                                    CollabToolCallStatus status,
                                    org.dean.codex.protocol.item.CollabDeliveryState deliveryState,
                                    String observation,
                                    ThreadId newThreadId,
                                    List<ThreadId> receiverThreadIds,
                                    Map<String, AgentStatus> agentStatuses,
                                    Map<String, AgentMailboxState> mailboxes,
                                    String wakeupCause,
                                    String prompt) {
        if (!isCollaborationAction(action.action())) {
            return;
        }
        emitItem(items, itemConsumer, new CollabToolCallItem(
                new ItemId(UUID.randomUUID().toString()),
                collaborationToolName(action.action()),
                status,
                deliveryState,
                senderThreadId,
                receiverThreadIds == null ? List.of() : receiverThreadIds,
                newThreadId,
                prompt,
                agentStatuses == null ? Map.of() : agentStatuses,
                mailboxes == null ? Map.of() : mailboxes,
                wakeupCause,
                Instant.now()));
    }

    private String normalizeWakeupCause(String message, boolean timedOut) {
        if (timedOut) {
            return "timed_out";
        }
        if (message == null || message.isBlank()) {
            return null;
        }
        String normalized = message.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("mailbox updated") || normalized.contains("mailbox changed")) {
            return "mailbox_updated";
        }
        if (normalized.contains("produced a new turn")) {
            return "turn_result";
        }
        if (normalized.contains("status changed")) {
            return "status_changed";
        }
        return "mailbox_event";
    }

    private org.dean.codex.protocol.item.CollabDeliveryState collaborationDeliveryState(ToolAction action, boolean terminal) {
        if (terminal) {
            return switch (action) {
                case WAIT_AGENT -> org.dean.codex.protocol.item.CollabDeliveryState.WAKEUP;
                case SEND_MESSAGE -> org.dean.codex.protocol.item.CollabDeliveryState.QUEUED;
                case ASSIGN_TASK, SEND_INPUT, SPAWN_AGENT, RESUME_AGENT -> org.dean.codex.protocol.item.CollabDeliveryState.DISPATCHED;
                case CLOSE_AGENT -> org.dean.codex.protocol.item.CollabDeliveryState.COMPLETED;
                case LIST_AGENTS, READ_FILE, SEARCH_FILES, LIST_DIR, WEB_SEARCH, APPLY_PATCH, WRITE_FILE, RUN_COMMAND, EXEC_COMMAND, WRITE_STDIN -> null;
            };
        }
        return switch (action) {
            case SEND_MESSAGE -> org.dean.codex.protocol.item.CollabDeliveryState.QUEUED;
            case ASSIGN_TASK, SEND_INPUT, SPAWN_AGENT, RESUME_AGENT -> org.dean.codex.protocol.item.CollabDeliveryState.DISPATCHED;
            case WAIT_AGENT -> org.dean.codex.protocol.item.CollabDeliveryState.WAITING;
            case CLOSE_AGENT -> org.dean.codex.protocol.item.CollabDeliveryState.COMPLETED;
            case LIST_AGENTS, READ_FILE, SEARCH_FILES, LIST_DIR, WEB_SEARCH, APPLY_PATCH, WRITE_FILE, RUN_COMMAND, EXEC_COMMAND, WRITE_STDIN -> null;
        };
    }

    private Map<String, AgentMailboxState> mailboxStateMap(AgentControl agentControl, ThreadId agentThreadId) {
        if (agentControl == null || agentThreadId == null) {
            return Map.of();
        }
        AgentMailboxState mailboxState = agentControl.mailboxState(agentThreadId);
        if (mailboxState == null) {
            return Map.of();
        }
        return Map.of(agentThreadId.value(), mailboxState);
    }

    private boolean isCollaborationAction(ToolAction action) {
        return switch (action) {
            case SPAWN_AGENT, SEND_MESSAGE, ASSIGN_TASK, SEND_INPUT, WAIT_AGENT, RESUME_AGENT, CLOSE_AGENT -> true;
            case READ_FILE, SEARCH_FILES, LIST_DIR, WEB_SEARCH, APPLY_PATCH, WRITE_FILE, RUN_COMMAND, EXEC_COMMAND, WRITE_STDIN, LIST_AGENTS -> false;
        };
    }

    private boolean isParallelSafe(ToolAction action) {
        return action != null && toolCapabilityRegistry.supportsParallelExecution(action.name());
    }

    private String collaborationToolName(ToolAction action) {
        return switch (action) {
            case SPAWN_AGENT -> "spawn_agent";
            case SEND_MESSAGE -> "send_message";
            case ASSIGN_TASK -> "assign_task";
            case SEND_INPUT -> "send_input";
            case WAIT_AGENT -> "wait_agent";
            case RESUME_AGENT -> "resume_agent";
            case CLOSE_AGENT -> "close_agent";
            case LIST_AGENTS -> "list_agents";
            case READ_FILE -> "read_file";
            case SEARCH_FILES -> "search_files";
            case LIST_DIR -> "list_dir";
            case WEB_SEARCH -> "web_search";
            case APPLY_PATCH -> "apply_patch";
            case WRITE_FILE -> "write_file";
            case RUN_COMMAND -> "run_command";
            case EXEC_COMMAND -> "exec_command";
            case WRITE_STDIN -> "write_stdin";
        };
    }

    private List<ToolActionRequest> parseActions(JsonNode root) {
        if (root == null || root.isMissingNode()) {
            return List.of();
        }

        List<ToolActionRequest> actions = new ArrayList<>();
        JsonNode actionsNode = root.get("actions");
        if (actionsNode != null && actionsNode.isArray()) {
            for (JsonNode actionNode : actionsNode) {
                ToolActionRequest action = parseAction(actionNode);
                if (action != null) {
                    actions.add(action);
                }
            }
            return List.copyOf(actions);
        }

        ToolActionRequest legacyAction = parseAction(root);
        return legacyAction == null ? List.of() : List.of(legacyAction);
    }

    private EditPlan parseEditPlan(JsonNode planNode) {
        if (planNode == null || planNode.isMissingNode() || planNode.isNull()) {
            return null;
        }

        String summary = textValue(planNode.get("summary"));
        JsonNode editsNode = planNode.get("edits");
        if (editsNode == null || !editsNode.isArray()) {
            return summary.isBlank() ? null : new EditPlan(summary, List.of());
        }

        List<PlannedEdit> edits = new ArrayList<>();
        for (JsonNode editNode : editsNode) {
            PlannedEdit edit = parsePlannedEdit(editNode);
            if (edit != null) {
                edits.add(edit);
            }
        }
        return summary.isBlank() && edits.isEmpty() ? null : new EditPlan(summary, edits);
    }

    private PlannedEdit parsePlannedEdit(JsonNode editNode) {
        if (editNode == null || editNode.isMissingNode() || editNode.isNull()) {
            return null;
        }

        String path = textValue(editNode.get("path"));
        String description = firstNonBlank(textValue(editNode.get("description")), textValue(editNode.get("intent")));
        String typeText = textValue(editNode.get("type"));
        PlannedEditType type;
        try {
            type = typeText.isBlank() ? PlannedEditType.MODIFY : PlannedEditType.valueOf(typeText.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException exception) {
            type = PlannedEditType.MODIFY;
        }

        if (path.isBlank() && description.isBlank()) {
            return null;
        }
        return new PlannedEdit(path, type, description);
    }

    private ToolActionRequest parseAction(JsonNode actionNode) {
        if (actionNode == null || actionNode.isMissingNode()) {
            return null;
        }

        String actionText = textValue(actionNode.get("action"));
        if (actionText.isBlank()) {
            return null;
        }

        try {
            return new ToolActionRequest(
                    ToolAction.valueOf(normalizeActionName(actionText)),
                    textValue(actionNode.get("path")),
                    firstNonNullInteger(parseInteger(actionNode.get("maxDepth")), parseInteger(actionNode.get("max_depth"))),
                    textValue(actionNode.get("query")),
                    textValue(actionNode.get("oldText")),
                    textValue(actionNode.get("newText")),
                    actionNode != null && actionNode.path("replaceAll").asBoolean(false),
                    textValue(actionNode.get("content")),
                    textValue(actionNode.get("command")),
                    firstNonBlank(textValue(actionNode.get("sessionId")), textValue(actionNode.get("session_id"))),
                    firstNonBlank(textValue(actionNode.get("input")), textValue(actionNode.get("chars"))),
                    textValue(actionNode.get("threadId")),
                    parseThreadIds(actionNode.get("threadIds")),
                    textValue(actionNode.get("taskName")),
                    textValue(actionNode.get("prompt")),
                    textValue(actionNode.get("nickname")),
                    textValue(actionNode.get("role")),
                    parseInteger(actionNode.get("depth")),
                    textValue(actionNode.get("modelProvider")),
                    textValue(actionNode.get("model")),
                    textValue(actionNode.get("cwd")),
                    actionNode != null && actionNode.path("recursive").asBoolean(false),
                    firstNonNullInteger(parseInteger(actionNode.get("maxResults")), parseInteger(actionNode.get("max_results"))),
                    firstNonNullLong(parseLong(actionNode.get("timeoutMillis")), parseLong(actionNode.get("timeout_millis"))),
                    firstNonNullLong(parseLong(actionNode.get("yieldTimeMillis")), parseLong(actionNode.get("yield_time_ms"))),
                    firstNonNullLong(parseLong(actionNode.get("maxRuntimeMillis")), parseLong(actionNode.get("max_runtime_ms"))),
                    actionNode != null && actionNode.path("pty").asBoolean(false),
                    actionNode != null && actionNode.path("interrupt").asBoolean(false)
            );
        }
        catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String validateActions(List<ToolActionRequest> actions) {
        for (int index = 0; index < actions.size(); index++) {
            ToolActionRequest action = actions.get(index);
            int displayIndex = index + 1;
            switch (action.action()) {
                case READ_FILE -> {
                    if (action.path().isBlank()) {
                        return "Action %d (READ_FILE) requires a non-blank path.".formatted(displayIndex);
                    }
                }
                case SEARCH_FILES -> {
                    if (action.query().isBlank()) {
                        return "Action %d (SEARCH_FILES) requires a non-blank query.".formatted(displayIndex);
                    }
                }
                case LIST_DIR -> {
                    if (action.maxDepth() != null && action.maxDepth() < 0) {
                        return "Action %d (LIST_DIR) requires maxDepth >= 0 when provided.".formatted(displayIndex);
                    }
                }
                case WEB_SEARCH -> {
                    if (action.query().isBlank()) {
                        return "Action %d (WEB_SEARCH) requires a non-blank query.".formatted(displayIndex);
                    }
                    if (action.maxResults() != null && action.maxResults() < 1) {
                        return "Action %d (WEB_SEARCH) requires maxResults >= 1 when provided.".formatted(displayIndex);
                    }
                }
                case APPLY_PATCH -> {
                    if (action.path().isBlank()) {
                        return "Action %d (APPLY_PATCH) requires a non-blank path.".formatted(displayIndex);
                    }
                    if (action.oldText().isBlank()) {
                        return "Action %d (APPLY_PATCH) requires oldText.".formatted(displayIndex);
                    }
                }
                case WRITE_FILE -> {
                    if (action.path().isBlank()) {
                        return "Action %d (WRITE_FILE) requires a non-blank path.".formatted(displayIndex);
                    }
                    if (action.content().isBlank()) {
                        return "Action %d (WRITE_FILE) requires content.".formatted(displayIndex);
                    }
                }
                case RUN_COMMAND -> {
                    if (action.command().isBlank()) {
                        return "Action %d (RUN_COMMAND) requires a non-blank command.".formatted(displayIndex);
                    }
                }
                case EXEC_COMMAND -> {
                    if (action.command().isBlank()) {
                        return "Action %d (EXEC_COMMAND) requires a non-blank command.".formatted(displayIndex);
                    }
                    if (action.yieldTimeMillis() != null && action.yieldTimeMillis() < 0L) {
                        return "Action %d (EXEC_COMMAND) requires yieldTimeMillis >= 0 when provided.".formatted(displayIndex);
                    }
                    if (action.maxRuntimeMillis() != null && action.maxRuntimeMillis() < 0L) {
                        return "Action %d (EXEC_COMMAND) requires maxRuntimeMillis >= 0 when provided.".formatted(displayIndex);
                    }
                }
                case WRITE_STDIN -> {
                    if (action.sessionId().isBlank()) {
                        return "Action %d (WRITE_STDIN) requires a non-blank sessionId.".formatted(displayIndex);
                    }
                    if (action.yieldTimeMillis() != null && action.yieldTimeMillis() < 0L) {
                        return "Action %d (WRITE_STDIN) requires yieldTimeMillis >= 0 when provided.".formatted(displayIndex);
                    }
                }
                case SPAWN_AGENT -> {
                    if (action.taskName().isBlank()) {
                        return "Action %d (SPAWN_AGENT) requires a non-blank taskName.".formatted(displayIndex);
                    }
                    if (action.depth() != null && action.depth() < 0) {
                        return "Action %d (SPAWN_AGENT) requires depth >= 0 when provided.".formatted(displayIndex);
                    }
                }
                case SEND_MESSAGE, ASSIGN_TASK, SEND_INPUT, RESUME_AGENT, CLOSE_AGENT -> {
                    if (action.threadId().isBlank()) {
                        return "Action %d (%s) requires a non-blank threadId.".formatted(displayIndex, action.action());
                    }
                    if ((action.action() == ToolAction.SEND_MESSAGE
                            || action.action() == ToolAction.ASSIGN_TASK
                            || action.action() == ToolAction.SEND_INPUT)
                            && action.content().isBlank()) {
                        return "Action %d (%s) requires a non-blank content.".formatted(displayIndex, action.action());
                    }
                }
                case WAIT_AGENT -> {
                    if (action.timeoutMillis() != null && action.timeoutMillis() < 1L) {
                        return "Action %d (WAIT_AGENT) requires timeoutMillis >= 1 when provided.".formatted(displayIndex);
                    }
                }
                case LIST_AGENTS -> {
                    // no required fields
                }
            }
        }
        return null;
    }

    private String normalizeActionName(String actionText) {
        return actionText == null ? "" : actionText.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private AgentControl requireAgentControl() {
        AgentControl agentControl = agentControlSupplier.get();
        if (agentControl == null) {
            throw new IllegalStateException("Agent control is unavailable in this runtime.");
        }
        return agentControl;
    }

    private List<ThreadId> parseThreadIds(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            String value = textValue(node);
            return value.isBlank() ? List.of() : List.of(new ThreadId(value));
        }
        List<ThreadId> threadIds = new ArrayList<>();
        for (JsonNode threadIdNode : node) {
            String value = textValue(threadIdNode);
            if (!value.isBlank()) {
                threadIds.add(new ThreadId(value));
            }
        }
        return List.copyOf(threadIds);
    }

    private Integer parseInteger(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isInt() || node.isLong()) {
            return node.intValue();
        }
        String value = textValue(node);
        if (value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        }
        catch (NumberFormatException exception) {
            return null;
        }
    }

    private Long parseLong(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isLong() || node.isInt()) {
            return node.longValue();
        }
        String value = textValue(node);
        if (value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        }
        catch (NumberFormatException exception) {
            return null;
        }
    }

    private Long firstNonNullLong(Long first, Long second) {
        return first != null ? first : second;
    }

    private Integer firstNonNullInteger(Integer first, Integer second) {
        return first != null ? first : second;
    }

    private String textValue(JsonNode node) {
        return node == null || node.isNull() ? "" : node.asText("");
    }

    private AgentMessageItem agentMessageItem(String text) {
        return new AgentMessageItem(new ItemId(UUID.randomUUID().toString()), text, Instant.now());
    }

    private PlanItem planItem(EditPlan editPlan) {
        return new PlanItem(new ItemId(UUID.randomUUID().toString()), editPlan, Instant.now());
    }

    private ToolCallItem toolCallItem(String toolName, String target) {
        return new ToolCallItem(new ItemId(UUID.randomUUID().toString()), toolName, target, Instant.now());
    }

    private ToolResultItem toolResultItem(String toolName, String summary) {
        return new ToolResultItem(new ItemId(UUID.randomUUID().toString()), toolName, summary, Instant.now());
    }

    private ApprovalItem approvalItem(ApprovalState state, String approvalId, String command, String detail) {
        return new ApprovalItem(new ItemId(UUID.randomUUID().toString()), state, approvalId, command, detail, Instant.now());
    }

    private RuntimeErrorItem runtimeErrorItem(String message) {
        return new RuntimeErrorItem(new ItemId(UUID.randomUUID().toString()), message, Instant.now());
    }

    private ExecCommandTool requireExecCommandTool() {
        if (execCommandTool == null) {
            throw new IllegalStateException("Unified exec command tool is unavailable in this runtime.");
        }
        return execCommandTool;
    }

    enum ToolAction {
        READ_FILE,
        SEARCH_FILES,
        LIST_DIR,
        WEB_SEARCH,
        APPLY_PATCH,
        WRITE_FILE,
        RUN_COMMAND,
        EXEC_COMMAND,
        WRITE_STDIN,
        SPAWN_AGENT,
        SEND_MESSAGE,
        ASSIGN_TASK,
        SEND_INPUT,
        WAIT_AGENT,
        RESUME_AGENT,
        CLOSE_AGENT,
        LIST_AGENTS
    }

    record ToolActionRequest(ToolAction action,
                             String path,
                             Integer maxDepth,
                             String query,
                             String oldText,
                             String newText,
                             boolean replaceAll,
                             String content,
                             String command,
                             String sessionId,
                             String input,
                             String threadId,
                             List<ThreadId> threadIds,
                             String taskName,
                             String prompt,
                             String nickname,
                             String role,
                             Integer depth,
                             String modelProvider,
                             String model,
                             String cwd,
                             boolean recursive,
                             Integer maxResults,
                             Long timeoutMillis,
                             Long yieldTimeMillis,
                             Long maxRuntimeMillis,
                             boolean pty,
                             boolean interrupt) {
    }

    record PlannerStep(String summary,
                       EditPlan editPlan,
                       List<ToolActionRequest> actions,
                       String finalAnswer,
                       String validationError) {

        static PlannerStep actions(String summary, EditPlan editPlan, List<ToolActionRequest> actions) {
            return new PlannerStep(summary, editPlan, List.copyOf(actions), null, null);
        }

        static PlannerStep finish(String summary, EditPlan editPlan, String finalAnswer) {
            return new PlannerStep(summary, editPlan, List.of(), finalAnswer, null);
        }

        static PlannerStep invalid(String summary, EditPlan editPlan, List<ToolActionRequest> actions, String validationError) {
            return new PlannerStep(summary, editPlan, List.copyOf(actions), null, validationError);
        }

        boolean isFinished() {
            return finalAnswer != null && !finalAnswer.isBlank();
        }
    }

    private record ExecutionOutcome(TurnStatus status, List<TurnItem> items, String finalAnswer) {
    }

    List<ResolvedSkill> selectedSkillsForInput(String input) {
        return skillService.resolveSkills(input, false);
    }

    private SkillUseItem skillUseItem(List<SkillMetadata> selectedSkills) {
        return new SkillUseItem(new ItemId(UUID.randomUUID().toString()), selectedSkills, Instant.now());
    }

    record BatchExecutionOutcome(String observation,
                                 boolean awaitingApproval,
                                 List<String> approvalIds,
                                 List<ExecSessionObservation> execSessionObservations) {
    }

    private record ParallelActionExecution(int index,
                                           ToolActionRequest action,
                                           ActionExecutionOutcome outcome,
                                           List<TurnItem> items) {
    }

    private record ActionExecutionOutcome(String observation,
                                          boolean awaitingApproval,
                                          List<String> approvalIds,
                                          ExecSessionObservation execSessionObservation) {
    }

    private record ExecSessionObservation(String sessionId,
                                          String command,
                                          String status,
                                          Long processId,
                                          boolean pty,
                                          String stdoutPreview,
                                          String stderrPreview,
                                          boolean running,
                                          int pollIncrement) {
    }

    private record ActiveExecSessionState(String sessionId,
                                          String command,
                                          String status,
                                          Long processId,
                                          boolean pty,
                                          int pollCount,
                                          String stdoutPreview,
                                          String stderrPreview) {

        private static ActiveExecSessionState from(ExecSessionObservation observation) {
            return new ActiveExecSessionState(
                    observation.sessionId(),
                    observation.command(),
                    observation.status(),
                    observation.processId(),
                    observation.pty(),
                    observation.pollIncrement(),
                    observation.stdoutPreview(),
                    observation.stderrPreview());
        }

        private ActiveExecSessionState merge(ExecSessionObservation observation) {
            return new ActiveExecSessionState(
                    observation.sessionId(),
                    preferNonBlank(observation.command(), command),
                    preferNonBlank(observation.status(), status),
                    observation.processId() == null ? processId : observation.processId(),
                    observation.pty(),
                    pollCount + observation.pollIncrement(),
                    preferNonBlank(observation.stdoutPreview(), stdoutPreview),
                    preferNonBlank(observation.stderrPreview(), stderrPreview));
        }

        private PromptExecSessionContext toPromptContext() {
            return new PromptExecSessionContext(
                    sessionId,
                    command,
                    status,
                    processId,
                    pty,
                    pollCount,
                    stdoutPreview,
                    stderrPreview);
        }

        private String preferNonBlank(String first, String second) {
            return first == null || first.isBlank() ? second : first;
        }
    }
}
