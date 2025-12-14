package org.mozilla.javascript.tools.shell;

import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.tools.Console;
import org.mozilla.javascript.tools.ConsoleProvider;

public class BasicConsoleProvider implements ConsoleProvider {
    @Override
    public Console newConsole(Scriptable scope) {
        return new BasicConsole();
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public boolean isInteractive() {
        return true;
    }

    @Override
    public boolean supportsEditing() {
        return false;
    }
}
