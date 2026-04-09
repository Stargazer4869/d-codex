package org.dean.codex.runtime.springai.thread;

import org.dean.codex.protocol.appserver.ThreadListParams;
import org.dean.codex.protocol.appserver.ThreadListResponse;
import org.dean.codex.protocol.appserver.ThreadSortKey;
import org.dean.codex.protocol.appserver.ThreadSourceKind;
import org.dean.codex.protocol.conversation.ThreadId;
import org.dean.codex.protocol.conversation.ThreadStatus;
import org.dean.codex.protocol.conversation.ThreadSummary;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Component
public class DefaultThreadCatalogService implements ThreadCatalogService {

    private static final String CURSOR_VERSION = "v1";
    private static final char CURSOR_SEPARATOR = '\u001f';

    @Override
    public ThreadListResponse listThreads(List<ThreadSummary> threads, ThreadListParams params) {
        List<ThreadSummary> filtered = filterThreads(threads, params);
        ThreadSortKey sortKey = resolveSortKey(params);
        Comparator<ThreadSummary> comparator = comparator(sortKey);
        List<ThreadSummary> sorted = filtered.stream().sorted(comparator).toList();

        ThreadCursor cursor = decodeCursor(params == null ? null : params.cursor());
        int startIndex = cursor == null ? 0 : findStartIndex(sorted, cursor, sortKey);
        int limit = normalizeLimit(params == null ? null : params.limit(), sorted.size());
        int endExclusive = Math.min(startIndex + limit, sorted.size());
        List<ThreadSummary> page = sorted.subList(Math.min(startIndex, sorted.size()), endExclusive);
        String nextCursor = page.isEmpty() || endExclusive >= sorted.size()
                ? null
                : encodeCursor(sortKey, page.get(page.size() - 1));
        return new ThreadListResponse(page, nextCursor);
    }

    private List<ThreadSummary> filterThreads(List<ThreadSummary> threads, ThreadListParams params) {
        Stream<ThreadSummary> stream = (threads == null ? List.<ThreadSummary>of() : threads).stream();
        if (params != null && params.modelProviders() != null && !params.modelProviders().isEmpty()) {
            Set<String> providers = Set.copyOf(params.modelProviders());
            stream = stream.filter(thread -> thread.modelProvider() != null && providers.contains(thread.modelProvider()));
        }
        if (params != null && params.sourceKinds() != null && !params.sourceKinds().isEmpty()) {
            Set<ThreadSourceKind> sourceKinds = Set.copyOf(params.sourceKinds());
            stream = stream.filter(thread -> sourceKinds.contains(toSourceKind(thread)));
        }
        boolean explicitArchivedFilter = params != null && params.archived() != null;
        boolean archivedOnly = explicitArchivedFilter && params.archived();
        stream = stream.filter(thread -> explicitArchivedFilter ? archivedOnly == thread.archived() : !thread.archived());
        if (params != null && params.cwd() != null && !params.cwd().isBlank()) {
            stream = stream.filter(thread -> params.cwd().equals(thread.cwd()));
        }
        if (params != null && params.searchTerm() != null && !params.searchTerm().isBlank()) {
            String needle = params.searchTerm().toLowerCase();
            stream = stream.filter(thread -> {
                String title = thread.title() == null ? "" : thread.title().toLowerCase();
                String preview = thread.preview() == null ? "" : thread.preview().toLowerCase();
                String firstUserInput = thread.firstUserInput() == null ? "" : thread.firstUserInput().toLowerCase();
                return title.contains(needle) || preview.contains(needle) || firstUserInput.contains(needle);
            });
        }
        if (params != null && params.sandboxModes() != null && !params.sandboxModes().isEmpty()) {
            Set<String> sandboxModes = Set.copyOf(params.sandboxModes());
            stream = stream.filter(thread -> thread.sandboxMode() != null && sandboxModes.contains(thread.sandboxMode()));
        }
        if (params != null && params.approvalModes() != null && !params.approvalModes().isEmpty()) {
            Set<String> approvalModes = Set.copyOf(params.approvalModes());
            stream = stream.filter(thread -> thread.approvalMode() != null && approvalModes.contains(thread.approvalMode()));
        }
        if (params != null && params.statuses() != null && !params.statuses().isEmpty()) {
            Set<ThreadStatus> statuses = Set.copyOf(params.statuses());
            stream = stream.filter(thread -> statuses.contains(thread.status()));
        }
        if (params != null && params.parentThreadId() != null) {
            ThreadId parentThreadId = params.parentThreadId();
            stream = stream.filter(thread -> parentThreadId.equals(thread.parentThreadId()));
        }
        return stream.toList();
    }

    private ThreadSortKey resolveSortKey(ThreadListParams params) {
        return params != null && params.sortKey() != null ? params.sortKey() : ThreadSortKey.UPDATED_AT;
    }

    private Comparator<ThreadSummary> comparator(ThreadSortKey sortKey) {
        Comparator<ThreadSummary> comparator = Comparator.comparing(summary -> sortValue(summary, sortKey));
        return comparator.reversed().thenComparing(summary -> summary.threadId().value());
    }

    private int findStartIndex(List<ThreadSummary> sorted, ThreadCursor cursor, ThreadSortKey requestedSortKey) {
        if (cursor.sortKey() != requestedSortKey) {
            throw new IllegalArgumentException("Cursor sort key does not match requested sort key");
        }
        for (int index = 0; index < sorted.size(); index++) {
            if (isAfterAnchor(sorted.get(index), cursor, requestedSortKey)) {
                return index;
            }
        }
        return sorted.size();
    }

    private boolean isAfterAnchor(ThreadSummary summary, ThreadCursor cursor, ThreadSortKey sortKey) {
        Instant threadSortValue = sortValue(summary, sortKey);
        int comparison = threadSortValue.compareTo(cursor.sortValue());
        if (comparison < 0) {
            return true;
        }
        if (comparison > 0) {
            return false;
        }
        return summary.threadId().value().compareTo(cursor.threadId().value()) > 0;
    }

    private Instant sortValue(ThreadSummary summary, ThreadSortKey sortKey) {
        Instant value = sortKey == ThreadSortKey.CREATED_AT ? summary.createdAt() : summary.updatedAt();
        return value == null ? Instant.EPOCH : value;
    }

    private String encodeCursor(ThreadSortKey sortKey, ThreadSummary summary) {
        String payload = String.join(String.valueOf(CURSOR_SEPARATOR),
                CURSOR_VERSION,
                sortKey.name(),
                sortValue(summary, sortKey).toString(),
                summary.threadId().value());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private ThreadCursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split(String.valueOf(CURSOR_SEPARATOR), 4);
            if (parts.length != 4 || !CURSOR_VERSION.equals(parts[0])) {
                throw new IllegalArgumentException("Invalid cursor: " + cursor);
            }
            return new ThreadCursor(ThreadSortKey.valueOf(parts[1]), Instant.parse(parts[2]), new ThreadId(parts[3]));
        }
        catch (Exception exception) {
            throw new IllegalArgumentException("Invalid cursor: " + cursor, exception);
        }
    }

    private int normalizeLimit(Integer limit, int defaultValue) {
        if (limit == null) {
            return Math.max(0, defaultValue);
        }
        if (limit < 0) {
            throw new IllegalArgumentException("limit must be >= 0");
        }
        return limit;
    }

    private ThreadSourceKind toSourceKind(ThreadSummary thread) {
        return switch (thread.source()) {
            case CLI -> ThreadSourceKind.CLI;
            case APP_SERVER -> ThreadSourceKind.APP_SERVER;
            case EXEC -> ThreadSourceKind.EXEC;
            case SUB_AGENT -> ThreadSourceKind.SUB_AGENT;
            case UNKNOWN -> ThreadSourceKind.UNKNOWN;
        };
    }

    private record ThreadCursor(ThreadSortKey sortKey, Instant sortValue, ThreadId threadId) {
    }
}
