package org.dean.codex.runtime.springai.prompt;

import java.util.List;

public class DefaultToolContractResolver implements ToolContractResolver {

    private static final String FILE_AND_COMMAND_ACTION_SCHEMA = """
                    {
                      "action": "READ_FILE | SEARCH_FILES | APPLY_PATCH | WRITE_FILE | RUN_COMMAND",
                      "path": "relative/path/when-needed",
                      "query": "search query when searching",
                      "oldText": "exact text to replace when applying a patch",
                      "newText": "replacement text when applying a patch",
                      "replaceAll": false,
                      "content": "file content when writing",
                      "command": "shell command when running one"
                    }""";

    private static final List<ResolvedToolDefinition> DEFAULT_VISIBLE_TOOLS = List.of(
            new ResolvedToolDefinition(
                    "READ_FILE",
                    "read a file relative to the workspace root",
                    FILE_AND_COMMAND_ACTION_SCHEMA,
                    List.of()),
            new ResolvedToolDefinition(
                    "SEARCH_FILES",
                    "search for text or regex matches across the workspace or inside a scoped path",
                    FILE_AND_COMMAND_ACTION_SCHEMA,
                    List.of()),
            new ResolvedToolDefinition(
                    "APPLY_PATCH",
                    "replace exact old text with new text inside an existing file",
                    FILE_AND_COMMAND_ACTION_SCHEMA,
                    List.of("Prefer APPLY_PATCH for targeted edits over rewriting whole files.")),
            new ResolvedToolDefinition(
                    "WRITE_FILE",
                    "create or overwrite a file relative to the workspace root",
                    FILE_AND_COMMAND_ACTION_SCHEMA,
                    List.of("Use WRITE_FILE for new files or explicit full rewrites.")),
            new ResolvedToolDefinition(
                    "RUN_COMMAND",
                    "run a zsh command from the workspace root, subject to approval policy",
                    FILE_AND_COMMAND_ACTION_SCHEMA,
                    List.of("Avoid destructive shell commands.")),
            new ResolvedToolDefinition(
                    "spawn_agent",
                    "spawn a delegated sub-agent from the current thread",
                    """
                    {
                      "action": "spawn_agent",
                      "taskName": "delegate a focused task",
                      "prompt": "optional task prompt for the child agent",
                      "nickname": "optional agent nickname",
                      "role": "optional agent role",
                      "depth": 1,
                      "modelProvider": "optional model provider",
                      "model": "optional model name",
                      "cwd": "optional child workspace cwd"
                    }""",
                    List.of()),
            new ResolvedToolDefinition(
                    "send_message",
                    "queue a plain message to an existing sub-agent thread without starting work",
                    """
                    {
                      "action": "send_message",
                      "threadId": "agent-thread-id",
                      "content": "message to queue for the agent"
                    }""",
                    List.of()),
            new ResolvedToolDefinition(
                    "assign_task",
                    "queue work for an existing sub-agent thread and let it start if idle",
                    """
                    {
                      "action": "assign_task",
                      "threadId": "agent-thread-id",
                      "content": "task to assign to the agent",
                      "interrupt": false
                    }""",
                    List.of()),
            new ResolvedToolDefinition(
                    "send_input",
                    "compatibility alias for assign_task",
                    """
                    {
                      "action": "send_input",
                      "threadId": "agent-thread-id",
                      "content": "message to send to the agent",
                      "interrupt": false
                    }""",
                    List.of()),
            new ResolvedToolDefinition(
                    "wait_agent",
                    "wait for one or more sub-agents to change status or mailbox state",
                    """
                    {
                      "action": "wait_agent",
                      "threadIds": ["agent-thread-id"],
                      "timeoutMillis": 1000
                    }""",
                    List.of()),
            new ResolvedToolDefinition(
                    "resume_agent",
                    "resume a paused or waiting sub-agent thread",
                    """
                    {
                      "action": "resume_agent",
                      "threadId": "agent-thread-id"
                    }""",
                    List.of()),
            new ResolvedToolDefinition(
                    "close_agent",
                    "close a sub-agent thread subtree",
                    """
                    {
                      "action": "close_agent",
                      "threadId": "agent-thread-id"
                    }""",
                    List.of()),
            new ResolvedToolDefinition(
                    "list_agents",
                    "list sub-agents under the current thread or a requested parent thread",
                    """
                    {
                      "action": "list_agents",
                      "threadId": "optional-parent-thread-id",
                      "recursive": true
                    }""",
                    List.of()));

    @Override
    public ResolvedToolContract resolvePlannerToolContract() {
        return new ResolvedToolContract(DEFAULT_VISIBLE_TOOLS, false);
    }
}
