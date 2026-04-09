# Natural-Language Sub-Agent Delegation

## Goal

Describe how upstream Codex turns an ordinary user prompt like “create a sub-agent to implement this feature” into real sub-agent work, and what that implies for the Java rebuild.

This is not a CLI-command feature. In Codex, delegation is a natural outcome of the model seeing collaboration tools and deciding to use them.

## Upstream Behavior

The end-to-end flow in upstream Codex is:

1. The user submits a normal prompt.
2. The model sees collaboration tools in its tool registry.
3. The model chooses one of the collaboration tools:
   - `spawn_agent`
   - `send_message`
   - `followup_task`
   - `wait_agent`
4. The runtime creates a new sub-thread or routes a message to an existing one.
5. The app-server surfaces the collaboration activity as streamed turn items, including `collabToolCall`.

The important point is that the user does not need to type a special “spawn” command. Delegation is model-driven.

### Tool exposure

The collaboration tools are registered in the tool plan, not hard-coded into the CLI UX:

- `tools/src/agent_tool.rs` defines the collaboration tools and their model-facing descriptions.
- `tools/src/tool_registry_plan.rs` registers those tools when collaboration is enabled.

The `spawn_agent` description is especially important because it tells the model when delegation is appropriate. It only authorizes spawning when the user explicitly asks for sub-agents, delegation, or parallel agent work, and it warns the model not to treat vague requests for depth or research as permission to spawn.

### Spawn path

When the model chooses `spawn_agent`, the runtime handles it in the multi-agent spawn handler:

- `core/src/tools/handlers/multi_agents_v2/spawn.rs`

That path does several things:

- builds the child agent config
- applies role/model/depth overrides
- injects spawned-agent developer instructions
- creates a new thread through `AgentControl.spawn_agent_with_metadata(...)`
- sends the initial delegated task into the child thread
- emits collaboration lifecycle events for the parent turn

### Messaging path

Upstream Codex distinguishes different collaboration semantics:

- `send_message` queues text on an existing agent without necessarily waking it
- `followup_task` sends work that should trigger the target to run
- `wait_agent` waits on mailbox changes rather than busy-polling a thread state

These are implemented in:

- `core/src/tools/handlers/multi_agents_v2/message_tool.rs`
- `core/src/tools/handlers/multi_agents_v2/wait.rs`

That split matters because delegation is not just “send input to a child.” It is a small collaboration protocol with queue-only messaging, task-triggering messaging, and explicit waiting.

### Runtime and identity

The runtime creates a child thread/session through `AgentControl`:

- `core/src/agent/control.rs`

The sub-agent is represented as a threaded session with lineage and metadata. The session source encodes that it is a sub-agent spawned from another thread:

- `protocol/src/protocol.rs`

That source carries the parent thread id, depth, agent path, nickname, and role. In other words, a sub-agent is not just a temporary tool call; it becomes a first-class thread with lineage.

### App-server surface

The app-server surfaces collaboration activity as streamed notifications and item types:

- `app-server/README.md`

The key concept is `collabToolCall`, which records collaboration actions such as `spawn_agent`, `send_input`, `resume_agent`, `wait`, and `close_agent`. That makes collaboration visible to clients as part of the runtime item stream instead of hiding it behind ad hoc CLI behavior.

## What This Means For The Java Rebuild

Our Java rebuild already has useful sub-agent mechanics, but we should treat them as a first draft, not the final shape.

### What we do differently today

- We expose explicit `agent/*` app-server RPCs in the Java surface.
- We treat sub-agent control as a first-class runtime API.
- We already have a mailbox-like wait path and sub-agent lineage metadata.

That is functional, but it is more runtime-local than the upstream Codex model.

### Recommended direction

1. Keep delegation model-driven, not command-driven.
2. Move collaboration visibility toward stream items/events instead of relying on a separate `agent/*` UX.
3. Preserve the `spawn_agent` / `send_message` / `followup_task` / `wait_agent` semantic split.
4. Keep sub-agents as real sub-threads with lineage metadata.
5. Make the CLI mostly a client of the runtime, not the place where delegation semantics live.

### Architectural cutline

The Java rebuild should aim for this shape:

- user prompt
- model chooses collaboration tools
- runtime creates or routes sub-agent threads
- app-server emits collaboration items/events
- CLI renders the stream

That keeps the Java version aligned with Codex’s real collaboration model instead of inventing a separate “manual sub-agent control” UX.

## Concrete Implementation Sequence

This should be implemented as a runtime-first migration, not as a CLI feature sprint.

### Phase 1: Freeze the user-facing delegation model

Goal:

- keep delegation natural-language driven
- avoid adding new user commands for spawning or supervising sub-agents

Implementation notes:

- plain user prompts remain the only primary user entrypoint
- the model continues to decide whether to use collaboration tools
- any existing Java-only direct `agent/*` controls should be treated as transitional runtime wiring, not as the desired public UX

Cutline:

- users should not need a dedicated spawn command to create delegated work
- CLI help and docs should describe delegation as prompt-driven behavior

### Phase 2: Add collaboration items to the Java protocol

Goal:

- represent collaboration in the turn/item stream the way Codex represents `collabToolCall`

Implementation notes:

- add a first-class collaboration item type in `codex-protocol`
- model item lifecycle states such as in-progress, completed, and failed
- include sender thread id, receiver thread id, new child thread id, prompt preview, and agent status where available

Cutline:

- collaboration actions become visible as turn items instead of only runtime-side status changes
- app-server notifications can carry collaboration item updates cleanly

### Phase 3: Make the runtime emit collaboration items from existing agent control

Goal:

- keep the existing Java sub-agent mechanics, but surface them through the item stream

Implementation notes:

- wire `spawnAgent`, `sendMessage`, `assignTask`, `waitAgent`, `resumeAgent`, and `closeAgent` to emit collaboration items/events
- keep mailbox-driven wait semantics
- preserve sub-thread lineage metadata and agent identity fields

Cutline:

- a client observing turn items can understand delegated work without calling special runtime-only APIs
- collaboration state is reconstructable from persisted thread history

### Phase 4: Make the CLI render collaboration items instead of relying on direct control UX

Goal:

- make the CLI behave like a client of collaboration events, not the owner of delegation semantics

Implementation notes:

- render collaboration items in the same streamed output path as other turn items
- keep agent tree navigation as an inspection tool, not the primary delegation mechanism
- preserve plain user prompting as the main way to trigger delegation

Cutline:

- users can see when Codex delegates, to whom, and with what status through normal streamed output
- CLI-specific delegation commands are no longer required for ordinary usage

### Phase 5: Downgrade or remove the public `agent/*` app-server surface

Goal:

- align the public Java app-server with Codex’s collaboration-item-first model

Implementation notes:

- once collaboration items are complete, treat explicit `agent/*` RPCs as internal, compatibility-only, or removable
- keep whatever internal runtime control surface is still useful for testing and orchestration
- avoid baking Java-only agent control RPCs deeper into future clients

Cutline:

- collaboration is primarily observable and driven through tool calls plus streamed items
- the public transport shape is closer to upstream Codex

### Phase 6: Add system-owned agents after the collaboration model is stable

Goal:

- support Codex-like internal agent patterns such as reviewer or guardian-style sub-agents

Implementation notes:

- do this only after the collaboration-item model is stable
- treat system-owned agents as specialized uses of the same sub-thread and item model, not a separate subsystem

Cutline:

- internal agents reuse the same lineage, mailbox, and collaboration event model as user-triggered delegation

## Immediate Next Increment

The best next implementation slice is:

1. add a `collabToolCall`-style item to the Java protocol
2. emit it from the existing sub-agent runtime paths
3. render it in the CLI stream
4. leave current `agent/*` app-server methods in place temporarily as a compatibility bridge

That sequence preserves working behavior while moving the architecture toward Codex instead of away from it.
