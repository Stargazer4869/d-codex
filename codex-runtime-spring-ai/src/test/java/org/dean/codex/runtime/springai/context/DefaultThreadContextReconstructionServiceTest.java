package org.dean.codex.runtime.springai.context;

import org.dean.codex.core.context.ContextManager;
import org.dean.codex.core.conversation.ConversationStore;
import org.dean.codex.core.conversation.InMemoryConversationStore;
import org.dean.codex.protocol.conversation.ItemId;
import org.dean.codex.protocol.conversation.MessageRole;
import org.dean.codex.protocol.conversation.ThreadId;
import org.dean.codex.protocol.conversation.TurnId;
import org.dean.codex.protocol.conversation.TurnStatus;
import org.dean.codex.protocol.conversation.ConversationTurn;
import org.dean.codex.protocol.context.ReconstructedReplayItem;
import org.dean.codex.protocol.history.CompactedHistoryItem;
import org.dean.codex.protocol.history.CompactionStrategy;
import org.dean.codex.protocol.history.HistoryCompactionSummaryItem;
import org.dean.codex.protocol.history.HistoryCollabToolCallItem;
import org.dean.codex.protocol.history.HistoryMessageItem;
import org.dean.codex.protocol.history.HistoryMailboxMessageItem;
import org.dean.codex.protocol.history.HistoryToolCallItem;
import org.dean.codex.protocol.history.ThreadHistoryItem;
import org.dean.codex.protocol.item.CollabDeliveryState;
import org.dean.codex.protocol.item.CollabToolCallItem;
import org.dean.codex.protocol.item.CollabToolCallStatus;
import org.dean.codex.protocol.item.AgentMessageItem;
import org.dean.codex.protocol.item.MailboxDeliveryKind;
import org.dean.codex.protocol.item.MailboxMessageItem;
import org.dean.codex.protocol.agent.AgentMailboxState;
import org.dean.codex.protocol.item.ToolCallItem;
import org.dean.codex.protocol.item.UserMessageItem;
import org.dean.codex.protocol.context.ReconstructedThreadContext;
import org.dean.codex.protocol.context.ThreadMemory;
import org.dean.codex.runtime.springai.history.InMemoryThreadHistoryStore;
import org.dean.codex.runtime.springai.history.ThreadHistoryMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultThreadContextReconstructionServiceTest {

    @Test
    void reconstructReplaysCanonicalHistoryAndAppliesCompactionReplacement() {
        ConversationStore store = new InMemoryConversationStore();
        InMemoryThreadHistoryStore historyStore = new InMemoryThreadHistoryStore();
        ThreadId threadId = store.createThread("Reconstruction thread");

        Instant base = Instant.parse("2026-03-30T10:15:30Z");

        TurnId firstTurn = store.startTurn(threadId, "Inspect repo", base);
        UserMessageItem firstUser = new UserMessageItem(new ItemId("item-1"), "Inspect repo", base.plusSeconds(1));
        ToolCallItem firstToolCall = new ToolCallItem(new ItemId("item-2"), "READ_FILE", "README.md", base.plusSeconds(2));
        AgentMessageItem firstAssistant = new AgentMessageItem(new ItemId("item-3"), "Inspected repo", base.plusSeconds(3));
        store.appendTurnItems(threadId, firstTurn, List.of(firstUser, firstToolCall, firstAssistant));
        historyStore.append(threadId, ThreadHistoryMapper.map(List.of(firstUser, firstToolCall, firstAssistant)));
        store.completeTurn(threadId, firstTurn, TurnStatus.COMPLETED, "Inspected repo", base.plusSeconds(4));

        TurnId secondTurn = store.startTurn(threadId, "Update docs", base.plusSeconds(5));
        UserMessageItem secondUser = new UserMessageItem(new ItemId("item-4"), "Update docs", base.plusSeconds(6));
        ToolCallItem secondToolCall = new ToolCallItem(new ItemId("item-5"), "READ_FILE", "README.md", base.plusSeconds(7));
        CollabToolCallItem secondCollab = new CollabToolCallItem(
                new ItemId("item-5b"),
                "spawn_agent",
                CollabToolCallStatus.COMPLETED,
                CollabDeliveryState.DISPATCHED,
                threadId,
                List.of(new ThreadId("thread-agent")),
                new ThreadId("thread-agent"),
                "inspect repository",
                java.util.Map.of("thread-agent", org.dean.codex.protocol.agent.AgentStatus.IDLE),
                java.util.Map.of("thread-agent", new AgentMailboxState(new ThreadId("thread-agent"), 2L, 0, base.plusSeconds(7))),
                "mailbox_updated",
                base.plusSeconds(7));
        MailboxMessageItem secondMailbox = new MailboxMessageItem(
                new ItemId("item-5c"),
                new ThreadId("thread-agent"),
                threadId,
                MailboxDeliveryKind.CHILD_COMPLETION,
                "Sub-agent worker-1 completed. Final answer:\nREADME reviewed",
                base.plusSeconds(7));
        AgentMessageItem secondAssistant = new AgentMessageItem(new ItemId("item-6"), "Updated docs", base.plusSeconds(8));
        store.appendTurnItems(threadId, secondTurn, List.of(secondUser, secondToolCall, secondCollab, secondMailbox, secondAssistant));
        historyStore.append(threadId, ThreadHistoryMapper.map(List.of(secondUser, secondToolCall, secondCollab, secondMailbox, secondAssistant)));
        store.completeTurn(threadId, secondTurn, TurnStatus.COMPLETED, "Updated docs", base.plusSeconds(9));

        TurnId thirdTurn = store.startTurn(threadId, "Run tests", base.plusSeconds(10));
        UserMessageItem thirdUser = new UserMessageItem(new ItemId("item-7"), "Run tests", base.plusSeconds(11));
        AgentMessageItem thirdAssistant = new AgentMessageItem(new ItemId("item-8"), "Ran tests", base.plusSeconds(12));
        store.appendTurnItems(threadId, thirdTurn, List.of(thirdUser, thirdAssistant));
        historyStore.append(threadId, ThreadHistoryMapper.map(List.of(thirdUser, thirdAssistant)));
        store.completeTurn(threadId, thirdTurn, TurnStatus.COMPLETED, "Ran tests", base.plusSeconds(13));

        List<ThreadHistoryItem> replacementHistory = List.of(
                new HistoryCompactionSummaryItem(secondTurn, "Compacted summary: keep the README change", base.plusSeconds(14)),
                new HistoryMessageItem(MessageRole.USER, "Update docs", base.plusSeconds(6)),
                new HistoryToolCallItem("READ_FILE", "README.md", base.plusSeconds(7)),
                new HistoryMailboxMessageItem(
                        new ThreadId("thread-agent"),
                        threadId,
                        MailboxDeliveryKind.CHILD_COMPLETION,
                        "Sub-agent worker-1 completed. Final answer:\nREADME reviewed",
                        base.plusSeconds(7)),
                new HistoryMessageItem(MessageRole.ASSISTANT, "Updated docs", base.plusSeconds(8)),
                new HistoryMessageItem(MessageRole.USER, "Run tests", base.plusSeconds(11)),
                new HistoryMessageItem(MessageRole.ASSISTANT, "Ran tests", base.plusSeconds(12)));
        historyStore.append(threadId, List.<ThreadHistoryItem>of(new CompactedHistoryItem(
                "Compacted earlier thread context.",
                replacementHistory,
                base.plusSeconds(15),
                CompactionStrategy.LOCAL_SUMMARY)));

        ThreadMemory threadMemory = new ThreadMemory(
                "memory-1",
                threadId,
                "Compacted earlier thread context.",
                List.of(firstTurn),
                1,
                base.plusSeconds(16));
        DefaultThreadContextReconstructionService service = new DefaultThreadContextReconstructionService(
                store,
                historyStore,
                new FixedContextManager(threadMemory),
                2);

        ReconstructedThreadContext reconstructed = service.reconstruct(threadId);

        assertEquals("memory-1", reconstructed.threadMemory().memoryId());
        assertEquals(List.of(secondTurn, thirdTurn), reconstructed.recentTurns().stream().map(ConversationTurn::turnId).toList());
        assertEquals(List.of("Compacted summary: keep the README change", "Update docs", "MAILBOX thread-agent: Sub-agent worker-1 completed. Final answer:\nREADME reviewed", "Updated docs", "Run tests", "Ran tests"),
                reconstructed.recentMessages().stream().map(message -> message.content()).toList());
        assertTrue(reconstructed.recentMessages().stream().noneMatch(message -> message.content().contains("Inspect repo")));
        assertTrue(reconstructed.recentActivities().stream()
                .anyMatch(activity -> activity.detail().contains("Compacted summary: keep the README change")));
        assertTrue(reconstructed.recentActivities().stream()
                .anyMatch(activity -> activity.detail().contains("READ_FILE")));
        assertTrue(reconstructed.replaySummary().stream()
                .anyMatch(item -> item.kind().equals("collaboration")
                        && item.detail().contains("spawn_agent")
                        && item.deliveryState() == CollabDeliveryState.DISPATCHED
                        && item.mailboxSummary() != null
                        && item.wakeupCause().equals("mailbox_updated")));
        assertTrue(reconstructed.replaySummary().stream()
                .anyMatch(item -> item.kind().equals("mailbox")
                        && item.detail().contains("child_completion")
                        && item.detail().contains("README reviewed")));
        assertTrue(reconstructed.replaySummary().stream()
                .anyMatch(item -> item.kind().equals("collaboration")
                        && item.turnId() != null
                        && item.turnId().equals(secondTurn)
                        && item.detail().contains("spawn_agent")));
        assertTrue(reconstructed.replaySummary().stream()
                .anyMatch(item -> item.kind().equals("mailbox")
                        && item.turnId() != null
                        && item.turnId().equals(secondTurn)
                        && item.detail().contains("README reviewed")));
    }

    private static final class FixedContextManager implements ContextManager {

        private final ThreadMemory threadMemory;

        private FixedContextManager(ThreadMemory threadMemory) {
            this.threadMemory = threadMemory;
        }

        @Override
        public Optional<ThreadMemory> latestThreadMemory(ThreadId threadId) {
            return Optional.of(threadMemory);
        }

        @Override
        public ThreadMemory compactThread(ThreadId threadId) {
            return threadMemory;
        }
    }
}
