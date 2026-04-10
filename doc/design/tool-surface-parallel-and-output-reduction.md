# Tool Surface, Parallel Calls, And Output Reduction

## Goal

Improve the Java Codex runtime in three related ways:

1. slightly enrich the built-in tool surface so a `tool_search` tool is not necessary
2. let the model request multiple tool calls in one planner step and execute safe subsets in parallel
3. selectively reduce or discard large tool outputs without invoking LLM compaction

This document is intentionally scoped to the Java CLI/runtime rebuild in this repo. It is informed by upstream Codex, but it does not try to replicate every upstream tool or transport detail in one step.

## Upstream Research

### 1. Upstream Codex does not rely on `tool_search` for its core built-in toolset

Upstream does have a `tool_search` tool, but it is primarily for connector/app/plugin discovery, not for the core local coding toolset:

- [`tool_registry_plan.rs`](/Users/chenzhu/Git/codex/codex-rs/tools/src/tool_registry_plan.rs)
- [`tool_discovery.rs`](/Users/chenzhu/Git/codex/codex-rs/tools/src/tool_discovery.rs)
- [`ClientRequest.ts`](/Users/chenzhu/Git/codex/codex-rs/app-server-protocol/schema/typescript/ClientRequest.ts)

The core built-in tool mix is still explicit and relatively understandable:

- shell / unified exec / write stdin
- apply patch
- list dir
- web search
- view image
- request permissions
- request user input
- plan/update_plan
- sub-agent tools
- MCP resource tools

Relevant references:

- [`tool_registry_plan.rs`](/Users/chenzhu/Git/codex/codex-rs/tools/src/tool_registry_plan.rs)
- [`tool_spec.rs`](/Users/chenzhu/Git/codex/codex-rs/tools/src/tool_spec.rs)
- [`utility_tool.rs`](/Users/chenzhu/Git/codex/codex-rs/tools/src/utility_tool.rs)

Design implication for Java:

- we do not need a `tool_search` tool for the local CLI/runtime phase
- we do need a slightly better first-class built-in set than the current one

### 2. Upstream Codex supports parallel tool calls at both model and tool levels

Upstream parallel tool calling is not a single switch. It has two levels of control:

- model capability: [`supports_parallel_tool_calls`](/Users/chenzhu/Git/codex/codex-rs/protocol/src/openai_models.rs)
- tool capability: [`ConfiguredToolSpec.supports_parallel_tool_calls`](/Users/chenzhu/Git/codex/codex-rs/core/src/tools/registry.rs)

That capability is passed through to the Responses API request body as `parallel_tool_calls`:

- [`codex-api/src/common.rs`](/Users/chenzhu/Git/codex/codex-rs/codex-api/src/common.rs)
- [`core/src/codex.rs`](/Users/chenzhu/Git/codex/codex-rs/core/src/codex.rs)
- [`models-manager/models.json`](/Users/chenzhu/Git/codex/codex-rs/models-manager/models.json)

Upstream also tracks whether an individual tool spec may safely participate in parallel execution:

- [`tools/registry.rs`](/Users/chenzhu/Git/codex/codex-rs/core/src/tools/registry.rs)
- [`tools/router.rs`](/Users/chenzhu/Git/codex/codex-rs/core/src/tools/router.rs)

Design implication for Java:

- we should not interpret "multiple actions in one planner step" as "always run all of them concurrently"
- we should add per-tool parallel-safety metadata and schedule actions in safe parallel waves

### 3. Upstream Codex already reduces large tool outputs without LLM compaction

Upstream has several non-LLM reduction layers:

- model-visible truncation policy:
  - [`openai_models.rs`](/Users/chenzhu/Git/codex/codex-rs/protocol/src/openai_models.rs)
  - [`protocol.rs`](/Users/chenzhu/Git/codex/codex-rs/protocol/src/protocol.rs)
- shell output formatting for model consumption:
  - [`tools/mod.rs`](/Users/chenzhu/Git/codex/codex-rs/core/src/tools/mod.rs)
  - [`user_shell_command.rs`](/Users/chenzhu/Git/codex/codex-rs/core/src/user_shell_command.rs)
- history-time truncation for tool outputs:
  - [`context_manager/history.rs`](/Users/chenzhu/Git/codex/codex-rs/core/src/context_manager/history.rs)
- reusable truncation helpers:
  - [`utils/output-truncation/src/lib.rs`](/Users/chenzhu/Git/codex/codex-rs/utils/output-truncation/src/lib.rs)
- exec capture caps:
  - [`core/src/exec.rs`](/Users/chenzhu/Git/codex/codex-rs/core/src/exec.rs)
  - [`CommandExecParams.ts`](/Users/chenzhu/Git/codex/codex-rs/app-server-protocol/schema/typescript/v2/CommandExecParams.ts)
  - [`app-server/README.md`](/Users/chenzhu/Git/codex/codex-rs/app-server/README.md)

This means upstream can discard or shrink large tool output before it ever needs a compaction turn.

Design implication for Java:

- output reduction should happen at the tool/result boundary and at the planner scratchpad boundary
- LLM compaction should stay a last resort for broad prompt shrinkage, not the first line of defense for noisy tool output

### 4. Upstream web search is native; our Java repo still lacks it

Upstream has a native `web_search` tool:

- [`tool_spec.rs`](/Users/chenzhu/Git/codex/codex-rs/tools/src/tool_spec.rs)
- [`tool_registry_plan.rs`](/Users/chenzhu/Git/codex/codex-rs/tools/src/tool_registry_plan.rs)
- [`tui/src/cli.rs`](/Users/chenzhu/Git/codex/codex-rs/tui/src/cli.rs)

Its local transcript representation is also lightweight. `WebSearchCall` is recorded as a compact call/action/status item rather than a giant raw result blob:

- [`models.rs`](/Users/chenzhu/Git/codex/codex-rs/protocol/src/models.rs)

Design implication for Java:

- adding web search should not mean persisting giant raw web-result payloads into replay history

## Current Java Baseline

The current Java planner-visible tool surface is still relatively small:

- `READ_FILE`
- `SEARCH_FILES`
- `APPLY_PATCH`
- `WRITE_FILE`
- `RUN_COMMAND`
- `EXEC_COMMAND`
- `WRITE_STDIN`
- sub-agent control actions

Relevant references:

- [`DefaultToolContractResolver.java`](/Users/chenzhu/Git/play-with-ai/codex-runtime-spring-ai/src/main/java/org/dean/codex/runtime/springai/prompt/DefaultToolContractResolver.java)
- [`SpringAiCodexAgent.java`](/Users/chenzhu/Git/play-with-ai/codex-runtime-spring-ai/src/main/java/org/dean/codex/runtime/springai/agent/SpringAiCodexAgent.java)

Important observations about the current implementation:

1. We already allow multiple actions in a planner step, but execute them sequentially.
   - [`SpringAiCodexAgent.java`](/Users/chenzhu/Git/play-with-ai/codex-runtime-spring-ai/src/main/java/org/dean/codex/runtime/springai/agent/SpringAiCodexAgent.java)

2. We already summarize persisted history fairly aggressively.
   - [`ThreadHistoryMapper.java`](/Users/chenzhu/Git/play-with-ai/codex-runtime-spring-ai/src/main/java/org/dean/codex/runtime/springai/history/ThreadHistoryMapper.java)

3. We already truncate `READ_FILE` results and cap `SEARCH_FILES` matches.
   - [`FileReaderToolImpl.java`](/Users/chenzhu/Git/play-with-ai/codex-tools-local/src/main/java/org/dean/codex/tools/local/FileReaderToolImpl.java)
   - [`FileSearchToolImpl.java`](/Users/chenzhu/Git/play-with-ai/codex-tools-local/src/main/java/org/dean/codex/tools/local/FileSearchToolImpl.java)

4. Our biggest output-growth gap is inside the live planner loop.
   The planner scratchpad currently appends raw JSON observations from every executed action:
   - [`SpringAiCodexAgent.java`](/Users/chenzhu/Git/play-with-ai/codex-runtime-spring-ai/src/main/java/org/dean/codex/runtime/springai/agent/SpringAiCodexAgent.java)
   - [`DefaultPromptAssemblyService.java`](/Users/chenzhu/Git/play-with-ai/codex-runtime-spring-ai/src/main/java/org/dean/codex/runtime/springai/prompt/DefaultPromptAssemblyService.java)

That means large tool payloads can still blow up same-turn context before history reconstruction ever helps.

## Design Direction

### A. Slightly enrich the built-in tool surface

Do not add `tool_search`.

Instead, keep the Java tool surface explicit and small, but add a few missing high-value built-ins:

1. `LIST_DIR`
   - upstream precedent exists
   - fills the main repo-discovery gap in our current toolset
   - should support relative path, shallow depth, and bounded entries

2. `WEB_SEARCH`
   - upstream precedent exists
   - should be a native built-in, not a disguised shell command pattern
   - should expose a compact result shape suitable for replay and planner reuse

3. Optional later: `VIEW_IMAGE`
   - upstream precedent exists
   - useful, but lower priority than `LIST_DIR` and `WEB_SEARCH` for the CLI rebuild

The purpose of this enrichment is not to create a large tool catalog. It is to make the explicit built-ins sufficient for ordinary coding and research tasks.

### B. Parallelize tool execution in safe waves

Because our planner already returns an `actions[]` array, we do not need Responses-API-native tool calling to get parallel execution. We can layer safe parallel scheduling into the existing planner batch model.

Phase-1 rule:

- only parallelize independent, read-oriented actions

Initial parallel-safe tool set:

- `READ_FILE`
- `SEARCH_FILES`
- `LIST_DIR`
- `WEB_SEARCH`

Explicitly non-parallel in phase 1:

- `APPLY_PATCH`
- `WRITE_FILE`
- `RUN_COMMAND`
- `EXEC_COMMAND`
- `WRITE_STDIN`
- all approval-sensitive shell flows
- all sub-agent control actions

Scheduler model:

- parse the planner batch in original order
- split into waves
- run each wave concurrently
- preserve original action index when rendering results back into the observation JSON

That gives us the performance benefit of parallel reads/searches without weakening determinism or safety.

### C. Add deterministic model-visible output reduction

Introduce a dedicated reduction layer between raw tool results and planner scratchpad/history input.

Important distinction:

- full result: what the CLI/app-server may expose to the user
- reduced observation: what is fed back into the planner scratchpad and replay context

The reduced observation should be tool-specific.

Examples:

- `READ_FILE`
  - keep current truncated content behavior
  - possibly reduce scratchpad copy further to excerpt + `truncated` + `totalCharacters`

- `SEARCH_FILES`
  - keep bounded matches
  - reduce to top matches + `totalMatches` + `truncated`

- `LIST_DIR`
  - keep top entries + `totalEntries` + `truncated`

- `WEB_SEARCH`
  - keep query/action metadata
  - keep a compact result summary only
  - do not persist or replay giant raw search-result bodies

- `RUN_COMMAND`, `EXEC_COMMAND`, `WRITE_STDIN`
  - keep status, exit code, session/process metadata, approval state
  - keep short stdout/stderr previews
  - drop large full outputs from planner scratchpad

This is intentionally separate from thread compaction. The purpose is to keep ordinary tool use cheap enough that compaction remains occasional rather than constant.

## Proposed Architecture Changes

### 1. Tool capability descriptor

Add a small runtime descriptor for planner-visible tools, for example:

- planner action name
- executor
- supports parallel execution
- observation reducer

This avoids hard-coding tool behavior in multiple unrelated places.

### 2. New local tool additions

Add protocol + tool implementations for:

- `LIST_DIR`
- `WEB_SEARCH`

Keep both bounded by design.

### 3. Parallel batch executor

Refactor the current sequential batch executor in [`SpringAiCodexAgent.java`](/Users/chenzhu/Git/play-with-ai/codex-runtime-spring-ai/src/main/java/org/dean/codex/runtime/springai/agent/SpringAiCodexAgent.java) into:

- action validation
- wave planning
- concurrent execution for one wave
- stable aggregation back into observation JSON

### 4. Observation reduction service

Add a service that turns raw tool result payloads into:

- client-visible result
- model-visible reduced observation

Use the reduced observation for:

- planner scratchpad
- prompt replay summaries when appropriate

Keep `ToolResultItem` and thread history summary-oriented.

## Follow-Up Checklist

### Phase 1: Tool Surface

- [x] Add a small tool capability registry for planner-visible tools
- [x] Add `LIST_DIR` protocol/result types
- [x] Implement local `LIST_DIR` tool with bounded depth and bounded entries
- [x] Add `LIST_DIR` to the planner tool contract and action enum
- [x] Add `WEB_SEARCH` protocol/result types
- [x] Implement a native `WEB_SEARCH` tool with a compact result shape
- [x] Add `WEB_SEARCH` to the planner tool contract and action enum
- [x] Keep `tool_search` out of the Java built-in tool plan

### Phase 2: Parallel Tool Calling

- [x] Add `supportsParallelExecution` metadata for planner-visible tools
- [x] Update prompt instructions so the model knows independent read-only actions may be batched together
- [x] Refactor batch execution into ordered execution waves
- [x] Execute phase-1 read-oriented waves concurrently
- [x] Preserve deterministic result ordering by original action index
- [x] Keep write/mutation/shell/sub-agent actions sequential in phase 1
- [x] Add tests for mixed sequential and parallel batches

### Phase 3: Output Reduction

- [x] Add a `ToolObservationReducer` service or equivalent runtime seam
- [x] Reduce `READ_FILE` observations for planner scratchpad use
- [x] Reduce `SEARCH_FILES` observations for planner scratchpad use
- [x] Reduce `LIST_DIR` observations for planner scratchpad use
- [x] Reduce `WEB_SEARCH` observations for planner scratchpad use
- [x] Reduce `RUN_COMMAND` / `EXEC_COMMAND` / `WRITE_STDIN` observations to compact previews and status metadata
- [x] Stop appending large raw action JSON directly into the planner scratchpad
- [x] Keep persisted thread history summary-oriented
- [x] Add tests proving large tool output is reduced without invoking thread compaction

### Phase 4: Nice-To-Have Follow-Ons

- [ ] Consider `VIEW_IMAGE` after `LIST_DIR` and `WEB_SEARCH` land
- [ ] Consider richer per-tool prompt hints based on capability metadata
- [ ] Consider exposing reduced-vs-full observation distinction in the app-server protocol later if clients need both views

## Explicit Non-Goals

- building a Java `tool_search` system for connector/app/plugin discovery
- implementing every upstream tool before improving runtime behavior
- replacing thread compaction with output reduction
- parallelizing mutation-heavy or approval-sensitive tool flows in the first pass

## Recommendation

Implement this work in the following order:

1. `LIST_DIR`
2. `WEB_SEARCH`
3. tool capability registry
4. parallel read-only execution waves
5. planner scratchpad output reduction

That sequence keeps the tool surface modest, gives immediate UX gains, and closes the biggest same-turn context-growth problem without waiting for broader compaction.
