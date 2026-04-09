package org.dean.codex.runtime.springai.prompt;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultToolContractPromptRendererTest {

    @Test
    void renderListsAvailableToolsFromDefinitions() {
        DefaultToolContractPromptRenderer renderer = new DefaultToolContractPromptRenderer();
        ResolvedToolContract toolContract = new ResolvedToolContract(List.of(
                new ResolvedToolDefinition("READ_FILE", "read a file", "", List.of()),
                new ResolvedToolDefinition("spawn_agent", "delegate work", "", List.of())), false);

        String rendered = renderer.render(toolContract);

        assertTrue(rendered.contains("Available actions:"));
        assertTrue(rendered.contains("- READ_FILE: read a file"));
        assertTrue(rendered.contains("- spawn_agent: delegate work"));
    }
}
