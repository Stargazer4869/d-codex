package org.dean.codex.protocol.appserver;

import org.dean.codex.protocol.agent.AgentSummary;

import java.util.List;

public record AgentListResponse(List<AgentSummary> agents) {
}
