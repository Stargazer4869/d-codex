package org.dean.codex.runtime.springai.prompt;

import java.util.List;

public record ResolvedPromptOutputContract(String responseFormat,
                                           String schemaText,
                                           List<String> rules,
                                           int maxActionsPerStep) {

    public ResolvedPromptOutputContract {
        responseFormat = responseFormat == null ? "" : responseFormat;
        schemaText = schemaText == null ? "" : schemaText;
        rules = rules == null ? List.of() : List.copyOf(rules);
    }
}
