package org.dean.codex.runtime.springai.prompt;

public interface ToolContractPromptRenderer {

    String render(ResolvedToolContract toolContract);
}
