package org.dean.codex.cli.tui;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.input.MouseAction;
import com.googlecode.lanterna.input.MouseActionType;
import org.dean.codex.cli.config.CliApprovalMode;
import org.dean.codex.cli.config.CliConfigOverrides;
import org.dean.codex.cli.config.CliSandboxMode;
import org.dean.codex.cli.interactive.SlashCommandSpec;
import org.dean.codex.cli.launch.CliLaunchRequest;
import org.dean.codex.core.appserver.CodexAppServerSession;
import org.dean.codex.core.approval.CommandApprovalService;
import org.dean.codex.protocol.approval.ApprovalStatus;
import org.dean.codex.protocol.approval.CommandApprovalRequest;
import org.dean.codex.protocol.appserver.AgentMailboxUpdatedNotification;
import org.dean.codex.protocol.appserver.AppServerNotification;
import org.dean.codex.protocol.appserver.CommandExecutionCompletedNotification;
import org.dean.codex.protocol.appserver.CommandExecutionOutputDeltaNotification;
import org.dean.codex.protocol.appserver.ConfigGetParams;
import org.dean.codex.protocol.appserver.ConfigUpdateParams;
import org.dean.codex.protocol.appserver.ModelListParams;
import org.dean.codex.protocol.appserver.SkillsListParams;
import org.dean.codex.protocol.appserver.ThreadCompactStartParams;
import org.dean.codex.protocol.appserver.ThreadForkParams;
import org.dean.codex.protocol.appserver.ThreadListResponse;
import org.dean.codex.protocol.appserver.ThreadMetadataUpdatedNotification;
import org.dean.codex.protocol.appserver.ThreadNameUpdatedNotification;
import org.dean.codex.protocol.appserver.ThreadReadParams;
import org.dean.codex.protocol.appserver.ThreadReadResponse;
import org.dean.codex.protocol.appserver.ThreadResumeParams;
import org.dean.codex.protocol.appserver.ThreadStartParams;
import org.dean.codex.protocol.appserver.ThreadStatusChangedNotification;
import org.dean.codex.protocol.appserver.TurnCompletedNotification;
import org.dean.codex.protocol.appserver.TurnInterruptParams;
import org.dean.codex.protocol.appserver.TurnItemNotification;
import org.dean.codex.protocol.appserver.TurnResumeParams;
import org.dean.codex.protocol.appserver.TurnStartParams;
import org.dean.codex.protocol.appserver.TurnStartedNotification;
import org.dean.codex.protocol.appserver.TurnSteerParams;
import org.dean.codex.protocol.appserver.TurnSteerResponse;
import org.dean.codex.protocol.conversation.ConversationTurn;
import org.dean.codex.protocol.conversation.ThreadId;
import org.dean.codex.protocol.conversation.ThreadSource;
import org.dean.codex.protocol.conversation.ThreadSummary;
import org.dean.codex.protocol.conversation.TurnStatus;
import org.dean.codex.protocol.item.AgentMessageItem;
import org.dean.codex.protocol.item.ApprovalItem;
import org.dean.codex.protocol.item.CollabToolCallItem;
import org.dean.codex.protocol.item.MailboxMessageItem;
import org.dean.codex.protocol.item.PlanItem;
import org.dean.codex.protocol.item.RawModelOutputItem;
import org.dean.codex.protocol.item.ReasoningItem;
import org.dean.codex.protocol.item.RuntimeErrorItem;
import org.dean.codex.protocol.item.SkillUseItem;
import org.dean.codex.protocol.item.ToolCallItem;
import org.dean.codex.protocol.item.ToolResultItem;
import org.dean.codex.protocol.item.TurnItem;
import org.dean.codex.protocol.item.UserImageItem;
import org.dean.codex.protocol.item.UserMessageItem;
import org.dean.codex.protocol.runtime.RuntimeTurn;
import org.dean.codex.protocol.skill.SkillMetadata;
import org.dean.codex.protocol.tool.ShellCommandResult;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class CodexTuiRunner {

    private static final Duration INPUT_POLL = Duration.ofMillis(60);
    private static final Duration FORCED_REDRAW = Duration.ofSeconds(2);
    private static final List<SlashCommandSpec> TUI_COMMANDS = List.of(
            new SlashCommandSpec("help", "/help", "Show TUI commands", List.of(), false, true),
            new SlashCommandSpec("new", "/new", "Start a new thread", List.of(), false, false),
            new SlashCommandSpec("threads", "/threads", "Open the session picker", List.of(), false, true),
            new SlashCommandSpec("resume", "/resume [thread-id-prefix]", "Switch to a thread", List.of(), true, false),
            new SlashCommandSpec("agent", "/agent [use <thread-id-prefix>]", "Navigate the current thread tree", List.of(), true, true),
            new SlashCommandSpec("model", "/model", "Open the model picker", List.of(), false, true),
            new SlashCommandSpec("skills", "/skills", "Show discovered skills", List.of(), false, true),
            new SlashCommandSpec("history", "/history", "Reload active thread history", List.of(), false, true),
            new SlashCommandSpec("compact", "/compact", "Compact the active thread", List.of(), false, true),
            new SlashCommandSpec("approvals", "/approvals", "Open pending approvals", List.of(), false, true),
            new SlashCommandSpec("interrupt", "/interrupt", "Interrupt the active turn", List.of(), false, true));

    private final CodexAppServerSession session;
    private final CommandApprovalService approvalService;
    private final CliLaunchRequest launchRequest;
    private final ThreadId initialThreadId;
    private final TuiAppState state = new TuiAppState();
    private final LinkedBlockingQueue<AppServerNotification> notifications = new LinkedBlockingQueue<>();

    public CodexTuiRunner(CodexAppServerSession session,
                          CommandApprovalService approvalService,
                          CliLaunchRequest launchRequest,
                          ThreadId initialThreadId) {
        this.session = session;
        this.approvalService = approvalService;
        this.launchRequest = launchRequest == null ? CliLaunchRequest.of() : launchRequest;
        this.initialThreadId = initialThreadId;
    }

    public static boolean interactiveTerminalAvailable() {
        String term = System.getenv("TERM");
        return System.console() != null && (term == null || !term.equalsIgnoreCase("dumb"));
    }

    public ThreadId run(String initialPrompt, boolean preferFreshPromptOnStart) {
        state.preferFreshPromptOnStart(preferFreshPromptOnStart);
        try (TerminalDriver terminal = new LanternaTerminalDriver()) {
            return run(terminal, initialPrompt);
        }
        catch (IOException exception) {
            throw new IllegalStateException("Unable to start terminal UI.", exception);
        }
    }

    ThreadId run(TerminalDriver terminal, String initialPrompt) throws IOException {
        try (AutoCloseable ignored = session.subscribe(notifications::offer)) {
            loadThread(initialThreadId);
            refreshSessions();
            refreshConfig();
            if (initialPrompt != null && !initialPrompt.isBlank()) {
                submitPlainInput(initialPrompt.trim());
            }
            boolean dirty = true;
            long nextForcedRedrawAt = 0L;
            while (!state.exitRequested()) {
                dirty = drainNotifications() || dirty;
                long now = System.nanoTime();
                if (dirty || now >= nextForcedRedrawAt) {
                    terminal.draw(state);
                    dirty = false;
                    nextForcedRedrawAt = now + FORCED_REDRAW.toNanos();
                }
                KeyStroke key = terminal.pollInput(INPUT_POLL);
                if (key != null) {
                    dirty = handleKey(key) || dirty;
                }
            }
            return state.activeThreadId();
        }
        catch (IOException exception) {
            throw exception;
        }
        catch (Exception exception) {
            throw new IllegalStateException("Terminal UI failed.", exception);
        }
    }

    private void loadThread(ThreadId threadId) {
        ThreadId targetThreadId = threadId == null ? createThread() : threadId;
        ThreadReadResponse response = session.threadRead(new ThreadReadParams(targetThreadId));
        state.activeThread(response.thread());
        List<ThreadSummary> tree = new ArrayList<>();
        if (response.thread() != null) {
            tree.add(response.thread());
        }
        tree.addAll(response.relatedThreads());
        state.replaceRelatedThreads(tree);
        state.replaceTranscript(cellsFromTurns(response.turns()));
        response.turns().stream()
                .filter(turn -> turn.status() == TurnStatus.RUNNING || turn.status() == TurnStatus.AWAITING_APPROVAL)
                .max(Comparator.comparing(ConversationTurn::startedAt))
                .ifPresentOrElse(
                        turn -> state.activeTurn(new RuntimeTurn(turn.threadId(), turn.turnId(), turn.status(), turn.startedAt(), turn.completedAt())),
                        () -> state.activeTurn(null));
        refreshConfig();
        state.statusMessage("Active thread " + shortThreadId(targetThreadId));
    }

    private ThreadId createThread() {
        CliConfigOverrides overrides = launchRequest.configOverrides();
        return session.threadStart(new ThreadStartParams(
                "Thread",
                sandboxValue(overrides.sandbox()),
                approvalValue(overrides.approvalMode()))).thread().threadId();
    }

    private List<TranscriptCell> cellsFromTurns(List<ConversationTurn> turns) {
        List<TranscriptCell> cells = new ArrayList<>();
        for (ConversationTurn turn : turns == null ? List.<ConversationTurn>of() : turns) {
            boolean sawUserItem = false;
            for (TurnItem item : turn.items()) {
                if (item instanceof UserMessageItem) {
                    sawUserItem = true;
                    break;
                }
            }
            if (!sawUserItem && turn.userInput() != null && !turn.userInput().isBlank()) {
                cells.add(new TranscriptCell("user", "user", turn.userInput()));
            }
            for (TurnItem item : turn.items()) {
                cells.add(cellFromItem(item));
            }
            if (turn.finalAnswer() != null && !turn.finalAnswer().isBlank()
                    && turn.items().stream().noneMatch(AgentMessageItem.class::isInstance)) {
                cells.add(new TranscriptCell("assistant", "assistant", turn.finalAnswer()));
            }
        }
        return cells;
    }

    private TranscriptCell cellFromItem(TurnItem item) {
        if (item instanceof UserMessageItem user) {
            return new TranscriptCell("user", "user", user.text());
        }
        if (item instanceof UserImageItem image) {
            return new TranscriptCell("user", "image", image.imageUrl() + (image.detail().isBlank() ? "" : " (" + image.detail() + ")"));
        }
        if (item instanceof AgentMessageItem message) {
            return new TranscriptCell("assistant", "assistant", message.text());
        }
        if (item instanceof ReasoningItem reasoning) {
            return new TranscriptCell("reasoning", "reasoning", firstNonBlank(reasoning.summary(), reasoning.detail()));
        }
        if (item instanceof ToolCallItem toolCall) {
            return new TranscriptCell("tool", "tool call", toolCall.toolName() + " " + blankToEmpty(toolCall.target()));
        }
        if (item instanceof ToolResultItem toolResult) {
            return new TranscriptCell("tool", "tool result", toolResult.toolName() + ": " + blankToEmpty(toolResult.summary()));
        }
        if (item instanceof ApprovalItem approval) {
            return new TranscriptCell("approval", "approval " + approval.state().name().toLowerCase(Locale.ROOT),
                    blankToEmpty(approval.command()) + "\n" + blankToEmpty(approval.detail()));
        }
        if (item instanceof PlanItem plan) {
            StringBuilder body = new StringBuilder(plan.plan() == null ? "" : plan.plan().summary());
            if (plan.plan() != null) {
                plan.plan().edits().forEach(edit -> body.append("\n")
                        .append(edit.type())
                        .append(" ")
                        .append(edit.path())
                        .append(" ")
                        .append(edit.description()));
            }
            return new TranscriptCell("plan", "plan", body.toString());
        }
        if (item instanceof MailboxMessageItem mailbox) {
            return new TranscriptCell("mailbox", "mailbox", mailbox.text());
        }
        if (item instanceof CollabToolCallItem collab) {
            return new TranscriptCell("agent", "collab " + collab.status().name().toLowerCase(Locale.ROOT),
                    firstNonBlank(collab.prompt(), collab.tool()));
        }
        if (item instanceof SkillUseItem skills) {
            return new TranscriptCell("skill", "skills", skills.skills().stream()
                    .map(SkillMetadata::name)
                    .toList()
                    .toString());
        }
        if (item instanceof RuntimeErrorItem error) {
            return new TranscriptCell("error", "error", error.message());
        }
        if (item instanceof RawModelOutputItem raw) {
            return new TranscriptCell("model", "model " + raw.modelItemType(), summarize(raw.payloadJson(), 300));
        }
        return new TranscriptCell("item", "item", item.toString());
    }

    private boolean drainNotifications() {
        boolean changed = false;
        AppServerNotification notification;
        while ((notification = notifications.poll()) != null) {
            handleNotification(notification);
            changed = true;
        }
        return changed;
    }

    private void handleNotification(AppServerNotification notification) {
        if (notification instanceof TurnStartedNotification started && sameThread(started.turn().threadId())) {
            state.activeTurn(started.turn());
            state.statusMessage("Turn running");
            return;
        }
        if (notification instanceof TurnItemNotification itemNotification
                && itemNotification.turn() != null
                && sameThread(itemNotification.turn().threadId())
                && itemNotification.item() != null) {
            state.appendCell(cellFromItem(itemNotification.item()));
            return;
        }
        if (notification instanceof TurnCompletedNotification completed && completed.turn() != null && sameThread(completed.turn().threadId())) {
            state.activeTurn(null);
            if (completed.finalAnswer() != null && !completed.finalAnswer().isBlank()) {
                state.appendCell(new TranscriptCell("assistant", "assistant", completed.finalAnswer()));
            }
            state.statusMessage("Turn completed");
            refreshThread();
            return;
        }
        if (notification instanceof CommandExecutionOutputDeltaNotification output && output.commandExecution() != null
                && sameThread(output.commandExecution().threadId())) {
            if (output.stdout() != null && !output.stdout().isBlank()) {
                state.appendCell(new TranscriptCell("command", "stdout", output.stdout()));
            }
            if (output.stderr() != null && !output.stderr().isBlank()) {
                state.appendCell(new TranscriptCell("command", "stderr", output.stderr()));
            }
            return;
        }
        if (notification instanceof CommandExecutionCompletedNotification completed && completed.commandExecution() != null
                && sameThread(completed.commandExecution().threadId())) {
            state.appendCell(new TranscriptCell("command", "command", "completed " + completed.commandExecution().status()));
            return;
        }
        if (notification instanceof ThreadMetadataUpdatedNotification updated && updated.thread() != null
                && sameThread(updated.thread().threadId())) {
            state.activeThread(updated.thread());
            refreshConfig();
            return;
        }
        if (notification instanceof ThreadNameUpdatedNotification updated && updated.thread() != null
                && sameThread(updated.thread().threadId())) {
            state.activeThread(updated.thread());
            return;
        }
        if (notification instanceof ThreadStatusChangedNotification status && status.thread() != null
                && sameThread(status.thread().threadId())) {
            state.activeThread(status.thread());
            state.statusMessage("Thread " + status.thread().status());
            return;
        }
        if (notification instanceof AgentMailboxUpdatedNotification mailbox && mailbox.mailbox() != null
                && mailbox.mailbox().threadId() != null
                && sameThread(mailbox.mailbox().threadId())) {
            state.statusMessage("Mailbox pending=" + mailbox.mailbox().pendingMessages());
        }
    }

    private boolean sameThread(ThreadId threadId) {
        return threadId != null && threadId.equals(state.activeThreadId());
    }

    private boolean handleKey(KeyStroke key) {
        if (key.getKeyType() == KeyType.EOF) {
            state.requestExit();
            return true;
        }
        if (key.getKeyType() == KeyType.MouseEvent) {
            return handleMouseEvent(key);
        }
        if (key.isCtrlDown() && key.getKeyType() == KeyType.Character
                && key.getCharacter() != null
                && Character.toLowerCase(key.getCharacter()) == 'c') {
            handleCtrlC();
            return true;
        }
        if (state.overlayOpen()) {
            return handleOverlayKey(key);
        }
        return switch (key.getKeyType()) {
            case Character -> handleCharacter(key.getCharacter());
            case Backspace -> {
                backspaceComposer();
                yield true;
            }
            case Enter -> {
                submitComposer();
                yield true;
            }
            case Escape -> {
                state.composer("");
                yield true;
            }
            case ArrowUp -> {
                state.scrollTranscript(3);
                yield true;
            }
            case ArrowDown -> {
                state.scrollTranscript(-3);
                yield true;
            }
            case PageUp -> {
                state.scrollTranscript(12);
                yield true;
            }
            case PageDown -> {
                state.scrollTranscript(-12);
                yield true;
            }
            case Home -> {
                state.scrollTranscript(Integer.MAX_VALUE / 4);
                yield true;
            }
            case End -> {
                state.scrollTranscriptToBottom();
                yield true;
            }
            default -> false;
        };
    }

    private boolean handleMouseEvent(KeyStroke key) {
        if (!(key instanceof MouseAction mouseAction)) {
            return false;
        }
        if (mouseAction.getActionType() == MouseActionType.SCROLL_UP) {
            state.scrollTranscript(5);
            return true;
        }
        if (mouseAction.getActionType() == MouseActionType.SCROLL_DOWN) {
            state.scrollTranscript(-5);
            return true;
        }
        return false;
    }

    private boolean handleCharacter(Character character) {
        if (character == null) {
            return false;
        }
        if (character == '/' && state.composer().isEmpty()) {
            state.composer("/");
            openSlashOverlay();
            return true;
        }
        state.composer(state.composer() + character);
        if (state.composer().startsWith("/") && !state.composer().contains(" ")) {
            openSlashOverlay();
        }
        return true;
    }

    private boolean handleOverlayKey(KeyStroke key) {
        PickerOverlay overlay = state.overlay();
        return switch (key.getKeyType()) {
            case ArrowUp -> {
                overlay.moveSelection(-1);
                yield true;
            }
            case ArrowDown -> {
                overlay.moveSelection(1);
                yield true;
            }
            case Escape -> {
                state.overlay(null);
                yield true;
            }
            case Backspace -> {
                if (overlay.kind() == PickerOverlay.Kind.SLASH) {
                    backspaceComposer();
                    if (!state.composer().startsWith("/")) {
                        state.overlay(null);
                    }
                    else {
                        overlay.setFilter(state.composer().substring(1));
                    }
                }
                else if (!overlay.filter().isBlank()) {
                    overlay.setFilter(overlay.filter().substring(0, overlay.filter().length() - 1));
                }
                else {
                    state.overlay(null);
                }
                yield true;
            }
            case Enter -> {
                acceptOverlaySelection();
                yield true;
            }
            case Character -> {
                Character character = key.getCharacter();
                if (character == null) {
                    yield false;
                }
                if (overlay.kind() == PickerOverlay.Kind.APPROVALS && Character.toLowerCase(character) == 'r') {
                    rejectSelectedApproval();
                    yield true;
                }
                if (overlay.kind() == PickerOverlay.Kind.APPROVALS && Character.toLowerCase(character) == 'a') {
                    approveSelectedApproval();
                    yield true;
                }
                if (overlay.kind() == PickerOverlay.Kind.SLASH) {
                    state.composer(state.composer() + character);
                    overlay.setFilter(state.composer().substring(1));
                }
                else {
                    overlay.setFilter(overlay.filter() + character);
                }
                yield true;
            }
            default -> false;
        };
    }

    private void acceptOverlaySelection() {
        PickerOverlay overlay = state.overlay();
        PickerItem selected = overlay.selectedItem();
        if (selected == null) {
            if (overlay.kind() == PickerOverlay.Kind.SLASH) {
                executeSlashComposerCommand();
            }
            return;
        }
        switch (overlay.kind()) {
            case SLASH -> acceptSlashOverlaySelection(selected);
            case AGENT, RESUME -> switchThread(new ThreadId(selected.id()));
            case MODEL -> selectModel(selected.id());
            case APPROVALS -> approveSelectedApproval();
            case SKILLS, HELP -> state.overlay(null);
        }
    }

    private void acceptSlashOverlaySelection(PickerItem selected) {
        String input = state.composer().trim();
        if (shouldExecuteSlashComposer(input, selected)) {
            executeSlashComposerCommand();
            return;
        }
        acceptSlashSelection(selected.id());
    }

    private boolean shouldExecuteSlashComposer(String input, PickerItem selected) {
        if (input == null || !input.startsWith("/")) {
            return false;
        }
        String body = input.substring(1).trim();
        if (body.isBlank()) {
            return false;
        }
        return body.contains(" ") || selected == null || body.equalsIgnoreCase(selected.id());
    }

    private void executeSlashComposerCommand() {
        String input = state.consumeComposer();
        state.overlay(null);
        if (input.isBlank()) {
            return;
        }
        executeSlashCommand(input);
    }

    private void acceptSlashSelection(String commandName) {
        state.overlay(null);
        switch (commandName) {
            case "agent" -> openAgentOverlay();
            case "threads" -> openResumeOverlay();
            case "resume" -> openResumeOverlay();
            case "model" -> openModelOverlay();
            case "skills" -> openSkillsOverlay();
            case "approvals" -> openApprovalsOverlay();
            case "help" -> openHelpOverlay();
            case "new" -> newThread();
            case "interrupt" -> interruptTurn();
            case "compact" -> compactThread();
            case "history" -> refreshThread();
            default -> state.composer("/" + commandName + " ");
        }
    }

    private void submitComposer() {
        String input = state.consumeComposer();
        state.overlay(null);
        if (input.isBlank()) {
            return;
        }
        if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) {
            state.requestExit();
            return;
        }
        if (input.startsWith("/")) {
            executeSlashCommand(input);
            return;
        }
        submitPlainInput(input);
    }

    private void executeSlashCommand(String input) {
        List<String> tokens = List.of(input.substring(1).trim().split("\\s+"));
        if (tokens.isEmpty() || tokens.get(0).isBlank()) {
            openSlashOverlay();
            return;
        }
        String command = tokens.get(0).toLowerCase(Locale.ROOT);
        String args = input.substring(1).trim().length() == command.length()
                ? ""
                : input.substring(input.indexOf(command) + command.length()).trim();
        switch (command) {
            case "help" -> openHelpOverlay();
            case "new" -> newThread();
            case "threads" -> openResumeOverlay();
            case "agent" -> {
                if (args.startsWith("use ")) {
                    switchThread(resolveThreadByPrefix(args.substring(4).trim()));
                }
                else {
                    openAgentOverlay();
                }
            }
            case "resume" -> {
                if (args.isBlank()) {
                    openResumeOverlay();
                }
                else {
                    switchThread(resolveThreadByPrefix(args));
                }
            }
            case "model" -> openModelOverlay();
            case "skills" -> openSkillsOverlay();
            case "approvals" -> openApprovalsOverlay();
            case "interrupt" -> interruptTurn();
            case "compact" -> compactThread();
            case "history" -> refreshThread();
            default -> state.statusMessage("Unknown command: /" + command);
        }
    }

    private void submitPlainInput(String input) {
        if (state.activeThreadId() == null) {
            loadThread(createThread());
        }
        if (state.hasActiveTurn() && !state.preferFreshPromptOnStart()) {
            TurnSteerResponse response = session.turnSteer(new TurnSteerParams(state.activeThreadId(), state.activeTurnId(), input));
            if (response.accepted()) {
                state.statusMessage("Input queued for active turn");
                return;
            }
            state.statusMessage("Active turn is not steerable yet");
            return;
        }
        state.preferFreshPromptOnStart(false);
        RuntimeTurn turn = session.turnStart(new TurnStartParams(state.activeThreadId(), input)).turn();
        state.activeTurn(turn);
        state.statusMessage("Turn running");
    }

    private void handleCtrlC() {
        if (state.hasActiveTurn()) {
            interruptTurn();
        }
        else {
            state.requestExit();
        }
    }

    private void interruptTurn() {
        if (!state.hasActiveTurn()) {
            state.statusMessage("No active turn to interrupt");
            return;
        }
        boolean accepted = session.turnInterrupt(new TurnInterruptParams(state.activeThreadId(), state.activeTurnId())).accepted();
        state.statusMessage(accepted ? "Interrupt requested" : "Interrupt was not accepted");
    }

    private void compactThread() {
        if (state.activeThreadId() == null) {
            return;
        }
        var response = session.threadCompactStart(new ThreadCompactStartParams(state.activeThreadId()));
        state.appendCell(new TranscriptCell("memory", "compact", response.threadMemory() == null ? "Compaction completed" : response.threadMemory().summary()));
        state.statusMessage("Thread compacted");
    }

    private void newThread() {
        ThreadId threadId = createThread();
        loadThread(threadId);
        refreshSessions();
    }

    private void switchThread(ThreadId threadId) {
        if (threadId == null) {
            state.statusMessage("No matching thread");
            return;
        }
        session.threadResume(new ThreadResumeParams(threadId));
        loadThread(threadId);
        state.overlay(null);
    }

    private void selectModel(String modelId) {
        session.configUpdate(new ConfigUpdateParams(state.activeThreadId(), "openai", modelId, null, null, null));
        refreshConfig();
        refreshThread();
        state.overlay(null);
        state.statusMessage("Model set to " + modelId);
    }

    private void approveSelectedApproval() {
        PickerItem selected = state.overlay() == null ? null : state.overlay().selectedItem();
        if (selected == null) {
            return;
        }
        CommandApprovalRequest approval = approvalService.approve(state.activeThreadId(), selected.id());
        printApprovalResult(approval, true);
        openApprovalsOverlay();
    }

    private void rejectSelectedApproval() {
        PickerItem selected = state.overlay() == null ? null : state.overlay().selectedItem();
        if (selected == null) {
            return;
        }
        CommandApprovalRequest approval = approvalService.reject(state.activeThreadId(), selected.id(), "Rejected from TUI");
        printApprovalResult(approval, false);
        openApprovalsOverlay();
    }

    private void printApprovalResult(CommandApprovalRequest approval, boolean approved) {
        state.appendCell(new TranscriptCell("approval", approved ? "approved" : "rejected",
                shortApprovalId(approval) + " " + approval.command()));
        ShellCommandResult result = approval.executionResult();
        if (result != null) {
            state.appendCell(new TranscriptCell("approval", "approval result",
                    "success=" + result.success() + " exitCode=" + result.exitCode()
                            + "\n" + blankToEmpty(result.stdout())
                            + "\n" + blankToEmpty(result.stderr())
                            + "\n" + blankToEmpty(result.error())));
        }
        if (approval.turnId() != null) {
            RuntimeTurn resumed = session.turnResume(new TurnResumeParams(state.activeThreadId(), approval.turnId())).turn();
            state.activeTurn(resumed);
        }
    }

    private void refreshThread() {
        if (state.activeThreadId() != null) {
            loadThread(state.activeThreadId());
        }
    }

    private void refreshSessions() {
        ThreadListResponse response = session.threadList();
        state.replaceSessions(response.threads().stream()
                .filter(thread -> !thread.archived())
                .toList());
    }

    private void refreshConfig() {
        state.config(session.configGet(new ConfigGetParams(state.activeThreadId())));
    }

    private void openSlashOverlay() {
        String filter = state.composer().startsWith("/") ? state.composer().substring(1) : "";
        PickerOverlay overlay = new PickerOverlay(
                PickerOverlay.Kind.SLASH,
                "Commands",
                "Type to filter commands",
                TUI_COMMANDS.stream()
                        .map(command -> new PickerItem(command.name(), command.syntax(), command.description()))
                        .toList());
        overlay.setFilter(filter);
        state.overlay(overlay);
    }

    private void openAgentOverlay() {
        List<PickerItem> items = state.relatedThreads().stream()
                .map(thread -> new PickerItem(thread.threadId().value(), agentLabel(thread), agentDetail(thread)))
                .toList();
        state.overlay(new PickerOverlay(PickerOverlay.Kind.AGENT, "Agents", "Enter switch  Esc close", items));
        state.composer("");
    }

    private void openResumeOverlay() {
        refreshSessions();
        List<PickerItem> items = state.sessions().stream()
                .map(thread -> new PickerItem(thread.threadId().value(), resumeLabel(thread), resumeDetail(thread)))
                .toList();
        state.overlay(new PickerOverlay(PickerOverlay.Kind.RESUME, "Resume Session", "Enter resume  Esc close", items));
        state.composer("");
    }

    private void openModelOverlay() {
        List<PickerItem> items = session.modelList(new ModelListParams(state.activeThreadId())).models().stream()
                .map(model -> new PickerItem(model.id(), model.displayName() + (model.current() ? " [current]" : ""), model.provider()))
                .toList();
        state.overlay(new PickerOverlay(PickerOverlay.Kind.MODEL, "Model", "Enter select  Esc close", items));
        state.composer("");
    }

    private void openSkillsOverlay() {
        List<PickerItem> items = session.skillsList(new SkillsListParams(false)).skills().stream()
                .map(skill -> new PickerItem(skill.name(), skill.name() + (skill.enabled() ? "" : " [disabled]"),
                        firstNonBlank(skill.shortDescription(), skill.description())))
                .toList();
        state.overlay(new PickerOverlay(PickerOverlay.Kind.SKILLS, "Skills", "Esc close", items));
        state.composer("");
    }

    private void openApprovalsOverlay() {
        List<PickerItem> items = approvalService.approvals(state.activeThreadId()).stream()
                .filter(approval -> approval.status() == ApprovalStatus.PENDING)
                .map(approval -> new PickerItem(approval.approvalId().value(), shortApprovalId(approval) + " " + approval.command(),
                        approval.reason()))
                .toList();
        if (items.isEmpty()) {
            items = List.of(new PickerItem("none", "No pending approvals", "", true));
        }
        state.overlay(new PickerOverlay(PickerOverlay.Kind.APPROVALS, "Approvals", "Enter/a approve  r reject  Esc close", items));
        state.composer("");
    }

    private void openHelpOverlay() {
        List<PickerItem> items = new ArrayList<>();
        for (SlashCommandSpec command : TUI_COMMANDS) {
            items.add(new PickerItem(command.name(), command.syntax(), command.description()));
        }
        state.overlay(new PickerOverlay(PickerOverlay.Kind.HELP, "Help", "Esc close", items));
        state.composer("");
    }

    private ThreadId resolveThreadByPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return null;
        }
        refreshSessions();
        Map<String, ThreadSummary> matches = new LinkedHashMap<>();
        for (ThreadSummary thread : state.sessions()) {
            if (thread.threadId().value().startsWith(prefix)) {
                matches.put(thread.threadId().value(), thread);
            }
        }
        for (ThreadSummary thread : state.relatedThreads()) {
            if (thread.threadId().value().startsWith(prefix)) {
                matches.put(thread.threadId().value(), thread);
            }
        }
        if (matches.size() == 1) {
            return matches.values().iterator().next().threadId();
        }
        state.statusMessage(matches.isEmpty() ? "No matching thread" : "Thread prefix is ambiguous");
        return null;
    }

    private void backspaceComposer() {
        String composer = state.composer();
        if (!composer.isEmpty()) {
            state.composer(composer.substring(0, composer.length() - 1));
        }
    }

    private String agentLabel(ThreadSummary thread) {
        if (thread.source() != ThreadSource.SUB_AGENT) {
            return "Main [default]";
        }
        String nickname = blankToEmpty(thread.agentNickname());
        String role = blankToEmpty(thread.agentRole());
        if (!nickname.isBlank() && !role.isBlank()) {
            return nickname + " [" + role + "]";
        }
        if (!nickname.isBlank()) {
            return nickname;
        }
        if (!role.isBlank()) {
            return "[" + role + "]";
        }
        return firstNonBlank(thread.title(), "Agent");
    }

    private String agentDetail(ThreadSummary thread) {
        List<String> details = new ArrayList<>();
        details.add(shortThreadId(thread.threadId()));
        details.add(thread.status().name().toLowerCase(Locale.ROOT));
        if (thread.agentClosedAt() != null) {
            details.add("closed");
        }
        return String.join(" ", details);
    }

    private String resumeLabel(ThreadSummary thread) {
        return shortThreadId(thread.threadId()) + " " + thread.title();
    }

    private String resumeDetail(ThreadSummary thread) {
        List<String> details = new ArrayList<>();
        details.add("turns=" + thread.turnCount());
        if (thread.cwd() != null) {
            details.add(thread.cwd());
        }
        if (thread.preview() != null) {
            details.add(summarize(thread.preview(), 80));
        }
        return String.join(" | ", details);
    }

    private String sandboxValue(CliSandboxMode mode) {
        return mode == null ? null : mode.cliValue();
    }

    private String approvalValue(CliApprovalMode mode) {
        return mode == null ? null : mode.cliValue();
    }

    private String shortThreadId(ThreadId threadId) {
        if (threadId == null || threadId.value() == null) {
            return "(unknown)";
        }
        return threadId.value().length() <= 8 ? threadId.value() : threadId.value().substring(0, 8);
    }

    private String shortApprovalId(CommandApprovalRequest approval) {
        if (approval == null || approval.approvalId() == null || approval.approvalId().value() == null) {
            return "(unknown)";
        }
        String value = approval.approvalId().value();
        return value.length() <= 8 ? value : value.substring(0, 8);
    }

    private String summarize(String text, int maxLength) {
        String normalized = text == null ? "" : text.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 1)) + "~";
    }

    private String firstNonBlank(String first, String second) {
        String normalized = first == null ? "" : first.trim();
        return normalized.isEmpty() ? blankToEmpty(second) : normalized;
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
