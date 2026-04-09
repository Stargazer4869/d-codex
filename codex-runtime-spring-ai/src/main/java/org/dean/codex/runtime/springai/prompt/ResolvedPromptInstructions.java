package org.dean.codex.runtime.springai.prompt;

import java.util.List;

public record ResolvedPromptInstructions(String baseText,
                                         List<String> developerSections,
                                         List<String> userSections) {

    public ResolvedPromptInstructions {
        baseText = baseText == null ? "" : baseText;
        developerSections = developerSections == null ? List.of() : List.copyOf(developerSections);
        userSections = userSections == null ? List.of() : List.copyOf(userSections);
    }
}
