package org.dean.codex.runtime.springai.model;

import org.dean.codex.core.model.ModelReasoningConfig;

final class ChatClientSystemInstructionRenderer {

    private ChatClientSystemInstructionRenderer() {
    }

    static String renderSystemInstructions(String systemInstructions, ModelReasoningConfig reasoningConfig) {
        String baseInstructions = systemInstructions == null ? "" : systemInstructions;
        if (reasoningConfig == null
                || (reasoningConfig.effort().isBlank() && reasoningConfig.summaryMode().isBlank())) {
            return baseInstructions;
        }
        StringBuilder builder = new StringBuilder("Reasoning configuration for this request:");
        if (!reasoningConfig.effort().isBlank()) {
            builder.append(System.lineSeparator()).append("- effort: ").append(reasoningConfig.effort());
        }
        if (!reasoningConfig.summaryMode().isBlank()) {
            builder.append(System.lineSeparator()).append("- summary: ").append(reasoningConfig.summaryMode());
        }
        builder.append(System.lineSeparator())
                .append("Use these settings when applicable. Do not change the required output contract.")
                .append(System.lineSeparator())
                .append(System.lineSeparator())
                .append(baseInstructions);
        return builder.toString();
    }
}
