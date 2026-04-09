package org.dean.codex.runtime.springai.prompt;

import org.dean.codex.core.skill.ResolvedSkill;
import org.dean.codex.protocol.context.ReconstructedThreadContext;
import org.dean.codex.protocol.skill.SkillMetadata;

import java.util.List;

public interface PromptAssemblyService {

    ResolvedPrompt assemblePlannerPrompt(ReconstructedThreadContext reconstructedContext,
                                         String input,
                                         String scratchpad,
                                         int step,
                                         List<ResolvedSkill> selectedSkills,
                                         List<SkillMetadata> availableSkills,
                                         List<String> steeringInputs);

    String buildSystemPrompt(List<SkillMetadata> availableSkills);

    String buildUserPrompt(ReconstructedThreadContext reconstructedContext,
                           String input,
                           String scratchpad,
                           int step,
                           List<ResolvedSkill> selectedSkills,
                           List<String> steeringInputs);
}
