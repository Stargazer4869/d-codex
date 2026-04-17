# Documentation Index

This directory holds the working design and architecture documents for the Java Codex rebuild.

## Available Documents

- [`roadmap.md`](roadmap.md) - prioritized next-step roadmap for the Java Codex rebuild
- [`design/phase-1-java-codex-modularization.md`](design/phase-1-java-codex-modularization.md) - phase-1 modularization design and scope
- [`design/non-blocking-cli-interaction.md`](design/non-blocking-cli-interaction.md) - design for Codex-style non-blocking CLI input, steering, and streamed turn interaction
- [`design/compaction-parity.md`](design/compaction-parity.md) - tracker for Codex-style compaction parity work
- [`design/thread-subagent-parity.md`](design/thread-subagent-parity.md) - tracker for thread lifecycle and sub-agent parity work
- [`design/natural-language-sub-agent-delegation.md`](design/natural-language-sub-agent-delegation.md) - design for natural-language delegation into sub-agents and collaboration-item parity
- [`design/cli-command-parity.md`](design/cli-command-parity.md) - tracker for top-level CLI parsing and interactive slash-command parity work
- [`design/prompt-layering-parity.md`](design/prompt-layering-parity.md) - design for splitting the Java planner prompt into base instructions, developer/tool guidance, and dynamic turn context
- [`design/responses-api-parity.md`](design/responses-api-parity.md) - tracker for bringing the Java runtime closer to the Responses API feature set that upstream Codex actually uses
- [`design/unified-exec-streaming-parity.md`](design/unified-exec-streaming-parity.md) - design for upstream-style long-running command sessions, output streaming, and model polling
- [`design/thread-metadata-and-realtime-parity.md`](design/thread-metadata-and-realtime-parity.md) - design for the next thread-management phase: richer metadata/index parity and stronger realtime thread-runtime behavior
- [`design/tool-surface-parallel-and-output-reduction.md`](design/tool-surface-parallel-and-output-reduction.md) - design for modest built-in tool enrichment, safe parallel tool execution, and non-LLM reduction of large tool outputs
- [`major-feature-acceptance-walkthrough.md`](major-feature-acceptance-walkthrough.md) - manual walkthrough for the currently implemented CLI, thread, sub-agent, approval, compaction, and app-server spot-check features
- [`architecture/module-map.md`](architecture/module-map.md) - module responsibilities and dependency direction
- [`adr/README.md`](adr/README.md) - placeholder for future architecture decision records
