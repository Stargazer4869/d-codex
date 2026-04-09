package org.dean.codex.runtime.springai.prompt;

import java.time.Instant;

public class DefaultThreadPromptSnapshotResolver implements ThreadPromptSnapshotResolver {

    private final BaseInstructionsResolver baseInstructionsResolver;
    private final UserInstructionsResolver userInstructionsResolver;

    public DefaultThreadPromptSnapshotResolver(BaseInstructionsResolver baseInstructionsResolver,
                                               UserInstructionsResolver userInstructionsResolver) {
        this.baseInstructionsResolver = baseInstructionsResolver;
        this.userInstructionsResolver = userInstructionsResolver;
    }

    @Override
    public ThreadPromptSnapshot resolveCurrentSnapshot() {
        return new ThreadPromptSnapshot(
                baseInstructionsResolver.resolveBaseInstructions(),
                userInstructionsResolver.resolveUserInstructions(),
                Instant.now());
    }
}
