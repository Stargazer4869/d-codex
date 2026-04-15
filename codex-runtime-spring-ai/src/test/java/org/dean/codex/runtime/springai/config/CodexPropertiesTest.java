package org.dean.codex.runtime.springai.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CodexPropertiesTest {

    @Test
    void defaultsReflectUnifiedAgentAndModelSettings() {
        CodexProperties properties = new CodexProperties();

        assertEquals(100, properties.getAgent().getMaxSteps());
        assertEquals(3, properties.getAgent().getMaxActionsPerStep());
        assertEquals(8, properties.getAgent().getHistoryWindow());
        assertEquals(4, properties.getAgent().getMaxDepth());
        assertEquals(272_000, properties.getModel().getContextWindow());
        assertEquals(200_000, properties.getModel().getAutoCompactTokenLimit());
        assertFalse(properties.getModel().isEmitRawOutputItems());
        assertEquals("", properties.getModel().getReasoningEffort());
        assertEquals("", properties.getModel().getReasoningSummaryMode());
        assertEquals(32 * 1024, properties.getPrompt().getProjectDocMaxBytes());
        assertEquals("", properties.getPrompt().getBaseInstructionsText());
        assertEquals("", properties.getPrompt().getBaseInstructionsFile());
        assertEquals("", properties.getPrompt().getProjectInstructionsText());
        assertEquals("", properties.getPrompt().getProjectInstructionsFile());
        assertEquals("", properties.getPrompt().getUserInstructionsText());
        assertEquals("", properties.getPrompt().getUserInstructionsFile());
    }

    @Test
    @SuppressWarnings("deprecation")
    void legacyMaxActionsPerTurnAliasDelegatesToPerStepSetting() {
        CodexProperties properties = new CodexProperties();

        properties.getAgent().setMaxActionsPerTurn(5);

        assertEquals(5, properties.getAgent().getMaxActionsPerStep());
        assertEquals(5, properties.getAgent().getMaxActionsPerTurn());
    }

    @Test
    void modelReasoningSettingsRoundTrip() {
        CodexProperties properties = new CodexProperties();

        properties.getModel().setReasoningEffort("high");
        properties.getModel().setReasoningSummaryMode("concise");

        assertEquals("high", properties.getModel().getReasoningEffort());
        assertEquals("concise", properties.getModel().getReasoningSummaryMode());
    }
}
