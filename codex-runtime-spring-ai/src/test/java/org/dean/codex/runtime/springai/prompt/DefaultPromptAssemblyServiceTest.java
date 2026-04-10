package org.dean.codex.runtime.springai.prompt;

import org.dean.codex.core.skill.ResolvedSkill;
import org.dean.codex.protocol.context.ReconstructedThreadContext;
import org.dean.codex.protocol.context.ReconstructedTurnActivity;
import org.dean.codex.protocol.context.ThreadMemory;
import org.dean.codex.protocol.conversation.ConversationMessage;
import org.dean.codex.protocol.conversation.MessageRole;
import org.dean.codex.protocol.conversation.ThreadId;
import org.dean.codex.protocol.conversation.TurnId;
import org.dean.codex.protocol.skill.SkillMetadata;
import org.dean.codex.protocol.skill.SkillScope;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultPromptAssemblyServiceTest {

    @Test
    void assemblePlannerPromptSeparatesLayersAndPreservesRenderedContext() {
        DefaultPromptAssemblyService service = new DefaultPromptAssemblyService(Path.of("/tmp/workspace"), 100, 3);
        ThreadId threadId = new ThreadId("thread-1");
        Instant now = Instant.now();
        SkillMetadata skillMetadata = new SkillMetadata(
                "reviewer",
                "Review code for bugs.",
                "Review code for bugs.",
                "/tmp/reviewer/SKILL.md",
                SkillScope.USER,
                true);
        ReconstructedThreadContext reconstructedContext = new ReconstructedThreadContext(
                threadId,
                new ThreadMemory("memory-1", threadId, "summary", List.of(), 0, now),
                List.of(new ConversationMessage(new TurnId("turn-1"), MessageRole.USER, "Replay canonical history", now)),
                List.of(),
                List.of(new ReconstructedTurnActivity(new TurnId("turn-1"), "historyToolCall", "toolCall: READ_FILE README.md", now)),
                now);

        ResolvedPrompt prompt = service.assemblePlannerPrompt(
                reconstructedContext,
                "Please use $reviewer",
                "",
                1,
                List.of(new ResolvedSkill(skillMetadata, "# reviewer\n\nReview carefully.")),
                List.of(skillMetadata),
                List.of("Stay focused on the README"),
                List.of(new PromptExecSessionContext(
                        "exec-session-1",
                        "npm test",
                        "RUNNING",
                        12345L,
                        false,
                        1,
                        "first chunk",
                        "")));

        assertTrue(prompt.instructions().baseText().contains("You are Codex"));
        assertEquals("json", prompt.outputContract().responseFormat());
        assertEquals(3, prompt.outputContract().maxActionsPerStep());
        assertEquals(threadId, prompt.context().threadId());
        assertTrue(prompt.toolContract().visibleToolNames().contains("READ_FILE"));
        assertTrue(prompt.toolContract().visibleToolNames().contains("LIST_DIR"));
        assertTrue(prompt.toolContract().visibleToolNames().contains("WEB_SEARCH"));
        assertTrue(prompt.toolContract().visibleToolNames().contains("spawn_agent"));
        assertTrue(prompt.toolContract().visibleToolNames().contains("exec_command"));
        assertTrue(prompt.systemPrompt().contains("Workspace root: /tmp/workspace"));
        assertTrue(prompt.systemPrompt().contains("Available actions:"));
        assertTrue(prompt.systemPrompt().contains("Available skills:"));
        assertTrue(prompt.systemPrompt().contains("Do not return more than 3 actions in one step."));
        assertTrue(prompt.systemPrompt().contains("Prefer LIST_DIR to discover directory structure before reading full files."));
        assertTrue(prompt.systemPrompt().contains("Prefer WEB_SEARCH for external documentation, ecosystem facts, or recent references when local search is not enough."));
        assertTrue(prompt.outputContract().schemaText().contains("\"action\": \"send_message\""));
        assertTrue(prompt.outputContract().schemaText().contains("\"action\": \"assign_task\""));
        assertTrue(prompt.outputContract().schemaText().contains("\"action\": \"LIST_DIR\""));
        assertTrue(prompt.outputContract().schemaText().contains("\"action\": \"exec_command\""));
        assertTrue(prompt.outputContract().schemaText().contains("\"action\": \"write_stdin\""));
        assertTrue(prompt.userPrompt().contains("USER: Replay canonical history"));
        assertTrue(prompt.userPrompt().contains("toolCall: READ_FILE README.md"));
        assertTrue(prompt.userPrompt().contains("Active exec sessions:"));
        assertTrue(prompt.userPrompt().contains("sessionId: exec-session-1"));
        assertTrue(prompt.userPrompt().contains("command: npm test"));
        assertTrue(prompt.userPrompt().contains("Skill: reviewer"));
        assertTrue(prompt.userPrompt().contains("Stay focused on the README"));
        assertFalse(prompt.userPrompt().contains("Available skills:"));
    }

    @Test
    void buildSystemPromptOmitsAvailableSkillsSectionWhenNoSkillsAreVisible() {
        DefaultPromptAssemblyService service = new DefaultPromptAssemblyService(Path.of("/tmp/workspace"), 100, 3);

        String prompt = service.buildSystemPrompt(List.of());

        assertTrue(prompt.contains("You are Codex"));
        assertTrue(prompt.contains("Available actions:"));
        assertTrue(prompt.contains("Rules:"));
        assertFalse(prompt.contains("Available skills:"));
    }

    @Test
    void buildSystemPromptUsesInjectedBaseInstructionsAndOutputContractRenderer() {
        DefaultPromptAssemblyService service = new DefaultPromptAssemblyService(
                () -> "Custom base instructions",
                () -> List.of("Project instructions:\nFollow repo style."),
                () -> new ResolvedToolContract(List.of(
                        new ResolvedToolDefinition(
                                "CUSTOM_TOOL",
                                "custom tool description",
                                """
                                {
                                  "action": "custom_tool"
                                }""",
                                List.of("Custom tool guidance"))), false),
                toolContract -> "Custom tool contract",
                outputContract -> "Custom output contract",
                100,
                3);

        String prompt = service.buildSystemPrompt(List.of());

        assertTrue(prompt.contains("Custom base instructions"));
        assertTrue(prompt.contains("Project instructions:\nFollow repo style."));
        assertTrue(prompt.contains("Custom tool contract"));
        assertTrue(prompt.contains("Custom output contract"));
        assertFalse(prompt.contains("Available actions:"));
        assertFalse(prompt.contains("Rules:"));
    }

    @Test
    void assemblePlannerPromptPrefersPersistedThreadPromptSnapshot() {
        ThreadId threadId = new ThreadId("thread-1");
        ThreadPromptStateStore promptStateStore = new InMemoryThreadPromptStateStore();
        promptStateStore.write(threadId, new ThreadPromptSnapshot(
                "Persisted base instructions",
                List.of("Project instructions:\nPersisted project rules."),
                Instant.parse("2026-04-09T00:00:00Z")));
        DefaultPromptAssemblyService service = new DefaultPromptAssemblyService(
                () -> "Current base instructions",
                () -> List.of("Project instructions:\nCurrent project rules."),
                promptStateStore,
                () -> new ResolvedToolContract(List.of(
                        new ResolvedToolDefinition(
                                "READ_FILE",
                                "Read a file",
                                """
                                {
                                  "action": "read_file"
                                }""",
                                List.of())), false),
                toolContract -> "Available actions:\n- READ_FILE",
                outputContract -> "Custom output contract",
                100,
                3);

        ReconstructedThreadContext reconstructedContext = new ReconstructedThreadContext(
                threadId,
                null,
                List.of(),
                List.of(),
                List.of(),
                Instant.now());

        ResolvedPrompt prompt = service.assemblePlannerPrompt(
                reconstructedContext,
                "Inspect repo",
                "",
                1,
                List.of(),
                List.of(),
                List.of());

        assertTrue(prompt.systemPrompt().contains("Persisted base instructions"));
        assertTrue(prompt.systemPrompt().contains("Persisted project rules."));
        assertFalse(prompt.systemPrompt().contains("Current base instructions"));
        assertFalse(prompt.systemPrompt().contains("Current project rules."));
    }

    private static final class InMemoryThreadPromptStateStore implements ThreadPromptStateStore {

        private final java.util.Map<ThreadId, ThreadPromptSnapshot> snapshots = new java.util.HashMap<>();

        @Override
        public java.util.Optional<ThreadPromptSnapshot> read(ThreadId threadId) {
            return java.util.Optional.ofNullable(snapshots.get(threadId));
        }

        @Override
        public ThreadPromptSnapshot write(ThreadId threadId, ThreadPromptSnapshot snapshot) {
            snapshots.put(threadId, snapshot);
            return snapshot;
        }
    }
}
