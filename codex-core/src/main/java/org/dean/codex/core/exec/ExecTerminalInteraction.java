package org.dean.codex.core.exec;

public record ExecTerminalInteraction(String kind,
                                      Integer inputLength,
                                      Integer columns,
                                      Integer rows) {

    public ExecTerminalInteraction {
        kind = kind == null ? "" : kind;
    }
}
