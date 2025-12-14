package org.mozilla.javascript.cli;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.tools.Console;
import org.mozilla.javascript.tools.ConsoleProvider;

import java.io.IOException;

public class JLineConsoleProvider implements ConsoleProvider {
    private final Terminal terminal;

    public JLineConsoleProvider() {
        Terminal t;
        try {
            t = TerminalBuilder.builder().system(true).dumb(true).build();
        } catch (IOException ioe) {
            t = null;
        }
        terminal = t;
    }

    @Override
    public Console newConsole(Scriptable scope) {
        assert terminal != null;
        return new JLineConsole(terminal, scope);
    }

    @Override
    public boolean isSupported() {
        return (terminal != null && !Terminal.TYPE_DUMB.equals(terminal.getType()) &&
                    !Terminal.TYPE_DUMB_COLOR.equals(terminal.getType()));
    }

    @Override
    public boolean isInteractive() {
        return true;
    }

    @Override
    public boolean supportsEditing() {
        return true;
    }
}
