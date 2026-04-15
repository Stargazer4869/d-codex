package org.dean.codex.runtime.springai.prompt;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DefaultToolCapabilityRegistry implements ToolCapabilityRegistry {

    private static final List<ToolCapability> DEFAULT_CAPABILITIES = List.of(
            capability("READ_FILE",
                    "read a file relative to the workspace root",
                    """
                    {
                      "action": "READ_FILE",
                      "path": "relative/path/to/file"
                    }""",
                    defaultOutputSchema("path", "content", "truncated", "sizeBytes"),
                    true,
                    List.of()),
            capability("SEARCH_FILES",
                    "search for text or regex matches across the workspace or inside a scoped path",
                    """
                    {
                      "action": "SEARCH_FILES",
                      "query": "search query when searching",
                      "path": "optional scoped path"
                    }""",
                    defaultOutputSchema("query", "scope", "matches", "matchCount"),
                    true,
                    List.of()),
            capability("LIST_DIR",
                    "list directory contents relative to the workspace root with bounded depth and bounded entries",
                    """
                    {
                      "action": "LIST_DIR",
                      "path": "optional relative directory path",
                      "maxDepth": 1
                    }""",
                    defaultOutputSchema("path", "items", "truncated", "itemCount"),
                    true,
                    List.of("Use LIST_DIR to discover directory structure before reading files.")),
            capability("WEB_SEARCH",
                    "search the public web for concise external references when repository-local search is not enough",
                    """
                    {
                      "action": "WEB_SEARCH",
                      "query": "search query for external documentation or facts",
                      "maxResults": 5
                    }""",
                    defaultOutputSchema("query", "results", "resultCount"),
                    true,
                    List.of("Use WEB_SEARCH for external documentation, ecosystem facts, or recent references when local workspace search is not enough.")),
            capability("APPLY_PATCH",
                    "replace exact old text with new text inside an existing file",
                    """
                    {
                      "action": "APPLY_PATCH",
                      "path": "relative/path/to/file",
                      "oldText": "exact text to replace when applying a patch",
                      "newText": "replacement text when applying a patch",
                      "replaceAll": false
                    }""",
                    defaultOutputSchema("path", "replacements", "message"),
                    false,
                    List.of("Prefer APPLY_PATCH for targeted edits over rewriting whole files.")),
            capability("WRITE_FILE",
                    "create or overwrite a file relative to the workspace root",
                    """
                    {
                      "action": "WRITE_FILE",
                      "path": "relative/path/to/file",
                      "content": "file content when writing"
                    }""",
                    defaultOutputSchema("path", "created", "bytesWritten", "message"),
                    false,
                    List.of("Use WRITE_FILE for new files or explicit full rewrites.")),
            capability("RUN_COMMAND",
                    "run a zsh command from the workspace root, subject to approval policy",
                    """
                    {
                      "action": "RUN_COMMAND",
                      "command": "shell command when running one"
                    }""",
                    defaultOutputSchema("command", "exitCode", "stdout", "stderr", "approved", "decision"),
                    false,
                    List.of("Avoid destructive shell commands.")),
            capability("exec_command",
                    "start a long-running or interactive shell command and return bounded initial output with a reusable session id",
                    """
                    {
                      "action": "exec_command",
                      "command": "shell command to start",
                      "yieldTimeMillis": 10000,
                      "maxRuntimeMillis": 60000,
                      "pty": false
                    }""",
                    defaultOutputSchema("command", "sessionId", "running", "exitCode", "stdout", "stderr", "timedOut"),
                    false,
                    List.of(
                            "Use exec_command when you may need to poll or continue observing the same command later.",
                            "If the command keeps running, keep the returned sessionId and use write_stdin with empty input to poll for more output.")),
            capability("write_stdin",
                    "send stdin to a running exec session, or poll it with empty input for more output",
                    """
                    {
                      "action": "write_stdin",
                      "sessionId": "exec-session-id",
                      "input": "",
                      "yieldTimeMillis": 250
                    }""",
                    defaultOutputSchema("sessionId", "running", "exitCode", "stdout", "stderr", "timedOut"),
                    false,
                    List.of("Use empty input with write_stdin to poll for additional output without sending new stdin.")),
            capability("spawn_agent",
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
                    defaultOutputSchema("threadId", "nickname", "role", "status"),
                    false,
                    List.of()),
            capability("send_message",
                    "queue a plain message to an existing sub-agent thread without starting work",
                    """
                    {
                      "action": "send_message",
                      "threadId": "agent-thread-id",
                      "content": "message to queue for the agent"
                    }""",
                    defaultOutputSchema("threadId", "status", "queued"),
                    false,
                    List.of()),
            capability("assign_task",
                    "queue work for an existing sub-agent thread and let it start if idle",
                    """
                    {
                      "action": "assign_task",
                      "threadId": "agent-thread-id",
                      "content": "task to assign to the agent",
                      "interrupt": false
                    }""",
                    defaultOutputSchema("threadId", "status", "queued", "interrupted"),
                    false,
                    List.of()),
            capability("send_input",
                    "compatibility alias for assign_task",
                    """
                    {
                      "action": "send_input",
                      "threadId": "agent-thread-id",
                      "content": "message to send to the agent",
                      "interrupt": false
                    }""",
                    defaultOutputSchema("threadId", "status", "queued", "interrupted"),
                    false,
                    List.of()),
            capability("wait_agent",
                    "wait for one or more sub-agents to change status or mailbox state",
                    """
                    {
                      "action": "wait_agent",
                      "threadIds": ["agent-thread-id"],
                      "timeoutMillis": 1000
                    }""",
                    defaultOutputSchema("threadIds", "timedOut", "results"),
                    false,
                    List.of()),
            capability("resume_agent",
                    "resume a paused or waiting sub-agent thread",
                    """
                    {
                      "action": "resume_agent",
                      "threadId": "agent-thread-id"
                    }""",
                    defaultOutputSchema("threadId", "status"),
                    false,
                    List.of()),
            capability("close_agent",
                    "close a sub-agent thread subtree",
                    """
                    {
                      "action": "close_agent",
                      "threadId": "agent-thread-id"
                    }""",
                    defaultOutputSchema("threadId", "status", "closedAt"),
                    false,
                    List.of()),
            capability("list_agents",
                    "list sub-agents under the current thread or a requested parent thread",
                    """
                    {
                      "action": "list_agents",
                      "threadId": "optional-parent-thread-id",
                      "recursive": true
                    }""",
                    defaultOutputSchema("threadId", "recursive", "agents"),
                    false,
                    List.of()));

    @Override
    public List<ToolCapability> plannerToolCapabilities() {
        return DEFAULT_CAPABILITIES;
    }

    private static ToolCapability capability(String name,
                                             String description,
                                             String schema,
                                             String outputSchema,
                                             boolean supportsParallelExecution,
                                             List<String> supplementaryInstructions) {
        return new ToolCapability(
                new ResolvedToolDefinition(
                        name,
                        description,
                        schema,
                        schema,
                        outputSchema,
                        supportsParallelExecution,
                        supplementaryInstructions),
                supportsParallelExecution);
    }

    private static String defaultOutputSchema(String... fields) {
        String properties = java.util.Arrays.stream(fields)
                .map(DefaultToolCapabilityRegistry::jsonProperty)
                .collect(java.util.stream.Collectors.joining(",\n      "));
        String required = java.util.Arrays.stream(fields)
                .map(field -> "\"" + field + "\"")
                .collect(java.util.stream.Collectors.joining(", "));
        String extraProperties = properties.isBlank() ? "" : ",\n      " + properties;
        String extraRequired = required.isBlank() ? "" : ", " + required;
        return """
                {
                  "type": "object",
                  "properties": {
                    "success": {"type": "boolean"}%s
                  },
                  "required": ["success"%s],
                  "additionalProperties": true
                }""".formatted(extraProperties, extraRequired);
    }

    private static String jsonProperty(String field) {
        String type = inferredJsonType(field);
        return "\"%s\": {\"type\": \"%s\"}".formatted(field, type);
    }

    private static String inferredJsonType(String field) {
        String normalized = field == null ? "" : field.toLowerCase(java.util.Locale.ROOT);
        if (normalized.endsWith("count")
                || normalized.endsWith("code")
                || normalized.endsWith("bytes")
                || normalized.endsWith("millis")) {
            return "integer";
        }
        if (normalized.startsWith("is")
                || normalized.equals("success")
                || normalized.equals("truncated")
                || normalized.equals("created")
                || normalized.equals("approved")
                || normalized.equals("queued")
                || normalized.equals("interrupted")
                || normalized.equals("running")
                || normalized.equals("timedout")
                || normalized.equals("recursive")
                || normalized.equals("pty")) {
            return "boolean";
        }
        if (normalized.endsWith("s") || normalized.equals("results")) {
            return "array";
        }
        return "string";
    }
}
