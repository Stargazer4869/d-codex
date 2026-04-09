package org.dean.codex.runtime.springai.prompt;

import java.util.List;
import java.util.Objects;

public record ResolvedToolContract(List<ResolvedToolDefinition> visibleTools,
                                   boolean supportsParallelToolCalls) {

    public ResolvedToolContract {
        visibleTools = visibleTools == null ? List.of() : List.copyOf(visibleTools);
    }

    public List<String> visibleToolNames() {
        return visibleTools.stream()
                .map(ResolvedToolDefinition::name)
                .filter(name -> name != null && !name.isBlank())
                .toList();
    }

    public List<String> supplementaryInstructions() {
        return visibleTools.stream()
                .flatMap(tool -> tool.supplementaryInstructions().stream())
                .filter(instruction -> instruction != null && !instruction.isBlank())
                .distinct()
                .toList();
    }

    public List<String> plannerActionSchemaFragments() {
        return visibleTools.stream()
                .map(ResolvedToolDefinition::plannerActionSchemaFragment)
                .filter(fragment -> fragment != null && !fragment.isBlank())
                .filter(distinctByValue())
                .toList();
    }

    private static java.util.function.Predicate<String> distinctByValue() {
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        return value -> seen.add(Objects.requireNonNullElse(value, ""));
    }
}
