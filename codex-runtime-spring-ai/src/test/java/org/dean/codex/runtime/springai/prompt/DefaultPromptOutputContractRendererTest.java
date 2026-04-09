package org.dean.codex.runtime.springai.prompt;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultPromptOutputContractRendererTest {

    @Test
    void renderProducesRulesBlockWithoutDuplicatingJsonInstruction() {
        DefaultPromptOutputContractRenderer renderer = new DefaultPromptOutputContractRenderer();
        ResolvedPromptOutputContract outputContract = new ResolvedPromptOutputContract(
                "json",
                "{\"actions\":[]}",
                List.of(
                        "Return JSON only. Do not wrap it in prose.",
                        "Do not return more than 3 actions in one step."),
                3);

        String rendered = renderer.render(outputContract);

        assertTrue(rendered.contains("Rules:"));
        assertTrue(rendered.contains("- Use this schema exactly:"));
        assertTrue(rendered.contains("{\"actions\":[]}"));
        assertTrue(rendered.contains("- Do not return more than 3 actions in one step."));
        assertFalse(rendered.contains("Return JSON only. Do not wrap it in prose.\n- Return JSON only. Do not wrap it in prose."));
    }
}
