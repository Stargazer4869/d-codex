# Prompt Layering Parity Design

## Goal

Move the Java runtime from a single hardcoded planner prompt to a layered prompt architecture that is closer to upstream Codex.

Status:

- Phases 1 through 5 below are now implemented in the Java runtime.
- The next parity gap is making inherited prompt state more explicit in higher-level thread/session metadata and protocol surfaces.

The target shape is:

- reusable base instructions
- separable developer/tool instructions
- dynamic thread context as ordinary turn input
- tool visibility provided by the runtime/tool registry, not only by prompt prose

This design is intentionally runtime-first. The first objective is to make prompt construction correct and composable before adding higher-level features such as `AGENTS.md`, richer tool registries, or guardian/review roles.

## Why This Matters

Today the Java runtime works, but prompt assembly is too monolithic.

Current Java behavior:

- [SpringAiCodexAgent.java](../../codex-runtime-spring-ai/src/main/java/org/dean/codex/runtime/springai/agent/SpringAiCodexAgent.java) builds one large system prompt in `buildSystemPrompt(...)`
- that prompt mixes:
  - agent identity
  - tool descriptions
  - planner output schema
  - editing preferences
  - delegation semantics
- dynamic thread state is then packed into a second hand-built user prompt in `buildUserPrompt(...)`

That works for a planner loop, but it is not the same shape as upstream Codex.

Upstream Codex separates:

- `base_instructions` for session-level behavior
- developer instructions that supplement the session
- user/project instructions such as project docs
- tool schemas passed separately in the model request
- a runtime loop that interprets tool calls and drives the act-observe-follow-up cycle

The Java rebuild should move toward that shape so future features are easier to add without further prompt inflation.

## Current Java Shape

The important current pieces are:

- [SpringAiCodexAgent.java](../../codex-runtime-spring-ai/src/main/java/org/dean/codex/runtime/springai/agent/SpringAiCodexAgent.java)
  - `buildSystemPrompt(...)`
  - `buildUserPrompt(...)`
  - `requestDecision(...)`
- [DefaultThreadContextReconstructionService.java](../../codex-runtime-spring-ai/src/main/java/org/dean/codex/runtime/springai/context/DefaultThreadContextReconstructionService.java)
  - reconstructs recent visible history and activity summaries
- [SpringAiThreadCompactionSummarizer.java](../../codex-runtime-spring-ai/src/main/java/org/dean/codex/runtime/springai/context/SpringAiThreadCompactionSummarizer.java)
  - already uses a separate specialized prompt for compaction

### Problems in the current shape

1. The system prompt carries too many responsibilities.
2. Tool semantics exist mostly as prose inside the planner prompt.
3. There is no first-class `base instructions` concept in Java config or protocol.
4. There is no clean place to add future `AGENTS.md`, developer overrides, or role-specific instructions.
5. Skills are split awkwardly:
   - available skill catalog is appended to the system prompt
   - selected skill instructions are inserted into the user prompt
6. The planner JSON contract is welded to the agent identity/instruction layer.

## Upstream Codex Reference Model

The closest upstream reference points are:

- [default.md](../../../codex/codex-rs/protocol/src/prompts/base_instructions/default.md)
- [models.rs](../../../codex/codex-rs/protocol/src/models.rs)
- [codex.rs](../../../codex/codex-rs/core/src/codex.rs)
- [client_common.rs](../../../codex/codex-rs/core/src/client_common.rs)
- [client.rs](../../../codex/codex-rs/core/src/client.rs)
- [tool_spec.rs](../../../codex/codex-rs/tools/src/tool_spec.rs)

Important upstream properties:

- `default.md` is the base behavioral layer, not the whole prompt contract.
- base instructions are resolved per session with precedence:
  - explicit config override
  - conversation/session history
  - model default
- tools are provided separately as model-visible tool specs.
- some tools also carry tool-specific usage guidance outside the generic base instructions.
- the act-observe-follow-up loop is runtime behavior, not only prompt wording.

That means our Java equivalent should not be “make `buildSystemPrompt(...)` bigger.” It should be “introduce the same architectural boundaries.”

## Target Java Architecture

Introduce an internal prompt model that separates instruction layers from dynamic turn input.

### 1. Session base instructions

Purpose:

- define the stable identity and operating behavior of the session
- replace the current hardcoded opening of `buildSystemPrompt(...)`

Examples of content:

- “You are Codex...”
- default coding behavior
- persistence/autonomy expectations
- response style defaults

This layer should be stable across turns in the same session unless explicitly overridden.

### 2. Developer instructions

Purpose:

- attach runtime-owned operating constraints that are not plain user intent
- carry policy and mode information that supplements the base instructions

Examples of content:

- sandbox/approval policy summaries
- collaboration-mode instructions
- role-specific instructions for spawned agents
- future guardian/review instructions
- selected skill instructions
- tool-specific supplementary guidance when a schema alone is not enough

This is the right home for most of what our current planner prompt describes as rules.

### 3. User/project instructions

Purpose:

- carry project-local or turn-local instructions that reflect user or repo context rather than runtime policy

Future examples:

- `AGENTS.md`
- project-specific instruction files
- explicit turn-level user instruction overrides

This layer does not need to be fully implemented in the first migration, but the architecture must reserve a place for it.

### 4. Tool contract

Purpose:

- expose available tools as structured runtime-visible capabilities rather than only as prose

In the current Spring AI transport we may still need textual tool guidance in the rendered system prompt. That is acceptable as a transport constraint. But internally, tool visibility should come from a tool registry model rather than from hand-written prompt text inside the agent.

### 5. Dynamic turn context

Purpose:

- represent what changes every planner step

Examples:

- active thread id
- recent conversation
- recent turn events
- latest user request
- scratchpad
- steering inputs

This remains ordinary prompt input and should stay separate from session instructions.

### 6. Output contract

Purpose:

- describe the planner response format independently of agent identity and coding policy

Examples:

- planner JSON schema
- `maxActionsPerStep`
- whether final output must be JSON or free text

This is logically separate from both base instructions and tool visibility.

## Proposed Java Types

Introduce internal domain types before changing external behavior.

### Prompt domain

- `ResolvedPrompt`
- `ResolvedPromptInstructions`
- `ResolvedPromptContext`
- `ResolvedPromptOutputContract`
- `ResolvedToolContract`

Suggested shape:

```text
ResolvedPrompt
  instructions
    baseText
    developerSections[]
    userSections[]
  toolContract
    visibleTools[]
    parallelToolCalls
    supplementaryInstructions[]
  context
    threadId
    recentMessages[]
    recentActivities[]
    latestUserRequest
    scratchpad
    steeringInputs[]
  outputContract
    mode
    schemaText
    maxActionsPerStep
```

### Service seams

- `BaseInstructionsResolver`
- `DeveloperInstructionsResolver`
- `UserInstructionsResolver`
- `ToolContractResolver`
- `PromptAssemblyService`
- `PromptRenderer`

The key is that `SpringAiCodexAgent` should stop owning prompt composition directly.

## Rendering Strategy

The Java runtime does not currently have upstream Codex’s full prompt transport model. Today it sends a `.system(...)` and `.user(...)` prompt through Spring AI.

That is acceptable for now. The important refactor is internal.

### Internal model

Build a layered `ResolvedPrompt`.

### Transport rendering

Render that internal model into the current Spring AI shape:

- `system` message:
  - base instructions
  - developer instructions
  - tool supplementary instructions
  - planner output contract
- `user` message:
  - dynamic turn context
  - latest user request
  - scratchpad and steering context

This gives us a clean migration path:

- internal architecture moves toward Codex immediately
- transport can stay simple until we need richer message-role support

## What Moves Out Of `SpringAiCodexAgent`

The following logic should stop living directly in the agent class:

- the raw base “You are Codex...” text
- tool descriptions as inline string literals
- planner JSON schema string assembly
- skill-catalog rendering policy
- prompt-layer precedence

The agent should mostly do:

1. resolve selected skills and step-local inputs
2. request a `ResolvedPrompt`
3. render it for Spring AI
4. call the model
5. parse the planner response

## Configuration Model

Add a prompt section to Java config over time, for example:

- `codex.prompt.base-instructions-file`
- `codex.prompt.base-instructions-text`
- `codex.prompt.developer-instructions-file`
- `codex.prompt.user-instructions-file`

Initial cutline:

- only base instructions need a real configurable override
- developer and user instructions can still be runtime-resolved first

Longer term:

- persist effective base instructions in thread/session metadata
- support per-thread overrides for fork/spawn/review flows

## Suggested First Migration Cutline

Do not try to ship `AGENTS.md`, full prompt-role parity, and tool-registry parity in one step.

### Phase 1: Introduce the internal prompt model

Goal:

- no behavior change
- extract prompt assembly behind services and types

Implementation:

- add `ResolvedPrompt` domain types
- move current prompt building into a `PromptAssemblyService`
- render back into the same system/user strings the runtime uses today

Success criteria:

- current tests still pass
- the emitted prompt text is materially unchanged
- `SpringAiCodexAgent` no longer owns the prompt string templates directly

### Phase 2: Split base instructions from planner contract

Goal:

- separate stable session behavior from planner output/schema rules

Implementation:

- move current identity/behavior text into a base instructions template
- move planner JSON requirements into an output-contract renderer

Success criteria:

- the planner still behaves the same
- base instructions can now be overridden without rewriting the whole planner prompt

### Phase 3: Move tool semantics toward a tool contract service

Goal:

- stop treating tool availability as only prompt prose

Implementation:

- derive visible tools from a tool registry/resolver
- keep textual supplementary guidance only where needed
- move `apply_patch`-style special instructions to tool-specific guidance

Success criteria:

- tool inventory is resolved outside `SpringAiCodexAgent`
- prompt prose no longer acts as the sole source of truth for tool availability

### Phase 4: Add user/project instruction layers

Goal:

- reserve the equivalent of upstream user/project instructions

Implementation:

- start with explicit config overrides
- later add `AGENTS.md` and repo-scoped project docs

Success criteria:

- the Java runtime can append project/user instructions without editing the base prompt template

### Phase 5: Persist and propagate base instructions through thread lifecycle

Goal:

- make base instructions a first-class session/thread property

Implementation:

- store effective base instructions in thread/session metadata
- preserve them across resume
- decide fork/spawn inheritance rules

Success criteria:

- resume/fork/spawn no longer implicitly depend on current process defaults alone

## What We Should Not Do

- Do not simply replace one big string with three big strings inside `SpringAiCodexAgent`.
- Do not add `AGENTS.md` parsing before the prompt architecture exists.
- Do not encode tool availability only in prose if we already know we need a registry model.
- Do not couple compaction, review, and ordinary planner prompts into one template.

## Immediate Next Increment

The best next slice is:

1. introduce `ResolvedPrompt` and `PromptAssemblyService`
2. move the current `buildSystemPrompt(...)` and `buildUserPrompt(...)` behavior behind that service with no functional change
3. move the base “You are Codex...” text into a resource-backed template
4. leave tool semantics and planner JSON schema behavior unchanged for that first pass

That gives the Java rebuild a clean prompt architecture without forcing a risky all-at-once migration.
