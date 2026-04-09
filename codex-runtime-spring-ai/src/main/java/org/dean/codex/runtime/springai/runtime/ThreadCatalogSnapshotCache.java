package org.dean.codex.runtime.springai.runtime;

import org.dean.codex.protocol.conversation.ThreadId;
import org.dean.codex.protocol.conversation.ThreadSummary;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

final class ThreadCatalogSnapshotCache {

    private final Map<ThreadId, ThreadSummary> summariesByThreadId = new LinkedHashMap<>();
    private List<ThreadSummary> cachedSnapshot;

    synchronized List<ThreadSummary> snapshot(Supplier<List<ThreadSummary>> loader) {
        Objects.requireNonNull(loader, "loader");
        if (cachedSnapshot == null) {
            refresh(loader.get());
        }
        return cachedSnapshot;
    }

    synchronized void invalidate(ThreadId threadId) {
        if (threadId != null) {
            summariesByThreadId.remove(threadId);
        }
        cachedSnapshot = null;
    }

    synchronized void invalidateAll() {
        summariesByThreadId.clear();
        cachedSnapshot = null;
    }

    private void refresh(List<ThreadSummary> summaries) {
        summariesByThreadId.clear();
        if (summaries != null) {
            for (ThreadSummary summary : summaries) {
                if (summary != null) {
                    summariesByThreadId.put(summary.threadId(), summary);
                }
            }
        }
        cachedSnapshot = List.copyOf(summariesByThreadId.values());
    }
}
