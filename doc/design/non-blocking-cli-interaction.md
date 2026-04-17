# Non-Blocking CLI Interaction Design

## Goal

Move the Java CLI from a synchronous "submit a prompt, then block until the turn finishes" model to a Codex-style interactive client that:

- stays responsive while a turn is running
- streams turn notifications continuously
- accepts plain user input during an active regular turn
- routes that input to `turn/steer` when possible
- falls back to `turn/start` when no active steerable turn exists

Status: implemented for ordinary interactive prompt usage, with explicit synchronous handling retained for lifecycle-heavy flows such as compaction and approval actions.

The reference point is the current upstream Codex app-server, TUI, and core session/task behavior in `../../../codex`.

## Current Gap

The Java runtime already supports steering, and the CLI now uses it for normal prompt submission. The remaining caveat is that a few explicit lifecycle-heavy flows still use synchronous helpers by design.

Current Java behavior:

- `CodexConsoleRunner.runInteractiveLoop(...)` keeps an input reader and notification loop active at the same time
- ordinary prompt submission no longer depends on `waitForTurn(...)`
- no user-facing `/steer` command exists; plain input is the steering path
- runtime-side steering already exists in `DefaultCodexRuntimeGateway.turnSteer(...)`
- the agent already drains steering input between planner steps
- streamed notifications are rendered while the prompt remains available

Target behavior:

- the CLI remains interactive while notifications stream
- plain input during an active regular turn becomes same-turn steering
- plain input while idle starts a new turn
- explicit interrupt and approval flows continue to work
- non-steerable turn kinds still reject same-turn steering cleanly

## Codex Reference Model

The upstream Rust Codex already assumes a non-blocking client model.

Observed behavior from the reference repo:

- `turn/start` returns immediately and the client continues consuming streamed notifications from the app-server
- `turn/steer` appends input to the current regular turn and requires `expectedTurnId`
- `turn/interrupt` cancels the active turn, but background terminals are independent
- the TUI remains in its event loop while turns are active and submits thread ops asynchronously
- when the user submits input, the TUI first tries `turn_steer(...)` if there is an active turn for the thread
- if steering fails because there is no active turn, the client clears stale active-turn state and starts a new turn instead
- if steering fails because the active turn kind is not steerable, the client surfaces that as a user-facing rejection instead of silently changing semantics
- the core session can also wake idle work from queued pending input and mailbox items, which reinforces that follow-up input is part of the runtime model, not a CLI hack

Key reference points:

- `codex-rs/app-server/README.md`
- `codex-rs/tui/src/app.rs`
- `codex-rs/core/src/codex_thread.rs`
- `codex-rs/core/src/tasks/mod.rs`

## Design Principles

1. Keep the CLI as a client, not a second runtime.
2. Reuse the app-server boundary instead of bypassing it.
3. Keep turn state authoritative in notifications and app-server responses, not in ad hoc CLI guesses.
4. Prefer plain-input steering semantics over adding more explicit commands.
5. Keep the line-oriented CLI simple; do not try to become a full TUI.

## Proposed Interaction Model

### User input routing

When a line is entered:

- if it is a slash command, handle it as a command
- if there is an active regular turn for the active thread, try `turn/steer`
- if there is no active turn, call `turn/start`
- if steering fails because the active turn disappeared, clear stale active-turn state and retry as `turn/start`
- if steering fails because the active turn is not steerable, show the reason and do not silently convert it into a new turn

### Notification handling

Notifications continue to stream while user input remains available.

The CLI should:

- subscribe once per active thread
- continuously process streamed app-server notifications
- update local active-turn state from `turn/started`, `turn/completed`, and item notifications
- serialize terminal output so streamed updates and prompt-related output do not corrupt each other

### Input behavior during active turns

For a regular active turn:

- plain user text means "steer the current turn"
- `/interrupt` remains explicit
- `/approve`, `/reject`, `/history`, `/threads`, and similar slash commands remain available

For a non-steerable active turn such as review or manual compaction:

- plain user text does not implicitly start a second turn
- the CLI tells the user the active turn cannot be steered
- the user can wait, interrupt, or use the appropriate explicit command flow

## Proposed CLI Architecture

The current `CodexConsoleRunner` does too much in one synchronous loop. The non-blocking version should introduce a small internal event model.

### Core pieces

#### 1. `ConsoleInputReader`

A dedicated stdin reader thread or virtual thread that:

- blocks on console input safely
- converts each completed line into a `ConsoleEvent`
- does not own turn waiting or notification handling

This keeps the overall CLI non-blocking without requiring non-portable terminal tricks.

#### 2. `ConsoleEventLoop`

A single coordinator loop that consumes:

- user input events
- app-server notifications
- lifecycle events such as transport errors or shutdown

This becomes the main owner of active-thread and active-turn interaction state.

#### 3. `ActiveTurnTracker`

A focused state holder that tracks, for the active thread:

- current active turn id
- whether the current turn is steerable
- whether a turn is awaiting approval
- whether a terminal state has been reached

This state should be driven by notifications first and by request results second.

#### 4. `ConsoleRenderer`

A single synchronized rendering path for:

- streamed turn items
- command results
- steering rejections
- approval prompts
- prompt redraws

The main job here is to avoid interleaved stdout output from multiple sources.

## Delivery Cutlines

- Interaction foundation MVP: Issues 1 through 4
- User-visible steering parity foundation: Issues 1 through 7
- Broader CLI parity follow-on: after Issue 7

## Dependency Order

1. Issue 1 -> Issue 2 -> Issue 3
2. Issue 4 depends on Issues 1 through 3
3. Issue 5 depends on Issues 2 through 4
4. Issue 6 depends on Issues 3 through 5
5. Issue 7 depends on Issues 4 through 6

## Issue 1: Separate stdin reading from turn waiting

Status: `completed`

Depends on: none

Scope:

- stop using a single synchronous loop that reads input and waits for turn completion in the same control path
- introduce a dedicated input reader that can block independently
- convert entered lines into internal console events

Acceptance:

- a running turn no longer prevents the process from accepting another line of input
- the CLI still supports normal line-based input in a terminal
- startup, shutdown, and `quit` remain correct

Likely touch points:

- `codex-cli/src/main/java/org/dean/codex/cli/CodexConsoleRunner.java`

## Issue 2: Introduce a CLI event loop and active-turn tracker

Status: `completed`

Depends on: Issue 1

Scope:

- add a small internal event model for user input, notifications, and lifecycle changes
- track the active turn for the active thread without relying on `waitForTurn(...)`
- make turn completion and interruption update that state cleanly

Acceptance:

- the CLI can tell whether the active thread currently has a turn in progress
- active-turn state survives normal notification ordering
- stale active-turn state can be cleared when the app-server rejects steering with "no active turn to steer"

Likely touch points:

- `codex-cli/src/main/java/org/dean/codex/cli/CodexConsoleRunner.java`
- `codex-cli/src/main/java/org/dean/codex/cli/appserver/**`

## Issue 3: Route plain input to `turn/steer` before `turn/start`

Status: `completed`

Depends on: Issue 2

Scope:

- on normal user input, attempt `turn/steer` if the active thread has an active regular turn
- if there is no active turn, call `turn/start`
- if steering fails due to missing active turn, clear the stale tracker state and retry as `turn/start`

Acceptance:

- follow-up plain input during an active regular turn is appended to that turn
- follow-up plain input after a turn finishes starts a new turn
- no second turn is created accidentally while a steerable active turn is still running

Likely touch points:

- `codex-cli/src/main/java/org/dean/codex/cli/CodexConsoleRunner.java`
- `codex-protocol`
- `codex-cli/src/main/java/org/dean/codex/cli/appserver/transport/jsonrpc/**`

## Issue 4: Reject steering cleanly for non-steerable turn kinds

Status: `completed`

Depends on: Issues 1 through 3

Scope:

- distinguish "no active turn" from "active turn exists but cannot be steered"
- surface non-steerable cases as explicit user-visible feedback
- do not silently convert non-steerable same-turn input into a fresh turn

Acceptance:

- review and manual compaction turns reject same-turn steering explicitly
- the CLI error message is understandable and actionable
- normal regular turns remain steerable

Likely touch points:

- `codex-cli/src/main/java/org/dean/codex/cli/CodexConsoleRunner.java`
- `codex-cli/src/main/java/org/dean/codex/cli/appserver/transport/jsonrpc/**`

## Issue 5: Unify notification rendering with interactive prompt behavior

Status: `completed`

Depends on: Issues 2 through 4

Scope:

- keep streamed turn output visible while user input remains available
- prevent app-server notifications from corrupting the user’s current prompt line
- keep tool activity and approval messages readable under concurrent output

Acceptance:

- streamed notifications and typed user input can coexist without unreadable output
- the current input prompt is restored or refreshed after async output
- tool usage and assistant deltas remain understandable in a plain terminal

Likely touch points:

- `codex-cli/src/main/java/org/dean/codex/cli/CodexConsoleRunner.java`

## Issue 6: Make explicit commands coexist with active-turn input

Status: `completed`

Depends on: Issues 3 through 5

Scope:

- keep slash commands available while a turn is running
- define how `/interrupt`, `/approve`, `/reject`, `/history`, `/threads`, and `/resume` behave during active turns
- avoid ambiguous command-versus-steering behavior

Acceptance:

- slash commands still work while a turn is active
- `/interrupt` remains immediate and obvious
- state-changing commands against a different thread remain guarded or clearly scoped

Likely touch points:

- `codex-cli/src/main/java/org/dean/codex/cli/CodexConsoleRunner.java`
- `codex-cli/src/main/java/org/dean/codex/cli/interactive/**`

## Issue 7: Remove `waitForTurn(...)` as the primary interactive control path

Status: `completed`

Depends on: Issues 4 through 6

Scope:

- retire synchronous turn waiting for the main interactive mode
- keep any remaining blocking helpers only for non-interactive or one-shot flows if still needed
- align interactive CLI semantics around notification-driven turn state

Acceptance:

- interactive CLI no longer depends on `waitForTurn(...)` for ordinary prompt submission
- steering is a normal part of the interactive loop
- the CLI feels closer to Codex’s client/runtime interaction model

Likely touch points:

- `codex-cli/src/main/java/org/dean/codex/cli/CodexConsoleRunner.java`

## Notes

- Do not try to turn the CLI into the full Rust TUI; this is still a line-oriented CLI.
- Keep the app-server as the source of truth for turn state.
- Do not add ad hoc "shadow turns" in the CLI to simulate steering.
- Keep the implementation compatible with future app-server clients and multi-agent supervision flows.
