package org.dean.codex.runtime.springai.prompt;

import java.util.List;

public interface UserInstructionsResolver {

    List<String> resolveUserInstructions();
}
