# Native Responses Backend Migration

## Goal

Move the Java runtime from the current chat-backed Spring AI transport to a native OpenAI Responses transport, while keeping the existing internal `ResponsesModelClient` / `ResponsesCompactClient` seam and typed runtime model intact.

This is the concrete transport migration that follows the broader internal parity work in [`responses-api-parity.md`](responses-api-parity.md).

## Why This Needs Its Own Design

The existing Responses parity design mostly covered the runtime shape:

- typed request and response items
- model-facing tool specs
- streamed runtime items
- reasoning and multimodal request support
- session snapshot plumbing
- compact-client seams

That work gave the Java runtime a Responses-shaped internal contract, but the concrete backend still uses chat-oriented Spring AI adapters:

- [`ChatClientResponsesModelClient.java`](../../codex-runtime-spring-ai/src/main/java/org/dean/codex/runtime/springai/model/ChatClientResponsesModelClient.java)
- [`ChatClientResponsesCompactClient.java`](../../codex-runtime-spring-ai/src/main/java/org/dean/codex/runtime/springai/model/ChatClientResponsesCompactClient.java)
- [`CodexRuntimeSpringAiConfig.java`](../../codex-runtime-spring-ai/src/main/java/org/dean/codex/runtime/springai/config/CodexRuntimeSpringAiConfig.java)

The acceptance walkthrough also still treats a native Responses backend as unfinished work in [`major-feature-acceptance-walkthrough.md`](../major-feature-acceptance-walkthrough.md).

## Current State

### What is already in place

- `SpringAiCodexAgent` already depends on `ResponsesModelClient`, not directly on Spring chat calls.
- The runtime already has typed `ModelInputItem` and `ModelOutputItem` abstractions.
- Tool contracts already produce model-visible tool specs and output schemas.
- The planner/runtime loop already understands streamed assistant, reasoning, tool-call, and raw-output items.
- Thread session state already has a persistence seam through `ThreadModelSessionSnapshot`.
- Compaction already goes through `ResponsesCompactClient`.

### What is still missing

- normal generation still goes through a chat-backed `ChatClient` transport rather than `/v1/responses`
- compact generation is still chat-backed too
- response and session metadata are still largely synthetic or empty rather than native Responses metadata
- there is no native HTTP streaming or websocket/session transport
- the Java runtime does not yet exercise the actual upstream Responses transport behavior that Codex relies on

## Target Outcome

After this migration:

- ordinary model turns use a native Responses backend by default
- compaction uses a native Responses-style compact path
- streamed runtime items come from native Responses events, not from a chat fallback wrapper
- thread and sub-agent follow-up state maps onto real Responses response/session continuity
- websocket/session transport is supported where the provider supports it
- the old chat-backed transport remains available only as an explicit fallback during rollout

## Non-Goals

- rewriting prompt assembly or planner logic from scratch
- changing CLI or app-server protocol shapes unless transport metadata requires additive fields
- introducing realtime audio or WebRTC support in this increment
- removing the current fallback transport on day one

## Upstream Reference Model

The main upstream reference remains the Rust Codex client stack in `../../../codex`, especially:

- `codex-rs/core/src/client.rs`
- `codex-rs/core/src/stream_events_utils.rs`
- `codex-rs/tools/src/responses_api.rs`
- `codex-rs/tools/src/tool_spec.rs`

The important part is not only the endpoint name. It is the runtime behavior:

- native typed response items
- streamed event handling
- sticky response/session continuity
- real metadata propagation for sub-agents and follow-up turns

## Design Principles

1. Keep the existing internal `ResponsesModelClient` seam stable.
2. Treat the transport swap as a provider/backend migration, not as a planner rewrite.
3. Prefer additive rollout controls over a flag day cutover.
4. Keep raw transport events observable in diagnostics so parity bugs are debuggable.
5. Match upstream session continuity semantics closely enough that future parity work stops depending on chat-era adapters.

## Proposed Architecture

### 1. Add a native Responses transport implementation

Introduce concrete runtime clients alongside the current chat-backed adapters:

- `OpenAiResponsesModelClient`
- `OpenAiResponsesCompactClient`
- optional `OpenAiResponsesSessionClient` or equivalent websocket/session helper

These implementations should satisfy the existing interfaces:

- `ResponsesModelClient`
- `ResponsesCompactClient`

The current chat-backed clients remain available during rollout:

- `ChatClientResponsesModelClient`
- `ChatClientResponsesCompactClient`

### 2. Add explicit transport mode configuration

Add runtime configuration for model transport selection, for example:

- `chat-fallback`
- `responses-http`
- `responses-ws`

The important behavior:

- initial rollout can keep the current backend as default
- test and acceptance environments can opt into native Responses explicitly
- final cutover can switch the default once parity is proven

This configuration should stay outside committed secrets and continue to rely on runtime environment variables for credentials and base URLs.

### 3. Map internal requests onto native Responses requests

The native client should translate `ModelRequest` into real Responses request bodies, including:

- system instructions
- typed input items
- tool definitions
- structured output schema
- reasoning controls
- parallel-tool-call flags
- model selection
- metadata for thread id, turn id, parent thread, and sub-agent lineage where appropriate

The migration should stop assuming that the transport is only:

- one system prompt
- one user prompt
- one final assistant text blob

### 4. Map native Responses events back into runtime items

The native client should convert streamed Responses output into the existing Java runtime item model:

- assistant message items
- reasoning items
- tool call items
- tool result items
- raw transport items for diagnostics

This mapping should preserve enough transport detail that CLI and app-server streaming behavior stays at least as rich as it is now.

### 5. Use real response and session continuity

The current `ThreadModelSessionSnapshot` seam should start carrying actual native transport continuity values, such as:

- response id
- session id
- follow-up linkage such as previous-response semantics
- inherited session state rules for forked threads and spawned agents

Design rules:

- normal follow-up turns on the same thread should reuse native continuity where safe
- forked threads should inherit durable model settings but start a fresh response chain unless we explicitly choose otherwise
- sub-agents should inherit parent configuration but maintain their own response/session continuity
- rollback should invalidate stale continuity that no longer matches visible thread history

### 6. Put compaction on the native backend too

`ResponsesCompactClient` should stop being only a naming seam and should become a real native transport path.

That means:

- compact requests should be mapped through the native Responses transport
- compact response metadata should be preserved like normal generation metadata where useful
- native transport selection should apply to compaction as well as ordinary turns

### 7. Keep diagnostics strong during rollout

The native backend will be harder to debug than the current chat wrapper unless we preserve observability.

Keep or extend:

- raw transport item passthrough
- per-session CLI diagnostics logs
- request/response metadata capture
- transport error summaries that are visible without dumping credentials

## Delivery Plan

### Phase 1: native HTTP request/response path

Tracker:

- [x] add a native HTTP `ResponsesModelClient`
- [x] add request mapping from `ModelRequest` to Responses request bodies
- [x] add response mapping back into `ModelResponse` and streamed `ModelOutputItem`s
- [x] add transport-mode configuration

Cutline:

- Java can run ordinary turns against `/v1/responses` without using the chat-backed adapter
- The current implementation keeps request flow stateless and preserves the existing prompt-driven planner contract by requesting `text.format = json_object`; native response-chain reuse and default native tool-calling stay deferred until the planner stops reconstructing full prompt context every step.

### Phase 2: compact path and metadata continuity

Tracker:

- [ ] add a native `ResponsesCompactClient`
- [ ] persist real response/session continuity metadata
- [ ] define rollback/fork/sub-agent invalidation and inheritance rules

Cutline:

- ordinary turns and compaction both run through native Responses transport with real continuity metadata
- Interim note: compaction summary generation now has a native `/responses` transport option, but the runtime still stores plain-text thread memory, so the opaque `/responses/compact` API remains a follow-up.

### Phase 3: websocket/session transport

Tracker:

- [ ] add websocket or session-based transport where provider support is available
- [ ] map streamed events onto the existing runtime item model
- [ ] preserve graceful HTTP fallback when websocket/session mode is unavailable

Cutline:

- Java supports a native session-oriented Responses transport rather than only stateless HTTP calls

### Phase 4: rollout and default cutover

Tracker:

- [ ] add acceptance coverage for native Responses mode
- [ ] make native Responses the default transport once parity is good enough
- [ ] keep chat fallback as opt-in compatibility only

Cutline:

- the repo’s default runtime path is native Responses transport, not chat-backed fallback

## Recommended Issue Breakdown

- [x] Issue 1: add transport-mode configuration and wiring
- [x] Issue 2: implement native HTTP `ResponsesModelClient`
- [ ] Issue 3: map typed tool specs and structured output onto native request bodies
- [x] Issue 4: map native streamed events back into `ModelOutputItem`s
- [ ] Issue 5: implement native compact transport
- [ ] Issue 6: persist and reuse real response/session continuity metadata
- [ ] Issue 7: define fork/sub-agent/rollback continuity rules
- [ ] Issue 8: add websocket/session transport support
- [ ] Issue 9: expand acceptance coverage and cut over the default backend

## Test Plan

- unit-test request mapping from `ModelRequest` to native Responses payloads
- unit-test streamed native event mapping into `ModelOutputItem`s
- unit-test continuity metadata persistence and reuse
- integration-test native HTTP transport against a mock Responses server
- integration-test fallback behavior when websocket/session transport is unavailable
- rerun acceptance for:
  - ordinary streamed turns
  - approvals
  - sub-agent delegation and parent resume
  - compaction
  - app-server raw-item diagnostics

## Success Criteria

We can consider this migration complete when:

- the default Java runtime path no longer depends on chat completions for ordinary turns
- the compact path is native too
- parent/sub-agent flows work with real native continuity metadata
- streamed CLI/app-server behavior remains readable and debuggable
- the walkthrough no longer has to disclaim the native Responses backend as missing
