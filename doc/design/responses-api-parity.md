# Responses API Parity Design

## Goal

Move the Java Codex rebuild from a chat-style model integration toward the actual feature set that upstream Codex uses from OpenAI's Responses API.

This is not only about switching endpoints. It is about matching the runtime shape that upstream Codex depends on:

- typed input and output items
- tool calling as structured response items
- streaming multi-item output
- streaming interactive turn updates to the CLI and app-server
- reasoning controls and reasoning summaries
- structured output schemas
- multimodal input items
- session and turn state for follow-up requests
- compact endpoint usage

The reference point is the current upstream Codex implementation in `/Users/chenzhu/Git/codex`.

## Progress Tracker

Overall status: `complete`

### Delivery phases

- [x] Phase A: Responses request model and transport seam
- [x] Phase B: tool spec and structured output parity
- [x] Phase C: streamed response-item runtime
- [x] Phase D: reasoning, multimodal, and session-state parity
- [x] Phase E: compact endpoint and raw-item app-server parity

### Implementation tasks

- [x] Add a Java-side `ResponseItem`-style internal model for model-visible input and output
- [x] Add a dedicated `ResponsesModelClient` abstraction instead of relying on chat-only transport assumptions
- [x] Represent visible tools as Responses-compatible tool specs, not only prompt prose
- [x] Carry structured output schemas through the tool registry and planner/runtime pipeline
- [x] Add streamed multi-item response handling instead of assuming one assistant text blob
- [x] Add streaming interaction surfaces for assistant text, reasoning summaries, and tool activity
- [x] Preserve reasoning items and reasoning summaries as first-class runtime data
- [x] Add typed multimodal input items for text and images
- [x] Add session and turn metadata handling for follow-up requests
- [x] Add raw Responses item passthrough for debugging and app-server parity
- [x] Add explicit `/responses/compact` parity on top of the current Java compaction model

## Why This Matters

Upstream Codex is not built like a traditional chat wrapper.

Important upstream behavior depends on Responses-era capabilities:

1. the model emits tool calls as typed output items
2. the runtime streams and records multiple item kinds, not only assistant text
3. the user can see the turn evolve as streamed output arrives
4. reasoning controls are part of request construction
5. tool outputs can be structured with explicit schemas
6. multimodal inputs flow through the same request model
7. compaction uses a Responses-specific compact path

If the Java rebuild keeps treating the model as "system prompt + user prompt -> assistant text", parity work will get harder over time. We would keep re-encoding Responses behavior into prompt wording and Spring-side heuristics instead of adopting the same runtime contract.

## Upstream Codex Features Actually Using Responses API

These are the important upstream capabilities this design is targeting.

### 1. Structured tool specs

Upstream Codex builds model-visible tools as Responses-compatible JSON in:

- [`tool_spec.rs`](/Users/chenzhu/Git/codex/codex-rs/tools/src/tool_spec.rs)
- [`responses_api.rs`](/Users/chenzhu/Git/codex/codex-rs/tools/src/responses_api.rs)

This includes:

- function tools
- freeform tools
- web search
- image generation
- MCP-backed tools with Responses-compatible names

### 2. Typed response items

Upstream Codex consumes output as `ResponseItem`s and processes each completed item in:

- [`stream_events_utils.rs`](/Users/chenzhu/Git/codex/codex-rs/core/src/stream_events_utils.rs)

Important item kinds include:

- assistant messages
- reasoning items
- tool calls
- tool outputs
- web search calls

### 3. Streaming transport

Upstream Codex has a session-scoped Responses client with websocket support and HTTP fallback in:

- [`client.rs`](/Users/chenzhu/Git/codex/codex-rs/core/src/client.rs)

That transport carries:

- streamed output items
- sticky turn state
- session identity headers
- subagent and parent-thread metadata

### 4. Reasoning controls

Upstream builds explicit reasoning config in:

- [`client.rs`](/Users/chenzhu/Git/codex/codex-rs/core/src/client.rs)

This includes:

- reasoning effort
- reasoning summary configuration

### 5. Structured output schemas

Tool definitions in upstream frequently carry `output_schema`, for example in:

- [`local_tool.rs`](/Users/chenzhu/Git/codex/codex-rs/tools/src/local_tool.rs)
- [`agent_tool.rs`](/Users/chenzhu/Git/codex/codex-rs/tools/src/agent_tool.rs)
- [`mcp_tool.rs`](/Users/chenzhu/Git/codex/codex-rs/tools/src/mcp_tool.rs)

So Codex is not relying only on "return JSON" prompt instructions.

### 6. Parallel tool call capability

Upstream prompt construction explicitly carries `parallel_tool_calls` in:

- [`codex.rs`](/Users/chenzhu/Git/codex/codex-rs/core/src/codex.rs)

### 7. Multimodal typed input

Upstream prompt input is composed from typed items rather than only plain chat messages. The same request model can carry text, images, and tool-related items.

### 8. Compact endpoint support

Upstream uses a dedicated compact call in:

- [`client.rs`](/Users/chenzhu/Git/codex/codex-rs/core/src/client.rs)

That is distinct from ordinary text generation and is part of the real runtime behavior.

## Current Java State

The Java rebuild has made good progress in adjacent areas, but it is still not Responses-native.

Current Java strengths:

- layered prompt assembly via [`DefaultPromptAssemblyService.java`](/Users/chenzhu/Git/play-with-ai/codex-runtime-spring-ai/src/main/java/org/dean/codex/runtime/springai/prompt/DefaultPromptAssemblyService.java)
- a growing tool contract layer via [`DefaultToolContractResolver.java`](/Users/chenzhu/Git/play-with-ai/codex-runtime-spring-ai/src/main/java/org/dean/codex/runtime/springai/prompt/DefaultToolContractResolver.java)
- strong thread history and compaction work
- unified exec sessions and streamed command notifications

Main remaining gaps:

1. The model transport is still effectively chat-oriented.
2. We do not have a first-class Java `ResponseItem` model for the planner/runtime path.
3. Tool visibility is still partly rendered as prompt prose instead of fully transport-driven tool specs.
4. We do not have a real streaming response-item interaction model for assistant/reasoning/tool updates.
5. We do not preserve model reasoning items as first-class runtime records.
6. Multimodal request items are not part of the core Java planner path.
7. Session/turn routing metadata is not modeled like upstream Responses transport.
8. Compaction parity exists behaviorally, but not through a Responses compact client surface.

## Design Principles

1. Do not couple Responses parity to Spring AI prompt rendering details.
2. Keep the internal request and response model provider-neutral where practical, but preserve the shape that Responses actually uses.
3. Preserve the layered prompt work already completed; do not throw it away.
4. Treat response items, tool calls, reasoning, and multimodal input as runtime data, not just prompt text.
5. Prefer one coherent transport seam over scattered ad hoc endpoint calls.

## Target Java Architecture

### 1. Add an internal Responses-style request model

Introduce internal model types such as:

- `ModelInputItem`
- `ModelOutputItem`
- `AssistantMessageItem`
- `ReasoningItem`
- `ToolCallItem`
- `ToolResultItem`
- `InputTextItem`
- `InputImageItem`

This should become the canonical model-facing shape between prompt assembly, model transport, and runtime item processing.

### 2. Add a dedicated model transport seam

Introduce a transport abstraction such as:

- `ResponsesModelClient`
- `ResponsesModelSession`

Responsibilities:

- create model requests from layered prompt + tool contract + typed input items
- stream output items
- expose response/session metadata
- support future websocket or incremental transport behavior

This should sit beside, and eventually replace, the current chat-only request path in [`SpringAiCodexAgent.java`](/Users/chenzhu/Git/play-with-ai/codex-runtime-spring-ai/src/main/java/org/dean/codex/runtime/springai/agent/SpringAiCodexAgent.java).

### 3. Make tool specs first-class

The existing Java `ToolContractResolver` work should evolve into true transport-visible tool specs:

- function-like tool spec
- freeform/custom tool spec
- output schema
- parallel-call support flag

The prompt should no longer be the only place where the model learns about tools.

### 4. Separate assistant text from reasoning and tool items

The runtime should process streamed model output item-by-item:

- assistant message text
- reasoning summary/content
- tool call
- tool call completion/output

This is the main behavioral seam we need to match upstream follow-up loops.

### 4a. Add a streaming interaction surface

The Java runtime should expose streamed turn progress as first-class runtime behavior, not only as an implementation detail inside the model client.

That should include:

- assistant text deltas or completed assistant-message items
- reasoning summary deltas or completed reasoning items
- tool call started/completed events
- explicit end-of-turn completion

Target surfaces:

- internal runtime event stream
- app-server notifications
- interactive CLI rendering

The key point is that users should be able to watch a turn unfold instead of waiting only for a final answer blob.

### 5. Make reasoning first-class

Add runtime-visible request and response support for:

- reasoning effort
- reasoning summary mode
- reasoning summary content in streamed output

That state should be configurable per turn and visible in thread/turn protocol surfaces where appropriate.

### 6. Add multimodal request items

The request model should be able to carry:

- input text
- image references
- future richer content items

This should reuse the same model transport seam instead of creating special-case image prompt code.

### 7. Add session and turn state transport

Introduce explicit handling for:

- thread/session identity
- parent thread / subagent lineage
- sticky turn state
- per-turn metadata
- optional raw item passthrough for debugging

The main point is not to copy every header literally on day one. The point is to reserve a real session-state layer instead of treating every request as unrelated chat.

### 8. Add compact endpoint support

The Java runtime already has compaction semantics, but the model-client layer should also reserve a dedicated compact path so future parity work is not forced through ordinary generation calls.

## Delivery Plan

### Phase A: Responses request model and transport seam

Tracker:

- [x] Add internal `ModelInputItem` / `ModelOutputItem` domain types
- [x] Add a `ResponsesModelClient` interface
- [x] Add a first concrete runtime implementation, even if initially backed by existing Spring transport
- [x] Make `SpringAiCodexAgent` depend on the new transport seam rather than assembling the whole request inline

Cutline:

- the Java runtime has a dedicated model transport abstraction that no longer assumes "chat completion text in, text out"

### Phase B: tool spec and structured output parity

Tracker:

- [x] Extend the tool contract layer to produce transport-visible tool specs
- [x] Carry output schemas into tool definitions
- [x] Mark per-tool parallel-call support in the contract
- [x] Stop maintaining a second hardcoded tool list inside prompt prose

Cutline:

- tools are represented once as structured model-visible capabilities

### Phase C: streamed response-item runtime

Tracker:

- [x] Introduce runtime handling for streamed `ModelOutputItem`s
- [x] Separate assistant text, reasoning, and tool-call items in the runtime loop
- [x] Add streaming interaction events for assistant text, reasoning, tool calls, and turn completion
- [x] Render streamed interaction cleanly through the app-server and CLI
- [x] Record those items into turn/thread state
- [x] Add optional raw item exposure for debugging and app-server parity

Cutline:

- the planner/runtime loop works from typed output items rather than only from a single assistant text blob
- users can observe streamed turn progress instead of waiting only for final completion

### Phase D: reasoning, multimodal, and session-state parity

Tracker:

- [x] Add request-side reasoning controls
- [x] Add response-side reasoning summary/item handling
- [x] Add text + image typed input items
- [x] Add session and turn state transport metadata
- [x] Define inheritance rules for forked threads and spawned agents

Cutline:

- reasoning, multimodal input, and session continuity are first-class parts of the Java model client

### Phase E: compact endpoint and raw-item app-server parity

Tracker:

- [x] Add a dedicated compact-client seam to the model transport
- [x] Connect compaction runtime behavior to that seam where appropriate
- [x] Add app-server support for optional raw Responses item passthrough
- [x] Expose enough metadata for debugging streamed model output

Cutline:

- Java has a Responses-shaped transport story for ordinary generation and compact-style generation

## Recommended Issue Breakdown

- [x] Issue 1: add internal `ModelInputItem` and `ModelOutputItem` types
- [x] Issue 2: add a `ResponsesModelClient` seam and migrate `SpringAiCodexAgent` onto it
- [x] Issue 3: upgrade tool contract resolution into transport-visible tool specs
- [x] Issue 4: carry `output_schema` through tool definitions and planner/runtime handling
- [x] Issue 5: add streamed item processing for assistant, reasoning, and tool-call items
- [x] Issue 6: add streamed interaction events and CLI/app-server rendering for turn progress
- [x] Issue 7: persist reasoning items and summaries into turn/thread state
- [x] Issue 8: add typed multimodal input items
- [x] Issue 9: add turn/session metadata and inheritance rules for subagents
- [x] Issue 10: add raw item passthrough on the app-server event path
- [x] Issue 11: add dedicated compact-client parity

## What We Should Not Do

- Do not try to get Responses parity only by copying more text into prompts.
- Do not weld provider transport details directly into `SpringAiCodexAgent` again.
- Do not represent reasoning as invisible text if upstream treats it as a real item type.
- Do not add multimodal support as a separate side channel disconnected from the main model request path.
- Do not make tool schemas exist in both runtime code and prompt prose forever.

## Suggested First Slice

The best first bounded slice is:

1. add internal `ModelInputItem` and `ModelOutputItem` types
2. add `ResponsesModelClient`
3. migrate `SpringAiCodexAgent` to build a typed request through that seam
4. keep the concrete backend behavior-preserving at first

That gives us the architectural pivot before we start moving tool specs, reasoning items, and multimodal inputs onto it.
