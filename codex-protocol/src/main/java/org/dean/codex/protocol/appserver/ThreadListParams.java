package org.dean.codex.protocol.appserver;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.dean.codex.protocol.conversation.ThreadId;
import org.dean.codex.protocol.conversation.ThreadStatus;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ThreadListParams(String cursor,
                               Integer limit,
                               ThreadSortKey sortKey,
                               List<String> modelProviders,
                               List<ThreadSourceKind> sourceKinds,
                               Boolean archived,
                               String cwd,
                               String searchTerm,
                               List<String> sandboxModes,
                               List<String> approvalModes,
                               List<ThreadStatus> statuses,
                               ThreadId parentThreadId) {

    public ThreadListParams(String cursor,
                            Integer limit,
                            ThreadSortKey sortKey,
                            List<String> modelProviders,
                            List<ThreadSourceKind> sourceKinds,
                            Boolean archived,
                            String cwd,
                            String searchTerm) {
        this(cursor, limit, sortKey, modelProviders, sourceKinds, archived, cwd, searchTerm, null, null);
    }

    public ThreadListParams(String cursor,
                            Integer limit,
                            ThreadSortKey sortKey,
                            List<String> modelProviders,
                            List<ThreadSourceKind> sourceKinds,
                            Boolean archived,
                            String cwd,
                            String searchTerm,
                            List<String> sandboxModes,
                            List<String> approvalModes) {
        this(cursor, limit, sortKey, modelProviders, sourceKinds, archived, cwd, searchTerm, sandboxModes, approvalModes, null, null);
    }

    public ThreadListParams(String cursor,
                            Integer limit,
                            ThreadSortKey sortKey,
                            List<String> modelProviders,
                            List<ThreadSourceKind> sourceKinds,
                            Boolean archived,
                            String cwd,
                            String searchTerm,
                            List<String> sandboxModes,
                            List<String> approvalModes,
                            List<ThreadStatus> statuses,
                            ThreadId parentThreadId) {
        this.cursor = cursor;
        this.limit = limit;
        this.sortKey = sortKey;
        this.modelProviders = modelProviders == null ? null : List.copyOf(modelProviders);
        this.sourceKinds = sourceKinds == null ? null : List.copyOf(sourceKinds);
        this.archived = archived;
        this.cwd = cwd;
        this.searchTerm = searchTerm;
        this.sandboxModes = sandboxModes == null ? null : List.copyOf(sandboxModes);
        this.approvalModes = approvalModes == null ? null : List.copyOf(approvalModes);
        this.statuses = statuses == null ? null : List.copyOf(statuses);
        this.parentThreadId = parentThreadId;
    }
}
