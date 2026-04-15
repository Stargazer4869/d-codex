package org.dean.codex.runtime.springai.config;

import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "codex")
public class CodexProperties {

    private String workspaceRoot = "";
    private String storageRoot = "";
    private final Agent agent = new Agent();
    private final Model model = new Model();
    private final Shell shell = new Shell();
    private final Skills skills = new Skills();
    private final Context context = new Context();
    private final Prompt prompt = new Prompt();

    public String getWorkspaceRoot() {
        return workspaceRoot;
    }

    public void setWorkspaceRoot(String workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    public String getStorageRoot() {
        return storageRoot;
    }

    public void setStorageRoot(String storageRoot) {
        this.storageRoot = storageRoot;
    }

    public Agent getAgent() {
        return agent;
    }

    public Model getModel() {
        return model;
    }

    public Shell getShell() {
        return shell;
    }

    public Skills getSkills() {
        return skills;
    }

    public Context getContext() {
        return context;
    }

    public Prompt getPrompt() {
        return prompt;
    }

    public static class Agent {
        private int maxSteps = 100;
        private int maxActionsPerStep = 3;
        private int historyWindow = 8;
        private int maxDepth = 4;

        public int getMaxSteps() {
            return maxSteps;
        }

        public void setMaxSteps(int maxSteps) {
            this.maxSteps = maxSteps;
        }

        public int getMaxActionsPerStep() {
            return maxActionsPerStep;
        }

        public void setMaxActionsPerStep(int maxActionsPerStep) {
            this.maxActionsPerStep = maxActionsPerStep;
        }

        @Deprecated
        @DeprecatedConfigurationProperty(replacement = "codex.agent.max-actions-per-step")
        public int getMaxActionsPerTurn() {
            return getMaxActionsPerStep();
        }

        @Deprecated
        public void setMaxActionsPerTurn(int maxActionsPerTurn) {
            setMaxActionsPerStep(maxActionsPerTurn);
        }

        public int getHistoryWindow() {
            return historyWindow;
        }

        public void setHistoryWindow(int historyWindow) {
            this.historyWindow = historyWindow;
        }

        public int getMaxDepth() {
            return maxDepth;
        }

        public void setMaxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
        }
    }

    public static class Model {
        private int contextWindow = 272_000;
        private int autoCompactTokenLimit = 200_000;
        private boolean emitRawOutputItems = false;
        private String reasoningEffort = "";
        private String reasoningSummaryMode = "";

        public int getContextWindow() {
            return contextWindow;
        }

        public void setContextWindow(int contextWindow) {
            this.contextWindow = contextWindow;
        }

        public int getAutoCompactTokenLimit() {
            return autoCompactTokenLimit;
        }

        public void setAutoCompactTokenLimit(int autoCompactTokenLimit) {
            this.autoCompactTokenLimit = autoCompactTokenLimit;
        }

        public boolean isEmitRawOutputItems() {
            return emitRawOutputItems;
        }

        public void setEmitRawOutputItems(boolean emitRawOutputItems) {
            this.emitRawOutputItems = emitRawOutputItems;
        }

        public String getReasoningEffort() {
            return reasoningEffort;
        }

        public void setReasoningEffort(String reasoningEffort) {
            this.reasoningEffort = reasoningEffort;
        }

        public String getReasoningSummaryMode() {
            return reasoningSummaryMode;
        }

        public void setReasoningSummaryMode(String reasoningSummaryMode) {
            this.reasoningSummaryMode = reasoningSummaryMode;
        }
    }

    public static class Shell {
        private String approvalMode = "review-sensitive";
        private int timeoutSeconds = 60;

        public String getApprovalMode() {
            return approvalMode;
        }

        public void setApprovalMode(String approvalMode) {
            this.approvalMode = approvalMode;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    public static class Skills {
        private boolean enabled = true;
        private String userRoot = "";
        private String workspaceRelativeRoot = ".codex/skills";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUserRoot() {
            return userRoot;
        }

        public void setUserRoot(String userRoot) {
            this.userRoot = userRoot;
        }

        public String getWorkspaceRelativeRoot() {
            return workspaceRelativeRoot;
        }

        public void setWorkspaceRelativeRoot(String workspaceRelativeRoot) {
            this.workspaceRelativeRoot = workspaceRelativeRoot;
        }
    }

    public static class Context {
        private int preserveRecentTurns = 4;

        public int getPreserveRecentTurns() {
            return preserveRecentTurns;
        }

        public void setPreserveRecentTurns(int preserveRecentTurns) {
            this.preserveRecentTurns = preserveRecentTurns;
        }
    }

    public static class Prompt {
        private int projectDocMaxBytes = 32 * 1024;
        private String baseInstructionsText = "";
        private String baseInstructionsFile = "";
        private String projectInstructionsText = "";
        private String projectInstructionsFile = "";
        private String userInstructionsText = "";
        private String userInstructionsFile = "";

        public int getProjectDocMaxBytes() {
            return projectDocMaxBytes;
        }

        public void setProjectDocMaxBytes(int projectDocMaxBytes) {
            this.projectDocMaxBytes = projectDocMaxBytes;
        }

        public String getBaseInstructionsText() {
            return baseInstructionsText;
        }

        public void setBaseInstructionsText(String baseInstructionsText) {
            this.baseInstructionsText = baseInstructionsText;
        }

        public String getBaseInstructionsFile() {
            return baseInstructionsFile;
        }

        public void setBaseInstructionsFile(String baseInstructionsFile) {
            this.baseInstructionsFile = baseInstructionsFile;
        }

        public String getProjectInstructionsText() {
            return projectInstructionsText;
        }

        public void setProjectInstructionsText(String projectInstructionsText) {
            this.projectInstructionsText = projectInstructionsText;
        }

        public String getProjectInstructionsFile() {
            return projectInstructionsFile;
        }

        public void setProjectInstructionsFile(String projectInstructionsFile) {
            this.projectInstructionsFile = projectInstructionsFile;
        }

        public String getUserInstructionsText() {
            return userInstructionsText;
        }

        public void setUserInstructionsText(String userInstructionsText) {
            this.userInstructionsText = userInstructionsText;
        }

        public String getUserInstructionsFile() {
            return userInstructionsFile;
        }

        public void setUserInstructionsFile(String userInstructionsFile) {
            this.userInstructionsFile = userInstructionsFile;
        }
    }
}
