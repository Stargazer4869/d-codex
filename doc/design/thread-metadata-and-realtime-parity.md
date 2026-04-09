# Thread Metadata And Realtime Parity

## Goal

Make the next thread-management phase more concrete by splitting the remaining Codex gap into two tracks:

1. richer thread metadata and listing/index parity
2. stronger realtime and thread-runtime behavior

This document is not a full roadmap replacement. It is the design note for what "thread management in depth" means after the recent listing/filtering work landed.

## Why This Matters

The Java rebuild now has a solid thread foundation:

- thread lifecycle operations
- thread discovery with filtering and stable cursors
- first-pass background terminal cleanup
- collaboration-aware replay summaries

What is still missing is the deeper Codex shape:

- threads are still thinner as persisted records
- thread-scoped runtime behavior is still smaller than upstream Codex

Those are different problems and should not be solved in one refactor.

## Upstream Codex Reference

The two main upstream references are:

- [`thread_metadata.rs`](/Users/chenzhu/Git/codex/codex-rs/state/src/model/thread_metadata.rs)
- [`app-server/README.md`](/Users/chenzhu/Git/codex/codex-rs/app-server/README.md)

Important upstream thread metadata fields:

- `reasoning_effort`
- `cli_version`
- `tokens_used`
- `git_sha`
- `git_branch`
- `git_origin_url`
- `rollout_path`
- `sandbox_policy`
- `approval_mode`
- `first_user_message`

Important upstream thread-runtime operations:

- `thread/shellCommand`
- `thread/backgroundTerminals/clean`
- `thread/realtime/start`
- `thread/realtime/appendAudio`
- `thread/realtime/appendText`
- `thread/realtime/stop`
- `thread/realtime/*` notifications

## Current Java State

The Java thread summary in [`ThreadSummary.java`](/Users/chenzhu/Git/play-with-ai/codex-protocol/src/main/java/org/dean/codex/protocol/conversation/ThreadSummary.java) already includes:

- `firstUserInput`
- `sandboxMode`
- `approvalMode`
- lineage fields like `parentThreadId`, `agentNickname`, `agentRole`, and `agentPath`
- current runtime `status`

The Java app-server surface in [`CodexAppServerSession.java`](/Users/chenzhu/Git/play-with-ai/codex-core/src/main/java/org/dean/codex/core/appserver/CodexAppServerSession.java) already includes:

- `thread/name/set`
- `thread/metadata/update`
- `thread/shellCommand`
- `thread/backgroundTerminals/clean`

But it still does not include realtime thread APIs, and the persisted thread metadata is still thinner than upstream Codex.

## Track 1: Richer Metadata And Index Parity

### Problem

Our threads are now easier to list and filter, but the underlying metadata is still too thin compared with Codex.

That limits:

- better resume/navigation UX
- richer filters later
- stronger parity for `thread/metadata/update`
- future thread catalogs and history UIs

### Most Important Missing Fields

These are the highest-value missing fields to add first:

1. `gitSha`
2. `gitBranch`
3. `gitOriginUrl`
4. `cliVersion`
5. `reasoningEffort`

`tokensUsed` should stay deferred until the runtime can persist it honestly. We should not add fake or proxy token metrics.

### Why Git Metadata First

Upstream Codex already uses `thread/metadata/update` to patch persisted git info. That makes git metadata the cleanest next parity step.

It is also directly useful in the CLI:

- users can recognize the right thread by repo state
- future review flows can show better thread context
- thread listing becomes closer to Codex without a broader runtime change

### Recommended Metadata Sequence

#### Phase A: Git metadata parity

- extend persisted thread metadata with:
  - `gitSha`
  - `gitBranch`
  - `gitOriginUrl`
- update `thread/metadata/update` so it can patch those fields cleanly
- expose those fields in `thread/read`, `thread/resume`, and `thread/list`
- show a compact git summary in `/threads` output only when present

#### Phase B: Session/runtime metadata parity

- add `cliVersion`
- add `reasoningEffort`
- persist them when known at thread creation or resume time
- surface them through thread reads and listings

#### Phase C: Future metadata parity

- add `tokensUsed` only after durable runtime support exists
- consider `rolloutPath` only if it fits the Java storage model cleanly

### Cutline For Track 1

Track 1 is in a good place when:

- git metadata is persisted and patchable
- thread listing can surface meaningful git/session context
- `thread/metadata/update` is closer to upstream Codex semantics
- later filter/index work has stronger metadata to build on

## Track 2: Stronger Realtime And Thread-Runtime Behavior

### Problem

Our Java runtime has only the first thread-scoped runtime slice:

- `thread/shellCommand`
- `thread/backgroundTerminals/clean`

That is useful, but it is still much smaller than Codex's model of a thread as a live runtime container.

### Background Terminals Gap

Current Java behavior is closer to:

- thread-owned background work can exist
- cleanup is explicit

Upstream Codex is closer to:

- thread-owned terminal sessions with stronger lifecycle
- clearer separation between turns and background runtime activity

### Realtime Gap

Java currently has no thread realtime API. Upstream Codex already supports:

- start/stop of thread-scoped realtime sessions
- append text/audio to those sessions
- thread-scoped realtime notifications

That means our thread runtime is still missing an entire behavior family.

### Recommended Runtime Sequence

#### Phase A: Background terminal lifecycle

- assign stable ids to background terminal sessions
- track them as thread-owned runtime objects
- distinguish terminal lifecycle from turn lifecycle more explicitly
- allow future selective cleanup instead of only "clean all"

#### Phase B: Text-only realtime

- add:
  - `thread/realtime/start`
  - `thread/realtime/appendText`
  - `thread/realtime/stop`
- emit `thread/realtime/*` notifications for lifecycle and text updates
- keep this out of `ThreadItem` history at first, matching upstream's ephemeral transport behavior

#### Phase C: Audio/WebRTC later

- defer `appendAudio` and browser/WebRTC transport support
- only take this on after text realtime works and the thread-runtime lifecycle is solid

### Cutline For Track 2

Track 2 is in a good place when:

- background terminals behave like real thread-owned runtime resources
- realtime has a small but real text session API
- thread runtime behavior is visibly closer to upstream Codex

## Recommended Order

If we want the next highest-value Codex-aligned step, the order should be:

1. Track 1, Phase A: git metadata parity
2. Track 1, Phase B: session/runtime metadata parity
3. Track 2, Phase A: background terminal lifecycle
4. Track 2, Phase B: text-only realtime

Why this order:

- metadata parity builds directly on the thread discovery work we just completed
- it improves CLI thread UX immediately
- it is lower risk than realtime
- it gives the Java runtime a stronger thread model before adding more live runtime complexity

## What We Should Not Do Yet

- do not add fake `tokensUsed`
- do not jump straight to audio/WebRTC realtime
- do not introduce a new persisted thread database or catalog just for this phase
- do not try to solve metadata parity and realtime parity in one large refactor

## Suggested Next Implementation Slice

The best next bounded increment is:

1. extend persisted thread metadata with `gitSha`, `gitBranch`, and `gitOriginUrl`
2. align `thread/metadata/update` to patch those fields
3. expose them in `thread/read`, `thread/resume`, and `/threads`
4. update thread listing output to show a compact git summary when present

That is the cleanest next step toward Codex thread parity.
