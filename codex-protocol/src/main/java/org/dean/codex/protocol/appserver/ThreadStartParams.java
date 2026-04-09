package org.dean.codex.protocol.appserver;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ThreadStartParams(String title,
                                String sandboxMode,
                                String approvalMode) {

    public ThreadStartParams(String title) {
        this(title, null, null);
    }
}
