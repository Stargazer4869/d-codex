package org.dean.codex.runtime.springai.prompt;

public record ResolvedPrompt(ResolvedPromptInstructions instructions,
                             ResolvedToolContract toolContract,
                             ResolvedPromptContext context,
                             ResolvedPromptOutputContract outputContract,
                             String systemPrompt,
                             String userPrompt) {

    public ResolvedPrompt {
        if (instructions == null) {
            throw new IllegalArgumentException("instructions must not be null");
        }
        if (toolContract == null) {
            throw new IllegalArgumentException("toolContract must not be null");
        }
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (outputContract == null) {
            throw new IllegalArgumentException("outputContract must not be null");
        }
        systemPrompt = systemPrompt == null ? "" : systemPrompt;
        userPrompt = userPrompt == null ? "" : userPrompt;
    }
}
