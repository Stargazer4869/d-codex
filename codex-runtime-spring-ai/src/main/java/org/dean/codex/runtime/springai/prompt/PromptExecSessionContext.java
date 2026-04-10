package org.dean.codex.runtime.springai.prompt;

public record PromptExecSessionContext(String sessionId,
                                       String command,
                                       String status,
                                       Long processId,
                                       boolean pty,
                                       int pollCount,
                                       String stdoutPreview,
                                       String stderrPreview) {

    public PromptExecSessionContext {
        sessionId = sessionId == null ? "" : sessionId;
        command = command == null ? "" : command;
        status = status == null ? "" : status;
        stdoutPreview = stdoutPreview == null ? "" : stdoutPreview;
        stderrPreview = stderrPreview == null ? "" : stderrPreview;
    }
}
