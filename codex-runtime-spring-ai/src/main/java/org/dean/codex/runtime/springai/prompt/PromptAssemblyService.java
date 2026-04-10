package org.dean.codex.runtime.springai.prompt;

import org.dean.codex.core.skill.ResolvedSkill;
import org.dean.codex.protocol.context.ReconstructedThreadContext;
import org.dean.codex.protocol.skill.SkillMetadata;

import java.util.List;

public interface PromptAssemblyService {

    default ResolvedPrompt assemblePlannerPrompt(ReconstructedThreadContext reconstructedContext,
                                                 String input,
                                                 String scratchpad,
                                                 int step,
                                                 List<ResolvedSkill> selectedSkills,
                                                 List<SkillMetadata> availableSkills,
                                                 List<String> steeringInputs) {
        return assemblePlannerPrompt(
                reconstructedContext,
                input,
                scratchpad,
                step,
                selectedSkills,
                availableSkills,
                steeringInputs,
                List.of());
    }

    ResolvedPrompt assemblePlannerPrompt(ReconstructedThreadContext reconstructedContext,
                                         String input,
                                         String scratchpad,
                                         int step,
                                         List<ResolvedSkill> selectedSkills,
                                         List<SkillMetadata> availableSkills,
                                         List<String> steeringInputs,
                                         List<PromptExecSessionContext> activeExecSessions);

    String buildSystemPrompt(List<SkillMetadata> availableSkills);

    default String buildUserPrompt(ReconstructedThreadContext reconstructedContext,
                                   String input,
                                   String scratchpad,
                                   int step,
                                   List<ResolvedSkill> selectedSkills,
                                   List<String> steeringInputs) {
        return buildUserPrompt(
                reconstructedContext,
                input,
                scratchpad,
                step,
                selectedSkills,
                steeringInputs,
                List.of());
    }

    String buildUserPrompt(ReconstructedThreadContext reconstructedContext,
                           String input,
                           String scratchpad,
                           int step,
                           List<ResolvedSkill> selectedSkills,
                           List<String> steeringInputs,
                           List<PromptExecSessionContext> activeExecSessions);
}
