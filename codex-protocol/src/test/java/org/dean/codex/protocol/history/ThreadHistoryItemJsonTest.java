package org.dean.codex.protocol.history;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dean.codex.protocol.conversation.MessageRole;
import org.dean.codex.protocol.agent.AgentMailboxState;
import org.dean.codex.protocol.conversation.TurnId;
import org.dean.codex.protocol.conversation.ThreadId;
import org.dean.codex.protocol.item.ApprovalState;
import org.dean.codex.protocol.item.CollabDeliveryState;
import org.dean.codex.protocol.item.CollabToolCallStatus;
import org.dean.codex.protocol.planning.EditPlan;
import org.dean.codex.protocol.planning.PlannedEdit;
import org.dean.codex.protocol.planning.PlannedEditType;
import org.dean.codex.protocol.skill.SkillMetadata;
import org.dean.codex.protocol.skill.SkillScope;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreadHistoryItemJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void roundTripsMixedHistoryIncludingCompaction() throws Exception {
        Instant now = Instant.parse("2026-03-31T00:00:00Z");
        List<ThreadHistoryItem> history = List.of(
                new HistoryMessageItem(new TurnId("turn-1"), MessageRole.USER, "Inspect repo", now),
                new HistoryImageItem(new TurnId("turn-1"), "file:///tmp/screenshot.png", "high", now.plusSeconds(1)),
                new HistoryToolCallItem(new TurnId("turn-1"), "READ_FILE", "README.md", now.plusSeconds(2)),
                new HistoryToolResultItem(new TurnId("turn-1"), "READ_FILE", "success path=README.md", now.plusSeconds(3)),
                new HistoryCollabToolCallItem(
                        new TurnId("turn-1"),
                        "spawn_agent",
                        CollabToolCallStatus.COMPLETED,
                        CollabDeliveryState.DISPATCHED,
                        new ThreadId("thread-parent"),
                        List.of(new ThreadId("thread-agent")),
                        new ThreadId("thread-agent"),
                        "inspect repository",
                        java.util.Map.of("thread-agent", org.dean.codex.protocol.agent.AgentStatus.IDLE),
                        java.util.Map.of("thread-agent", new AgentMailboxState(new ThreadId("thread-agent"), 1L, 0, now.plusSeconds(4))),
                        "mailbox_event",
                        now.plusSeconds(4)),
                new HistoryPlanItem(
                        new TurnId("turn-2"),
                        new EditPlan(
                                "Update docs",
                                List.of(new PlannedEdit("README.md", PlannedEditType.MODIFY, "Clarify setup"))),
                        now.plusSeconds(5)),
                new HistorySkillUseItem(
                        new TurnId("turn-2"),
                        List.of(new SkillMetadata(
                                "doc-reader",
                                "Read documentation files",
                                "Read docs",
                                "skills/doc-reader",
                                SkillScope.WORKSPACE,
                                true)),
                        now.plusSeconds(6)),
                new HistoryApprovalItem(
                        new TurnId("turn-3"),
                        ApprovalState.APPROVED,
                        "approval-1",
                        "echo ok",
                        "Approved by user",
                        now.plusSeconds(7)),
                new HistoryReasoningItem(
                        new TurnId("turn-3"),
                        "Need to inspect build config",
                        "The pom likely defines the relevant plugin version.",
                        now.plusSeconds(8)),
                new CompactedHistoryItem(
                        "Compacted earlier thread context.",
                        List.of(
                                new HistoryCompactionSummaryItem(new TurnId("turn-1"), "Compacted earlier thread context.", now.plusSeconds(9)),
                                new HistoryToolResultItem(new TurnId("turn-4"), "SEARCH_FILES", "2 matches", now.plusSeconds(10))),
                        now.plusSeconds(11),
                        CompactionStrategy.LOCAL_SUMMARY),
                new HistoryRuntimeErrorItem(new TurnId("turn-5"), "Synthetic runtime error", now.plusSeconds(12)));

        TypeReference<List<ThreadHistoryItem>> historyType = new TypeReference<>() {
        };
        String json = objectMapper.writerFor(historyType).writeValueAsString(history);
        List<ThreadHistoryItem> restored = objectMapper.readValue(
                json,
                historyType);

        assertEquals(history, restored);

        assertTrue(restored.stream().anyMatch(HistoryCollabToolCallItem.class::isInstance));
        assertInstanceOf(HistoryReasoningItem.class, restored.get(8));
        CompactedHistoryItem compacted = assertInstanceOf(CompactedHistoryItem.class, restored.get(9));
        assertEquals("Compacted earlier thread context.", compacted.summaryText());
        assertEquals(CompactionStrategy.LOCAL_SUMMARY, compacted.strategy());
        assertEquals(2, compacted.replacementHistory().size());
        HistoryCompactionSummaryItem summary = assertInstanceOf(HistoryCompactionSummaryItem.class, compacted.replacementHistory().get(0));
        assertEquals(new TurnId("turn-1"), summary.anchorTurnId());
        assertInstanceOf(HistoryToolResultItem.class, compacted.replacementHistory().get(1));
    }

    @Test
    void compactedHistoryDefaultsCollectionsButKeepsValuesStable() throws Exception {
        Instant now = Instant.parse("2026-03-31T00:00:00Z");
        CompactedHistoryItem item = new CompactedHistoryItem(
                "Summary",
                null,
                now,
                CompactionStrategy.REMOTE_COMPACT);

        String json = objectMapper.writeValueAsString(item);
        CompactedHistoryItem restored = objectMapper.readValue(json, CompactedHistoryItem.class);

        assertEquals(item, restored);
        assertTrue(restored.replacementHistory().isEmpty());
    }
}
