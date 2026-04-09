package org.dean.codex.runtime.springai.prompt;

public class DefaultPromptOutputContractRenderer implements PromptOutputContractRenderer {

    @Override
    public String render(ResolvedPromptOutputContract outputContract) {
        String lineSeparator = System.lineSeparator();
        StringBuilder prompt = new StringBuilder();
        prompt.append("Rules:").append(lineSeparator);
        if ("json".equalsIgnoreCase(outputContract.responseFormat())) {
            prompt.append("- Return JSON only. Do not wrap it in prose.").append(lineSeparator);
        }
        prompt.append("- Use this schema exactly:").append(lineSeparator);
        prompt.append(outputContract.schemaText()).append(lineSeparator);
        for (String rule : outputContract.rules()) {
            if ("Return JSON only. Do not wrap it in prose.".equals(rule)
                    && "json".equalsIgnoreCase(outputContract.responseFormat())) {
                continue;
            }
            prompt.append("- ").append(rule).append(lineSeparator);
        }
        return prompt.toString().stripTrailing();
    }
}
