package org.mozilla.javascript.tools;

public interface ConsoleProvider {
    Console newConsole();

    boolean isSupported();

    boolean isInteractive();

    boolean supportsEditing();
}
