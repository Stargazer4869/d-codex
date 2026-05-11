package org.dean.codex.cli.tui;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.Screen.RefreshType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TuiRenderer {

    private static final int MIN_WIDTH = 20;

    public void draw(Screen screen, TuiAppState state) throws IOException {
        TerminalSize size = screen.getTerminalSize();
        List<String> lines = renderLines(state, size.getColumns(), size.getRows());
        TextGraphics graphics = screen.newTextGraphics();
        for (int row = 0; row < lines.size() && row < size.getRows(); row++) {
            drawLine(graphics, row, lines.get(row), size.getColumns(), rowRole(row, lines.size(), state));
        }
        screen.setCursorPosition(cursorPosition(state, size));
        screen.refresh(RefreshType.DELTA);
    }

    public List<String> renderLines(TuiAppState state, int width, int height) {
        int actualWidth = Math.max(MIN_WIDTH, width);
        int actualHeight = Math.max(8, height);
        List<String> lines = new ArrayList<>();
        lines.add(fit(header(state), actualWidth));

        int footerRows = 3;
        int overlayRows = state.overlayOpen() ? Math.min(8, Math.max(4, actualHeight / 3)) : 0;
        int transcriptRows = Math.max(1, actualHeight - 1 - footerRows - overlayRows);
        lines.addAll(transcriptLines(state, actualWidth, transcriptRows));
        if (overlayRows > 0) {
            lines.addAll(overlayLines(state.overlay(), actualWidth, overlayRows));
        }
        lines.add(fit(statusLine(state), actualWidth));
        lines.add(fit("> " + state.composer(), actualWidth));
        lines.add(fit(helpLine(state), actualWidth));

        while (lines.size() < actualHeight) {
            lines.add("");
        }
        if (lines.size() > actualHeight) {
            return lines.subList(0, actualHeight);
        }
        return lines;
    }

    private List<String> transcriptLines(TuiAppState state, int width, int rows) {
        List<String> rendered = new ArrayList<>();
        for (TranscriptCell cell : state.transcript()) {
            rendered.add(fit("[" + cell.title() + "]", width));
            List<String> body = wrap(cell.body(), Math.max(1, width - 2));
            if (body.isEmpty()) {
                rendered.add("");
            }
            for (String line : body) {
                rendered.add(fit("  " + line, width));
            }
        }
        if (rendered.isEmpty()) {
            rendered.add("Start typing, or press / for commands.");
        }
        int end = rendered.size() - Math.min(state.transcriptScroll(), Math.max(0, rendered.size() - 1));
        int start = Math.max(0, end - rows);
        List<String> visible = new ArrayList<>(rendered.subList(start, end));
        while (visible.size() < rows) {
            visible.add(0, "");
        }
        return visible;
    }

    private List<String> overlayLines(PickerOverlay overlay, int width, int rows) {
        List<String> lines = new ArrayList<>();
        String filter = overlay.filter().isBlank() ? "" : " filter=" + overlay.filter();
        lines.add(fit("+ " + overlay.title() + filter, width));
        List<PickerItem> items = overlay.filteredItems();
        int maxItems = Math.max(1, rows - 2);
        int selected = overlay.selectedIndex();
        int start = Math.max(0, Math.min(selected - maxItems + 1, Math.max(0, items.size() - maxItems)));
        for (int i = 0; i < maxItems; i++) {
            int itemIndex = start + i;
            if (itemIndex >= items.size()) {
                lines.add("");
                continue;
            }
            PickerItem item = items.get(itemIndex);
            String marker = itemIndex == selected ? "> " : "  ";
            String disabled = item.disabled() ? " (disabled)" : "";
            String detail = item.detail().isBlank() ? "" : " - " + item.detail();
            lines.add(fit(marker + item.label() + disabled + detail, width));
        }
        lines.add(fit(overlay.hint(), width));
        while (lines.size() < rows) {
            lines.add("");
        }
        return lines;
    }

    private String header(TuiAppState state) {
        String thread = state.activeThread() == null
                ? "(no thread)"
                : shortId(state.activeThread().threadId() == null ? "" : state.activeThread().threadId().value())
                + " "
                + state.activeThread().title();
        String running = state.hasActiveTurn() ? "running" : "idle";
        return "Codex Java TUI | " + running + " | " + thread;
    }

    private String statusLine(TuiAppState state) {
        String model = state.config() == null || state.config().model() == null ? "model=unknown" : "model=" + state.config().model();
        String sandbox = state.config() == null || state.config().sandboxMode() == null ? "sandbox=default" : "sandbox=" + state.config().sandboxMode();
        String approval = state.config() == null || state.config().approvalMode() == null ? "approval=default" : "approval=" + state.config().approvalMode();
        return model + "  " + sandbox + "  " + approval + "  |  " + state.statusMessage();
    }

    private String helpLine(TuiAppState state) {
        if (state.overlayOpen()) {
            if (state.overlay().kind() == PickerOverlay.Kind.APPROVALS) {
                return "Enter approve  r reject  Esc close  Up/Down move";
            }
            return "Enter select  Esc close  Up/Down move  type to filter";
        }
        if (state.hasActiveTurn()) {
            return "Enter steer  Ctrl-C interrupt  / commands  PgUp/PgDn scroll";
        }
        return "Enter send  Ctrl-C quit  / commands  PgUp/PgDn scroll";
    }

    private TerminalPosition cursorPosition(TuiAppState state, TerminalSize size) {
        int row = Math.max(0, size.getRows() - 2);
        int col = Math.min(size.getColumns() - 1, 2 + state.composer().length());
        return new TerminalPosition(Math.max(0, col), row);
    }

    private void drawLine(TextGraphics graphics, int row, String line, int width, RowRole role) {
        graphics.setForegroundColor(color(role));
        if (role == RowRole.HEADER || role == RowRole.STATUS || role == RowRole.HELP) {
            graphics.enableModifiers(SGR.BOLD);
        }
        else {
            graphics.disableModifiers(SGR.BOLD);
        }
        graphics.putString(0, row, fit(line, width));
    }

    private RowRole rowRole(int row, int totalRows, TuiAppState state) {
        if (row == 0) {
            return RowRole.HEADER;
        }
        if (row >= totalRows - 3) {
            return row == totalRows - 3 ? RowRole.STATUS : RowRole.HELP;
        }
        if (state.overlayOpen() && row >= totalRows - 11) {
            return RowRole.OVERLAY;
        }
        return RowRole.TRANSCRIPT;
    }

    private TextColor color(RowRole role) {
        return switch (role) {
            case HEADER -> TextColor.ANSI.CYAN;
            case STATUS -> TextColor.ANSI.YELLOW;
            case HELP -> TextColor.ANSI.GREEN;
            case OVERLAY -> TextColor.ANSI.WHITE;
            case TRANSCRIPT -> TextColor.ANSI.DEFAULT;
        };
    }

    private List<String> wrap(String text, int width) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        List<String> lines = new ArrayList<>();
        for (String rawLine : text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            String line = rawLine.stripTrailing();
            while (line.length() > width) {
                int split = line.lastIndexOf(' ', width);
                if (split <= 0) {
                    split = width;
                }
                lines.add(line.substring(0, split).stripTrailing());
                line = line.substring(split).stripLeading();
            }
            lines.add(line);
        }
        return lines;
    }

    private String fit(String value, int width) {
        String normalized = value == null ? "" : value;
        if (normalized.length() > width) {
            return normalized.substring(0, Math.max(0, width - 1)) + "~";
        }
        return normalized + " ".repeat(Math.max(0, width - normalized.length()));
    }

    private String shortId(String value) {
        if (value == null || value.isBlank()) {
            return "(unknown)";
        }
        return value.length() <= 8 ? value : value.substring(0, 8);
    }

    private enum RowRole {
        HEADER,
        TRANSCRIPT,
        OVERLAY,
        STATUS,
        HELP
    }
}
