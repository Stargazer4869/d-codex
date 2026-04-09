package org.dean.codex.runtime.springai.thread;

import org.dean.codex.protocol.appserver.ThreadListParams;
import org.dean.codex.protocol.appserver.ThreadListResponse;
import org.dean.codex.protocol.appserver.ThreadSortKey;
import org.dean.codex.protocol.appserver.ThreadSourceKind;
import org.dean.codex.protocol.agent.AgentStatus;
import org.dean.codex.protocol.conversation.ThreadId;
import org.dean.codex.protocol.conversation.ThreadSource;
import org.dean.codex.protocol.conversation.ThreadStatus;
import org.dean.codex.protocol.conversation.ThreadSummary;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DefaultThreadCatalogServiceTest {

    private final ThreadCatalogService service = new DefaultThreadCatalogService();

    @Test
    void filtersByLifecycleMetadataAndParentThread() {
        ThreadId parent = new ThreadId("thread-parent");
        ThreadId child = new ThreadId("thread-child");
        ThreadId grandchild = new ThreadId("thread-grandchild");

        List<ThreadSummary> threads = List.of(
                summary(parent, "Parent", Instant.parse("2026-04-01T00:00:00Z"), Instant.parse("2026-04-01T00:00:05Z"),
                        ThreadStatus.ACTIVE, null, "workspace-write", "review-sensitive", "parent prompt"),
                summary(child, "Child", Instant.parse("2026-04-01T00:01:00Z"), Instant.parse("2026-04-01T00:01:05Z"),
                        ThreadStatus.IDLE, parent, "workspace-write", "review-sensitive", "child prompt"),
                summary(grandchild, "Grandchild", Instant.parse("2026-04-01T00:02:00Z"), Instant.parse("2026-04-01T00:02:05Z"),
                        ThreadStatus.IDLE, child, "workspace-write", "review-sensitive", "grandchild prompt"));

        ThreadListResponse response = service.listThreads(threads, new ThreadListParams(
                null,
                null,
                ThreadSortKey.UPDATED_AT,
                null,
                List.of(ThreadSourceKind.CLI),
                Boolean.FALSE,
                null,
                "child",
                List.of("workspace-write"),
                List.of("review-sensitive"),
                List.of(ThreadStatus.IDLE),
                parent));

        assertEquals(1, response.threads().size());
        assertEquals(child, response.threads().get(0).threadId());
        assertEquals(parent, response.threads().get(0).parentThreadId());
    }

    @Test
    void pagesDeterministicallyWithOpaqueCursorAnchors() {
        List<ThreadSummary> threads = List.of(
                summary(new ThreadId("thread-c"), "Thread C", Instant.parse("2026-04-01T00:00:00Z"), Instant.parse("2026-04-01T00:05:00Z"),
                        ThreadStatus.IDLE, null, "workspace-write", "review-sensitive", "one"),
                summary(new ThreadId("thread-a"), "Thread A", Instant.parse("2026-04-01T00:00:01Z"), Instant.parse("2026-04-01T00:04:00Z"),
                        ThreadStatus.IDLE, null, "workspace-write", "review-sensitive", "two"),
                summary(new ThreadId("thread-b"), "Thread B", Instant.parse("2026-04-01T00:00:02Z"), Instant.parse("2026-04-01T00:04:00Z"),
                        ThreadStatus.IDLE, null, "workspace-write", "review-sensitive", "three"),
                summary(new ThreadId("thread-d"), "Thread D", Instant.parse("2026-04-01T00:00:03Z"), Instant.parse("2026-04-01T00:03:00Z"),
                        ThreadStatus.IDLE, null, "workspace-write", "review-sensitive", "four"));

        ThreadListResponse firstPage = service.listThreads(threads, new ThreadListParams(
                null,
                2,
                ThreadSortKey.UPDATED_AT,
                null,
                null,
                null,
                null,
                null));

        assertEquals(List.of(new ThreadId("thread-c"), new ThreadId("thread-a")),
                firstPage.threads().stream().map(ThreadSummary::threadId).toList());
        assertNotNull(firstPage.nextCursor());
        assertNotEquals("2", firstPage.nextCursor());
        assertNotEquals(null, firstPage.nextCursor());

        ThreadListResponse secondPage = service.listThreads(threads, new ThreadListParams(
                firstPage.nextCursor(),
                2,
                ThreadSortKey.UPDATED_AT,
                null,
                null,
                null,
                null,
                null));

        assertEquals(List.of(new ThreadId("thread-b"), new ThreadId("thread-d")),
                secondPage.threads().stream().map(ThreadSummary::threadId).toList());
    }

    private ThreadSummary summary(ThreadId threadId,
                                  String title,
                                  Instant createdAt,
                                  Instant updatedAt,
                                  ThreadStatus status,
                                  ThreadId parentThreadId,
                                  String sandboxMode,
                                  String approvalMode,
                                  String firstUserInput) {
        return new ThreadSummary(
                threadId,
                title,
                createdAt,
                updatedAt,
                1,
                firstUserInput,
                firstUserInput,
                sandboxMode,
                approvalMode,
                false,
                "openai",
                "gpt-5.4",
                status,
                List.of(),
                "/tmp/" + threadId.value(),
                "/Users/chenzhu/Git/play-with-ai",
                ThreadSource.CLI,
                true,
                null,
                null,
                null,
                null,
                parentThreadId,
                null,
                AgentStatus.IDLE,
                null);
    }
}
