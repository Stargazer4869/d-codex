package org.dean.codex.cli.interactive;

import org.jline.keymap.KeyMap;
import org.jline.reader.Binding;
import org.jline.reader.Buffer;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.Reference;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public final class JLineConsoleSupport implements AutoCloseable {
    private static final String SLASH_COMPLETE_WIDGET = "codex-slash-complete";
    private final Terminal terminal;
    private final LineReader lineReader;
    private final ConsoleAgentPicker agentPicker;

    private JLineConsoleSupport(Terminal terminal, LineReader lineReader) {
        this.terminal = terminal;
        this.lineReader = lineReader;
        this.agentPicker = new ConsoleAgentPicker(terminal);
    }

    public static boolean richInputPossible() {
        return System.console() != null
                && !isDisabled(System.getProperty("codex.cli.richInput.disabled"))
                && !isDisabled(System.getenv("CODEX_CLI_RICH_INPUT_DISABLED"));
    }

    public static JLineConsoleSupport open(SlashCommandRegistry registry) throws IOException {
        Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .build();
        LineReader lineReader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(new SlashCommandCompleter(registry))
                .variable(LineReader.LIST_MAX, 100)
                .build();
        installSlashCompleteWidget(lineReader);
        return new JLineConsoleSupport(terminal, lineReader);
    }

    public String readLine() {
        try {
            return lineReader.readLine("> ");
        }
        catch (UserInterruptException exception) {
            return "/interrupt";
        }
        catch (EndOfFileException exception) {
            return null;
        }
    }

    public Optional<AgentPickerEntry> chooseAgent(List<AgentPickerEntry> entries) throws IOException {
        return agentPicker.choose(entries);
    }

    @Override
    public void close() throws IOException {
        terminal.close();
    }

    private static void installSlashCompleteWidget(LineReader lineReader) {
        lineReader.getWidgets().put(SLASH_COMPLETE_WIDGET, () -> {
            Buffer buffer = lineReader.getBuffer();
            if (buffer.cursor() == 0 && buffer.length() == 0) {
                buffer.write("/");
                lineReader.callWidget(LineReader.COMPLETE_WORD);
                return true;
            }
            buffer.write("/");
            return true;
        });
        KeyMap<Binding> main = lineReader.getKeyMaps().get(LineReader.MAIN);
        if (main != null) {
            main.bind(new Reference(SLASH_COMPLETE_WIDGET), "/");
        }
    }

    private static boolean isDisabled(String value) {
        return value != null && ("true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim()));
    }
}
