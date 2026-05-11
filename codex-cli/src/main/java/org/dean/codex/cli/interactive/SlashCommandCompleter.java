package org.dean.codex.cli.interactive;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class SlashCommandCompleter implements Completer {
    private final SlashCommandRegistry registry;

    public SlashCommandCompleter(SlashCommandRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        if (line == null || candidates == null) {
            return;
        }
        String input = line.line() == null ? "" : line.line();
        int cursor = Math.max(0, Math.min(line.cursor(), input.length()));
        int commandStart = firstNonWhitespace(input);
        if (commandStart < 0 || input.charAt(commandStart) != '/') {
            return;
        }
        int tokenStart = commandStart + 1;
        int tokenEnd = tokenStart;
        while (tokenEnd < input.length() && !Character.isWhitespace(input.charAt(tokenEnd))) {
            tokenEnd++;
        }
        if (cursor > tokenEnd) {
            return;
        }

        String typed = input.substring(tokenStart, Math.max(tokenStart, cursor)).toLowerCase(Locale.ROOT);
        Set<String> added = new LinkedHashSet<>();
        for (SlashCommandSpec command : registry.commands()) {
            if (!matches(command, typed)) {
                continue;
            }
            String value = "/" + command.name();
            if (added.add(value)) {
                candidates.add(new Candidate(
                        value,
                        command.syntax(),
                        null,
                        command.description(),
                        null,
                        null,
                        true));
            }
        }
    }

    private boolean matches(SlashCommandSpec command, String typed) {
        if (typed.isEmpty() || command.name().startsWith(typed)) {
            return true;
        }
        for (String alias : command.aliases()) {
            if (alias.startsWith(typed)) {
                return true;
            }
        }
        return false;
    }

    private int firstNonWhitespace(String input) {
        for (int index = 0; index < input.length(); index++) {
            if (!Character.isWhitespace(input.charAt(index))) {
                return index;
            }
        }
        return -1;
    }
}
