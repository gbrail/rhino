package org.mozilla.javascript.tools;

import org.mozilla.javascript.Scriptable;

public interface ConsoleProvider {
    Console newConsole(Scriptable scope);

    boolean isSupported();

    boolean isInteractive();

    boolean supportsEditing();
}
