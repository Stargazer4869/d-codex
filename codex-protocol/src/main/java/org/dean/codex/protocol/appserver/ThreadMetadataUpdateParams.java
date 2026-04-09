package org.dean.codex.protocol.appserver;

import org.dean.codex.protocol.conversation.ThreadId;

public record ThreadMetadataUpdateParams(ThreadId threadId,
                                         String cwd,
                                         String modelProvider,
                                         String model,
                                         String sandboxMode,
                                         String approvalMode,
                                         String gitSha,
                                         String gitBranch,
                                         String gitOriginUrl,
                                         String cliVersion) {

    public ThreadMetadataUpdateParams(ThreadId threadId,
                                      String cwd,
                                      String modelProvider,
                                      String model) {
        this(threadId, cwd, modelProvider, model, null, null, null, null, null, null);
    }

    public ThreadMetadataUpdateParams(ThreadId threadId,
                                      String cwd,
                                      String modelProvider,
                                      String model,
                                      String sandboxMode,
                                      String approvalMode) {
        this(threadId, cwd, modelProvider, model, sandboxMode, approvalMode, null, null, null, null);
    }

    public ThreadMetadataUpdateParams(ThreadId threadId,
                                      String cwd,
                                      String modelProvider,
                                      String model,
                                      String sandboxMode,
                                      String approvalMode,
                                      String gitSha,
                                      String gitBranch,
                                      String gitOriginUrl) {
        this(threadId, cwd, modelProvider, model, sandboxMode, approvalMode, gitSha, gitBranch, gitOriginUrl, null);
    }
}
