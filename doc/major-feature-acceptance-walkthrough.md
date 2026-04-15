# Major Feature Acceptance Walkthrough

This repo now has enough runtime and CLI behavior to test the main Codex-like features end to end from the terminal. This walkthrough is intentionally practical: it focuses on what a human can manually run and observe today.

Do not use `:` commands here. The current CLI command surface is slash-only, and plain input during an active regular turn is treated as steering.

## 1. Start The CLI

Launch the CLI the same way you normally do for this repo.

What to verify:

- the app starts cleanly
- the help text shows slash commands and does not advertise `:` commands
- `/help`, `/new`, `/threads`, `/resume`, `/history`, `/compact`, `/approvals`, `/approve`, `/reject`, `/fork`, `/archive`, `/unarchive`, `/rollback`, `/subagents`, `/agent`, `/skills`, and `/interrupt` are available
- `/steer` is not part of the user-facing CLI anymore
- top-level `resume`, `fork`, and `completion` commands are available outside the interactive loop

Suggested first commands:

```text
/help
/new
```

## 2. Exercise Non-Blocking Steering

Start a normal prompt and then send a second plain prompt while the turn is still running.

Suggested first prompt:

```text
Look around this repository and summarize what this Java Codex rebuild already supports.
```

While the turn is still active, send another plain prompt:

```text
Also focus on thread management and the app-server/runtime boundary.
```

What to verify:

- the CLI stays responsive while the turn is running
- the second plain prompt is treated as steering
- the turn does not require a separate `/steer` command
- streamed tool and item output stays visible while input is still accepted

## 3. Trigger Natural-Language Sub-Agent Delegation

Ask for delegated work in plain language.

Suggested prompt:

```text
Create a sub-agent to inspect the remaining thread-management gap versus upstream Codex and report back the top missing capabilities.
```

What to verify:

- the model delegates naturally without a special user command
- collaboration activity appears in the streamed output
- the related sub-thread shows up in `/subagents`
- `/threads all --source sub-agent` can find the delegated work
- `/threads all --parent <thread-id-prefix>` can narrow children of the main thread

## 4. Exercise Approvals

Run a shell command that is likely to require approval, then inspect and resolve it.

Suggested prompt:

```text
Use a shell command to fetch only the headers from https://example.com and tell me what happened.
```

What to verify:

- a pending approval appears
- `/approvals` lists it
- `/reject <approval-id-prefix> <reason>` records a rejection
- a follow-up approval can be approved with `/approve <approval-id-prefix>`
- the turn resumes or completes as expected after the approval decision

## 5. Exercise History And Compaction

Use the current thread history and then compact it.

Suggested commands:

```text
/history
/compact
/history
```

What to verify:

- the history view reflects the current thread
- compaction emits started/completed lifecycle output
- compaction completes and produces a durable handoff summary
- the reconstructed history remains readable after compaction

## 6. Exercise Thread Lifecycle

Use a thread you have already created, then fork, archive, restore, and roll it back.

Suggested commands:

```text
/fork feature-check
/threads all --search feature-check
/archive <fork-thread-id-prefix>
/threads archived --search feature-check
/unarchive <fork-thread-id-prefix>
/rollback <fork-thread-id-prefix> 1
```

What to verify:

- fork creates a separate child thread
- archive removes the thread from the active list
- unarchive restores it
- rollback trims visible turns without breaking the thread record

## 7. Exercise Thread Listing And Filtering

Try the current `/threads` filters.

Suggested commands:

```text
/threads all --search codex
/threads all --cwd /Users/chenzhu/Git/play-with-ai
/threads all --status idle
/threads all --source cli
/threads all --parent <thread-id-prefix>
```

What to verify:

- search narrows by text
- cwd narrows by workspace path
- status filters by thread state
- source filters by thread origin
- parent filters direct child threads

## 8. App-Server-Only Spot Checks

Some features are implemented at the app-server/protocol layer rather than as CLI commands. Use the stdio app-server directly to verify them.

Spot check unified exec RPCs:

- start the stdio app-server transport
- initialize a thread
- send `command/exec` with a short command such as `printf 'one\n'; sleep 1; printf 'two\n'`
- follow with `command/exec/write` using empty input to poll for more output
- if the session is PTY-backed, send `command/exec/resize`
- finish with `command/exec/terminate` on a long-running session

What to verify:

- `command/exec` returns a session id and an initial bounded output snapshot
- `command/exec/write` can poll incremental output without sending stdin
- output-delta and completion notifications stream back through the app-server
- `resize` reports whether it was applied
- `terminate` ends the session cleanly

Spot check git metadata:

- start the stdio app-server transport
- initialize a thread
- send `thread/metadata/update` with `gitSha`, `gitBranch`, and `gitOriginUrl`
- call `thread/read`

What to verify:

- the returned thread summary includes the git metadata
- `/threads` shows the compact git summary when present

Spot check background terminals:

- run a background shell command through `thread/shellCommand`
- read the thread back
- run `thread/backgroundTerminals/clean`

What to verify:

- background terminal summaries include stable ids and live metadata
- the background terminal maps onto the shared unified-exec session model
- the cleanup call removes them from the thread view

## What This Walkthrough Does Not Cover

This walkthrough intentionally does not claim support for:

- realtime thread sessions
- audio/WebRTC realtime
- a native Responses HTTP/WebSocket backend instead of the current chat-backed fallback transport

Those are still separate roadmap items.
