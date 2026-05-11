package org.dean.codex.cli.tui;

import com.googlecode.lanterna.input.KeyStroke;

import java.io.IOException;
import java.time.Duration;

public interface TerminalDriver extends AutoCloseable {

    void draw(TuiAppState state) throws IOException;

    KeyStroke pollInput(Duration timeout) throws IOException;

    @Override
    void close() throws IOException;
}
