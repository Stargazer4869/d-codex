package org.dean.codex.runtime.springai.prompt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultToolContractResolverTest {

    @Test
    void resolvePlannerToolContractIncludesVisibleToolsAndSchemaFragments() {
        DefaultToolContractResolver resolver = new DefaultToolContractResolver();

        ResolvedToolContract toolContract = resolver.resolvePlannerToolContract();

        assertTrue(toolContract.supportsParallelToolCalls());
        assertTrue(toolContract.visibleToolNames().contains("READ_FILE"));
        assertTrue(toolContract.visibleToolNames().contains("LIST_DIR"));
        assertTrue(toolContract.visibleToolNames().contains("WEB_SEARCH"));
        assertTrue(toolContract.visibleToolNames().contains("send_message"));
        assertTrue(toolContract.visibleToolNames().contains("assign_task"));
        assertTrue(toolContract.visibleToolNames().contains("send_input"));
        assertTrue(toolContract.visibleToolNames().contains("exec_command"));
        assertTrue(toolContract.visibleToolNames().contains("write_stdin"));
        assertTrue(toolContract.supplementaryInstructions().contains("Prefer APPLY_PATCH for targeted edits over rewriting whole files."));
        assertTrue(toolContract.plannerActionSchemaFragments().stream()
                .anyMatch(fragment -> fragment.contains("\"action\": \"LIST_DIR\"")));
        assertTrue(toolContract.plannerActionSchemaFragments().stream()
                .anyMatch(fragment -> fragment.contains("\"action\": \"WEB_SEARCH\"")));
        assertTrue(toolContract.plannerActionSchemaFragments().stream()
                .anyMatch(fragment -> fragment.contains("\"action\": \"send_message\"")));
        assertTrue(toolContract.plannerActionSchemaFragments().stream()
                .anyMatch(fragment -> fragment.contains("\"action\": \"assign_task\"")));
        assertTrue(toolContract.plannerActionSchemaFragments().stream()
                .anyMatch(fragment -> fragment.contains("\"action\": \"send_input\"")));
        assertTrue(toolContract.plannerActionSchemaFragments().stream()
                .anyMatch(fragment -> fragment.contains("\"action\": \"exec_command\"")));
        assertTrue(toolContract.plannerActionSchemaFragments().stream()
                .anyMatch(fragment -> fragment.contains("\"action\": \"write_stdin\"")));

        ResolvedToolDefinition readFile = toolContract.visibleTools().stream()
                .filter(tool -> "READ_FILE".equals(tool.name()))
                .findFirst()
                .orElseThrow();
        assertNotNull(readFile.inputSchema());
        assertFalse(readFile.inputSchema().isBlank());
        assertNotNull(readFile.outputSchema());
        assertFalse(readFile.outputSchema().isBlank());
        assertTrue(readFile.supportsParallelExecution());
    }
}
