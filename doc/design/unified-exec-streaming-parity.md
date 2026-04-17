# Unified Exec Streaming Parity

## Goal

Move the Java runtime from a one-shot shell-command model to a Codex-style unified exec model that supports:

- persistent command sessions
- partial command output returned to the model
- live output deltas streamed to the CLI/app-server
- follow-up polling while a process is still running
- eventual stdin, PTY, resize, and terminate support

This design is specifically about the behavior where Codex appears to "watch" a long-running command and comment on it while the command is still running.

The reference point is the current upstream Codex implementation in `../../../codex`.

## Progress Tracker

Overall status: `in progress`

### Delivery phases

- [x] Phase A: runtime foundation
- [x] Phase B: streaming output to CLI/app-server
- [x] Phase C: model-facing unified exec tools
- [x] Phase D: PTY and richer terminal interaction

### Implementation tasks

- [x] Add unified exec session ids, summaries, statuses, and poll-result types
- [x] Add an `ExecSessionManager` runtime service
- [x] Delegate `ShellCommandToolImpl` through the unified exec manager
- [x] Delegate `thread/shellCommand` through the unified exec manager
- [x] Add transcript buffering with bounded model snapshots and incremental UI deltas
- [x] Add background output watchers and output-delta notifications
- [x] Add command-exec app-server RPCs
- [x] Add model-facing `exec_command`
- [x] Add model-facing `write_stdin`
- [x] Update the planner loop to support repeated polling and follow-up execution
- [x] Converge background-terminal lifecycle onto unified exec sessions
- [x] Add PTY, resize, terminate, and terminal-interaction support

## Why This Matters

Right now the Java rebuild has two shell-related behaviors, but neither matches upstream unified exec:

1. ordinary shell execution is one-shot
2. background terminals are detached launch records, not interactive exec sessions

That means the Java agent cannot currently do the upstream pattern:

- start a command
- get some initial output back quickly
- keep the process alive
- observe more output in later tool calls
- comment on progress
- optionally send more input

This is a visible gap in CLI parity because long-running commands are one of the most obvious places where upstream Codex feels more capable than the current Java version.

## Upstream Codex Reference

The relevant upstream pieces are spread across tools, runtime, and app-server protocol layers.

### Model-facing tools

Upstream exposes two core tools in [`local_tool.rs`](../../../codex/codex-rs/tools/src/local_tool.rs):

- `exec_command`
- `write_stdin`

Important semantics:

- `exec_command` starts a command and returns output seen so far
- if the process is still alive, the result includes a session id / process id
- `write_stdin` can send input to an existing session
- `write_stdin` can also poll with empty input

That polling behavior is explicit in the tool description and is the key to incremental observation.

### Unified exec handlers

The main runtime handler is [`unified_exec.rs`](../../../codex/codex-rs/core/src/tools/handlers/unified_exec.rs).

Important upstream defaults:

- initial exec yield is much longer than stdin polling
- `exec_command` yields a bounded early snapshot
- `write_stdin` uses short polling windows

This matches the user-visible behavior:

- first call starts work and returns something useful
- later calls quickly poll for more output

### Process/session management

The persistent process/session logic lives in [`process_manager.rs`](../../../codex/codex-rs/core/src/unified_exec/process_manager.rs).

Important upstream behavior:

- a live process is stored as a reusable exec session
- the initial tool call waits only until a deadline, not until process exit
- follow-up tool calls can read more output from the same session
- session results include bounded output plus "still running" metadata

### Live output streaming

Background output streaming lives in [`async_watcher.rs`](../../../codex/codex-rs/core/src/unified_exec/async_watcher.rs).

Important upstream behavior:

- output is read continuously while the process is alive
- output is emitted as deltas on safe text boundaries
- a final end event is emitted when the process exits

This is what lets the UI keep updating in real time even before the model runs again.

### Tool output returned to the model

The model does not consume raw stdout deltas directly. Upstream formats exec results into tool output items in [`tools/context.rs`](../../../codex/codex-rs/core/src/tools/context.rs) and [`tools/mod.rs`](../../../codex/codex-rs/core/src/tools/mod.rs).

That is an important design point:

- UI gets live deltas
- model gets bounded, structured snapshots

### Follow-up model loop

When the model emits a tool call, upstream marks that a follow-up model pass is needed in [`stream_events_utils.rs`](../../../codex/codex-rs/core/src/stream_events_utils.rs), and the main agent loop continues in [`codex.rs`](../../../codex/codex-rs/core/src/codex.rs).

That is why the assistant can appear to "notice" new output and then react to it:

- tool call returns output snapshot
- runtime schedules another model pass
- model comments or polls again
- repeat until completion

### App-server notifications

The live event surface is visible in:

- [`common.rs`](../../../codex/codex-rs/app-server-protocol/src/protocol/common.rs)
- [`v2.rs`](../../../codex/codex-rs/app-server-protocol/src/protocol/v2.rs)

Relevant notifications include:

- `item/commandExecution/outputDelta`
- `item/commandExecution/terminalInteraction`

## Current Java State

The Java rebuild currently splits shell behavior across a few smaller features.

### One-shot tool execution

[`ShellCommandToolImpl.java`](../../codex-tools-local/src/main/java/org/dean/codex/tools/local/ShellCommandToolImpl.java) runs:

- `zsh -lc <command>`
- waits for process completion or timeout
- collects full stdout/stderr
- returns one final `ShellCommandResult`

This is useful for short commands, but it has no concept of:

- a live session id
- partial output snapshots
- follow-up polling
- stdin continuation
- live output streaming

### Thread shell command API

The app-server currently exposes [`thread/shellCommand`](../../codex-core/src/main/java/org/dean/codex/core/appserver/CodexAppServerSession.java) through [`ThreadShellCommandResponse.java`](../../codex-protocol/src/main/java/org/dean/codex/protocol/appserver/ThreadShellCommandResponse.java).

That response shape is still small:

- one final `ShellCommandResult`
- optional `BackgroundTerminalSummary`

It does not model an upstream-style interactive exec session.

### Background terminals

[`InProcessCodexAppServer.java`](../../codex-runtime-spring-ai/src/main/java/org/dean/codex/runtime/springai/appserver/InProcessCodexAppServer.java) supports background terminal launch and cleanup.

That helps with detached work, but it is still not unified exec:

- it launches a background process by shelling out and capturing `$!`
- it stores a pid and metadata
- it can later clean up active background processes

It does not support:

- output delta notifications
- interactive stdin
- bounded polling for model follow-up
- process transcript snapshots
- terminal resize or PTY behavior

### Planner/runtime behavior

The current Java agent can run a shell tool as one action, but it cannot keep observing a single long-running command in the upstream way because the tool contract is still one-shot.

## Design Principles

1. Keep UI streaming separate from model observation.
2. Use one execution backend for both CLI-visible terminals and model-visible command polling.
3. Preserve bounded tool outputs for the model; do not stuff raw unbounded deltas into prompt history.
4. Treat exec sessions as runtime-owned resources that may outlive a single planner step.
5. Converge the current shell-command path and background-terminal path instead of keeping two unrelated terminal systems forever.

## Proposed Java Architecture

### 1. Introduce a unified exec session model

Add a new runtime abstraction, for example:

- `ExecSessionId`
- `ExecSessionSummary`
- `ExecSessionStatus`
- `ExecSessionOutputChunk`
- `ExecPollResult`

Each session should track at least:

- stable session id
- owning thread id
- command
- cwd
- whether PTY was requested
- process id when available
- started time
- exit code when completed
- transcript buffer
- current read offset / cursor

This is the core missing primitive. Without it, the rest becomes ad hoc.

### 2. Add a single execution manager

Introduce a runtime service such as `ExecSessionManager` that owns:

- start
- poll
- write stdin
- resize
- terminate
- cleanup

This service should replace the current split between:

- one-shot shell execution in `ShellCommandToolImpl`
- thread-scoped detached terminals in `InProcessCodexAppServer`

In the short term, the old APIs can delegate into this manager so we do not break the current product surface while parity work is in flight.

### 3. Split session transcript into two views

Each live exec session should maintain:

- a full runtime transcript buffer for event delivery and later inspection
- bounded per-call snapshots for model/tool results

That gives us the upstream split:

- UI receives incremental deltas
- model receives truncated snapshots

This avoids prompt bloat and makes repeated polling cheap.

### 4. Stream output deltas as runtime notifications

Add background output watchers that:

- continuously read stdout/stderr while the process is alive
- append to session transcript state
- emit output delta notifications through the app-server

The Java equivalent should be close in spirit to upstream:

- output delta event
- final process end event
- optional terminal-interaction event later

This is the main ingredient for CLI-visible "live command" behavior.

### 5. Expose model-facing `exec_command` and `write_stdin`

The agent/tool layer should stop treating shell execution as a single blocking tool.

Add a new model-facing contract roughly shaped like upstream:

- `exec_command`
- `write_stdin`

Expected behavior:

- `exec_command` starts a session and returns output seen before `yield_time_ms`
- if the process is still alive, the result returns a `sessionId`
- `write_stdin` can send input or poll with empty input
- both return bounded snapshots, not full transcripts

This is the mechanism that lets the model observe long-running commands incrementally.

### 6. Drive follow-up sampling from exec tool results

The agent loop should treat exec results like any other tool output:

- run tool
- record tool result item
- continue planner loop

If the process is still alive, the model can then:

- comment on output
- poll again
- send stdin
- stop polling and move on

This reproduces the upstream "observe, react, observe again" behavior without textual ReAct formatting hacks.

### 7. Converge background terminals onto unified exec

Background terminals should become a policy mode of unified exec, not a second subsystem.

That means:

- a background terminal is just an exec session marked detached or user-visible
- cleanup and listing operate on the same session store
- later CLI terminal attach behavior becomes possible

## Proposed Protocol Surface

The protocol gap is larger than just one tool. We need both app-server and model-facing surfaces.

### App-server additions

Add a command-exec family close to upstream:

- `command/exec`
- `command/exec/write`
- `command/exec/resize`
- `command/exec/terminate`

Add streamed notifications such as:

- `item/commandExecution/outputDelta`
- `item/commandExecution/completed`
- `item/commandExecution/terminalInteraction`

The exact names can be adapted to the Java protocol style, but the behavior should match upstream.

### Compatibility path

Keep these existing APIs temporarily:

- `thread/shellCommand`
- `thread/backgroundTerminals/clean`

But move them onto the same underlying exec manager. This gives us an incremental path instead of a flag day rewrite.

## Proposed Delivery Phases

### Phase A: Runtime foundation

Tracker:

- [x] Add exec session ids and summaries
- [x] Add an in-memory exec session manager
- [x] Add bounded polling result types
- [x] Make one-shot shell execution delegate internally to the exec manager

Add:

- exec session ids and summaries
- in-memory exec session manager
- bounded polling result type
- one-shot shell path delegating to the new manager internally

Cutline:

- short commands still work
- live sessions can exist in memory
- sessions can be polled programmatically

### Phase B: Streaming output to CLI/app-server

Tracker:

- [x] Add output watchers
- [x] Add output delta notifications
- [x] Add completed and terminated notifications
- [x] Add CLI rendering for live command output

Add:

- output watchers
- output delta notifications
- completed/terminated notifications
- CLI rendering for live command output

Cutline:

- CLI can see a long-running command update while it runs
- current background-terminal listing/cleanup still works

### Phase C: Model-facing unified exec tools

Tracker:

- [x] Add `exec_command`
- [x] Add `write_stdin`
- [x] Back agent tool execution with the unified exec manager
- [x] Add bounded tool result formatting with session ids

Add:

- `exec_command`
- `write_stdin`
- agent tool execution backed by the exec manager
- bounded tool result formatting with session ids

Cutline:

- model can start a command, observe initial output, and poll again while it is running

### Phase D: PTY and richer terminal interaction

Tracker:

- [x] Add PTY-backed sessions
- [x] Add stdin writes
- [x] Add resize support
- [x] Add terminal-interaction events

Add:

- PTY-backed sessions
- stdin writes
- resize support
- terminal-interaction events

Cutline:

- interactive terminal programs become possible
- app-server surface is much closer to upstream Codex

## Recommended Issue Breakdown

- [x] Issue 1: add unified exec session types and a runtime session manager
- [x] Issue 2: delegate existing shell-command execution through the new manager
- [x] Issue 3: add output watchers and output delta notifications
- [x] Issue 4: add command-exec app-server RPCs
- [x] Issue 5: add model-facing `exec_command` and `write_stdin`
- [x] Issue 6: update the planner loop to support repeated polling and follow-up
- [x] Issue 7: converge background-terminal storage and cleanup onto unified exec sessions
- [x] Issue 8: add PTY, resize, and terminal-interaction support

## What We Should Not Do

- Do not fake incremental observation by repeatedly rerunning the same shell command.
- Do not expose raw unbounded stdout deltas directly to the model prompt.
- Do not keep one-shot shell execution and background terminals as permanently separate execution stacks.
- Do not make long-running process state depend on a single planner step surviving in memory without a session abstraction.

## Suggested Next Implementation Slice

The best first bounded slice is:

1. introduce exec session ids, summaries, and an in-memory `ExecSessionManager`
2. make `thread/shellCommand` and `ShellCommandToolImpl` delegate into it
3. support bounded "start and poll" behavior internally, even before the full public protocol lands

That gives us the core runtime primitive first. Once that exists, CLI streaming and model polling become straightforward follow-on work instead of another refactor.
