# Roadmap

This repository is rebuilding Codex in Java, with the CLI as the first product surface and Spring AI as the runtime backbone.

## Priority Order

These are the highest-value directions to close next. The goal is to keep the Java rebuild aligned with Codex as a runtime platform, not just a tool-driven demo.

### 1. Non-blocking CLI interaction and steering

This item is effectively complete for ordinary prompt usage. The CLI now stays interactive while a regular turn runs, treats plain input as steering, and keeps streamed output readable. The remaining synchronous paths are intentional lifecycle commands such as compaction and approval flows.

#### Codex reference model

Rust Codex already assumes an event-driven client:

- `turn/start` begins a turn and immediately returns while notifications continue streaming through the app-server.
- `turn/steer` appends input to the currently active regular turn, requires `expectedTurnId`, and does not start a new turn or accept turn overrides.
- `turn/interrupt` cancels the active turn, but background terminals remain independent.
- The TUI keeps running its input/render loop while turns are active and submits thread operations asynchronously.
- On user submit, the TUI first tries `turn_steer(...)` if the thread already has an active turn; only if there is no active turn does it fall back to `turn_start(...)`.
- The runtime can also wake idle sessions from queued pending input and mailbox work, so same-thread follow-up input is part of the normal session model rather than a special escape hatch.

Reference points:

- `codex-rs/app-server/README.md`
- `codex-rs/tui/src/app.rs`
- `codex-rs/core/src/codex_thread.rs`
- `codex-rs/core/src/tasks/mod.rs`

#### Current Java state

The Java runtime already has meaningful steering support, and the CLI now uses it in the normal interactive path:

- `DefaultCodexRuntimeGateway.turnSteer(...)` accepts same-turn input.
- `SpringAiCodexAgent` consumes steering between planner steps.
- The CLI does not expose `/steer`; plain input steers an in-progress regular turn.
- `CodexConsoleRunner.runInteractiveLoop(...)` keeps reading input while a turn is active.
- streamed app-server notifications and prompt output coexist in the same terminal

Explicit synchronous helpers still exist for intentional lifecycle flows:

- compaction waits for its completion notifications
- approval resume/reject flows remain direct, user-triggered actions

Reference points:

- `codex-cli/src/main/java/org/dean/codex/cli/CodexConsoleRunner.java`
- `codex-runtime-spring-ai/src/main/java/org/dean/codex/runtime/springai/runtime/DefaultCodexRuntimeGateway.java`
- `codex-runtime-spring-ai/src/main/java/org/dean/codex/runtime/springai/agent/SpringAiCodexAgent.java`

#### Main gap

The main interactive gap is now small and intentional:

- remaining synchronous paths are limited to explicit lifecycle commands, not ordinary prompt submission
- the CLI is still line-oriented rather than a full TUI
- prompt redraw can still be a little noisy under very chatty async output

#### Implementation direction

1. Keep the non-blocking interactive loop as the default prompt path.
2. Preserve explicit synchronous helpers only for lifecycle-heavy flows that need them.
3. Continue tightening prompt redraw and notification formatting when noisy async output appears.
4. Treat plain input during an active regular turn as `turn/steer`.
5. Treat plain input while idle as `turn/start`.
6. Preserve explicit rejection for non-steerable turn kinds such as review or manual compaction.

### 2. Thread management

We already have more than basic session persistence, but the Java runtime still does not treat a thread as fully as Codex does: a loaded, subscribable, metadata-rich runtime object.

#### Codex reference model

Rust Codex thread management includes much more than start/resume:

- lifecycle operations such as `thread/start`, `thread/resume`, `thread/fork`, `thread/archive`, `thread/unarchive`, `thread/unsubscribe`, `thread/rollback`, and `thread/compact/start`
- thread-scoped operations such as `thread/shellCommand`, `thread/backgroundTerminals/clean`, and `thread/realtime/*`
- thread metadata patching via `thread/metadata/update`
- user-facing naming via `thread/name/set`
- notifications like `thread/status/changed`, `thread/started`, `thread/closed`, `thread/archived`, and `thread/unarchived`
- richer persisted metadata including reasoning effort, sandbox policy, approval mode, token usage, first user message, git info, rollout path, and archive state

Reference points:

- `codex-rs/app-server/README.md`
- `codex-rs/state/src/model/thread_metadata.rs`
- `codex-rs/core/src/codex/rollout_reconstruction.rs`

#### Current Java state

The Java app-server/runtime already supports:

- `threadStart`, `threadResume`, `threadFork`, `threadArchive`, `threadUnarchive`, `threadRollback`, and `threadCompactStart`
- `threadUnsubscribe`, `threadNameSet`, `threadMetadataUpdate`, and the first thread-scoped runtime service slice via `threadShellCommand`
- `threadBackgroundTerminalsClean` as the explicit cleanup path for thread-owned background process state
- persisted thread summaries with title, model, cwd/path, archive state, and agent lineage
- thread tree navigation and related-thread reads

Reference points:

- `codex-core/src/main/java/org/dean/codex/core/appserver/CodexAppServerSession.java`
- `codex-protocol/src/main/java/org/dean/codex/protocol/conversation/ThreadSummary.java`
- `codex-runtime-spring-ai/src/main/java/org/dean/codex/runtime/springai/conversation/FileSystemConversationStore.java`

#### Main gap

The gap is no longer "can we persist sessions?" It is that our threads are still less operationally complete than Codex threads:

- `thread/unsubscribe`, `thread/name/set`, and minimal `thread/metadata/update` now exist, but the semantics are still smaller than Codex's full connection-scoped and metadata-rich model
- `thread/unsubscribe` is now closer to connection-scoped behavior, but it still needs richer loaded-thread lifecycle and notification parity to feel like Codex
- `thread/resume` now behaves more like a reattachment point, but loaded-thread/session attachment semantics are still simpler than Codex's
- loaded/not-loaded lifecycle semantics are still weaker than Codex's
- the first thread-scoped runtime service exists now (`thread/shellCommand`), and thread-owned background cleanup now exists, but full ownership/lifecycle tracking is still simpler than Codex
- thinner metadata and weaker list/filter/index behavior
- no realtime sessions yet
- simpler reconstruction/rollback semantics than Codex rollout reconstruction

#### Implementation direction

1. Add explicit thread subscription and unload semantics.
2. Add thread naming and metadata patch operations.
3. Expand `ThreadSummary` and backing persistence with approval/sandbox/model-effort/token/git metadata.
4. Move toward stronger list/filter/index support over persisted threads.
5. Add thread-scoped runtime services such as background-terminal ownership and realtime sessions after the lifecycle model is solid.

### 3. Multi-agent support

We already have real sub-agent mechanics, but Codex treats collaboration as a richer runtime/protocol feature than we currently do.

#### Codex reference model

Rust Codex supports a broad multi-agent surface:

- `spawn_agent`, `send_input`, `send_message`, `assign_task`, `resume_agent`, `wait_agent`, and `list_agents`
- mailbox-oriented waiting and richer agent-message delivery semantics in MultiAgentV2
- path-based or canonical target resolution for agents
- collaboration activity represented in the event/item stream via `collabToolCall`
- internal system-owned agent patterns such as `guardian_subagent`

Reference points:

- `codex-rs/tools/src/agent_tool.rs`
- `codex-rs/core/src/tools/handlers/multi_agents_v2`
- `codex-rs/app-server/README.md`

#### Current Java state

The Java runtime already supports:

- `spawnAgent`, `sendInput`, `waitAgent`, `resumeAgent`, `closeAgent`, and `listAgents`
- app-server/protocol split for `agent/sendMessage` and `agent/assignTask`, with `agent/sendInput` kept as a compatibility alias
- mailbox state now carries a monotonic sequence and pending-message count, and the app-server publishes `agent/mailbox/updated` when collaboration mail arrives
- persisted sub-agent lineage in thread storage and thread summaries
- app-server/protocol exposure for the existing multi-agent control primitives
- CLI tree navigation for related agent threads

Reference points:

- `codex-core/src/main/java/org/dean/codex/core/agent/AgentControl.java`
- `codex-runtime-spring-ai/src/main/java/org/dean/codex/runtime/springai/runtime/DefaultCodexRuntimeGateway.java`
- `codex-protocol/src/main/java/org/dean/codex/protocol/conversation/ThreadSummary.java`
- `codex-cli/src/main/java/org/dean/codex/cli/CodexConsoleRunner.java`

#### Main gap

The current Java implementation has multi-agent mechanics, but not yet Codex-grade collaboration semantics:

- delegation is still more runtime-local than collaboration-item-first
- the runtime now exposes `send_message` and `assign_task`, and mailbox state is visible through `agent/mailbox/updated`, but `send_input` remains a compatibility alias
- waiting behavior is now mailbox-sequence driven, but it is still simpler than Codex’s richer delivery/mailbox model
- collaboration is still not represented as first-class thread items/events in the same way
- the Java app-server currently exposes explicit `agent/*` controls, while upstream Codex’s public client-facing shape is more centered on collaboration tools plus streamed `collabToolCall` items
- there are no internal system-owned agent flows such as guardian/reviewer agents
- CLI supervision is still shallow compared with Codex’s broader agent-control model

#### Implementation direction

1. Keep delegation prompt-driven and model-driven, not command-driven.
2. Preserve the collaboration semantic split: `spawn_agent`, queue-only messaging, task-triggering follow-up, and mailbox-driven waiting.
3. Represent collaboration actions as first-class thread items/events in the Java protocol and runtime.
4. Make the CLI render collaboration items in the normal turn stream instead of depending on direct delegation UX.
5. Treat public `agent/*` app-server methods as a transitional compatibility bridge, not the target public architecture.
6. Add room for internal system-owned agents after the collaboration-item model is stable.

## Later Priorities

These remain important, but they should follow the top three because they depend on the runtime shape being correct first.

### 4. App-server/client lifecycle

The transport boundary should keep growing into a stronger client-facing runtime surface with clearer initialization, connection-scoped behavior, and transport semantics.

### 5. Metadata and indexing

Threads need richer metadata for listing, filtering, and resuming. This is partly a thread-management concern, but it is also a standalone indexing concern once more sessions accumulate.

### 6. Extensibility surface

Codex grows through skills, plugins, apps, and related discovery flows. The Java version should extend the skill system without hard-coding all future extension types into the CLI.

### 7. Approvals, sandboxing, and execution UX

Approval-aware execution exists, but the user workflow still needs to feel more like a first-class runtime path.

### 8. Context reconstruction and compaction

Prompt construction should keep moving out of the agent and into reusable runtime services, with compaction and replay getting closer to Codex-style behavior over time.

## How To Use This Roadmap

Keep this document short and directional. Add new items here when they affect the core runtime shape, the CLI interaction model, or the app-server boundary.
