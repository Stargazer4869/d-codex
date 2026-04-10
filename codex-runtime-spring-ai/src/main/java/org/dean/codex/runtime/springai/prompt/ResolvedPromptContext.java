package org.dean.codex.runtime.springai.prompt;

import org.dean.codex.core.skill.ResolvedSkill;
import org.dean.codex.protocol.context.ReconstructedTurnActivity;
import org.dean.codex.protocol.conversation.ConversationMessage;
import org.dean.codex.protocol.conversation.ThreadId;

import java.util.List;

public record ResolvedPromptContext(ThreadId threadId,
                                    List<ConversationMessage> recentMessages,
                                    List<ReconstructedTurnActivity> recentActivities,
                                    List<PromptExecSessionContext> activeExecSessions,
                                    String latestUserRequest,
                                    String scratchpad,
                                    int step,
                                    int maxSteps,
                                    List<ResolvedSkill> selectedSkills,
                                    List<String> steeringInputs) {

    public ResolvedPromptContext {
        recentMessages = recentMessages == null ? List.of() : List.copyOf(recentMessages);
        recentActivities = recentActivities == null ? List.of() : List.copyOf(recentActivities);
        activeExecSessions = activeExecSessions == null ? List.of() : List.copyOf(activeExecSessions);
        latestUserRequest = latestUserRequest == null ? "" : latestUserRequest;
        scratchpad = scratchpad == null ? "" : scratchpad;
        selectedSkills = selectedSkills == null ? List.of() : List.copyOf(selectedSkills);
        steeringInputs = steeringInputs == null ? List.of() : List.copyOf(steeringInputs);
    }
}
