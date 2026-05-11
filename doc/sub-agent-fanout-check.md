# Sub-agent fanout check

## Combined summary

The remaining gaps cluster into three areas. First, sub-agent coordination still needs stronger parent-thread parity: child completion should be delivered and recorded in the parent thread as first-class mailbox/history data, and spawned sub-agents still need fuller lifecycle and realtime orchestration parity across notifications and parent-child state transitions.

Second, the backend is still mid-migration to a native Responses implementation. The runtime still depends on a chat-backed Spring AI path rather than a true OpenAI Responses transport, and it still needs the supporting backend work for request/response mapping, streaming event translation, tool-call item handling, and compact-model support. Beyond transport, the runtime also does not yet fully model the Responses-native shape Codex expects, including typed input/output items, structured tool-call/result items, multi-item streaming output, reasoning controls/summaries, and interactive turn updates.

Third, the acceptance walkthrough and CLI UX still leave usability gaps. The walkthrough assumes users already know the slash-command surface and does not provide a compact command cheat sheet for the steps it asks them to perform. It also focuses on verification without showing enough expected console output for key transitions such as active turns, steering, approvals, and thread actions, making it harder to distinguish correct behavior from merely different behavior.
