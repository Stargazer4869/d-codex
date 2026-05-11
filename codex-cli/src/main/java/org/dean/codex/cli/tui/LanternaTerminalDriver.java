package org.dean.codex.cli.tui;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.screen.Screen.RefreshType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.MouseCaptureMode;

import java.io.IOException;
import java.time.Duration;

public final class LanternaTerminalDriver implements TerminalDriver {

    private final Screen screen;
    private final TuiRenderer renderer;
    private TerminalSize lastSize;

    public LanternaTerminalDriver() throws IOException {
        this(createScreen(), new TuiRenderer());
    }

    LanternaTerminalDriver(Screen screen, TuiRenderer renderer) throws IOException {
        this.screen = screen;
        this.renderer = renderer == null ? new TuiRenderer() : renderer;
        this.screen.startScreen();
        this.screen.doResizeIfNecessary();
        this.lastSize = this.screen.getTerminalSize();
        this.screen.clear();
        this.screen.refresh(RefreshType.COMPLETE);
    }

    @Override
    public void draw(TuiAppState state) throws IOException {
        TerminalSize resized = screen.doResizeIfNecessary();
        TerminalSize currentSize = screen.getTerminalSize();
        if (resized != null || lastSize == null || !lastSize.equals(currentSize)) {
            screen.clear();
            lastSize = currentSize;
        }
        renderer.draw(screen, state);
    }

    @Override
    public KeyStroke pollInput(Duration timeout) throws IOException {
        long timeoutMillis = Math.max(1L, timeout == null ? 50L : timeout.toMillis());
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        KeyStroke keyStroke;
        while ((keyStroke = screen.pollInput()) == null && System.nanoTime() < deadline) {
            try {
                Thread.sleep(Math.min(25L, timeoutMillis));
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return keyStroke;
    }

    @Override
    public void close() throws IOException {
        screen.stopScreen();
    }

    private static Screen createScreen() throws IOException {
        DefaultTerminalFactory terminalFactory = new DefaultTerminalFactory();
        if (mouseCaptureEnabled()) {
            terminalFactory.setMouseCaptureMode(MouseCaptureMode.CLICK_RELEASE_DRAG_MOVE);
        }
        return terminalFactory.createScreen();
    }

    private static boolean mouseCaptureEnabled() {
        String value = System.getenv("CODEX_TUI_MOUSE_CAPTURE");
        if (value == null || value.isBlank()) {
            value = System.getenv("CODEX_TUI_MOUSE");
        }
        if (value == null) {
            return false;
        }
        return switch (value.trim().toLowerCase()) {
            case "1", "true", "yes", "on" -> true;
            default -> false;
        };
    }
}
