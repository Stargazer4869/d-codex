package org.dean.codex.runtime.springai.thread;

import org.dean.codex.protocol.appserver.ThreadListParams;
import org.dean.codex.protocol.appserver.ThreadListResponse;
import org.dean.codex.protocol.conversation.ThreadSummary;

import java.util.List;

public interface ThreadCatalogService {

    ThreadListResponse listThreads(List<ThreadSummary> threads, ThreadListParams params);
}
