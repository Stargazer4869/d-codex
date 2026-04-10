package org.dean.codex.runtime.springai.prompt;

public record ToolCapability(ResolvedToolDefinition definition,
                             boolean supportsParallelExecution) {
}
