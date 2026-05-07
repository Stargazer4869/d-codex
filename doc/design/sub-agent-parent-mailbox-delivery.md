# Parent Inbox Delivery For Sub-Agent Completion

## Summary

The Java runtime already supports spawned sub-agents, mailbox-style waiting, and collaboration tool items, but it still diverged from upstream Codex in one important way: child completion was visible inside the child thread and through `wait_agent(...)`, yet it was not delivered into the parent thread as first-class mailbox mail.

This design closes that gap for the Java rebuild by adding a parent-facing mailbox delivery path for child completion, while keeping the existing `agent/*` app-server surface stable for now.

## Upstream Reference Model

Upstream Codex uses a real mailbox abstraction and forwards child completion into the parent session mailbox instead of treating `wait_agent` as the primary content channel.

Relevant reference points:

- mailbox abstraction: [`codex-rs/core/src/agent/mailbox.rs`](../../../codex/codex-rs/core/src/agent/mailbox.rs)
- child completion forwarding inside the session runtime: [`codex-rs/core/src/session/mod.rs`](../../../codex/codex-rs/core/src/session/mod.rs)
- `wait_agent` as a wake primitive in the public tool surface: [`codex-rs/tools/src/agent_tool.rs`](../../../codex/codex-rs/tools/src/agent_tool.rs)

The important upstream behavior is:

1. a child completes a turn
2. the runtime turns that completion into inter-agent mailbox communication
3. the parent session consumes that communication in the active turn or the next turn, depending on timing
4. `wait_agent` wakes the parent so the model can look at mailbox mail, rather than acting as the only place where the child result exists

## Current Java Gap

Before this change, the Java runtime had:

- queued agent input for spawned child threads
- mailbox sequence tracking for `wait_agent(...)`
- collaboration activity items such as `collabToolCall`
- no parent inbox delivery for child completion

That meant a child could finish, the parent could observe status changes, and `wait_agent(...)` could wake, but the parent planner still had no first-class mailbox item to consume. In practice that encouraged repeated `wait_agent` loops because the result had not actually entered the parent thread history or prompt context.

## Design Cutline

This increment adds the Java analogue of upstream parent-inbox delivery, but not the full upstream answer-boundary phase model.

The safe Java rule for now is:

- if the parent turn is actively running, child completion mail is available to that turn on the next planner step
- if the parent turn is idle, the mail is deferred to the next turn or resume
- if the parent turn ends before draining the mail, the undrained message is re-queued for the next turn

## Implementation

### Protocol and persistence

Add a first-class mailbox turn item and matching persisted history item:

- `MailboxMessageItem`
- `HistoryMailboxMessageItem`
- `MailboxDeliveryKind`

For this increment the main delivery kind is `CHILD_COMPLETION`, with room for future `QUEUE_ONLY` and `TRIGGER_TURN` flows.

These items are:

- persisted through `ThreadHistoryMapper`
- replayed by `DefaultThreadContextReconstructionService`
- visible in `/history` and reconstructed replay summaries

### Runtime gateway

`DefaultCodexRuntimeGateway` now owns a parent-facing mailbox queue in addition to the existing queued child-input mailbox.

When a child thread completes:

- the runtime builds one mailbox message for the direct parent
- the message includes child identity plus the child final answer
- if the parent thread has an active running turn, the message is appended to that running turn's mailbox buffer
- otherwise the message is queued for the next parent turn or resume

To avoid dropping late-arriving child results, the running turn re-queues any undrained mailbox messages when the turn exits.

`waitAgent(...)` remains the same public method, but its semantics are stronger now:

- a newly completed child turn wakes the wait
- `timedOut` is `false`
- `finalAnswer` is populated from the completed child turn when available

### Agent and prompt behavior

`SpringAiCodexAgent` now drains mailbox mail alongside steering input at the top of each planner step.

Each drained message is emitted as a `MailboxMessageItem`, and reconstruction renders it back into prompt context as assistant-role history with explicit provenance:

`MAILBOX <sender-thread-id>: <message>`

That gives the parent model the child result as ordinary thread context without pretending it was direct user input.

### CLI behavior

The CLI now renders mailbox deliveries as dedicated streamed items:

- `[mailbox] child-completion ...`

Low-level `agent/mailbox/updated` transport notifications remain non-user-facing and stay out of the normal interactive transcript.

## Tests

This increment adds focused coverage for:

- protocol JSON round-trips for mailbox item/history types
- runtime delivery to an active parent turn
- runtime delivery to an idle parent thread on the next turn
- `waitAgent(...)` returning the child final answer
- agent emission of `MailboxMessageItem` from drained turn-control mail
- reconstruction of mailbox deliveries into replay and prompt-visible history
- CLI rendering of mailbox items

## Non-Goals

This change does not yet attempt to clone every upstream MultiAgentV2 timing detail.

Still out of scope for this increment:

- reopening a completed parent answer boundary the way upstream can in some cases
- removing the current `agent/*` app-server bridge
- internal system-owned agent patterns such as guardian/reviewer flows
