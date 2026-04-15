package org.dean.codex.core.model;

import java.util.List;

public record ModelToolSpec(ModelToolKind kind,
                            String name,
                            String description,
                            String inputSchema,
                            String outputSchema,
                            boolean supportsParallelExecution,
                            List<String> supplementaryInstructions) {

    public ModelToolSpec {
        kind = kind == null ? ModelToolKind.FUNCTION : kind;
        name = name == null ? "" : name;
        description = description == null ? "" : description;
        inputSchema = inputSchema == null ? "" : inputSchema;
        outputSchema = outputSchema == null ? "" : outputSchema;
        supplementaryInstructions = supplementaryInstructions == null ? List.of() : List.copyOf(supplementaryInstructions);
    }

    public ModelToolSpec(ModelToolKind kind,
                         String name,
                         String description,
                         String inputSchema,
                         String outputSchema,
                         List<String> supplementaryInstructions) {
        this(kind, name, description, inputSchema, outputSchema, false, supplementaryInstructions);
    }
}
