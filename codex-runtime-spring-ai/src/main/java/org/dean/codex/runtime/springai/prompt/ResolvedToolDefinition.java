package org.dean.codex.runtime.springai.prompt;

import java.util.List;

public record ResolvedToolDefinition(String name,
                                     String description,
                                     String plannerActionSchemaFragment,
                                     String inputSchema,
                                     String outputSchema,
                                     boolean supportsParallelExecution,
                                     List<String> supplementaryInstructions) {

    public ResolvedToolDefinition {
        name = name == null ? "" : name;
        description = description == null ? "" : description;
        plannerActionSchemaFragment = plannerActionSchemaFragment == null ? "" : plannerActionSchemaFragment;
        inputSchema = inputSchema == null ? "" : inputSchema;
        outputSchema = outputSchema == null ? "" : outputSchema;
        supplementaryInstructions = supplementaryInstructions == null ? List.of() : List.copyOf(supplementaryInstructions);
    }

    public ResolvedToolDefinition(String name,
                                  String description,
                                  String plannerActionSchemaFragment,
                                  List<String> supplementaryInstructions) {
        this(name,
                description,
                plannerActionSchemaFragment,
                plannerActionSchemaFragment,
                "",
                false,
                supplementaryInstructions);
    }
}
