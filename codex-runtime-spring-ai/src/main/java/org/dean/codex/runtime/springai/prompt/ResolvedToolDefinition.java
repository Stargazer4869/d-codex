package org.dean.codex.runtime.springai.prompt;

import java.util.List;

public record ResolvedToolDefinition(String name,
                                     String description,
                                     String plannerActionSchemaFragment,
                                     List<String> supplementaryInstructions) {

    public ResolvedToolDefinition {
        name = name == null ? "" : name;
        description = description == null ? "" : description;
        plannerActionSchemaFragment = plannerActionSchemaFragment == null ? "" : plannerActionSchemaFragment;
        supplementaryInstructions = supplementaryInstructions == null ? List.of() : List.copyOf(supplementaryInstructions);
    }
}
