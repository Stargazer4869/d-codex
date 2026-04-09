package org.dean.codex.runtime.springai.prompt;

public class DefaultToolContractPromptRenderer implements ToolContractPromptRenderer {

    @Override
    public String render(ResolvedToolContract toolContract) {
        String lineSeparator = System.lineSeparator();
        StringBuilder prompt = new StringBuilder("Available actions:").append(lineSeparator);
        for (ResolvedToolDefinition tool : toolContract.visibleTools()) {
            prompt.append("- ")
                    .append(tool.name())
                    .append(": ")
                    .append(tool.description())
                    .append(lineSeparator);
        }
        return prompt.toString().stripTrailing();
    }
}
