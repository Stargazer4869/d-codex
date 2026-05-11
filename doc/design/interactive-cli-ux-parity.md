# Codex-Style Terminal UI Rewrite

## Goal

Replace the Java interactive line console with a full-screen terminal UI that is closer to upstream Codex:

- a persistent transcript pane
- a bottom composer
- `/` command palette
- reusable selection overlays for agents, sessions, models, skills, and approvals
- footer/status line for active thread, model, sandbox, approval mode, and turn state
- readable structured rendering for user, assistant, reasoning, tool, approval, plan, mailbox, skill, and error items

The upstream reference is `/Users/chenzhu/Git/codex/codex-rs/tui`, especially:

- `src/app.rs`
- `src/bottom_pane/chat_composer.rs`
- `src/bottom_pane/command_popup.rs`
- `src/bottom_pane/list_selection_view.rs`
- `src/bottom_pane/footer.rs`
- `src/app/agent_navigation.rs`

## Current Implementation

The Java CLI now has two interactive surfaces:

1. **Lanterna TUI**, used by default when the process has a real interactive terminal.
2. **Legacy line console**, retained for redirected stdin, tests, and scripted usage.

The TUI is implemented in `codex-cli/src/main/java/org/dean/codex/cli/tui`:

- `CodexTuiRunner` owns the app-server subscription and event loop.
- `TuiAppState` stores active thread, turn, transcript, composer, config, and overlay state.
- `TuiRenderer` renders state into a Lanterna screen and has a pure `renderLines` path for tests.
- `PickerOverlay` and `PickerItem` provide the reusable picker model.
- `LanternaTerminalDriver` adapts Lanterna `Screen` input and rendering.

`CodexConsoleRunner` still owns launch parsing and non-interactive commands. Interactive launches delegate to `CodexTuiRunner` when `codex.cli.tui.enabled=true` and a terminal is available. `CODEX_TUI_FORCE=true` forces the TUI path.

Lanterna mouse capture is disabled by default so the host terminal keeps native
double-click, triple-click, drag selection, and copy behavior. Users who prefer
mouse wheel scroll events inside the TUI can opt in with
`CODEX_TUI_MOUSE_CAPTURE=true` or `CODEX_TUI_MOUSE=true`, accepting that native
terminal text selection may be unavailable while mouse capture is enabled.

## Protocol Additions

The app-server protocol now includes the minimal UI data needed by the TUI:

- `model/list` returns selectable `ModelOption` rows.
- `config/get` returns model, provider, sandbox mode, approval mode, cwd, and feature flags.
- `config/update` updates active thread/session config fields and returns the refreshed config plus thread summary.

Existing thread, turn, skills, agent, and approval services remain the source of truth for their domains.

## User Interaction

The first TUI milestone supports:

- `Enter`: submit composer input or select overlay item
- `/`: open slash command palette
- `Esc`: close overlay or clear composer
- `Ctrl-C`: interrupt active turn; quit when idle
- `PageUp` / `PageDown`: scroll transcript
- overlay `Up` / `Down`: move selection
- approval overlay `a` / `Enter`: approve selected request
- approval overlay `r`: reject selected request

Slash commands still accept textual forms in the composer. Key commands include:

- `/agent`
- `/agent use <thread-id-prefix>`
- `/resume [thread-id-prefix]`
- `/model`
- `/skills`
- `/approvals`
- `/new`
- `/history`
- `/compact`
- `/interrupt`
- `/help`

## Non-Goals For This Milestone

This milestone does not implement full upstream parity for MCP views, plugin management, hooks browser, feedback UI, file mention search, image rendering, full markdown/diff fidelity, or external editor integration. Mouse wheel scrolling is available only when Lanterna mouse capture is explicitly enabled.

## Acceptance

- interactive terminal launches open the Lanterna TUI
- redirected stdin and tests can still use the line console
- `/` opens a command palette
- `/agent`, `/resume`, `/model`, `/skills`, and `/approvals` open selection overlays
- model/config picker data flows through app-server session APIs
- transcript and footer update from app-server notifications
- focused CLI tests pass with `mvn -pl codex-cli -am -Dtest=AgentPickerModelTest,SlashCommandParserTest,CodexConsoleRunnerTest,TuiRendererTest -Dsurefire.failIfNoSpecifiedTests=false test`
