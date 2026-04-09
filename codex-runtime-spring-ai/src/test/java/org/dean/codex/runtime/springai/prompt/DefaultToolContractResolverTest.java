package org.dean.codex.runtime.springai.prompt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultToolContractResolverTest {

    @Test
    void resolvePlannerToolContractIncludesVisibleToolsAndSchemaFragments() {
        DefaultToolContractResolver resolver = new DefaultToolContractResolver();

        ResolvedToolContract toolContract = resolver.resolvePlannerToolContract();

        assertTrue(toolContract.visibleToolNames().contains("READ_FILE"));
        assertTrue(toolContract.visibleToolNames().contains("send_message"));
        assertTrue(toolContract.visibleToolNames().contains("assign_task"));
        assertTrue(toolContract.visibleToolNames().contains("send_input"));
        assertTrue(toolContract.supplementaryInstructions().contains("Prefer APPLY_PATCH for targeted edits over rewriting whole files."));
        assertTrue(toolContract.plannerActionSchemaFragments().stream()
                .anyMatch(fragment -> fragment.contains("\"action\": \"send_message\"")));
        assertTrue(toolContract.plannerActionSchemaFragments().stream()
                .anyMatch(fragment -> fragment.contains("\"action\": \"assign_task\"")));
        assertTrue(toolContract.plannerActionSchemaFragments().stream()
                .anyMatch(fragment -> fragment.contains("\"action\": \"send_input\"")));
    }
}
