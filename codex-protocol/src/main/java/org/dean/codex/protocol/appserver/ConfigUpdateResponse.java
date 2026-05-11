package org.dean.codex.protocol.appserver;

import org.dean.codex.protocol.conversation.ThreadSummary;

public record ConfigUpdateResponse(ConfigGetResponse config,
                                   ThreadSummary thread) {
}
